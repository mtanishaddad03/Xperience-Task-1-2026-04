package com.xperience.hero;

import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventRepository;
import com.xperience.hero.guest.Guest;
import com.xperience.hero.guest.GuestRepository;
import com.xperience.hero.outbox.OutboundMessage;
import com.xperience.hero.outbox.OutboundMessageRepository;
import com.xperience.hero.reply.Reply;
import com.xperience.hero.reply.ReplyChoice;
import com.xperience.hero.reply.ReplyEngine;
import com.xperience.hero.reply.ReplyRepository;
import com.xperience.hero.reply.ReplyResult;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Base for tests against real PostgreSQL, schema {@code hero_test}. Every table is emptied before each test.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

	@Autowired
	protected JdbcTemplate jdbc;
	@Autowired
	protected TransactionTemplate tx;
	@Autowired
	protected ReplyEngine replyEngine;
	@Autowired
	protected EventRepository events;
	@Autowired
	protected GuestRepository guests;
	@Autowired
	protected ReplyRepository replies;
	@Autowired
	protected OutboundMessageRepository messages;

	@BeforeEach
	void emptyTables() {
		jdbc.execute("truncate hero_test.outbound_message, hero_test.reply, hero_test.guest, hero_test.event restart identity cascade");
	}

	protected Instant dbNow() {
		return jdbc.queryForObject("select clock_timestamp()", OffsetDateTime.class).toInstant();
	}

	/** An Open event starting in one day. */
	protected long newEvent(Integer capacity) {
		return newEvent(capacity, dbNow().plus(Duration.ofDays(1)));
	}

	protected long newEvent(Integer capacity, Instant startTime) {
		return events.save(new Event("Party", "A party", "The garden", startTime, capacity, "host@example.com")).getId();
	}

	protected List<Long> newGuests(long eventId, int count) {
		return tx.execute(status -> {
			Event event = events.getReferenceById(eventId);
			List<Long> ids = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				ids.add(guests.save(new Guest(event, "guest" + i + "@example.com")).getId());
			}
			return ids;
		});
	}

	protected ReplyResult reply(long eventId, long guestId, ReplyChoice choice) {
		return replyEngine.submit(eventId, guestId, choice);
	}

	protected Optional<Reply> replyOf(long guestId) {
		return replies.findByGuestId(guestId);
	}

	protected List<Long> waitlistGuestIds(long eventId) {
		return replies.waitlistInOrder(eventId).stream().map(r -> r.getGuest().getId()).toList();
	}

	protected List<OutboundMessage> messagesOf(long eventId) {
		return messages.findByEventIdOrderById(eventId);
	}

	/** Inserts an invitation message directly, in a given status — the drain that normally writes status is Stage 2–3. */
	protected void insertInvitation(long eventId, long guestId, String status) {
		jdbc.update("insert into hero_test.outbound_message "
				+ "(event_id, guest_id, kind, status, created_at, attempts, next_attempt_at) "
				+ "values (?, ?, 'INVITATION', ?, clock_timestamp(), 1, clock_timestamp())", eventId, guestId, status);
	}

	/** Number of sessions currently blocked waiting for a lock. */
	protected int sessionsWaitingForLock() {
		return jdbc.queryForObject("select count(*) from pg_stat_activity "
				+ "where datname = current_database() and wait_event_type = 'Lock'", Integer.class);
	}
}
