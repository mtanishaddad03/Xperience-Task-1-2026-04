package com.xperience.hero.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The one switch that belongs in the first release (Rollout → Flags): pausing the drain without redeploying.
 * Queued messages simply wait, so pausing loses nothing. The operator holds no link, so it is changed with SQL:
 * {@code update hero.drain_control set paused = true;}
 */
@Entity
@Table(name = "drain_control")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrainControl {

	public static final int ROW_ID = 1;

	@Id
	private Integer id;

	@Column(nullable = false)
	private boolean paused;
}
