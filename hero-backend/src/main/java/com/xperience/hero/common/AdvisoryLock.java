package com.xperience.hero.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A transaction-scoped PostgreSQL advisory lock, used to count and check a per-address limit atomically (D14).
 * It is not the per-event lock and never replaces it.
 */
@Component
@RequiredArgsConstructor
public class AdvisoryLock {

	private final JdbcTemplate jdbc;

	/** Held until the current transaction ends. Two transactions with the same key cannot overlap. */
	public void takeFor(String key) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("an advisory lock outside a transaction would be released at once");
		}
		jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
				ps -> ps.setString(1, key), rs -> null);
	}
}
