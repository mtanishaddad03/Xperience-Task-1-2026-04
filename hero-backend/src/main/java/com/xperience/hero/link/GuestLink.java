package com.xperience.hero.link;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One guest's link. Written by the Outbox Drain only (KD13). A separate table from the host's, so the two kinds
 * of link can never be interchanged: each is looked up only where its own kind lives. */
@Entity
@Table(name = "guest_link", uniqueConstraints = {
		@UniqueConstraint(name = "uk_guest_link_guest", columnNames = "guest_id"),
		@UniqueConstraint(name = "uk_guest_link_hash", columnNames = "token_hash")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestLink {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guest_id", nullable = false)
	private Guest guest;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Column(name = "token_hash", nullable = false, length = 32)
	private byte[] tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public GuestLink(Guest guest, Event event, byte[] tokenHash, Instant createdAt) {
		this.guest = guest;
		this.event = event;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
	}

	public void replaceWith(byte[] tokenHash, Instant createdAt) {
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
	}
}
