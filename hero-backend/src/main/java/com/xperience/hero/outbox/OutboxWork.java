package com.xperience.hero.outbox;

import com.xperience.hero.common.DatabaseClock;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventStatus;
import com.xperience.hero.gate.LinkTokens;
import com.xperience.hero.guest.Guest;
import com.xperience.hero.link.GuestLink;
import com.xperience.hero.link.GuestLinkRepository;
import com.xperience.hero.link.ManagementLink;
import com.xperience.hero.link.ManagementLinkRepository;
import com.xperience.hero.reply.Reply;
import com.xperience.hero.reply.ReplyRepository;
import com.xperience.hero.reply.ReplyState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The database steps of the drain, each in its own short transaction. None of them takes the per-event lock.
 */
@Service
@RequiredArgsConstructor
public class OutboxWork {

	private final OutboundMessageRepository messages;
	private final ManagementLinkRepository managementLinks;
	private final GuestLinkRepository guestLinks;
	private final ReplyRepository replies;
	private final DrainControlRepository drainControl;
	private final DatabaseClock clock;
	private final HeroProperties properties;

	@Transactional(readOnly = true)
	public boolean paused() {
		return drainControl.findById(DrainControl.ROW_ID).map(DrainControl::isPaused).orElse(false);
	}

	/** A message left in <em>sending</em> past the reclaim threshold: the drain stopped while sending it. */
	@Transactional
	public void reclaimStuckMessages() {
		Instant now = clock.now();
		Instant staleBefore = now.minus(properties.drain().reclaimAfter());
		int maxAttempts = properties.maxAttempts();
		messages.failExhaustedStuck(staleBefore, now, maxAttempts);
		messages.reclaimStuck(staleBefore, now, maxAttempts);
	}

	@Transactional(readOnly = true)
	public List<Long> due() {
		return messages.due(clock.now(), Limit.of(properties.drain().batchSize()));
	}

	/**
	 * Claims one message, re-checks that it is still true (INV-B11), and — for invitation and management-link
	 * messages only — generates the link and stores its hash before the handoff (KD13).
	 *
	 * @return the job to hand over, or empty if the message was claimed by nobody, or skipped
	 */
	@Transactional
	public Optional<SendJob> claimAndPrepare(long messageId) {
		Instant now = clock.now();
		if (messages.claim(messageId, now) == 0) {
			return Optional.empty();
		}
		OutboundMessage message = messages.findById(messageId).orElseThrow();
		Event event = message.getEvent();
		Guest guest = message.getGuest();

		String skip = reasonToSkip(message, event, guest);
		if (skip != null) {
			messages.finish(messageId, now, MessageStatus.SKIPPED.name(), now, skip);
			return Optional.empty();
		}

		String link = null;
		if (message.getKind() == MessageKind.MANAGEMENT_LINK) {
			link = storeManagementLink(event, now);
		}
		else if (message.getKind() == MessageKind.INVITATION) {
			link = storeGuestLink(guest, event, now);
		}
		String recipient = guest == null ? event.getHostEmail() : guest.getEmail();
		return Optional.of(new SendJob(messageId, now,
				new OutgoingMail(recipient, message.getKind(), link, event.getTitle())));
	}

	/** Re-checked at send time, never at queue time: what was true when queued may not be true now. */
	private String reasonToSkip(OutboundMessage message, Event event, Guest guest) {
		boolean cancelled = event.getStatus() == EventStatus.CANCELLED;
		if (cancelled && message.getKind() != MessageKind.CANCELLATION_NOTICE) {
			return "the event was cancelled";
		}
		if (message.getKind() == MessageKind.PROMOTION_NOTICE) {
			ReplyState state = replies.findByGuestId(guest.getId()).map(Reply::getState).orElse(null);
			if (state != ReplyState.CONFIRMED) {
				return "the guest is no longer confirmed";
			}
		}
		return null;
	}

	private String storeManagementLink(Event event, Instant now) {
		String token = LinkTokens.generate();
		byte[] hash = LinkTokens.hash(token);
		managementLinks.findByEventId(event.getId())
				.ifPresentOrElse(existing -> existing.replaceWith(hash, now),
						() -> managementLinks.save(new ManagementLink(event, hash, now)));
		return properties.publicBaseUrl() + "/m/" + token;
	}

	private String storeGuestLink(Guest guest, Event event, Instant now) {
		String token = LinkTokens.generate();
		byte[] hash = LinkTokens.hash(token);
		guestLinks.findByGuestId(guest.getId())
				.ifPresentOrElse(existing -> existing.replaceWith(hash, now),
						() -> guestLinks.save(new GuestLink(guest, event, hash, now)));
		return properties.publicBaseUrl() + "/i/" + token;
	}

	@Transactional
	public void recordSent(SendJob job) {
		messages.finish(job.messageId(), job.claimedAt(), MessageStatus.SENT.name(), clock.now(), null);
	}

	/** A failed attempt waits for its next one; after the last, the message is final failed (D15). */
	@Transactional
	public void recordFailure(SendJob job, String error) {
		Instant now = clock.now();
		OutboundMessage message = messages.findById(job.messageId()).orElseThrow();
		List<java.time.Duration> delays = properties.drain().retryDelays();
		int attempts = message.getAttempts();
		if (attempts > delays.size()) {
			messages.finish(job.messageId(), job.claimedAt(), MessageStatus.FAILED.name(), now, error);
		}
		else {
			messages.requeueForRetry(job.messageId(), job.claimedAt(), now.plus(delays.get(attempts - 1)), error);
		}
	}
}
