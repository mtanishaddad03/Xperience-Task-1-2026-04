package com.xperience.hero.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Everything the drain and the limits are tuned with (DESIGN.md → Step 12, D14, D15). */
@ConfigurationProperties("hero")
public record HeroProperties(String publicBaseUrl, Drain drain, Limits limits) {

	public record Drain(boolean enabled, Duration interval, int batchSize, Duration sendTimeout,
			Duration reclaimAfter, List<Duration> retryDelays, int sendThreads) {

		public Drain {
			if (reclaimAfter.compareTo(sendTimeout) <= 0) {
				// A message still being sent would be reclaimed and sent a second time.
				throw new IllegalArgumentException("hero.drain.reclaim-after must be longer than send-timeout");
			}
			if (batchSize < 1 || sendThreads < 1) {
				throw new IllegalArgumentException("hero.drain.batch-size and send-threads must be at least 1");
			}
		}
	}

	public record Limits(int invitationsPerHostPerDay, int managementLinksPerAddressPerDay) {
	}

	/** The first attempt plus one per retry delay (D15). */
	public int maxAttempts() {
		return drain.retryDelays().size() + 1;
	}
}
