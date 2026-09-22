package com.xperience.hero.api;

import com.xperience.hero.outbox.MessageKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** One test per Stage 4 exit condition in DESIGN.md → Rollout and Migration Notes. */
class Stage4ExitConditionsTest extends ApiTest {

	private String noticeStatus(MessageKind kind) {
		return jdbc.queryForObject("select status from hero_test.outbound_message where kind = ?",
				String.class, kind.name());
	}

	@Test
	@DisplayName("Promoted, then declined before the notice is sent → notice skipped")
	void aPromotionNoticeIsSkippedWhenTheGuestNoLongerHasAPlace() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");
		replyAs(first, "YES");
		replyAs(second, "YES");
		replyAs(first, "NO"); // releases the place: second is promoted and the notice is recorded

		replyAs(second, "NO"); // no longer confirmed, before the drain reaches the notice
		drainUntilEmpty();

		assertThat(noticeStatus(MessageKind.PROMOTION_NOTICE)).isEqualTo("SKIPPED");
		assertThat(mail.sentOfKind(MessageKind.PROMOTION_NOTICE)).isEmpty();
	}

	@Test
	@DisplayName("Event cancelled after a promotion, before its notice → notice skipped")
	void aPromotionNoticeIsSkippedWhenTheEventIsCancelledFirst() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");
		replyAs(first, "YES");
		replyAs(second, "YES");
		replyAs(first, "NO");

		mvc.perform(withToken(post("/api/host/cancel"), host)).andReturn();
		drainUntilEmpty();

		assertThat(noticeStatus(MessageKind.PROMOTION_NOTICE)).isEqualTo("SKIPPED");
		assertThat(mail.sentOfKind(MessageKind.PROMOTION_NOTICE)).isEmpty();
		// The cancellation notices are the ones that are still true, and they do go out.
		assertThat(mail.sentOfKind(MessageKind.CANCELLATION_NOTICE)).hasSize(2);
	}

	@Test
	@DisplayName("A promotion notice is sent → the guest's original invitation link still works (INV-B13)")
	void aPromotionNoticeNeitherCarriesNorReplacesALink() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");
		replyAs(first, "YES");
		replyAs(second, "YES");
		String hashBefore = jdbc.queryForObject("""
				select encode(l.token_hash, 'hex') from hero_test.guest_link l
				join hero_test.guest g on g.id = l.guest_id where g.email = 'second@x.com'
				""", String.class);

		replyAs(first, "NO");
		drainUntilEmpty();

		assertThat(mail.sentOfKind(MessageKind.PROMOTION_NOTICE)).singleElement()
				.matches(m -> m.recipient().equals("second@x.com") && m.link() == null);
		assertThat(jdbc.queryForObject("""
				select encode(l.token_hash, 'hex') from hero_test.guest_link l
				join hero_test.guest g on g.id = l.guest_id where g.email = 'second@x.com'
				""", String.class)).isEqualTo(hashBefore);
		// The link the guest already holds still opens, and now shows their place.
		assertThat(body(guestView(second)).path("reply").path("state").asText()).isEqualTo("CONFIRMED");
	}

	@Test
	@DisplayName("400 invited guests, cancelled → 400 notices, paced, none with a link, every link still opens")
	void cancellingALargeEventTellsEveryInvitedGuest() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		String[] emails = new String[400];
		for (int i = 0; i < emails.length; i++) {
			emails[i] = "guest" + i + "@x.com";
		}
		invite(host, emails);
		drainUntilEmpty();
		List<String> tokens = new ArrayList<>();
		for (String email : List.of("guest0@x.com", "guest199@x.com", "guest399@x.com")) {
			tokens.add(tokenOf(mail.lastTo(email)));
		}
		assertThat(body(guestView(tokens.get(0))).path("event").path("repliesOpen").asBoolean()).isTrue();

		mvc.perform(withToken(post("/api/host/cancel"), host)).andReturn();

		assertThat(jdbc.queryForObject("select count(*) from hero_test.outbound_message "
				+ "where kind = 'CANCELLATION_NOTICE'", Long.class)).isEqualTo(400);
		// Paced: one pass of the drain takes a batch, not the whole backlog.
		int firstPass = drain.runOnce();
		assertThat(firstPass).isLessThanOrEqualTo(10);
		assertThat(mail.sentOfKind(MessageKind.CANCELLATION_NOTICE)).hasSize(firstPass);

		drainUntilEmpty();

		assertThat(mail.sentOfKind(MessageKind.CANCELLATION_NOTICE)).hasSize(400);
		assertThat(mail.sentOfKind(MessageKind.CANCELLATION_NOTICE)).allMatch(m -> m.link() == null);
		assertThat(jdbc.queryForObject("select count(*) from hero_test.outbound_message "
				+ "where kind = 'CANCELLATION_NOTICE' and status = 'SENT'", Long.class)).isEqualTo(400);
		// Every link a guest already holds still opens, and says the event is cancelled.
		for (String token : tokens) {
			var view = body(guestView(token));
			assertThat(view.path("event").path("status").asText()).isEqualTo("CANCELLED");
			assertThat(view.path("event").path("repliesOpen").asBoolean()).isFalse();
			assertThat(view.path("event").path("lockedReason").asText()).isEqualTo("CANCELLED");
		}
	}
}
