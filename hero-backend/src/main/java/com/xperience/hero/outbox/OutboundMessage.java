package com.xperience.hero.outbox;

import com.xperience.hero.event.Event;
import com.xperience.hero.guest.Guest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Created by the component that owns its cause, always as queued. Its status is written only by the Outbox Drain
 * (Stage 2–3). A resend creates a new message rather than resetting an old one.
 */
@Entity
@Table(name = "outbound_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboundMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	/** The recipient guest; null for a message to the host (management link). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "guest_id")
	private Guest guest;

	/** Stored as plain text, as in {@code Event#getStatus()}. */
	@Column(nullable = false, length = 32)
	private String kind;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Attempts so far, counted when the drain claims the message (D15). */
	@Column(nullable = false)
	private int attempts;

	/** When the drain may next attempt it: now for a new message, later after a failed attempt (D15). */
	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	/** When this attempt was claimed; used to reclaim a message the drain crashed on. */
	@Column(name = "claimed_at")
	private Instant claimedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	public static OutboundMessage queued(MessageKind kind, Event event, Guest guest, Instant createdAt) {
		OutboundMessage m = new OutboundMessage();
		m.kind = kind.name();
		m.event = event;
		m.guest = guest;
		m.status = MessageStatus.QUEUED.name();
		m.createdAt = createdAt;
		m.nextAttemptAt = createdAt;
		m.attempts = 0;
		return m;
	}

	public MessageKind getKind() {
		return MessageKind.valueOf(kind);
	}

	public MessageStatus getStatus() {
		return MessageStatus.valueOf(status);
	}
}
