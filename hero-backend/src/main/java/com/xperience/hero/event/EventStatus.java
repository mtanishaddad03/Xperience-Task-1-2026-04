package com.xperience.hero.event;

/** Open → Closed → Cancelled, or Open → Cancelled. Cancelled is terminal; nothing returns to Open (INV-B8). */
public enum EventStatus {
	OPEN, CLOSED, CANCELLED
}
