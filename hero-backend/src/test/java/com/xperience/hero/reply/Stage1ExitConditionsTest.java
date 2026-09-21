package com.xperience.hero.reply;

import com.xperience.hero.Concurrently;
import com.xperience.hero.IntegrationTest;
import com.xperience.hero.event.EventStatusService;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.MessageStatus;
import com.xperience.hero.outbox.OutboundMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** One test per Stage 1 exit condition in DESIGN.md → Rollout and Migration Notes. */
class Stage1ExitConditionsTest extends IntegrationTest {

	@Autowired
	EventStatusService eventStatus;

	@RepeatedTest(10)
	@DisplayName("X1: capacity 1, twenty simultaneous Yes → one Confirmed, nineteen Waitlisted in admission order")
	void x1_lastPlace() throws Exception {
		long eventId = newEvent(1);
		List<Long> guestIds = newGuests(eventId, 20);

		List<Callable<ReplyResult>> tasks = new ArrayList<>();
		for (long guestId : guestIds) {
			tasks.add(() -> reply(eventId, guestId, ReplyChoice.YES));
		}
		List<ReplyResult> results = Concurrently.run(tasks);

		assertThat(results).allMatch(ReplyResult::accepted);
		assertThat(results).filteredOn(r -> r.state() == ReplyState.CONFIRMED).hasSize(1);
		assertThat(results).filteredOn(r -> r.state() == ReplyState.WAITLISTED).hasSize(19);

		// Admission order = the order in which each unit acquired the event lock (its clock read after the lock).
		assertThat(new HashSet<>(results.stream().map(ReplyResult::decidedAt).toList())).hasSize(20);
		List<Integer> admitted = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			admitted.add(i);
		}
		admitted.sort(Comparator.comparing(i -> results.get(i).decidedAt()));

