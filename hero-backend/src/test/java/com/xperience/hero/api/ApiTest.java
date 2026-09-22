package com.xperience.hero.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.xperience.hero.IntegrationTest;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.OutboxDrain;
import com.xperience.hero.outbox.OutgoingMail;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Base for tests that go through HTTP against real PostgreSQL, with the sender replaced by a recording one. */
@AutoConfigureMockMvc
@Import(ApiTest.RecordingSenderConfig.class)
public abstract class ApiTest extends IntegrationTest {

	@TestConfiguration
	static class RecordingSenderConfig {

		@Bean
		@Primary
		RecordingMailSender recordingMailSender() {
			return new RecordingMailSender();
		}
	}

	@Autowired
	protected MockMvc mvc;
	@Autowired
	protected RecordingMailSender mail;
	@Autowired
	protected OutboxDrain drain;
	@Autowired
	protected ObjectMapper json;

	@BeforeEach
	void resetSenderAndDrain() {
		mail.reset();
		jdbc.update("update hero_test.drain_control set paused = false");
	}

	// --- requests ---------------------------------------------------------

	protected MvcResult createEventRequest(String hostEmail, Integer capacity, Instant startTime) throws Exception {
		java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
		request.put("title", "Party");
		request.put("description", "A party");
		request.put("location", "The garden");
		request.put("startTime", startTime.toString());
		request.put("capacity", capacity);
		request.put("hostEmail", hostEmail);
		String body = json.writeValueAsString(request);
		return mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}

	/** Creates an event, drains its management-link message and returns the token from the link. */
	protected String createEventAndVerifyHost(String hostEmail, Integer capacity) throws Exception {
		String token = createEventAndTakeToken(hostEmail, capacity);
		mvc.perform(withToken(post("/api/host/verify"), token)).andReturn();
		return token;
	}

	protected String createEventAndTakeToken(String hostEmail, Integer capacity) throws Exception {
		createEventRequest(hostEmail, capacity, Instant.now().plus(Duration.ofDays(1)));
		// The message goes to the normalised address, whatever spelling the request used.
		String recipient = hostEmail.strip().toLowerCase(java.util.Locale.ROOT);
		long alreadySent = mail.countTo(recipient);
		// The drain works in batches, so a backlog may sit in front of this message.
		for (int run = 0; run < 500 && mail.countTo(recipient) == alreadySent; run++) {
			if (drain.runOnce() == 0) {
				break;
			}
		}
		return tokenOf(mail.lastTo(recipient));
	}

	protected MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
		return builder.header("Authorization", "Bearer " + token);
	}

	protected MvcResult invite(String hostToken, String... emails) throws Exception {
		String body = json.writeValueAsString(java.util.Map.of("emails", emails));
		return mvc.perform(withToken(post("/api/host/invitations"), hostToken)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}

	protected MvcResult resend(String hostToken, String email) throws Exception {
		String body = json.writeValueAsString(java.util.Map.of("email", email));
		return mvc.perform(withToken(post("/api/host/invitations/resend"), hostToken)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}

	protected MvcResult hostView(String hostToken) throws Exception {
		return mvc.perform(withToken(get("/api/host/event"), hostToken)).andReturn();
	}

	protected MvcResult guestView(String guestToken) throws Exception {
		return mvc.perform(withToken(get("/api/guest"), guestToken)).andReturn();
	}

	protected MvcResult replyAs(String guestToken, String choice) throws Exception {
		return mvc.perform(withToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/api/guest/reply"), guestToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(java.util.Map.of("choice", choice)))).andReturn();
	}

	// --- helpers ----------------------------------------------------------

	protected JsonNode body(MvcResult result) throws Exception {
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** The token out of a link URL such as http://localhost:5171/i/<token>. */
	protected String tokenOf(OutgoingMail mail) {
		String link = mail.link();
		return link.substring(link.lastIndexOf('/') + 1);
	}

	/** Invites one guest, drains, and returns that guest's link token. */
	protected String inviteAndTakeToken(String hostToken, String guestEmail) throws Exception {
		invite(hostToken, guestEmail);
		drain.runOnce();
		return tokenOf(mail.lastTo(guestEmail));
	}

	/** Runs the drain until nothing is due, or fails if the backlog will not clear. */
	protected void drainUntilEmpty() {
		for (int run = 0; run < 500; run++) {
			if (drain.runOnce() == 0) {
				return;
			}
		}
		throw new IllegalStateException("backlog did not clear");
	}

	protected long messageCount(MessageKind kind, String status) {
		return jdbc.queryForObject("select count(*) from hero_test.outbound_message where kind = ? and status = ?",
				Long.class, kind.name(), status);
	}
}
