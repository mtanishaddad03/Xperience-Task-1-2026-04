package com.xperience.hero.api;

import com.xperience.hero.outbox.HeroProperties;
import com.xperience.hero.outbox.MessageKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class OutboxDrainTest extends ApiTest {

	@Autowired
	HeroProperties properties;

	private String statusOfInvitationTo(String email) {
		return jdbc.queryForObject("""
				select m.status from hero_test.outbound_message m
				join hero_test.guest g on g.id = m.guest_id
				where g.email = ? order by m.id desc limit 1
				""", String.class, email);
	}

	private int attemptsOfInvitationTo(String email) {
		return jdbc.queryForObject("""
				select m.attempts from hero_test.outbound_message m
				join hero_test.guest g on g.id = m.guest_id
				where g.email = ? order by m.id desc limit 1
				""", Integer.class, email);
	}

	@Test
	@DisplayName("Exit: drain paused → the backlog grows visibly and nothing is lost")
	void pausingTheDrainHoldsTheBacklog() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		jdbc.update("update hero_test.drain_control set paused = true");

		invite(host, "a@x.com", "b@x.com", "c@x.com");
		assertThat(drain.runOnce()).isZero();
		assertThat(mail.sentOfKind(MessageKind.INVITATION)).isEmpty();

		var outbox = body(hostView(host)).path("outbox");
		assertThat(outbox.path("queued").asInt()).isEqualTo(3);
		assertThat(outbox.path("paused").asBoolean()).isTrue();

		jdbc.update("update hero_test.drain_control set paused = false");
		drainUntilEmpty();

		assertThat(mail.sentOfKind(MessageKind.INVITATION)).hasSize(3);
		assertThat(body(hostView(host)).path("outbox").path("queued").asInt()).isZero();
	}

	@Test
	@DisplayName("Exit: one send hangs → it times out and the rest keep moving")
	void oneHangingSendDoesNotStopTheRest() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		mail.hangFor("hangs@x.com");
		invite(host, "a@x.com", "hangs@x.com", "b@x.com", "c@x.com");

		long startedAt = System.nanoTime();
		drainUntilEmpty();
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

		assertThat(mail.countTo("a@x.com")).isEqualTo(1);
		assertThat(mail.countTo("b@x.com")).isEqualTo(1);
		assertThat(mail.countTo("c@x.com")).isEqualTo(1);
		assertThat(mail.countTo("hangs@x.com")).isZero();
		// It was abandoned at the send timeout, and counted as a failed attempt.
		assertThat(statusOfInvitationTo("hangs@x.com")).isIn("QUEUED", "FAILED");
		assertThat(attemptsOfInvitationTo("hangs@x.com")).isPositive();
		assertThat(elapsed).isLessThan(Duration.ofSeconds(30));
	}

	@Test
	@DisplayName("Exit: application killed mid-backlog → sending resumes, at most one duplicate")
	void aCrashedSendIsReclaimedAndResentAtMostOnce() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		invite(host, "crashed@x.com", "other@x.com");
		drainUntilEmpty();
		assertThat(mail.countTo("crashed@x.com")).isEqualTo(1);

		// The state a crash leaves behind: handed to the provider, never marked.
		jdbc.update("""
				update hero_test.outbound_message set status = 'SENDING', claimed_at = clock_timestamp() - interval '1 hour'
				where id = (select min(id) from hero_test.outbound_message where kind = 'INVITATION')
				""");

		drainUntilEmpty();

		assertThat(mail.countTo("crashed@x.com")).isEqualTo(2); // exactly one duplicate
		assertThat(mail.countTo("other@x.com")).isEqualTo(1);
		assertThat(statusOfInvitationTo("crashed@x.com")).isEqualTo("SENT");
	}

	@Test
	void aCrashBeforeHandoffIsResentOnce() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		invite(host, "guest@x.com");
		jdbc.update("""
				update hero_test.outbound_message set status = 'SENDING', claimed_at = clock_timestamp() - interval '1 hour'
				where kind = 'INVITATION'
				""");

		drainUntilEmpty();

		assertThat(mail.countTo("guest@x.com")).isEqualTo(1);
		assertThat(statusOfInvitationTo("guest@x.com")).isEqualTo("SENT");
	}

	@Test
	@DisplayName("Reclaim never resurrects an exhausted message: it becomes failed, not queued")
	void reclaimOfAnExhaustedMessageMarksItFailed() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		invite(host, "guest@x.com");
		int maxAttempts = properties.drain().retryDelays().size() + 1;
		jdbc.update("""
				update hero_test.outbound_message
				set status = 'SENDING', claimed_at = clock_timestamp() - interval '1 hour', attempts = ?
				where kind = 'INVITATION'
				""", maxAttempts);

		drainUntilEmpty();

		assertThat(statusOfInvitationTo("guest@x.com")).isEqualTo("FAILED");
		assertThat(mail.countTo("guest@x.com")).isZero();
		assertThat(attemptsOfInvitationTo("guest@x.com")).isEqualTo(maxAttempts);
	}

	@Test
	@DisplayName("D15: a failed send is retried up to 3 times, with a delay, then marked failed")
	void failedSendsAreRetriedThenGiveUp() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		mail.failFor("guest@x.com");
		invite(host, "guest@x.com");

		drain.runOnce();
		assertThat(statusOfInvitationTo("guest@x.com")).isEqualTo("QUEUED");
		assertThat(attemptsOfInvitationTo("guest@x.com")).isEqualTo(1);

		// Not retried before its delay has passed.
		assertThat(drain.runOnce()).isZero();
		assertThat(attemptsOfInvitationTo("guest@x.com")).isEqualTo(1);

		int maxAttempts = properties.drain().retryDelays().size() + 1;
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (!statusOfInvitationTo("guest@x.com").equals("FAILED")) {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("never gave up: " + statusOfInvitationTo("guest@x.com"));
			}
			drain.runOnce();
		}
		assertThat(attemptsOfInvitationTo("guest@x.com")).isEqualTo(maxAttempts);
		assertThat(mail.countTo("guest@x.com")).isZero();
		assertThat(body(hostView(host)).path("outbox").path("failed").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("Exit: a failed invitation resent → the previous link stops working")
	void resendingReplacesTheLink() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		mail.failFor("guest@x.com");
		invite(host, "guest@x.com");
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (!statusOfInvitationTo("guest@x.com").equals("FAILED")) {
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("never failed");
			}
			drain.runOnce();
		}
		String hashOfFailedLink = jdbc.queryForObject(
				"select encode(token_hash, 'hex') from hero_test.guest_link", String.class);

		mail.stopFailingFor("guest@x.com");
		assertThat(resend(host, "guest@x.com").getResponse().getStatus()).isEqualTo(202);
		drainUntilEmpty();

		String newToken = tokenOf(mail.lastTo("guest@x.com"));
		assertThat(jdbc.queryForObject("select count(*) from hero_test.guest_link", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select encode(token_hash, 'hex') from hero_test.guest_link", String.class))
				.isNotEqualTo(hashOfFailedLink);
		assertThat(guestView(newToken).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void resendingASentInvitationStopsTheOldLinkWorking() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		String firstToken = inviteAndTakeToken(host, "guest@x.com");
		assertThat(guestView(firstToken).getResponse().getStatus()).isEqualTo(200);

		resend(host, "guest@x.com");
		drainUntilEmpty();
		String secondToken = tokenOf(mail.lastTo("guest@x.com"));

		assertThat(secondToken).isNotEqualTo(firstToken);
		assertThat(guestView(firstToken).getResponse().getStatus()).isEqualTo(404);
		assertThat(guestView(secondToken).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("INV-B11: a message no longer true when its turn comes is skipped")
	void messagesThatAreNoLongerTrueAreSkipped() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		invite(host, "guest@x.com");
		mvc.perform(withToken(post("/api/host/cancel"), host)).andReturn();

		drainUntilEmpty();

		assertThat(statusOfInvitationTo("guest@x.com")).isEqualTo("SKIPPED");
		assertThat(mail.countTo("guest@x.com")).isZero();
		// No cancellation notice exists: this guest's invitation had not been sent (D10).
		assertThat(jdbc.queryForObject("select count(*) from hero_test.outbound_message "
				+ "where kind = 'CANCELLATION_NOTICE'", Long.class)).isZero();
	}

	@Test
	void aPromotionNoticeForAGuestNoLongerConfirmedIsSkipped() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");
		replyAs(first, "YES");
		replyAs(second, "YES");
		replyAs(first, "NO"); // promotes second, and queues the notice
		replyAs(second, "NO"); // no longer confirmed before the notice is sent

		drainUntilEmpty();

		assertThat(jdbc.queryForObject("select status from hero_test.outbound_message "
				+ "where kind = 'PROMOTION_NOTICE'", String.class)).isEqualTo("SKIPPED");
		assertThat(mail.sentOfKind(MessageKind.PROMOTION_NOTICE)).isEmpty();
	}

	@Test
	void noticesCarryNoLink() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");
		replyAs(first, "YES");
		replyAs(second, "YES");
		replyAs(first, "NO");

		drainUntilEmpty();

		assertThat(mail.sentOfKind(MessageKind.PROMOTION_NOTICE)).singleElement()
				.matches(m -> m.recipient().equals("second@x.com") && m.link() == null);
		// The guest's invitation link still works (INV-B13).
		assertThat(guestView(second).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Each message is marked sending immediately before its own send, never the whole batch")
	void onlyTheMessageBeingSentIsMarkedSending() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		for (String guest : List.of("a@x.com", "b@x.com", "c@x.com", "d@x.com")) {
			mail.delayFor(guest, Duration.ofMillis(120));
		}
		invite(host, "a@x.com", "b@x.com", "c@x.com", "d@x.com");

		Thread pass = new Thread(drain::runOnce);
		int mostSeenAtOnce = 0;
		pass.start();
		while (pass.isAlive()) {
			Integer sending = jdbc.queryForObject(
					"select count(*) from hero_test.outbound_message where status = 'SENDING'", Integer.class);
			mostSeenAtOnce = Math.max(mostSeenAtOnce, sending);
		}
		pass.join();

		// With a batch-wide claim, all four would sit in sending from the start of the pass — and a message
		// claimed at batch time can outlive the reclaim threshold and be sent twice.
		assertThat(mostSeenAtOnce).isEqualTo(1);
		assertThat(mail.sentOfKind(MessageKind.INVITATION)).hasSize(4);
	}

	@Test
	void theLimitsAreTheOnesTheDesignDecided() {
		// The test profile shortens the drain's timings; these are not overridden.
		assertThat(properties.limits().invitationsPerHostPerDay()).isEqualTo(1000);
		assertThat(properties.limits().managementLinksPerAddressPerDay()).isEqualTo(5);
		assertThat(properties.drain().reclaimAfter()).isGreaterThan(properties.drain().sendTimeout());
	}
}
