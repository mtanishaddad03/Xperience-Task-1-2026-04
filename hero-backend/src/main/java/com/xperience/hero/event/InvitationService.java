package com.xperience.hero.event;

import com.xperience.hero.common.AdvisoryLock;
import com.xperience.hero.common.DatabaseClock;
import com.xperience.hero.common.EmailAddress;
import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.guest.Guest;
import com.xperience.hero.guest.GuestRepository;
import com.xperience.hero.outbox.HeroProperties;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.MessageStatus;
import com.xperience.hero.outbox.OutboundMessage;
import com.xperience.hero.outbox.OutboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * U2 and U10. Creates guests and queues their invitations; nothing is sent here.
 * <p>
 * This unit takes no per-event lock: an invite racing a cancel can queue messages for an event cancelled a moment
 * earlier, and those are skipped at send time (INV-B11). It does take a per-address advisory lock, so two batches
 * from one host cannot both pass the limit (D14).
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

	public record InviteResult(int invited, int alreadyInvited) {
	}

	private final EventRepository events;
	private final GuestRepository guests;
	private final OutboundMessageRepository messages;
	private final AdvisoryLock advisoryLock;
	private final DatabaseClock clock;
	private final HeroProperties properties;

	@Transactional
	public InviteResult invite(long eventId, List<String> emails) {
		Instant now = clock.now();
		Event event = openEventForInviting(eventId, now);

		List<String> invalid = emails.stream().filter(email -> !EmailAddress.isValid(email)).toList();
		if (!invalid.isEmpty()) {
			throw new EventErrors.InvalidAddressesException(invalid);
		}

		Set<String> candidates = new LinkedHashSet<>(emails.stream().map(EmailAddress::normalise).toList());
		Set<String> existing = Set.copyOf(guests.findEmailsByEventId(eventId));
		List<String> toInvite = candidates.stream().filter(email -> !existing.contains(email)).toList();
		int alreadyInvited = emails.size() - toInvite.size();

		checkInvitationLimit(event, toInvite.size(), now);

		List<Guest> created = new ArrayList<>();
		for (String email : toInvite) {
			created.add(guests.save(new Guest(event, email)));
		}
		for (Guest guest : created) {
			messages.save(OutboundMessage.queued(MessageKind.INVITATION, event, guest, now));
		}
		return new InviteResult(created.size(), alreadyInvited);
	}

	/** U10: a new message, never a reset of an old one, so the drain stays the only writer of message status. */
	@Transactional
	public void resend(long eventId, String email) {
		Instant now = clock.now();
		Event event = openEventForInviting(eventId, now);
		Guest guest = guests.findByEventIdAndEmail(eventId, EmailAddress.normalise(email))
				.orElseThrow(EventErrors.GuestNotFoundException::new);

		MessageStatus latest = messages.latestInvitationFor(guest.getId())
				.map(OutboundMessage::getStatus)
				.orElseThrow(EventErrors.ResendNotAllowedException::new);
		if (latest != MessageStatus.SENT && latest != MessageStatus.FAILED) {
			throw new EventErrors.ResendNotAllowedException();
		}

		checkInvitationLimit(event, 1, now);
		messages.save(OutboundMessage.queued(MessageKind.INVITATION, event, guest, now));
	}

	private Event openEventForInviting(long eventId, Instant now) {
		Event event = events.findById(eventId).orElseThrow(NotFoundException::new);
		if (!event.isHostVerified()) {
			throw new EventErrors.HostNotVerifiedException();
		}
		EventLock.LockReason locked = EventLock.reasonFor(event, now);
		if (locked != null) {
			throw new EventErrors.EventLockedException(locked);
		}
		return event;
	}

	/** D14, counted per verified host address across that host's events, under the advisory lock. */
	private void checkInvitationLimit(Event event, int requested, Instant now) {
		advisoryLock.takeFor("invitations:" + event.getHostEmail());
		int limit = properties.limits().invitationsPerHostPerDay();
		long used = messages.countInvitationsSince(event.getHostEmail(), now.minus(Duration.ofHours(24)));
		if (used + requested > limit) {
			throw new EventErrors.InvitationLimitException(limit, used, requested);
		}
	}
}
