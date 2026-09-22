package com.xperience.hero.api;

import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.event.EventErrors;
import com.xperience.hero.gate.LinkInvalidException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** One place where every refusal is written, so the link refusal cannot accidentally differ between paths. */
@RestControllerAdvice
public class ApiErrorHandler {

	/**
	 * The single refusal (INV-A4): unknown link, wrong kind, missing, malformed — and anything the gate never
	 * resolved. A guest or event that does not exist answers the same way, so nothing is revealed by elimination.
	 */
	@ExceptionHandler({LinkInvalidException.class, NotFoundException.class, NoResourceFoundException.class})
	public ResponseEntity<Map<String, Object>> linkInvalid() {
		return respond(HttpStatus.NOT_FOUND, "LINK_INVALID", "This link is not valid.", Map.of());
	}

	@ExceptionHandler(EventErrors.ValidationException.class)
	public ResponseEntity<Map<String, Object>> validation(EventErrors.ValidationException e) {
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION", "Some details are missing or not usable.",
				Map.of("fields", e.getFields()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> unreadable() {
		return respond(HttpStatus.BAD_REQUEST, "VALIDATION", "The request could not be read.",
				Map.of("fields", Map.of()));
	}

	@ExceptionHandler(EventErrors.InvalidAddressesException.class)
	public ResponseEntity<Map<String, Object>> invalidAddresses(EventErrors.InvalidAddressesException e) {
		return respond(HttpStatus.BAD_REQUEST, "INVALID_ADDRESSES",
				"No invitation was sent: these addresses cannot be used.", Map.of("invalid", e.getInvalid()));
	}

	@ExceptionHandler(EventErrors.HostNotVerifiedException.class)
	public ResponseEntity<Map<String, Object>> hostNotVerified() {
		return respond(HttpStatus.FORBIDDEN, "HOST_NOT_VERIFIED",
				"Confirm your email address from your management link before inviting anyone.", Map.of());
	}

	@ExceptionHandler(EventErrors.EventLockedException.class)
	public ResponseEntity<Map<String, Object>> eventLocked(EventErrors.EventLockedException e) {
		String message = switch (e.getReason()) {
			case CANCELLED -> "This event has been cancelled.";
			case CLOSED -> "This event is closed to further replies.";
			case STARTED -> "This event has already started.";
		};
		return respond(HttpStatus.CONFLICT, "EVENT_LOCKED", message, Map.of("reason", e.getReason().name()));
	}

	@ExceptionHandler(EventErrors.InvitationLimitException.class)
	public ResponseEntity<Map<String, Object>> invitationLimit(EventErrors.InvitationLimitException e) {
		return respond(HttpStatus.TOO_MANY_REQUESTS, "INVITATION_LIMIT",
				"No invitation was sent. This address may send %d invitations a day, and has used %d."
						.formatted(e.getLimit(), e.getUsedInWindow()),
				Map.of("limit", e.getLimit(), "usedInWindow", e.getUsedInWindow(), "requested", e.getRequested()));
	}

	@ExceptionHandler(EventErrors.ManagementLinkLimitException.class)
	public ResponseEntity<Map<String, Object>> managementLinkLimit(EventErrors.ManagementLinkLimitException e) {
		return respond(HttpStatus.TOO_MANY_REQUESTS, "MANAGEMENT_LINK_LIMIT",
				"Too many events have been created for this email address today. Try again tomorrow.",
				Map.of("limit", e.getLimit()));
	}

	@ExceptionHandler(EventErrors.ResendNotAllowedException.class)
	public ResponseEntity<Map<String, Object>> resendNotAllowed() {
		return respond(HttpStatus.CONFLICT, "RESEND_NOT_ALLOWED",
				"This invitation is still being sent. Wait until it is sent or has failed.", Map.of());
	}

	@ExceptionHandler(EventErrors.GuestNotFoundException.class)
	public ResponseEntity<Map<String, Object>> guestNotFound() {
		return respond(HttpStatus.NOT_FOUND, "GUEST_NOT_FOUND", "Nobody with that address was invited.", Map.of());
	}

	private static ResponseEntity<Map<String, Object>> respond(HttpStatus status, String error, String message,
			Map<String, Object> extra) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		body.putAll(extra);
		return ResponseEntity.status(status)
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(body);
	}
}
