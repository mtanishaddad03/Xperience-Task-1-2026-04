package com.xperience.hero;

import com.xperience.hero.guest.Guest;
import com.xperience.hero.reply.ReplyChoice;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Constraints are created once and never changed (T2), so their shape is checked here.
 */
class SchemaTest extends IntegrationTest {

	@Test
	void guestEmail_isNormalised_andUniquePerEvent() {
		long eventId = newEvent(null);
		tx.executeWithoutResult(s -> guests.save(new Guest(events.getReferenceById(eventId), "  Dana@X.com ")));

		assertThat(jdbc.queryForObject("select email from hero_test.guest", String.class)).isEqualTo("dana@x.com");
		assertThatThrownBy(() -> tx.executeWithoutResult(s ->
				guests.save(new Guest(events.getReferenceById(eventId), "dana@x.com"))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameEmail_mayBeInvitedToTwoEvents() {
		long a = newEvent(null);
		long b = newEvent(null);
		tx.executeWithoutResult(s -> {
			guests.save(new Guest(events.getReferenceById(a), "dana@x.com"));
			guests.save(new Guest(events.getReferenceById(b), "dana@x.com"));
		});
		assertThat(jdbc.queryForObject("select count(*) from hero_test.guest", Integer.class)).isEqualTo(2);
	}

	@Test
	void aGuestHasAtMostOneReply() {
		long eventId = newEvent(null);
		long guestId = newGuests(eventId, 1).get(0);
		reply(eventId, guestId, ReplyChoice.YES);

		assertThatThrownBy(() -> jdbc.update("insert into hero_test.reply (guest_id, state, entered_state_at) "
				+ "values (?, 'MAYBE', clock_timestamp())", guestId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enumColumns_haveNoCheckConstraints() {
		// A CHECK (x in (...)) would be frozen by ddl-auto: update, so a new state or kind could never be stored.
		List<String> checks = jdbc.queryForList("""
				select pg_get_constraintdef(c.oid) from pg_constraint c
				join pg_namespace n on n.oid = c.connamespace
				where n.nspname = 'hero_test' and c.contype = 'c'
				""", String.class);
		assertThat(checks).noneMatch(def -> def.contains("ANY") || def.contains(" IN "));
	}

	@Test
	void everyStoredTimeIsAnAbsoluteInstant() {
		// A local timestamp could not be compared with the database clock, which is what the lock rule does.
		List<String> localColumns = jdbc.queryForList("""
				select table_name || '.' || column_name from information_schema.columns
				where table_schema = 'hero_test' and data_type like 'timestamp%'
				  and data_type <> 'timestamp with time zone'
				""", String.class);
		assertThat(localColumns).isEmpty();
		assertThat(jdbc.queryForObject("""
				select count(*) from information_schema.columns
				where table_schema = 'hero_test' and data_type = 'timestamp with time zone'
				""", Integer.class)).isGreaterThanOrEqualTo(3);
	}
}
