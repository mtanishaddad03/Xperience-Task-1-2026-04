package com.xperience.hero.gate;

/** A resolved guest link: the one (event, guest) pair it names (INV-A2, INV-T2). */
public record GuestAccess(long eventId, long guestId) {
}
