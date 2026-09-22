package com.xperience.hero.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Written by Event Management only. Description and location are nullable here and required by validation
 * (DESIGN.md → Step 10, Stage 1 decision). Text columns are {@code text} because a column cannot be retyped later (T2).
 */
@Entity
@Table(name = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, columnDefinition = "text")
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(columnDefinition = "text")
	private String location;

	/** An absolute instant, compared with the database clock (Q5 decides how the host's entry becomes this). */
	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	/** Null = no capacity: every Yes is Confirmed and there is never a waitlist (A3). */
	private Integer capacity;

	/** Stored as plain text; see {@link #getStatus()}. */
	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "host_email", nullable = false, length = 320)
	private String hostEmail;

	@Column(name = "host_verified", nullable = false)
	private boolean hostVerified;

	public Event(String title, String description, String location, Instant startTime, Integer capacity,
			String hostEmail) {
		this.title = title;
		this.description = description;
		this.location = location;
		this.startTime = startTime;
		this.capacity = capacity;
		this.hostEmail = hostEmail;
		this.status = EventStatus.OPEN.name();
		this.hostVerified = false;
	}

	/**
	 * Enum columns are mapped as {@code String}: for any enum-typed attribute, even behind a converter, Hibernate adds
	 * a {@code CHECK (x IN (...))} constraint that {@code ddl-auto: update} never changes (T2), so a value added later
	 * could never be stored.
	 */
	public EventStatus getStatus() {
		return EventStatus.valueOf(status);
	}

	/** Only {@link EventStatusService}, under the event lock. */
	void setStatus(EventStatus status) {
		this.status = status.name();
	}

	/** U8 (D9): only an explicit confirmation from the management link reaches this. */
	void verifyHost() {
		this.hostVerified = true;
	}
}
