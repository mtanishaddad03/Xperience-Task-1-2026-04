package com.xperience.hero.common;

import java.util.Locale;

/** One definition of what an address is and how it is compared (D5). */
public final class EmailAddress {

	private EmailAddress() {
	}

	/** Trimmed and lower-cased. */
	public static String normalise(String email) {
		return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * Syntax only: one {@code @}, a non-empty local part, a domain containing a dot, no spaces, at most 254
	 * characters. Whether the address exists can only be learned by sending to it.
	 */
	public static boolean isValid(String email) {
		if (email == null) {
			return false;
		}
		String normalised = normalise(email);
		if (normalised.isEmpty() || normalised.length() > 254 || normalised.chars().anyMatch(Character::isWhitespace)) {
			return false;
		}
		int at = normalised.indexOf('@');
		if (at <= 0 || at != normalised.lastIndexOf('@') || at == normalised.length() - 1) {
			return false;
		}
		String domain = normalised.substring(at + 1);
		int dot = domain.indexOf('.');
		return dot > 0 && dot < domain.length() - 1 && !domain.contains("..");
	}
}
