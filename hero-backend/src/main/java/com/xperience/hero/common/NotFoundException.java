package com.xperience.hero.common;

/**
 * An event, or a guest within an event, that does not exist. The Access Gate (Stage 2) turns every such case into
 * the same refusal (INV-A4).
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("not found");
	}
}
