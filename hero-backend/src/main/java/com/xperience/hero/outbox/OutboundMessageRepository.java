package com.xperience.hero.outbox;

import com.xperience.hero.guest.Guest;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

	// --- limits (D14) -----------------------------------------------------

	@Query("""
			select count(m) from OutboundMessage m
			where m.kind = 'INVITATION' and m.event.hostEmail = :hostEmail and m.createdAt > :since
			""")
	long countInvitationsSince(@Param("hostEmail") String hostEmail, @Param("since") Instant since);

	@Query("""
			select count(m) from OutboundMessage m
			where m.kind = 'MANAGEMENT_LINK' and m.event.hostEmail = :address and m.createdAt > :since
			""")
	long countManagementLinksSince(@Param("address") String address, @Param("since") Instant since);

	// --- host view --------------------------------------------------------

	@Query("""
			select m.guest.id, m.status from OutboundMessage m
			where m.event.id = :eventId and m.kind = 'INVITATION' order by m.id
			""")
	List<Object[]> invitationStatuses(@Param("eventId") long eventId);

	@Query("select m.status, count(m) from OutboundMessage m where m.event.id = :eventId group by m.status")
	List<Object[]> statusCounts(@Param("eventId") long eventId);

	/** The guest's latest invitation, which decides whether a resend is allowed (U10). */
	@Query("""
			select m from OutboundMessage m
			where m.guest.id = :guestId and m.kind = 'INVITATION' order by m.id desc limit 1
			""")
	Optional<OutboundMessage> latestInvitationFor(@Param("guestId") long guestId);

	// --- drain ------------------------------------------------------------

	@Query("""
			select m.id from OutboundMessage m
			where m.status = 'QUEUED' and m.nextAttemptAt <= :now order by m.nextAttemptAt, m.id
			""")
	List<Long> due(@Param("now") Instant now, Limit limit);

	/** Claims one message for this attempt — only if it is still queued. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update OutboundMessage m set m.status = 'SENDING', m.claimedAt = :now, m.attempts = m.attempts + 1
			where m.id = :id and m.status = 'QUEUED'
			""")
	int claim(@Param("id") long id, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update OutboundMessage m set m.status = :status, m.finishedAt = :now, m.lastError = :error
			where m.id = :id and m.status = 'SENDING' and m.claimedAt = :claimedAt
			""")
	int finish(@Param("id") long id, @Param("claimedAt") Instant claimedAt, @Param("status") String status,
			@Param("now") Instant now, @Param("error") String error);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update OutboundMessage m
			set m.status = 'QUEUED', m.claimedAt = null, m.nextAttemptAt = :nextAttemptAt, m.lastError = :error
			where m.id = :id and m.status = 'SENDING' and m.claimedAt = :claimedAt
			""")
	int requeueForRetry(@Param("id") long id, @Param("claimedAt") Instant claimedAt,
			@Param("nextAttemptAt") Instant nextAttemptAt, @Param("error") String error);

	/** Reclaim after a crash: a message with attempts left waits to be tried again. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update OutboundMessage m set m.status = 'QUEUED', m.claimedAt = null, m.nextAttemptAt = :now,
			    m.lastError = 'reclaimed: the drain stopped while sending'
			where m.status = 'SENDING' and m.claimedAt < :staleBefore and m.attempts < :maxAttempts
			""")
	int reclaimStuck(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now,
			@Param("maxAttempts") int maxAttempts);

	/** Reclaim must not resurrect an exhausted message: it is final failed instead. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update OutboundMessage m set m.status = 'FAILED', m.claimedAt = null, m.finishedAt = :now,
			    m.lastError = 'reclaimed after the last attempt'
			where m.status = 'SENDING' and m.claimedAt < :staleBefore and m.attempts >= :maxAttempts
			""")
	int failExhaustedStuck(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now,
			@Param("maxAttempts") int maxAttempts);
}
