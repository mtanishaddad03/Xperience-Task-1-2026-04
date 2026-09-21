package com.xperience.hero.reply;

import java.util.function.BooleanSupplier;

/**
 * The reply transition table (DESIGN.md → Step 10), in one place. Pure: no database access.
 * <p>
 * Promotion (Waitlisted → Confirmed) is not here: it is never caused by the guest's own reply, only by another
 * guest releasing a place.
 */
public final class ReplyTransitions {

	/** {@code target == null} means no change: the state and its entered-state time stay as they are. */
	public record Transition(ReplyState target, boolean releasesPlace) {

		static final Transition UNCHANGED = new Transition(null, false);

		public boolean isNoOp() {
			return target == null;
		}
	}

	private ReplyTransitions() {
	}

	/**
	 * @param current   the guest's current state; {@code null} = Pending (no Reply yet)
	 * @param placeFree the capacity decision; consulted only for a Yes that needs one
	 */
	public static Transition decide(ReplyState current, ReplyChoice choice, BooleanSupplier placeFree) {
		if (current == null) {
			return switch (choice) {
				case YES -> to(placeFree.getAsBoolean() ? ReplyState.CONFIRMED : ReplyState.WAITLISTED);
				case NO -> to(ReplyState.DECLINED);
				case MAYBE -> to(ReplyState.MAYBE);
			};
		}
		return switch (current) {
			case CONFIRMED -> switch (choice) {
				case YES -> Transition.UNCHANGED;
				case NO -> release(ReplyState.DECLINED);  // F4
				case MAYBE -> release(ReplyState.MAYBE);  // D2: Maybe releases a place too
			};
			case WAITLISTED -> switch (choice) {
				case YES -> Transition.UNCHANGED;          // INV-B6: position kept
				case NO -> to(ReplyState.DECLINED);        // A7: releases nothing
				case MAYBE -> to(ReplyState.MAYBE);
			};
			case DECLINED -> switch (choice) {
				case YES -> to(placeFree.getAsBoolean() ? ReplyState.CONFIRMED : ReplyState.WAITLISTED);
				case NO -> Transition.UNCHANGED;
				case MAYBE -> to(ReplyState.MAYBE);
			};
			case MAYBE -> switch (choice) {
				case YES -> to(placeFree.getAsBoolean() ? ReplyState.CONFIRMED : ReplyState.WAITLISTED);
				case NO -> to(ReplyState.DECLINED);
				case MAYBE -> Transition.UNCHANGED;
			};
		};
	}

	private static Transition to(ReplyState target) {
		return new Transition(target, false);
	}

	private static Transition release(ReplyState target) {
		return new Transition(target, true);
	}
}
