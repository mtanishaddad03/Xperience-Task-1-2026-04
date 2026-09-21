package com.xperience.hero.guest;

import com.xperience.hero.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Written by Event Management, create only. An email address is invited at most once per event, compared after
 * normalisation (D5, INV-D6). A guest with no Reply is Pending.
 */
@Entity
@Table(name = "guest", uniqueConstraints = @UniqueConstraint(name = "uk_guest_event_email", columnNames = {"event_id", "email"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Column(nullable = false, length = 320)
	private String email;

	public Guest(Event event, String email) {
		this.event = event;
		this.email = normaliseEmail(email);
	}

	/** Trimmed and lower-cased (D5). */
	public static String normaliseEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}
}
