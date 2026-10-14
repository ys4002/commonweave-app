package com.commonweave.listing;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") Long id);

    /** Candidates for matching: only listings whose owner is still an active member of the circle. */
    @Query("select l from Listing l where l.circleId = :circleId and l.type = :type and l.status = :status "
            + "and l.ownerId <> :ownerId and exists (select m.id from Membership m where m.circleId = l.circleId and m.userId = l.ownerId and m.status = com.commonweave.circle.MemberStatus.ACTIVE)")
    List<Listing> findMatchCandidates(@Param("circleId") Long circleId, @Param("type") ListingType type,
                                      @Param("status") ListingStatus status, @Param("ownerId") Long ownerId);

    List<Listing> findByOwnerIdAndStatusAndCircleIdIn(Long ownerId, ListingStatus status, Collection<Long> circleIds);

    long countByStatus(ListingStatus status);

    List<Listing> findByOwnerIdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    @Query("select l.ownerId, count(l) from Listing l where l.ownerId in :ids group by l.ownerId")
    List<Object[]> countByOwners(@Param("ids") Collection<Long> ids);

    List<Listing> findByOwnerIdAndCircleIdAndStatus(Long ownerId, Long circleId, ListingStatus status);

    @Query("select l from Listing l where l.circleId in :circleIds and l.status <> :excluded and exists (select m.id from Membership m where m.circleId = l.circleId and m.userId = l.ownerId and m.status = com.commonweave.circle.MemberStatus.ACTIVE) "
            + "order by l.createdAt desc")
    List<Listing> findVisibleRecent(@Param("circleIds") Collection<Long> circleIds,
                                    @Param("excluded") ListingStatus excluded, Pageable pageable);

    @Query("select l.circleId, count(l) from Listing l where l.circleId in :ids and l.status = :status "
            + "and exists (select m.id from Membership m where m.circleId = l.circleId and m.userId = l.ownerId and m.status = com.commonweave.circle.MemberStatus.ACTIVE) group by l.circleId")
    List<Object[]> countByCircles(@Param("ids") Collection<Long> ids, @Param("status") ListingStatus status);
}
