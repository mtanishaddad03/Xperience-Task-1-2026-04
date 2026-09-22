package com.xperience.hero.outbox;

import java.time.Instant;

/** One claimed message, ready to hand over. The claim time guards the result: a reclaimed message is not marked. */
record SendJob(long messageId, Instant claimedAt, OutgoingMail mail) {
}
