package com.xperience.hero.outbox;

/** Only invitation and management-link messages carry a link; notices never do (INV-B13). */
public enum MessageKind {
	MANAGEMENT_LINK, INVITATION, PROMOTION_NOTICE, CANCELLATION_NOTICE
}
