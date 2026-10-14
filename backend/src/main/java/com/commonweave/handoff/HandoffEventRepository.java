package com.commonweave.handoff;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoffEventRepository extends JpaRepository<HandoffEvent, Long> {
    List<HandoffEvent> findByHandoffIdOrderByIdAsc(Long handoffId);
}
