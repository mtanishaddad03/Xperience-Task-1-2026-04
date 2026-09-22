package com.xperience.hero.event;

import java.time.Instant;

/**
 * "Locked" is a rule, not a state (KD4): the status is not Open, or the database clock has reached the start time.
 * One definition, used by the reply unit, by invitations and by both views.
 */
public final class EventLock {

	public enum LockReason {
		CANCELLED, CLOSED, STARTED
	}

	private EventLock() {
	}

	/** @param now must come from the database, and inside the reply unit only after the lock (KD8, RC-1) */
	public static LockReason reasonFor(Event event, Instant now) {
		if (event.getStatus() == EventStatus.CANCELLED) {
			return LockReason.CANCELLED;
		}
		if (event.getStatus() == EventStatus.CLOSED) {
			return LockReason.CLOSED;
		}
		if (!now.isBefore(event.getStartTime())) {
			return LockReason.STARTED;
		}
		return null;
	}
}
