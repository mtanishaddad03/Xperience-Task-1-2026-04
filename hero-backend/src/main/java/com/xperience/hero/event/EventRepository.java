package com.xperience.hero.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

	/**
	 * The per-event lock (KD2): {@code SELECT ... FOR UPDATE} on the event row, held until the transaction ends.
	 * Every reply change and every status change starts here; invitations and the drain never take it.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Event e where e.id = :id")
	Optional<Event> lockById(@Param("id") long id);
}
