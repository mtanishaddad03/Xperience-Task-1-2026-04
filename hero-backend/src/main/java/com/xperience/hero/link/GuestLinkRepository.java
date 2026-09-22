package com.xperience.hero.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GuestLinkRepository extends JpaRepository<GuestLink, Long> {

	@Query("select l from GuestLink l where l.tokenHash = :hash")
	Optional<GuestLink> findByTokenHash(@Param("hash") byte[] hash);

	@Query("select l from GuestLink l where l.guest.id = :guestId")
	Optional<GuestLink> findByGuestId(@Param("guestId") long guestId);
}
