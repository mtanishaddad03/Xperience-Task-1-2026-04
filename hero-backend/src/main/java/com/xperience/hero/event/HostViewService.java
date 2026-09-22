package com.xperience.hero.event;

import com.xperience.hero.common.DatabaseClock;
import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.guest.Guest;
import com.xperience.hero.guest.GuestRepository;
import com.xperience.hero.outbox.DrainControl;
import com.xperience.hero.outbox.DrainControlRepository;
import com.xperience.hero.outbox.MessageStatus;
import com.xperience.hero.outbox.OutboundMessageRepository;
import com.xperience.hero.reply.AttendanceCounts;
import com.xperience.hero.reply.Reply;
import com.xperience.hero.reply.ReplyRepository;
import com.xperience.hero.reply.ReplyState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** U5: the live picture the host consults. Every number is derived when asked (KD5, KD12). */
@Service
@RequiredArgsConstructor
public class HostViewService {

	public record EventSummary(String title, String description, String location, Instant startTime, Integer capacity,
			EventStatus status, boolean hostVerified, boolean repliesOpen, EventLock.LockReason lockedReason) {
	}

	public record Counts(long confirmed, long waitlisted, long maybe, long declined, long pending,
			Integer placesRemaining) {
	}

	public record GuestRow(String email, String state, Integer waitlistPosition, MessageStatus invitation) {
	}

	public record Outbox(long queued, long sending, long failed, boolean paused) {
	}

	public record HostView(EventSummary event, Counts counts, List<GuestRow> guests, Outbox outbox) {
	}

	private final EventRepository events;
	private final GuestRepository guests;
	private final ReplyRepository replies;
	private final OutboundMessageRepository messages;
	private final AttendanceCounts counts;
	private final DrainControlRepository drainControl;
	private final DatabaseClock clock;

	@Transactional(readOnly = true)
	public HostView view(long eventId) {
		Event event = events.findById(eventId).orElseThrow(NotFoundException::new);
		Instant now = clock.now();
		EventLock.LockReason locked = EventLock.reasonFor(event, now);

		long confirmed = counts.count(eventId, ReplyState.CONFIRMED);
		long waitlisted = counts.count(eventId, ReplyState.WAITLISTED);
		long maybe = counts.count(eventId, ReplyState.MAYBE);
		long declined = counts.count(eventId, ReplyState.DECLINED);
		long pending = guests.countByEventId(eventId) - (confirmed + waitlisted + maybe + declined);
		Integer placesRemaining = event.getCapacity() == null
				? null : Math.max(0, event.getCapacity() - (int) confirmed);

		Map<Long, ReplyState> states = new HashMap<>();
		for (Reply reply : replies.findByEventId(eventId)) {
			states.put(reply.getGuest().getId(), reply.getState());
		}
		Map<Long, Integer> positions = new HashMap<>();
		List<Reply> waitlist = replies.waitlistInOrder(eventId);
		for (int i = 0; i < waitlist.size(); i++) {
			positions.put(waitlist.get(i).getGuest().getId(), i + 1);
		}
		Map<Long, MessageStatus> invitations = new HashMap<>();
		for (Object[] row : messages.invitationStatuses(eventId)) {
			invitations.put((Long) row[0], MessageStatus.valueOf((String) row[1])); // ordered by id: the latest wins
		}

		List<GuestRow> rows = new ArrayList<>();
		for (Guest guest : guests.findByEventIdOrderByEmail(eventId)) {
			ReplyState state = states.get(guest.getId());
			rows.add(new GuestRow(guest.getEmail(), state == null ? "PENDING" : state.name(),
					positions.get(guest.getId()), invitations.get(guest.getId())));
		}

		Map<MessageStatus, Long> backlog = new HashMap<>();
		for (Object[] row : messages.statusCounts(eventId)) {
			backlog.put(MessageStatus.valueOf((String) row[0]), (Long) row[1]);
		}
		boolean paused = drainControl.findById(DrainControl.ROW_ID).map(DrainControl::isPaused).orElse(false);

		return new HostView(
				new EventSummary(event.getTitle(), event.getDescription(), event.getLocation(), event.getStartTime(),
						event.getCapacity(), event.getStatus(), event.isHostVerified(), locked == null, locked),
				new Counts(confirmed, waitlisted, maybe, declined, pending, placesRemaining),
				rows,
				new Outbox(backlog.getOrDefault(MessageStatus.QUEUED, 0L),
						backlog.getOrDefault(MessageStatus.SENDING, 0L),
						backlog.getOrDefault(MessageStatus.FAILED, 0L), paused));
	}

	/** U8: verification needs this explicit call; a page load never reaches it (E6). */
	@Transactional
	public boolean verifyHost(long eventId) {
		Event event = events.findById(eventId).orElseThrow(NotFoundException::new);
		event.verifyHost();
		return true;
	}
}
