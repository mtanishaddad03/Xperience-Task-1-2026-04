package com.xperience.hero.gate;

/** Unknown link, wrong kind, missing or malformed — all one refusal, revealing nothing (INV-A4). */
public class LinkInvalidException extends RuntimeException {

	public LinkInvalidException() {
		super("link invalid");
	}
}
