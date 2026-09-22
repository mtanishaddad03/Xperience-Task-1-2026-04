package com.xperience.hero.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The one scheduled task in the application (KD3). Tests run the drain directly instead. */
@Component
@ConditionalOnProperty(name = "hero.drain.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DrainScheduler {

	private final OutboxDrain drain;

	@Scheduled(fixedDelayString = "${hero.drain.interval}")
	public void drainOutbox() {
		try {
			drain.runOnce();
		}
		catch (RuntimeException e) {
			// A failing pass must not stop the schedule: the backlog is what makes the failure visible (O1).
			log.error("outbox drain pass failed", e);
		}
	}
}
