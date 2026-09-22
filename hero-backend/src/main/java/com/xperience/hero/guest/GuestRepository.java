package com.xperience.hero.guest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {

	/** Scoped by event: a guest id from another event is not found (INV-T1). */
	@Query("select g from Guest g where g.id = :guestId and g.event.id = :eventId")
	Optional<Guest> findInEvent(@Param("eventId") long eventId, @Param("guestId") long guestId);

	@Query("select g from Guest g where g.event.id = :eventId and g.email = :email")
	Optional<Guest> findByEventIdAndEmail(@Param("eventId") long eventId, @Param("email") String email);

	@Query("select g.email from Guest g where g.event.id = :eventId")
	List<String> findEmailsByEventId(@Param("eventId") long eventId);

	@Query("select g from Guest g where g.event.id = :eventId order by g.email")
	List<Guest> findByEventIdOrderByEmail(@Param("eventId") long eventId);

	@Query("select count(g) from Guest g where g.event.id = :eventId")
	long countByEventId(@Param("eventId") long eventId);
}
