package com.commonweave.moderation;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
    List<ModerationAction> findByCircleIdOrderByIdDesc(Long circleId, Pageable pageable);
}
