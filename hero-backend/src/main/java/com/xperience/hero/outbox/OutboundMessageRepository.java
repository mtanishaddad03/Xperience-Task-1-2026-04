package com.xperience.hero.outbox;

import com.xperience.hero.guest.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboundMessageRepository extends JpaRepository<OutboundMessage, Long> {

	List<OutboundMessage> findByEventIdOrderById(long eventId);

	/** Guests of the event with at least one invitation in status sent — the recipients of a cancellation notice (D10). */
	@Query("""
			select distinct m.guest from OutboundMessage m
			where m.event.id = :eventId
			  and m.kind = 'INVITATION'
			  and m.status = 'SENT'
			""")
	List<Guest> guestsWithSentInvitation(@Param("eventId") long eventId);
}
