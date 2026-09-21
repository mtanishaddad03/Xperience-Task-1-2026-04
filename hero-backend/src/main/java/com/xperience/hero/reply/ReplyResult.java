package com.xperience.hero.reply;

import java.time.Instant;

/**
 * Outcome of one reply unit.
 *
 * @param state          the guest's state after the unit (accepted only)
 * @param enteredStateAt when the guest entered that state (accepted only)
 * @param decidedAt      the database clock read right after the event lock was acquired
 * @param refusal        why it was refused (refused only)
 */
public record ReplyResult(boolean accepted, ReplyState state, Instant enteredStateAt, Instant decidedAt,
		ReplyRefusal refusal) {

	static ReplyResult accepted(Reply reply, Instant decidedAt) {
		return new ReplyResult(true, reply.getState(), reply.getEnteredStateAt(), decidedAt, null);
	}

	static ReplyResult refused(ReplyRefusal refusal, Instant decidedAt) {
		return new ReplyResult(false, null, null, decidedAt, refusal);
	}
}
