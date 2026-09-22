package com.xperience.hero.gate;

import com.xperience.hero.link.GuestLinkRepository;
import com.xperience.hero.link.ManagementLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one entry point (KD6). A management link resolves to exactly one event, a guest link to exactly one
 * (event, guest); anything else is the same refusal.
 * <p>
 * The two kinds cannot be interchanged because each is looked up only in its own table, and every path does the
 * same work — hash, one indexed lookup — so the refusal costs the same whatever the reason.
 */
@Component
@RequiredArgsConstructor
public class AccessGate {

	private final ManagementLinkRepository managementLinks;
	private final GuestLinkRepository guestLinks;

	@Transactional(readOnly = true)
	public HostAccess resolveHost(String token) {
		return managementLinks.findByTokenHash(hashOf(token))
				.map(link -> new HostAccess(link.getEvent().getId()))
				.orElseThrow(LinkInvalidException::new);
	}

	@Transactional(readOnly = true)
	public GuestAccess resolveGuest(String token) {
		return guestLinks.findByTokenHash(hashOf(token))
				.map(link -> new GuestAccess(link.getEvent().getId(), link.getGuest().getId()))
				.orElseThrow(LinkInvalidException::new);
	}

	/** A malformed or missing token is hashed and looked up like any other, so it takes the same path. */
	private static byte[] hashOf(String token) {
		return LinkTokens.hash(token == null ? "" : token);
	}
}
