package com.xperience.hero.reply;

/** A guest's stored reply state. Pending is not a state: it is a guest with no Reply. */
public enum ReplyState {
	CONFIRMED, WAITLISTED, DECLINED, MAYBE
}
