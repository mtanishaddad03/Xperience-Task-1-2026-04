package com.xperience.hero.api;

import com.xperience.hero.event.EventCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The gate's open path (U1). It never resolves, reads or reveals an existing event: it creates one and answers
 * the same way whatever happens next, because the management link travels only by email (D9).
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class OpenController {

	private final EventCreationService eventCreation;

	@PostMapping
	public ResponseEntity<Map<String, String>> create(@RequestBody EventCreationService.NewEvent request) {
		eventCreation.create(request);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(Map.of("message", "Check your email for the management link."));
	}
}
