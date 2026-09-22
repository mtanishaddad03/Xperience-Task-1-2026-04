package com.xperience.hero.event;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/** The refusals Event Management can give once a link has resolved. Each maps to one API response. */
public final class EventErrors {

	private EventErrors() {
	}

	/** A request that cannot be accepted as written; the fields say what is wrong. */
	@Getter
	public static class ValidationException extends RuntimeException {

		private final Map<String, String> fields;

		public ValidationException(Map<String, String> fields) {
			super("validation failed");
			this.fields = fields;
		}
	}

	/** A batch containing an address that is not an address — the whole batch is refused (D5). */
	@Getter
	public static class InvalidAddressesException extends RuntimeException {

		private final List<String> invalid;

		public InvalidAddressesException(List<String> invalid) {
			super("invalid addresses");
			this.invalid = invalid;
		}
	}

	/** No invitation before the host has verified their address (INV-A6, D9). */
	public static class HostNotVerifiedException extends RuntimeException {
	}

	/** The event is Closed, Cancelled or has started, so there is nothing an invitation could lead to. */
	@Getter
	public static class EventLockedException extends RuntimeException {

		private final EventLock.LockReason reason;

		public EventLockedException(EventLock.LockReason reason) {
			this.reason = reason;
		}
	}

	/** The per-host invitation limit (D14). The batch is refused whole. */
	@Getter
	public static class InvitationLimitException extends RuntimeException {

		private final int limit;
		private final long usedInWindow;
		private final int requested;

		public InvitationLimitException(int limit, long usedInWindow, int requested) {
			this.limit = limit;
			this.usedInWindow = usedInWindow;
			this.requested = requested;
		}
	}

	/** The per-address management-link cap (D14). Nothing is created. */
	@Getter
	public static class ManagementLinkLimitException extends RuntimeException {

		private final int limit;

		public ManagementLinkLimitException(int limit) {
			this.limit = limit;
		}
	}

	/** Resend is for an invitation that is sent or failed, never one the drain still has in hand (U10). */
	public static class ResendNotAllowedException extends RuntimeException {
	}

	/** An address the host named that is not a guest of this event. Safe to say: the host sees the list anyway. */
	public static class GuestNotFoundException extends RuntimeException {
	}
}
