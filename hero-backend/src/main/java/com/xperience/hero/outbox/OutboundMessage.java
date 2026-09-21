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

	public static OutboundMessage queued(MessageKind kind, Event event, Guest guest, Instant createdAt) {
		OutboundMessage m = new OutboundMessage();
		m.kind = kind.name();
		m.event = event;
		m.guest = guest;
		m.status = MessageStatus.QUEUED.name();
		m.createdAt = createdAt;
		return m;
	}

	public MessageKind getKind() {
		return MessageKind.valueOf(kind);
	}

	public MessageStatus getStatus() {
		return MessageStatus.valueOf(status);
	}
}
