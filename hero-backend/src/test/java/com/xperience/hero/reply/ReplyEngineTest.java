package com.xperience.hero.reply;

import com.xperience.hero.IntegrationTest;
import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.event.EventStatusService;
import com.xperience.hero.outbox.MessageKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sequential behaviour of the reply unit. The concurrent cases are in {@link Stage1ExitConditionsTest}. */
class ReplyEngineTest extends IntegrationTest {

	@Autowired
	EventStatusService eventStatus;

	@Test
	void noCapacity_everyYesIsConfirmed_andNobodyIsWaitlisted() {
		long eventId = newEvent(null);
		for (long guestId : newGuests(eventId, 5)) {
			assertThat(reply(eventId, guestId, ReplyChoice.YES).state()).isEqualTo(ReplyState.CONFIRMED);
		}
		assertThat(replies.countInState(eventId, ReplyState.CONFIRMED)).isEqualTo(5);
		assertThat(waitlistGuestIds(eventId)).isEmpty();
	}

	@Test
	void firstReplies_recordDeclinedAndMaybe() {
		long eventId = newEvent(1);
		List<Long> g = newGuests(eventId, 2);
		assertThat(reply(eventId, g.get(0), ReplyChoice.NO).state()).isEqualTo(ReplyState.DECLINED);
		assertThat(reply(eventId, g.get(1), ReplyChoice.MAYBE).state()).isEqualTo(ReplyState.MAYBE);
	}

	@Test
	void confirmedToMaybe_releasesThePlace_andPromotesWithNotice() {
		long eventId = newEvent(1);
		List<Long> g = newGuests(eventId, 2);
		reply(eventId, g.get(0), ReplyChoice.YES);
		reply(eventId, g.get(1), ReplyChoice.YES);

		assertThat(reply(eventId, g.get(0), ReplyChoice.MAYBE).state()).isEqualTo(ReplyState.MAYBE);

		assertThat(replyOf(g.get(1)).orElseThrow().getState()).isEqualTo(ReplyState.CONFIRMED);
		assertThat(messagesOf(eventId)).singleElement()
				.matches(m -> m.getKind() == MessageKind.PROMOTION_NOTICE && m.getGuest().getId().equals(g.get(1)));
	}

	@Test
	void promotion_setsEnteredStateTimeToTheReleasingUnitsClock() {
		long eventId = newEvent(1);
		List<Long> g = newGuests(eventId, 2);
		reply(eventId, g.get(0), ReplyChoice.YES);
		reply(eventId, g.get(1), ReplyChoice.YES);

		ReplyResult release = reply(eventId, g.get(0), ReplyChoice.NO);

		assertThat(replyOf(g.get(1)).orElseThrow().getEnteredStateAt()).isEqualTo(release.decidedAt());
	}

	@Test
	void waitlistedGuestLeaving_promotesNobody() {
		long eventId = newEvent(1);
		List<Long> g = newGuests(eventId, 3);
		reply(eventId, g.get(0), ReplyChoice.YES);
		reply(eventId, g.get(1), ReplyChoice.YES);
		reply(eventId, g.get(2), ReplyChoice.YES);

		assertThat(reply(eventId, g.get(1), ReplyChoice.NO).state()).isEqualTo(ReplyState.DECLINED);

		assertThat(replyOf(g.get(0)).orElseThrow().getState()).isEqualTo(ReplyState.CONFIRMED);
		assertThat(waitlistGuestIds(eventId)).containsExactly(g.get(2));
		assertThat(messagesOf(eventId)).isEmpty();
	}

	@Test
	void retriedNo_afterItsPromotion_isANoOp() {
		long eventId = newEvent(1);
		List<Long> g = newGuests(eventId, 3);
		reply(eventId, g.get(0), ReplyChoice.YES);
		reply(eventId, g.get(1), ReplyChoice.YES);
		reply(eventId, g.get(2), ReplyChoice.YES);
		reply(eventId, g.get(0), ReplyChoice.NO);
		Instant declinedAt = replyOf(g.get(0)).orElseThrow().getEnteredStateAt();

		ReplyResult retry = reply(eventId, g.get(0), ReplyChoice.NO);

		assertThat(retry.accepted()).isTrue();
		assertThat(retry.state()).isEqualTo(ReplyState.DECLINED);
		assertThat(replyOf(g.get(0)).orElseThrow().getEnteredStateAt()).isEqualTo(declinedAt);
		assertThat(waitlistGuestIds(eventId)).containsExactly(g.get(2));
		assertThat(messagesOf(eventId)).hasSize(1);
	}

	@Test
	void declinedThenYes_goesBackThroughTheCapacityDecision_atTheBack() {
		long eventId = newEvent(1);
		List<Long> g = newGuests(eventId, 3);
		reply(eventId, g.get(0), ReplyChoice.YES);
		reply(eventId, g.get(1), ReplyChoice.YES);
		reply(eventId, g.get(2), ReplyChoice.YES);
		reply(eventId, g.get(1), ReplyChoice.MAYBE);

		assertThat(reply(eventId, g.get(1), ReplyChoice.YES).state()).isEqualTo(ReplyState.WAITLISTED);
		assertThat(waitlistGuestIds(eventId)).containsExactly(g.get(2), g.get(1));
	}

	@Test
	void closedEvent_refusesEvenADecline() {
		long eventId = newEvent(null);
		long guestId = newGuests(eventId, 1).get(0);
		reply(eventId, guestId, ReplyChoice.YES);
		eventStatus.close(eventId);

		ReplyResult result = reply(eventId, guestId, ReplyChoice.NO);

		assertThat(result.accepted()).isFalse();
		assertThat(result.refusal()).isEqualTo(ReplyRefusal.CLOSED);
		assertThat(replyOf(guestId).orElseThrow().getState()).isEqualTo(ReplyState.CONFIRMED);
	}

	@Test
	void startedEvent_refusesReplies() {
		long eventId = newEvent(null, dbNow().minus(Duration.ofMinutes(1)));
		long guestId = newGuests(eventId, 1).get(0);

		ReplyResult result = reply(eventId, guestId, ReplyChoice.YES);

		assertThat(result.accepted()).isFalse();
		assertThat(result.refusal()).isEqualTo(ReplyRefusal.STARTED);
		assertThat(replyOf(guestId)).isEmpty();
	}

	@Test
	void guestOfAnotherEvent_isNotFound() {
		long eventA = newEvent(null);
		long eventB = newEvent(null);
		long guestOfB = newGuests(eventB, 1).get(0);

		assertThatThrownBy(() -> reply(eventA, guestOfB, ReplyChoice.YES)).isInstanceOf(NotFoundException.class);
		assertThat(replyOf(guestOfB)).isEmpty();
	}

	@Test
	void unknownEvent_isNotFound() {
		assertThatThrownBy(() -> reply(999_999, 1, ReplyChoice.YES)).isInstanceOf(NotFoundException.class);
	}
}
