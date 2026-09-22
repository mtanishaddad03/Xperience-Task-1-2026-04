package com.xperience.hero.api;

import com.xperience.hero.outbox.MessageKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class EventCreationTest extends ApiTest {

	@Test
	void creatingAnEventQueuesExactlyOneManagementLinkAndReturnsNothingElse() throws Exception {
		MvcResult result = createEventRequest("host@example.com", 10, Instant.now().plus(Duration.ofDays(2)));

		assertThat(result.getResponse().getStatus()).isEqualTo(202);
		String responseBody = result.getResponse().getContentAsString();
		assertThat(responseBody).doesNotContain("id").doesNotContain("token");
		assertThat(messageCount(MessageKind.MANAGEMENT_LINK, "QUEUED")).isEqualTo(1);
		assertThat(mail.sent()).isEmpty(); // nothing is sent inside the request

		drain.runOnce();
		assertThat(mail.sentOfKind(MessageKind.MANAGEMENT_LINK)).singleElement()
				.matches(m -> m.recipient().equals("host@example.com") && m.link().contains("/m/"));
	}

	@Test
	void startTimeMustBeInTheFuture() throws Exception {
		MvcResult result = createEventRequest("host@example.com", null, Instant.now().minus(Duration.ofMinutes(1)));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(body(result).path("error").asText()).isEqualTo("VALIDATION");
		assertThat(body(result).path("fields").has("startTime")).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from hero_test.event", Long.class)).isZero();
	}

	@Test
	void requiredFieldsAndCapacityAreValidated() throws Exception {
		MvcResult result = mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("""
				{"title":"  ","description":"","location":"","startTime":"2030-01-01T10:00:00Z",
				 "capacity":0,"hostEmail":"not-an-email"}
				""")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(body(result).path("fields").properties())
				.extracting(e -> e.getKey())
				.contains("title", "description", "location", "capacity", "hostEmail");
		assertThat(jdbc.queryForObject("select count(*) from hero_test.event", Long.class)).isZero();
	}

	@Test
	void theHostAddressIsStoredNormalised() throws Exception {
		createEventRequest("  Host@Example.COM ", null, Instant.now().plus(Duration.ofDays(1)));

		assertThat(jdbc.queryForObject("select host_email from hero_test.event", String.class))
				.isEqualTo("host@example.com");
	}

	@Test
	@DisplayName("D14: at most 5 management-link messages per address per 24 hours; the sixth creates nothing")
	void managementLinkCapPerAddress() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(createEventRequest("host@example.com", null, Instant.now().plus(Duration.ofDays(1)))
					.getResponse().getStatus()).isEqualTo(202);
		}

		MvcResult sixth = createEventRequest("HOST@example.com", null, Instant.now().plus(Duration.ofDays(1)));

		assertThat(sixth.getResponse().getStatus()).isEqualTo(429);
		assertThat(body(sixth).path("error").asText()).isEqualTo("MANAGEMENT_LINK_LIMIT");
		assertThat(jdbc.queryForObject("select count(*) from hero_test.event", Long.class)).isEqualTo(5);
		assertThat(messageCount(MessageKind.MANAGEMENT_LINK, "QUEUED")).isEqualTo(5);

		// Another address is unaffected.
		assertThat(createEventRequest("other@example.com", null, Instant.now().plus(Duration.ofDays(1)))
				.getResponse().getStatus()).isEqualTo(202);
	}

	@Test
	void verifyingTheHostNeedsAnExplicitCallAndIsIdempotent() throws Exception {
		String token = createEventAndTakeToken("host@example.com", null);

		assertThat(body(mvc.perform(withToken(post("/api/host/verify"), token)).andReturn())
				.path("hostVerified").asBoolean()).isTrue();
		assertThat(body(mvc.perform(withToken(post("/api/host/verify"), token)).andReturn())
				.path("hostVerified").asBoolean()).isTrue();
		assertThat(jdbc.queryForObject("select host_verified from hero_test.event", Boolean.class)).isTrue();
	}
}
