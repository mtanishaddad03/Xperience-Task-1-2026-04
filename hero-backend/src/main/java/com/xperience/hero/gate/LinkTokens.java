package com.xperience.hero.gate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Link tokens: 32 random bytes, URL-safe, stored only as their SHA-256 (INV-D3, KD13). */
public final class LinkTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private LinkTokens() {
	}

	public static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}

	/**
	 * Deterministic on purpose: a link must be found by its value. Safe only because tokens are long and random —
	 * there is nothing to guess and nothing to reverse from the stored form.
	 */
	public static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
