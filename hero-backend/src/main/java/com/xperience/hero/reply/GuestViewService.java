package com.xperience.hero.reply;

import com.xperience.hero.common.DatabaseClock;
import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventLock;
import com.xperience.hero.event.EventRepository;
import com.xperience.hero.event.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * U7: what a guest sees. The minimum until Q6 is answered — the event, and their own standing. No waitlist
 * position, no totals, nothing about any other guest (INV-A5).
 */
@Service
@RequiredArgsConstructor
public class GuestViewService {

	public record EventSummary(String title, String description, String location, Instant startTime,
			EventStatus status, boolean repliesOpen, EventLock.LockReason lockedReason) {
	}

	public record OwnReply(String state) {
	}

	public record GuestView(EventSummary event, OwnReply reply) {
	}

	private final EventRepository events;
	private final ReplyRepository replies;
	private final DatabaseClock clock;

	@Transactional(readOnly = true)
	public GuestView view(long eventId, long guestId) {
		Event event = events.findById(eventId).orElseThrow(NotFoundException::new);
		EventLock.LockReason locked = EventLock.reasonFor(event, clock.now());
		String state = replies.findByGuestId(guestId).map(reply -> reply.getState().name()).orElse("PENDING");
		return new GuestView(
				new EventSummary(event.getTitle(), event.getDescription(), event.getLocation(), event.getStartTime(),
						event.getStatus(), locked == null, locked),
				new OwnReply(state));
	}
}
