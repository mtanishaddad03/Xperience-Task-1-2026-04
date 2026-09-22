package com.xperience.hero.api;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class HostAndGuestViewTest extends ApiTest {

	@Test
	void theHostSeesCountsGuestStatesAndTheBacklog() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");
		String third = inviteAndTakeToken(host, "third@x.com");
		invite(host, "pending@x.com");
		replyAs(first, "YES");
		replyAs(second, "YES");
		replyAs(third, "MAYBE");

		JsonNode view = body(hostView(host));

		assertThat(view.path("event").path("title").asText()).isEqualTo("Party");
		assertThat(view.path("event").path("capacity").asInt()).isEqualTo(1);
		assertThat(view.path("event").path("repliesOpen").asBoolean()).isTrue();
		assertThat(view.path("counts").path("confirmed").asInt()).isEqualTo(1);
		assertThat(view.path("counts").path("waitlisted").asInt()).isEqualTo(1);
		assertThat(view.path("counts").path("maybe").asInt()).isEqualTo(1);
		assertThat(view.path("counts").path("pending").asInt()).isEqualTo(1);
		assertThat(view.path("counts").path("placesRemaining").asInt()).isZero();
		assertThat(view.path("guests")).hasSize(4);

		JsonNode waitlisted = view.path("guests").valueStream()
				.filter(g -> g.path("email").asText().equals("second@x.com")).findFirst().orElseThrow();
		assertThat(waitlisted.path("state").asText()).isEqualTo("WAITLISTED");
		assertThat(waitlisted.path("waitlistPosition").asInt()).isEqualTo(1);
		assertThat(waitlisted.path("invitation").asText()).isEqualTo("SENT");

		JsonNode pending = view.path("guests").valueStream()
				.filter(g -> g.path("email").asText().equals("pending@x.com")).findFirst().orElseThrow();
		assertThat(pending.path("state").asText()).isEqualTo("PENDING");
		assertThat(pending.path("waitlistPosition").isNull()).isTrue();
		assertThat(pending.path("invitation").asText()).isEqualTo("QUEUED");
		assertThat(view.path("outbox").path("queued").asInt()).isEqualTo(1);
	}

	@Test
	void withoutCapacityThereAreNoPlacesRemainingAndNoWaitlist() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		String guest = inviteAndTakeToken(host, "guest@x.com");
		replyAs(guest, "YES");

		JsonNode view = body(hostView(host));

		assertThat(view.path("counts").path("placesRemaining").isNull()).isTrue();
		assertThat(view.path("counts").path("waitlisted").asInt()).isZero();
		assertThat(view.path("counts").path("confirmed").asInt()).isEqualTo(1);
	}

	@Test
	void theGuestSeesOnlyTheirOwnStanding() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");
		replyAs(first, "YES");
		replyAs(second, "YES");

		String view = guestView(second).getResponse().getContentAsString();

		assertThat(view).contains("WAITLISTED").contains("Party").contains("The garden");
		assertThat(view).doesNotContain("first@x.com").doesNotContain("second@x.com")
				.doesNotContain("host@example.com").doesNotContain("waitlistPosition").doesNotContain("counts");
	}

	@Test
	void replyingThroughTheApiFollowsTheTransitionTable() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", 1);
		String first = inviteAndTakeToken(host, "first@x.com");
		String second = inviteAndTakeToken(host, "second@x.com");

		assertThat(body(replyAs(first, "YES")).path("state").asText()).isEqualTo("CONFIRMED");
		assertThat(body(replyAs(second, "YES")).path("state").asText()).isEqualTo("WAITLISTED");
		assertThat(body(replyAs(first, "MAYBE")).path("state").asText()).isEqualTo("MAYBE");
		assertThat(body(guestView(second)).path("reply").path("state").asText()).isEqualTo("CONFIRMED");
	}

	@Test
	void anInvalidChoiceIsARequestError() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		String guest = inviteAndTakeToken(host, "guest@x.com");

		MvcResult result = replyAs(guest, "PERHAPS");

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(body(result).path("error").asText()).isEqualTo("VALIDATION");
	}

	@Test
	void closingRefusesEveryReplyIncludingADecline() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		String guest = inviteAndTakeToken(host, "guest@x.com");
		replyAs(guest, "YES");
		mvc.perform(withToken(post("/api/host/close"), host)).andReturn();

		MvcResult refused = replyAs(guest, "NO");

		assertThat(refused.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(refused).path("error").asText()).isEqualTo("REPLY_LOCKED");
		assertThat(body(refused).path("reason").asText()).isEqualTo("CLOSED");
		assertThat(body(guestView(guest)).path("event").path("repliesOpen").asBoolean()).isFalse();
		assertThat(body(guestView(guest)).path("event").path("lockedReason").asText()).isEqualTo("CLOSED");
	}

	@Test
	void closeThenCancelIsAllowedButCancelIsTerminal() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);

		assertThat(body(mvc.perform(withToken(post("/api/host/close"), host)).andReturn())
				.path("status").asText()).isEqualTo("CLOSED");
		assertThat(body(mvc.perform(withToken(post("/api/host/close"), host)).andReturn())
				.path("changed").asBoolean()).isFalse();
		assertThat(body(mvc.perform(withToken(post("/api/host/cancel"), host)).andReturn())
				.path("status").asText()).isEqualTo("CANCELLED");

		MvcResult refused = mvc.perform(withToken(post("/api/host/close"), host)).andReturn();
		assertThat(refused.getResponse().getStatus()).isEqualTo(409);
		assertThat(body(refused).path("error").asText()).isEqualTo("STATUS_NOT_ALLOWED");
	}

	@Test
	void cancellingQueuesANoticeForEveryGuestWhoseInvitationWasSent() throws Exception {
		String host = createEventAndVerifyHost("host@example.com", null);
		inviteAndTakeToken(host, "sent@x.com");
		invite(host, "queued@x.com"); // never drained

		mvc.perform(withToken(post("/api/host/cancel"), host)).andReturn();

		assertThat(jdbc.queryForObject("""
				select g.email from hero_test.outbound_message m join hero_test.guest g on g.id = m.guest_id
				where m.kind = 'CANCELLATION_NOTICE'
				""", String.class)).isEqualTo("sent@x.com");
	}
}
