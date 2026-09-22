package com.xperience.hero.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AccessGateTest extends ApiTest {

	@Autowired
	RequestMappingHandlerMapping handlerMapping;

	private String describe(MvcResult result) {
		var response = result.getResponse();
		return response.getStatus() + " " + response.getHeaderNames().stream().sorted()
				.map(h -> h + "=" + response.getHeader(h)).toList() + " " + getContent(response);
	}

	private static String getContent(org.springframework.mock.web.MockHttpServletResponse response) {
		try {
			return response.getContentAsString();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	@DisplayName("Every link failure returns the same response: unknown, wrong kind, missing, malformed")
	void everyLinkFailureLooksTheSame() throws Exception {
		String hostToken = createEventAndVerifyHost("host@example.com", null);
		String guestToken = inviteAndTakeToken(hostToken, "guest@example.com");

		List<MvcResult> refusals = List.of(
				// a guest link where a management link is expected, and the reverse
				mvc.perform(withToken(get("/api/host/event"), guestToken)).andReturn(),
				mvc.perform(withToken(get("/api/guest"), hostToken)).andReturn(),
				// unknown, missing, malformed, empty
				mvc.perform(withToken(get("/api/host/event"), "Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFy")).andReturn(),
				mvc.perform(get("/api/host/event")).andReturn(),
				mvc.perform(withToken(get("/api/host/event"), "not a token")).andReturn(),
				mvc.perform(withToken(get("/api/guest"), "")).andReturn());

		String first = describe(refusals.get(0));
		assertThat(refusals.stream().map(this::describe)).allMatch(first::equals);
		assertThat(first).contains("404").contains("LINK_INVALID").contains("no-store");
		// The valid links still work, so the refusals are not simply "everything fails".
		assertThat(hostView(hostToken).getResponse().getStatus()).isEqualTo(200);
		assertThat(guestView(guestToken).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Every endpoint is either the one open request or behind the gate")
	void everyEndpointIsGuarded() {
		Set<String> open = Set.of("POST /api/events");

		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
			var patterns = entry.getKey().getPathPatternsCondition();
			if (patterns == null) {
				continue;
			}
			for (var pattern : patterns.getPatterns()) {
				String path = pattern.getPatternString();
				if (!path.startsWith("/api")) {
					continue;
				}
				String method = entry.getKey().getMethodsCondition().getMethods().stream().findFirst()
						.map(Enum::name).orElse("ANY");
				assertThat(open.contains(method + " " + path) || path.startsWith("/api/host/") || path.equals("/api/guest")
						|| path.startsWith("/api/guest/"))
						.as("%s %s is neither the open path nor behind the gate", method, path)
						.isTrue();
			}
		}
	}

	@Test
	void anUnknownApiPathIsRefusedLikeABadLink() throws Exception {
		MvcResult result = mvc.perform(get("/api/host/whatever")).andReturn();
		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		assertThat(result.getResponse().getContentAsString()).contains("LINK_INVALID");
	}

	@Test
	@DisplayName("Opening a link changes nothing: no reply is created, no host is verified")
	void openingALinkChangesNothing() throws Exception {
		String hostToken = createEventAndTakeToken("host@example.com", null);
		assertThat(body(hostView(hostToken)).path("event").path("hostVerified").asBoolean()).isFalse();

		mvc.perform(withToken(post("/api/host/verify"), hostToken)).andReturn();
		String guestToken = inviteAndTakeToken(hostToken, "guest@example.com");

		guestView(guestToken);
		guestView(guestToken);

		assertThat(jdbc.queryForObject("select count(*) from hero_test.reply", Long.class)).isZero();
		assertThat(body(guestView(guestToken)).path("reply").path("state").asText()).isEqualTo("PENDING");

		// Only an explicit submission records a reply.
		assertThat(replyAs(guestToken, "YES").getResponse().getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from hero_test.reply", Long.class)).isEqualTo(1);
	}

	@Test
	void aGuestLinkCannotActOnAnotherEventOrGuest() throws Exception {
		String hostA = createEventAndVerifyHost("a@example.com", null);
		String hostB = createEventAndVerifyHost("b@example.com", null);
		String guestA = inviteAndTakeToken(hostA, "guest@example.com");
		inviteAndTakeToken(hostB, "guest@example.com");

		replyAs(guestA, "YES");

		// The reply landed on event A's guest only; event B's guest of the same address is untouched.
		assertThat(body(hostView(hostA)).path("counts").path("confirmed").asInt()).isEqualTo(1);
		assertThat(body(hostView(hostB)).path("counts").path("confirmed").asInt()).isZero();
	}

	@Test
	void theOpenPathAcceptsOnlyEventCreation() throws Exception {
		assertThat(mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andReturn().getResponse().getStatus()).isEqualTo(400);
		assertThat(mvc.perform(put("/api/guest/reply").contentType(MediaType.APPLICATION_JSON)
				.content("{\"choice\":\"YES\"}")).andReturn().getResponse().getStatus()).isEqualTo(404);
	}
}
