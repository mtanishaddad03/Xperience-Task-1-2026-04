package com.xperience.hero.link;

import com.xperience.hero.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The host's link for one event. Written by the Outbox Drain only, at send time (KD13); only the hash is stored,
 * so a stored link can never be shown again — only replaced (INV-A7).
 */
@Entity
@Table(name = "management_link", uniqueConstraints = {
		@UniqueConstraint(name = "uk_management_link_event", columnNames = "event_id"),
		@UniqueConstraint(name = "uk_management_link_hash", columnNames = "token_hash")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManagementLink {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Column(name = "token_hash", nullable = false, length = 32)
	private byte[] tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public ManagementLink(Event event, byte[] tokenHash, Instant createdAt) {
		this.event = event;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
	}

	/** Replacing a link is one change to one row. */
	public void replaceWith(byte[] tokenHash, Instant createdAt) {
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
	}
}
