package com.commonweave.handoff;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HandoffRepository extends JpaRepository<Handoff, Long>, JpaSpecificationExecutor<Handoff> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Handoff h where h.id = :id")
    Optional<Handoff> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(HandoffStatus status);

    List<Handoff> findByProviderIdOrRecipientIdOrderByUpdatedAtDesc(Long providerId, Long recipientId, Pageable pageable);

    boolean existsByListingIdAndStatusIn(Long listingId, Collection<HandoffStatus> statuses);

    boolean existsByListingIdAndProposerIdAndStatusIn(Long listingId, Long proposerId,
                                                      Collection<HandoffStatus> statuses);

    List<Handoff> findByListingIdAndStatusIn(Long listingId, Collection<HandoffStatus> statuses);

    Optional<Handoff> findFirstByListingIdAndProposerIdAndStatusNotOrderByIdDesc(Long listingId, Long proposerId,
                                                                                 HandoffStatus excluded);

    List<Handoff> findByCircleIdInAndStatusInOrderByUpdatedAtDesc(Collection<Long> circleIds,
                                                                  Collection<HandoffStatus> statuses,
                                                                  Pageable pageable);

    List<Handoff> findByCircleIdAndStatusInAndUpdatedAtGreaterThanEqual(Long circleId,
                                                                        Collection<HandoffStatus> statuses,
                                                                        Instant since);

    @Query("select h from Handoff h where (h.providerId = :u or h.recipientId = :u) "
            + "and h.status in :statuses and h.updatedAt >= :since")
    List<Handoff> findMineSince(@Param("u") Long userId, @Param("statuses") Collection<HandoffStatus> statuses,
                                @Param("since") Instant since);

    @Query("select h from Handoff h where (h.providerId = :u or h.recipientId = :u) and h.status = :status")
    List<Handoff> findMineByStatus(@Param("u") Long userId, @Param("status") HandoffStatus status);

    @Query("select count(h) > 0 from Handoff h where h.circleId = :circleId "
            + "and (h.providerId = :u or h.recipientId = :u) and h.status in :statuses")
    boolean existsMineInCircle(@Param("circleId") Long circleId, @Param("u") Long userId,
                               @Param("statuses") Collection<HandoffStatus> statuses);

    List<Handoff> findByStatusAndScheduledAtBetween(HandoffStatus status, Instant from, Instant to);

    List<Handoff> findByStatusAndReturnExpectedTrueAndReturnByBetween(HandoffStatus status, Instant from, Instant to);

    List<Handoff> findByStatusAndReturnExpectedTrueAndReturnByBefore(HandoffStatus status, Instant before);

    @Query("select h.providerId, count(h) from Handoff h where h.providerId in :ids and h.status in :statuses "
            + "group by h.providerId")
    List<Object[]> countAsProvider(@Param("ids") Collection<Long> ids,
                                   @Param("statuses") Collection<HandoffStatus> statuses);

    @Query("select h.recipientId, count(h) from Handoff h where h.recipientId in :ids and h.status in :statuses "
            + "group by h.recipientId")
    List<Object[]> countAsRecipient(@Param("ids") Collection<Long> ids,
                                    @Param("statuses") Collection<HandoffStatus> statuses);
}
