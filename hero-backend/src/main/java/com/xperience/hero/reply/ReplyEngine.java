package com.xperience.hero.reply;

import com.xperience.hero.common.DatabaseClock;
import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventRepository;
import com.xperience.hero.event.EventStatus;
import com.xperience.hero.guest.Guest;
import com.xperience.hero.guest.GuestRepository;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.OutboundMessage;
import com.xperience.hero.outbox.OutboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The Reply Engine (U3, U4): the only component that makes a capacity decision or promotes (KD1).
 */
@Service
@RequiredArgsConstructor
public class ReplyEngine {

	private final EventRepository events;
	private final GuestRepository guests;
	private final ReplyRepository replies;
	private final OutboundMessageRepository messages;
	private final AttendanceCounts counts;
	private final DatabaseClock clock;

	/**
	 * The reply unit, in one transaction (DESIGN.md → Step 12):
	 * lock event → read clock → lock check → capacity decision or release → promotion if a place was released →
	 * write reply(s) → record promotion notice → commit. Nothing is sent here.
	 *
	 * @throws NotFoundException if the event does not exist or the guest is not one of its guests
	 */
	@Transactional
	public ReplyResult submit(long eventId, long guestId, ReplyChoice choice) {
		// The event lock must be the first load of the event in this transaction. If the event were already in the
		// persistence context, Hibernate would still take the row lock but return the cached copy without re-reading
		// it — a status or capacity from before the lock, which is exactly the stale read the lock exists to prevent.
		Event event = events.lockById(eventId).orElseThrow(NotFoundException::new);
		// "Now" is read after the lock, never at transaction start (KD8, RC-1).
		Instant now = clock.now();

		Guest guest = guests.findInEvent(eventId, guestId).orElseThrow(NotFoundException::new);

		ReplyRefusal refusal = lockCheck(event, now);
		if (refusal != null) {
			return ReplyResult.refused(refusal, now);
		}

		Reply reply = replies.findByGuestId(guestId).orElse(null);
		ReplyTransitions.Transition transition = ReplyTransitions.decide(
				reply == null ? null : reply.getState(), choice, () -> counts.placeFree(event));

		if (transition.isNoOp()) {
			return ReplyResult.accepted(reply, now);
		}
		if (transition.releasesPlace()) {
			promoteEarliest(event, now);
		}
		if (reply == null) {
			reply = replies.save(new Reply(guest, transition.target(), now));
		}
		else {
			reply.moveTo(transition.target(), now);
		}
		return ReplyResult.accepted(reply, now);
	}

	/** Locked = not Open, or the database clock has reached the start time (S3, INV-B7). A rule, not a state (KD4). */
	private static ReplyRefusal lockCheck(Event event, Instant now) {
		if (event.getStatus() == EventStatus.CANCELLED) {
			return ReplyRefusal.CANCELLED;
		}
		if (event.getStatus() == EventStatus.CLOSED) {
			return ReplyRefusal.CLOSED;
		}
		if (!now.isBefore(event.getStartTime())) {
			return ReplyRefusal.STARTED;
		}
		return null;
	}

	/**
	 * Promotion (S2): one released place promotes exactly one guest, the earliest waitlisted (INV-B3), and records
	 * their notice in the same transaction (INV-B10). With no capacity there is never a waitlist (INV-B2).
	 */
	private void promoteEarliest(Event event, Instant now) {
		if (event.getCapacity() == null) {
			return;
		}
		List<Reply> waitlist = replies.waitlistInOrder(event.getId());
		if (waitlist.isEmpty()) {
			return;
		}
		Reply promoted = waitlist.get(0);
		promoted.moveTo(ReplyState.CONFIRMED, now);
		messages.save(OutboundMessage.queued(MessageKind.PROMOTION_NOTICE, event, promoted.getGuest(), now));
	}
}
