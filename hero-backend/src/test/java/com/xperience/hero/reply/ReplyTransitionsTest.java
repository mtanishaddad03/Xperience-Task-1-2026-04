package com.xperience.hero.reply;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Every cell of the transition table in DESIGN.md → Data Ownership and State Model → Reply transitions. */
class ReplyTransitionsTest {

	@ParameterizedTest(name = "{0} + {1} (place free: {2}) → {3}, releases: {4}")
	@CsvSource(nullValues = "-", value = {
			// current,  choice, placeFree, target,     releases
			"-,          YES,    true,      CONFIRMED,  false",
			"-,          YES,    false,     WAITLISTED, false",
			"-,          NO,     true,      DECLINED,   false",
			"-,          MAYBE,  true,      MAYBE,      false",
			"CONFIRMED,  YES,    false,     -,          false",
			"CONFIRMED,  NO,     true,      DECLINED,   true",
			"CONFIRMED,  MAYBE,  true,      MAYBE,      true",
			"WAITLISTED, YES,    true,      -,          false",
			"WAITLISTED, YES,    false,     -,          false",
			"WAITLISTED, NO,     true,      DECLINED,   false",
			"WAITLISTED, MAYBE,  true,      MAYBE,      false",
			"DECLINED,   YES,    true,      CONFIRMED,  false",
			"DECLINED,   YES,    false,     WAITLISTED, false",
			"DECLINED,   NO,     true,      -,          false",
			"DECLINED,   MAYBE,  true,      MAYBE,      false",
			"MAYBE,      YES,    true,      CONFIRMED,  false",
			"MAYBE,      YES,    false,     WAITLISTED, false",
			"MAYBE,      NO,     true,      DECLINED,   false",
			"MAYBE,      MAYBE,  true,      -,          false",
	})
	void transition(ReplyState current, ReplyChoice choice, boolean placeFree, ReplyState target, boolean releases) {
		ReplyTransitions.Transition t = ReplyTransitions.decide(current, choice, () -> placeFree);

		assertThat(t.isNoOp()).isEqualTo(target == null);
		assertThat(t.target()).isEqualTo(target);
		assertThat(t.releasesPlace()).isEqualTo(releases);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = {"-, NO", "-, MAYBE", "CONFIRMED, NO", "WAITLISTED, YES", "WAITLISTED, NO", "DECLINED, MAYBE"})
	void capacityDecisionIsMadeOnlyWhenItMatters(ReplyState current, ReplyChoice choice) {
		ReplyTransitions.decide(current, choice, () -> {
			throw new AssertionError("capacity decision should not be made");
		});
	}
}
