package com.xperience.hero.reply;

import com.xperience.hero.guest.Guest;
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

/** Written by the Reply Engine only, inside the event lock. At most one per guest (INV-D2). */
@Entity
@Table(name = "reply", uniqueConstraints = @UniqueConstraint(name = "uk_reply_guest", columnNames = "guest_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reply {

	/** Also the waitlist tie-breaker (INV-D5). */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guest_id", nullable = false)
	private Guest guest;

	/** Stored as plain text, as in {@code Event#getStatus()}. */
	@Column(nullable = false, length = 32)
	private String state;

	/** Database clock after the event lock; written on every actual change of state, never on a no-op (INV-B6). */
	@Column(name = "entered_state_at", nullable = false)
	private Instant enteredStateAt;

	Reply(Guest guest, ReplyState state, Instant at) {
		this.guest = guest;
		this.state = state.name();
		this.enteredStateAt = at;
	}

	public ReplyState getState() {
		return ReplyState.valueOf(state);
	}

	void moveTo(ReplyState newState, Instant at) {
		this.state = newState.name();
		this.enteredStateAt = at;
	}
}
