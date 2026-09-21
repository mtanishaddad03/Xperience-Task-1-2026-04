package com.xperience.hero.reply;

import com.xperience.hero.event.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Counts are derived, never stored (KD5). One definition, shared by the capacity decision and the host view (KD12). */
@Component
@RequiredArgsConstructor
public class AttendanceCounts {

	private final ReplyRepository replies;

	public long count(long eventId, ReplyState state) {
		return replies.countInState(eventId, state);
	}

	/** The capacity decision (S1). With no capacity there is nothing to decide: a place is always free (A3). */
	public boolean placeFree(Event event) {
		return event.getCapacity() == null || count(event.getId(), ReplyState.CONFIRMED) < event.getCapacity();
	}
}
