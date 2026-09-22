package com.xperience.hero.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ManagementLinkRepository extends JpaRepository<ManagementLink, Long> {

	@Query("select l from ManagementLink l where l.tokenHash = :hash")
	Optional<ManagementLink> findByTokenHash(@Param("hash") byte[] hash);

	@Query("select l from ManagementLink l where l.event.id = :eventId")
	Optional<ManagementLink> findByEventId(@Param("eventId") long eventId);
}
