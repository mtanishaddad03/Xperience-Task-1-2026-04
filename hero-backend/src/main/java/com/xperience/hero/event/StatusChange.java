package com.xperience.hero.event;

public enum StatusChange {
	CHANGED,
	/** Already in the requested state; nothing was written and no notices were recorded again. */
	ALREADY_IN_STATE,
	/** Refused by the state guard: Cancelled is terminal (INV-B8). */
	NOT_ALLOWED
}
