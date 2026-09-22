package com.xperience.hero.api;

import com.xperience.hero.Concurrently;
import com.xperience.hero.event.InvitationService;
import com.xperience.hero.outbox.MessageKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class InvitationsTest extends ApiTest {

	@Autowired
	InvitationService invitations;

	private static String[] addresses(int count, String domain) {
		String[] emails = new String[count];
		for (int i = 0; i < count; i++) {
			emails[i] = "guest" + i + "@" + domain;
		}
		return emails;
	}

	private long guestCount() {
		return jdbc.queryForObject("select count(*) from hero_test.guest", Long.class);
	}

	@Test
	@DisplayName("Exit: Dana@x.com then dana@x.com → one guest")
	void theSameAddressInTwoSpellingsIsOneGuest() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);

		invite(host, "Dana@x.com");
		MvcResult second = invite(host, "dana@x.com");

		assertThat(body(second).path("invited").asInt()).isZero();
		assertThat(body(second).path("alreadyInvited").asInt()).isEqualTo(1);
		assertThat(guestCount()).isEqualTo(1);
		assertThat(messageCount(MessageKind.INVITATION, "QUEUED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select email from hero_test.guest", String.class)).isEqualTo("dana@x.com");
	}

	@Test
	@DisplayName("Exit: a 600-address batch submitted twice → 600 guests")
	void aBatchSubmittedTwiceCreates600Guests() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		String[] emails = addresses(600, "x.com");

		assertThat(body(invite(host, emails)).path("invited").asInt()).isEqualTo(600);
		MvcResult retry = invite(host, emails);

		assertThat(body(retry).path("invited").asInt()).isZero();
		assertThat(body(retry).path("alreadyInvited").asInt()).isEqualTo(600);
		assertThat(guestCount()).isEqualTo(600);
		assertThat(messageCount(MessageKind.INVITATION, "QUEUED")).isEqualTo(600);
	}

	@Test
	@DisplayName("Exit: no invitation can be queued before the host has verified")
	void invitationsRequireAVerifiedHost() throws Exception {
		String host = createEventAndTakeToken("host@example.com", null);

		MvcResult refused = invite(host, "guest@x.com");

		assertThat(refused.getResponse().getStatus()).isEqualTo(403);
		assertThat(body(refused).path("error").asText()).isEqualTo("HOST_NOT_VERIFIED");
		assertThat(guestCount()).isZero();
		assertThat(messageCount(MessageKind.INVITATION, "QUEUED")).isZero();

		mvc.perform(withToken(post("/api/host/verify"), host)).andReturn();
		assertThat(invite(host, "guest@x.com").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void aBatchWithAnInvalidAddressIsRejectedWhole() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);

		MvcResult result = invite(host, "good@x.com", "no-at-sign", "also bad@x.com", "other@x.com");

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(body(result).path("error").asText()).isEqualTo("INVALID_ADDRESSES");
		assertThat(body(result).path("invalid")).hasSize(2);
		assertThat(guestCount()).isZero();
	}

	@Test
	void invitingIsRefusedOnAClosedCancelledOrStartedEvent() throws Exception {
		String closed = createEventAndVerifyHost("closed@example.com", null);
		mvc.perform(withToken(post("/api/host/close"), closed)).andReturn();
		MvcResult refused = invite(closed, "guest@x.com");
		assertThat(refused.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(refused).path("error").asText()).isEqualTo("EVENT_LOCKED");
		assertThat(body(refused).path("reason").asText()).isEqualTo("CLOSED");

		String cancelled = createEventAndVerifyHost("cancelled@example.com", null);
		mvc.perform(withToken(post("/api/host/cancel"), cancelled)).andReturn();
		assertThat(body(invite(cancelled, "guest@x.com")).path("reason").asText()).isEqualTo("CANCELLED");

		assertThat(guestCount()).isZero();
	}

	@Test
	@DisplayName("D14: 1,000 invitations per host address per 24 hours, across that host's events")
	void theInvitationLimitIsCountedPerHostAddressAcrossEvents() throws Exception {
		String first = createEventAndVerifyHost("host@example.com", null);
		String second = createEventAndVerifyHost("Host@Example.com", null);
		assertThat(body(invite(first, addresses(600, "a.com"))).path("invited").asInt()).isEqualTo(600);
		assertThat(body(invite(second, addresses(400, "b.com"))).path("invited").asInt()).isEqualTo(400);

		MvcResult overLimit = invite(second, addresses(1, "c.com"));

		assertThat(overLimit.getResponse().getStatus()).isEqualTo(429);
		assertThat(body(overLimit).path("error").asText()).isEqualTo("INVITATION_LIMIT");
		assertThat(body(overLimit).path("limit").asInt()).isEqualTo(1000);
		assertThat(body(overLimit).path("usedInWindow").asInt()).isEqualTo(1000);
		assertThat(guestCount()).isEqualTo(1000);

		// Another host address is counted separately.
		String other = createEventAndVerifyHost("other@example.com", null);
		assertThat(invite(other, addresses(5, "d.com")).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void aBatchThatWouldExceedTheLimitIsRejectedWhole() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		invite(host, addresses(900, "a.com"));

		MvcResult tooBig = invite(host, addresses(200, "b.com"));

		assertThat(tooBig.getResponse().getStatus()).isEqualTo(429);
		assertThat(body(tooBig).path("requested").asInt()).isEqualTo(200);
		assertThat(guestCount()).isEqualTo(900); // none of the 200 was created
	}

	@Test
	@DisplayName("D14: two batches from one host at the same instant cannot both slip under the limit")
	void simultaneousBatchesFromOneHostAreCountedOnce() throws Exception {
		String a = createEventAndVerifyHost("host@example.com", null);
		String b = createEventAndVerifyHost("host@example.com", null);
		long eventA = jdbc.queryForObject("select min(id) from hero_test.event", Long.class);
		long eventB = jdbc.queryForObject("select max(id) from hero_test.event", Long.class);

		List<Callable<Object>> batches = new ArrayList<>();
		batches.add(() -> tryInvite(eventA, addresses(600, "a.com")));
		batches.add(() -> tryInvite(eventB, addresses(600, "b.com")));
		List<Object> outcomes = Concurrently.run(batches);

		assertThat(outcomes).filteredOn(o -> o instanceof InvitationService.InviteResult).hasSize(1);
		assertThat(guestCount()).isEqualTo(600);
		assertThat(a).isNotEqualTo(b);
	}

	private Object tryInvite(long eventId, String[] emails) {
		try {
			return invitations.invite(eventId, List.of(emails));
		}
		catch (RuntimeException e) {
			return e;
		}
	}

	@Test
	void resendIsAllowedOnSentOrFailedAndRefusedWhileQueuedOrSending() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		invite(host, "guest@x.com");

		MvcResult whileQueued = resend(host, "guest@x.com");
		assertThat(whileQueued.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(whileQueued).path("error").asText()).isEqualTo("RESEND_NOT_ALLOWED");

		drain.runOnce(); // now SENT
		assertThat(resend(host, "guest@x.com").getResponse().getStatus()).isEqualTo(202);
		drain.runOnce();

		assertThat(mail.countTo("guest@x.com")).isEqualTo(2);
		assertThat(messageCount(MessageKind.INVITATION, "SENT")).isEqualTo(2);
	}

	@Test
	void resendCountsTowardsTheLimitAndNeedsAKnownGuest() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		invite(host, addresses(1000, "a.com"));
		drainUntilEmpty();

		MvcResult overLimit = resend(host, "guest0@a.com");
		assertThat(overLimit.getResponse().getStatus()).isEqualTo(429);

		MvcResult unknown = resend(host, "stranger@a.com");
		assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
		assertThat(body(unknown).path("error").asText()).isEqualTo("GUEST_NOT_FOUND");
	}
}
