package com.commonweave.circle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByCircleIdAndUserId(Long circleId, Long userId);

    List<Membership> findByUserIdAndStatus(Long userId, MemberStatus status);

    List<Membership> findByUserIdAndStatusAndRole(Long userId, MemberStatus status, MemberRole role);

    List<Membership> findByCircleId(Long circleId);

    List<Membership> findByUserIdIn(Collection<Long> userIds);

    List<Membership> findByCircleIdAndStatus(Long circleId, MemberStatus status);

    List<Membership> findByCircleIdAndRoleAndStatus(Long circleId, MemberRole role, MemberStatus status);

    long countByCircleIdAndRoleAndStatus(Long circleId, MemberRole role, MemberStatus status);

    long countByCircleIdAndStatus(Long circleId, MemberStatus status);

    @Query("select m.circleId, count(m) from Membership m "
            + "where m.circleId in :ids and m.status = :status group by m.circleId")
    List<Object[]> countByCircles(@Param("ids") Collection<Long> ids, @Param("status") MemberStatus status);
}
