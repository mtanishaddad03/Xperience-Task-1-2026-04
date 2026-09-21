package com.xperience.hero.reply;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

	@Query("select r from Reply r where r.guest.id = :guestId")
	Optional<Reply> findByGuestId(@Param("guestId") long guestId);

	/** The one definition of a count per state (KD5, KD12) — used by the capacity decision and the host view. */
	default long countInState(long eventId, ReplyState state) {
		return countInState(eventId, state.name());
	}

	@Query("select count(r) from Reply r where r.guest.event.id = :eventId and r.state = :state")
	long countInState(@Param("eventId") long eventId, @Param("state") String state);

	/** The one definition of waitlist order: entered-state time, then reply id as tie-breaker (INV-D5). */
	@Query("""
			select r from Reply r
			where r.guest.event.id = :eventId
			  and r.state = 'WAITLISTED'
			order by r.enteredStateAt, r.id
			""")
	List<Reply> waitlistInOrder(@Param("eventId") long eventId);
}