		assertThat(results.get(admitted.get(0)).state()).isEqualTo(ReplyState.CONFIRMED);
		List<Long> expectedWaitlist = admitted.subList(1, 20).stream().map(guestIds::get).toList();
		assertThat(waitlistGuestIds(eventId)).containsExactlyElementsOf(expectedWaitlist);
		assertThat(replies.countInState(eventId, ReplyState.CONFIRMED)).isEqualTo(1);
	}

	@RepeatedTest(10)
	@DisplayName("X2: two Confirmed leave at once with two Waitlisted → each promoted exactly once, each with one notice")
	void x2_simultaneousReleases() throws Exception {
		long eventId = newEvent(2);
		List<Long> g = newGuests(eventId, 4);
		long a = g.get(0), b = g.get(1), c = g.get(2), d = g.get(3);
		reply(eventId, a, ReplyChoice.YES);
		reply(eventId, b, ReplyChoice.YES);
		reply(eventId, c, ReplyChoice.YES);
		reply(eventId, d, ReplyChoice.YES);
		assertThat(waitlistGuestIds(eventId)).containsExactly(c, d);

		List<ReplyResult> results = Concurrently.run(List.of(
				() -> reply(eventId, a, ReplyChoice.NO),
				() -> reply(eventId, b, ReplyChoice.NO)));

		assertThat(results).allMatch(r -> r.accepted() && r.state() == ReplyState.DECLINED);
		assertThat(replyOf(c).orElseThrow().getState()).isEqualTo(ReplyState.CONFIRMED);
		assertThat(replyOf(d).orElseThrow().getState()).isEqualTo(ReplyState.CONFIRMED);
		assertThat(replies.countInState(eventId, ReplyState.CONFIRMED)).isEqualTo(2);
		assertThat(waitlistGuestIds(eventId)).isEmpty();

		List<OutboundMessage> notices = messagesOf(eventId);
		assertThat(notices).hasSize(2);
		assertThat(notices).allMatch(m -> m.getKind() == MessageKind.PROMOTION_NOTICE && m.getStatus() == MessageStatus.QUEUED);
		assertThat(notices.stream().map(m -> m.getGuest().getId())).containsExactlyInAnyOrder(c, d);
	}

	@Test
	@DisplayName("RC-1: a reply whose transaction begins before the start but acquires the lock after it → refused")
	void rc1_transactionStartedBeforeStart_lockAcquiredAfter() throws Exception {
		Instant start = dbNow().plus(Duration.ofSeconds(2));
		long eventId = newEvent(null, start);
		long guestId = newGuests(eventId, 1).get(0);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch lockHeld = new CountDownLatch(1);
			// Holds the event lock until the database clock has passed the start time.
			Future<?> holder = pool.submit(() -> tx.executeWithoutResult(s -> {
				events.lockById(eventId);
				lockHeld.countDown();
				while (!dbNow().isAfter(start.plusMillis(200))) {
					pause(50);
				}
			}));
			assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

			AtomicReference<Instant> transactionStart = new AtomicReference<>();
			Future<ReplyResult> replying = pool.submit(() -> tx.execute(s -> {
				// now() is the transaction's start time: the value RC-1 warns must not be used.
				transactionStart.set(jdbc.queryForObject("select now()", OffsetDateTime.class).toInstant());
				return reply(eventId, guestId, ReplyChoice.YES);
			}));

			ReplyResult result = replying.get(30, TimeUnit.SECONDS);
			holder.get(30, TimeUnit.SECONDS);

			assertThat(transactionStart.get()).isBefore(start);
			assertThat(result.accepted()).isFalse();
			assertThat(result.refusal()).isEqualTo(ReplyRefusal.STARTED);
			assertThat(replyOf(guestId)).isEmpty();
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("X4: replies waiting behind a cancel → none recorded after it")
	void x4_repliesQueuedBehindCancel() throws Exception {
		long eventId = newEvent(null);
		List<Long> guestIds = newGuests(eventId, 10);

		ExecutorService pool = Executors.newFixedThreadPool(11);
		try {
			CountDownLatch cancelled = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			// The cancel commits only after every reply is blocked on the event lock.
			Future<?> cancel = pool.submit(() -> tx.executeWithoutResult(s -> {
				eventStatus.cancel(eventId);
				cancelled.countDown();
				await(release);
			}));
			assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();

			List<Future<ReplyResult>> pending = new ArrayList<>();
			for (long guestId : guestIds) {
				pending.add(pool.submit(() -> reply(eventId, guestId, ReplyChoice.YES)));
			}
			waitUntil(() -> sessionsWaitingForLock() >= 10);
			release.countDown();
			cancel.get(30, TimeUnit.SECONDS);

			for (Future<ReplyResult> f : pending) {
				ReplyResult r = f.get(30, TimeUnit.SECONDS);
				assertThat(r.accepted()).isFalse();
				assertThat(r.refusal()).isEqualTo(ReplyRefusal.CANCELLED);
			}
			assertThat(guestIds).allMatch(id -> replyOf(id).isEmpty());
		}
		finally {
			pool.shutdownNow();
		}
	}

	@RepeatedTest(5)
	@DisplayName("X4: replies racing a cancel → each is entirely before it or refused")
	void x4_repliesRacingCancel() throws Exception {
		long eventId = newEvent(null);
		List<Long> guestIds = newGuests(eventId, 10);

		AtomicReference<Instant> cancelLockedAt = new AtomicReference<>();
		List<Callable<ReplyResult>> tasks = new ArrayList<>();
		for (long guestId : guestIds) {
			tasks.add(() -> reply(eventId, guestId, ReplyChoice.YES));
		}
		tasks.add(() -> tx.execute(s -> {
			eventStatus.cancel(eventId);
			cancelLockedAt.set(dbNow()); // still inside the cancel's lock
			return null;
		}));
		List<ReplyResult> results = Concurrently.run(tasks);

		for (int i = 0; i < guestIds.size(); i++) {
			ReplyResult r = results.get(i);
			if (r.accepted()) {
				assertThat(replyOf(guestIds.get(i)).orElseThrow().getEnteredStateAt()).isBefore(cancelLockedAt.get());
			}
			else {
				assertThat(r.refusal()).isEqualTo(ReplyRefusal.CANCELLED);
				assertThat(replyOf(guestIds.get(i))).isEmpty();
			}
		}
		assertThat(reply(eventId, guestIds.get(0), ReplyChoice.NO).refusal()).isEqualTo(ReplyRefusal.CANCELLED);
	}

	@Test
	@DisplayName("INV-B6: a Waitlisted guest repeating Yes → same position")
	void invB6_repeatedYesKeepsPosition() {
		long eventId = newEvent(1);
		List<Long> g = newGuests(eventId, 3);
		long a = g.get(0), b = g.get(1), c = g.get(2);
		reply(eventId, a, ReplyChoice.YES);
		reply(eventId, b, ReplyChoice.YES);
		reply(eventId, c, ReplyChoice.YES);
		Instant enteredBefore = replyOf(b).orElseThrow().getEnteredStateAt();

		ReplyResult again = reply(eventId, b, ReplyChoice.YES);

		assertThat(again.accepted()).isTrue();
		assertThat(again.state()).isEqualTo(ReplyState.WAITLISTED);
		assertThat(replyOf(b).orElseThrow().getEnteredStateAt()).isEqualTo(enteredBefore);
		assertThat(waitlistGuestIds(eventId)).containsExactly(b, c);
	}

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timed out");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static void waitUntil(java.util.function.BooleanSupplier condition) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("condition not reached");
			}
			pause(20);
		}
	}
}
