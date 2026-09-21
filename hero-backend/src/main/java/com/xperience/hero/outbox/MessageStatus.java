package com.xperience.hero.outbox;

/** queued → sending → sent · failed · skipped. Written only by the Outbox Drain, after creation. */
public enum MessageStatus {
	QUEUED, SENDING, SENT, FAILED, SKIPPED
}
