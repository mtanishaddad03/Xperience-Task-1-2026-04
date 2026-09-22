package com.xperience.hero.api;

import com.xperience.hero.event.EventErrors;
import com.xperience.hero.gate.GuestAccess;
import com.xperience.hero.reply.GuestViewService;
import com.xperience.hero.reply.ReplyChoice;
import com.xperience.hero.reply.ReplyEngine;
import com.xperience.hero.reply.ReplyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The guest surface. A GET only displays; only this PUT reaches the reply unit, so a mail scanner opening the
 * link never replies on the guest's behalf (E6).
 */
@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestController {

	public record ReplyRequest(String choice) {
	}

	private final GuestViewService guestView;
	private final ReplyEngine replyEngine;

	@GetMapping
	public GuestViewService.GuestView view(GuestAccess access) {
		return guestView.view(access.eventId(), access.guestId());
	}

	@PutMapping("/reply")
	public ResponseEntity<?> reply(GuestAccess access, @RequestBody ReplyRequest request) {
		ReplyChoice choice = parse(request);
		ReplyResult result = replyEngine.submit(access.eventId(), access.guestId(), choice);
		if (!result.accepted()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
					"error", "REPLY_LOCKED",
					"reason", result.refusal().name(),
					"message", messageFor(result)));
		}
		return ResponseEntity.ok(Map.of("state", result.state().name()));
	}

	private static ReplyChoice parse(ReplyRequest request) {
		try {
			return ReplyChoice.valueOf(request.choice());
		}
		catch (RuntimeException e) {
			throw new EventErrors.ValidationException(Map.of("choice", "must be YES, NO or MAYBE"));
		}
	}

	private static String messageFor(ReplyResult result) {
		return switch (result.refusal()) {
			case CANCELLED -> "This event has been cancelled.";
			case CLOSED -> "The host has closed this event to further replies.";
			case STARTED -> "This event has already started, so replies are closed.";
		};
	}
}
