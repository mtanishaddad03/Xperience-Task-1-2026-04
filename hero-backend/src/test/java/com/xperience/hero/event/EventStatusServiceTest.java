package com.xperience.hero.event;

import com.xperience.hero.IntegrationTest;
import com.xperience.hero.common.NotFoundException;
import com.xperience.hero.outbox.MessageKind;
import com.xperience.hero.outbox.MessageStatus;
import com.xperience.hero.outbox.OutboundMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStatusServiceTest extends IntegrationTest {

	@Autowired
	EventStatusService eventStatus;

	private EventStatus statusOf(long eventId) {
		return events.findById(eventId).orElseThrow().getStatus();
	}

	@Test
	void openEvent_canBeClosed_thenCancelled() {
		long eventId = newEvent(null);

		assertThat(eventStatus.close(eventId)).isEqualTo(StatusChange.CHANGED);
		assertThat(statusOf(eventId)).isEqualTo(EventStatus.CLOSED);
		assertThat(eventStatus.cancel(eventId)).isEqualTo(StatusChange.CHANGED);
		assertThat(statusOf(eventId)).isEqualTo(EventStatus.CANCELLED);
	}

	@Test
	void cancelledEvent_cannotBeClosed() {
		long eventId = newEvent(null);
		eventStatus.cancel(eventId);

		assertThat(eventStatus.close(eventId)).isEqualTo(StatusChange.NOT_ALLOWED);
		assertThat(statusOf(eventId)).isEqualTo(EventStatus.CANCELLED);
	}

	@Test
	void repeatedCloseOrCancel_changesNothing() {
		long closed = newEvent(null);
		eventStatus.close(closed);
		assertThat(eventStatus.close(closed)).isEqualTo(StatusChange.ALREADY_IN_STATE);

		long cancelled = newEvent(null);
		long guestId = newGuests(cancelled, 1).get(0);
		insertInvitation(cancelled, guestId, "SENT");
		eventStatus.cancel(cancelled);
		assertThat(eventStatus.cancel(cancelled)).isEqualTo(StatusChange.ALREADY_IN_STATE);
		assertThat(messagesOf(cancelled)).filteredOn(m -> m.getKind() == MessageKind.CANCELLATION_NOTICE).hasSize(1);
	}

	@Test
	void cancel_recordsOneNoticeForEveryGuestWhoseInvitationWasSent() {
		long eventId = newEvent(null);
		List<Long> g = newGuests(eventId, 4);
		insertInvitation(eventId, g.get(0), "SENT");
		insertInvitation(eventId, g.get(0), "SENT"); // resent: still one notice
		insertInvitation(eventId, g.get(1), "QUEUED");
		insertInvitation(eventId, g.get(2), "FAILED");
		insertInvitation(eventId, g.get(3), "FAILED");
		insertInvitation(eventId, g.get(3), "SENT");

		eventStatus.cancel(eventId);

		List<OutboundMessage> notices = messagesOf(eventId).stream()
				.filter(m -> m.getKind() == MessageKind.CANCELLATION_NOTICE).toList();
		assertThat(notices).allMatch(m -> m.getStatus() == MessageStatus.QUEUED);
		assertThat(notices.stream().map(m -> m.getGuest().getId())).containsExactlyInAnyOrder(g.get(0), g.get(3));
	}

	@Test
	void close_recordsNoNotices() {
		long eventId = newEvent(null);
		long guestId = newGuests(eventId, 1).get(0);
		insertInvitation(eventId, guestId, "SENT");

		eventStatus.close(eventId);

		assertThat(messagesOf(eventId)).allMatch(m -> m.getKind() == MessageKind.INVITATION);
	}

	@Test
	void unknownEvent_isNotFound() {
		assertThatThrownBy(() -> eventStatus.cancel(999_999)).isInstanceOf(NotFoundException.class);
	}
}
