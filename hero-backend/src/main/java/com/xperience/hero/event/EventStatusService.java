package com.xperience.hero.event;

import com.xperience.hero.common.DatabaseClock;
import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.guest.Guest;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.OutboundMessage;
import com.xperience.hero.outbox.OutboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The status unit (U6): close and cancel. Part of Event Management; no endpoint until Stage 2.
 * <p>
 * Order: lock the event → state guard → write status → on cancel, record cancellation notices → commit.
 * Nothing is sent here.
 */
@Service
@RequiredArgsConstructor
public class EventStatusService {

	private final EventRepository events;
	private final OutboundMessageRepository messages;
	private final DatabaseClock clock;

	/** Close blocks every reply change, including declines (D11). Only an Open event can be closed. */
	@Transactional
	public StatusChange close(long eventId) {
		Event event = lock(eventId);
		switch (event.getStatus()) {
			case OPEN -> {
				event.setStatus(EventStatus.CLOSED);
				return StatusChange.CHANGED;
			}
			case CLOSED -> {
				return StatusChange.ALREADY_IN_STATE;
			}
			default -> {
				return StatusChange.NOT_ALLOWED;
			}
		}
	}

	/**
	 * Open or Closed → Cancelled (D7). In the same transaction, records a cancellation notice for every guest whose
	 * invitation was sent (D10, INV-B12).
	 */
	@Transactional
	public StatusChange cancel(long eventId) {
		Event event = lock(eventId);
		if (event.getStatus() == EventStatus.CANCELLED) {
			return StatusChange.ALREADY_IN_STATE;
		}
		event.setStatus(EventStatus.CANCELLED);
		Instant now = clock.now();
		for (Guest guest : messages.guestsWithSentInvitation(eventId)) {
			messages.save(OutboundMessage.queued(MessageKind.CANCELLATION_NOTICE, event, guest, now));
		}
		return StatusChange.CHANGED;
	}

	// Must be the first load of the event in the transaction; see ReplyEngine.submit.
	private Event lock(long eventId) {
		return events.lockById(eventId).orElseThrow(NotFoundException::new);
	}
}
