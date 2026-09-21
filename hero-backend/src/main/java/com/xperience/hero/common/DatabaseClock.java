package com.xperience.hero.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * The only source of "now" for decisions (KD8).
 * <p>
 * Uses {@code clock_timestamp()}, the actual current time. {@code now()} and {@code CURRENT_TIMESTAMP} return the
 * time the transaction began, which is stale after waiting for the event lock (RC-1). Call only after the lock.
 */
@Component
@RequiredArgsConstructor
public class DatabaseClock {

	private final JdbcTemplate jdbc;

	public Instant now() {
		return jdbc.queryForObject("select clock_timestamp()", OffsetDateTime.class).toInstant();
	}
}
