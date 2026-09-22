package com.xperience.hero.event;

import com.xperience.hero.common.AdvisoryLock;
import com.xperience.hero.common.DatabaseClock;
import com.xperience.hero.common.EmailAddress;
import com.xperience.hero.outbox.HeroProperties;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.OutboundMessage;
import com.xperience.hero.outbox.OutboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** U1: create an event and queue its management-link message. No link is returned; it travels by email (D9). */
@Service
@RequiredArgsConstructor
public class EventCreationService {

	public record NewEvent(String title, String description, String location, String startTime, Integer capacity,
			String hostEmail) {
	}

	private final EventRepository events;
	private final OutboundMessageRepository messages;
	private final AdvisoryLock advisoryLock;
	private final DatabaseClock clock;
	private final HeroProperties properties;

	@Transactional
	public void create(NewEvent request) {
		Instant now = clock.now();
		Instant startTime = validate(request, now);
		String hostEmail = EmailAddress.normalise(request.hostEmail());

		// U1 has no credential: without this cap, anyone could make the system email any address (D14).
		advisoryLock.takeFor("management-link:" + hostEmail);
		int cap = properties.limits().managementLinksPerAddressPerDay();
		if (messages.countManagementLinksSince(hostEmail, now.minus(Duration.ofHours(24))) >= cap) {
			throw new EventErrors.ManagementLinkLimitException(cap);
		}

		Event event = events.save(new Event(request.title().strip(), request.description().strip(),
				request.location().strip(), startTime, request.capacity(), hostEmail));
		messages.save(OutboundMessage.queued(MessageKind.MANAGEMENT_LINK, event, null, now));
	}

	private Instant validate(NewEvent request, Instant now) {
		Map<String, String> fields = new LinkedHashMap<>();
		requireText(fields, "title", request.title());
		requireText(fields, "description", request.description());
		requireText(fields, "location", request.location());
		if (!EmailAddress.isValid(request.hostEmail())) {
			fields.put("hostEmail", "must be an email address");
		}
		if (request.capacity() != null && request.capacity() < 1) {
			fields.put("capacity", "must be at least 1, or absent for no capacity");
		}
		Instant startTime = null;
		if (request.startTime() == null || request.startTime().isBlank()) {
			fields.put("startTime", "is required");
		}
		else {
			try {
				// An absolute instant, converted by the browser from the host's own time zone (D16).
				startTime = Instant.parse(request.startTime());
				if (!startTime.isAfter(now)) {
					fields.put("startTime", "must be in the future"); // INV-B9
				}
			}
			catch (RuntimeException e) {
				fields.put("startTime", "must be an instant such as 2026-10-03T16:00:00Z");
			}
		}
		if (!fields.isEmpty()) {
			throw new EventErrors.ValidationException(fields);
		}
		return startTime;
	}

	private static void requireText(Map<String, String> fields, String name, String value) {
		if (value == null || value.isBlank()) {
			fields.put(name, "is required");
		}
	}
}
