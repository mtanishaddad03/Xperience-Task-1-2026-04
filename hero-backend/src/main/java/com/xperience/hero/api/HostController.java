package com.xperience.hero.api;

import com.xperience.hero.event.EventErrors;
import com.xperience.hero.event.EventStatusService;
import com.xperience.hero.event.HostViewService;
import com.xperience.hero.event.InvitationService;
import com.xperience.hero.event.StatusChange;
import com.xperience.hero.gate.HostAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Everything the holder of a management link may do. The event is always the one the link resolved to. */
@RestController
@RequestMapping("/api/host")
@RequiredArgsConstructor
public class HostController {

	public record InviteRequest(List<String> emails) {
	}

	public record ResendRequest(String email) {
	}

	private final HostViewService hostView;
	private final InvitationService invitations;
	private final EventStatusService eventStatus;

	@GetMapping("/event")
	public HostViewService.HostView event(HostAccess access) {
		return hostView.view(access.eventId());
	}

	@PostMapping("/verify")
	public Map<String, Boolean> verify(HostAccess access) {
		return Map.of("hostVerified", hostView.verifyHost(access.eventId()));
	}

	@PostMapping("/invitations")
	public InvitationService.InviteResult invite(HostAccess access, @RequestBody InviteRequest request) {
		if (request == null || request.emails() == null || request.emails().isEmpty()) {
			throw new EventErrors.ValidationException(Map.of("emails", "is required"));
		}
		return invitations.invite(access.eventId(), request.emails());
	}

	@PostMapping("/invitations/resend")
	public ResponseEntity<Map<String, Boolean>> resend(HostAccess access, @RequestBody ResendRequest request) {
		if (request == null || request.email() == null || request.email().isBlank()) {
			throw new EventErrors.ValidationException(Map.of("email", "is required"));
		}
		invitations.resend(access.eventId(), request.email());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("queued", true));
	}

	@PostMapping("/close")
	public ResponseEntity<?> close(HostAccess access) {
		return statusResponse(eventStatus.close(access.eventId()), "CLOSED");
	}

	@PostMapping("/cancel")
	public ResponseEntity<?> cancel(HostAccess access) {
		return statusResponse(eventStatus.cancel(access.eventId()), "CANCELLED");
	}

	private ResponseEntity<?> statusResponse(StatusChange change, String status) {
		if (change == StatusChange.NOT_ALLOWED) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
					"error", "STATUS_NOT_ALLOWED",
					"message", "A cancelled event cannot change again."));
		}
		return ResponseEntity.ok(Map.of("status", status, "changed", change == StatusChange.CHANGED));
	}
}
