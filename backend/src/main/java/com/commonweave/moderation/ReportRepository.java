package com.commonweave.moderation;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {
    List<Report> findByCircleIdOrderByIdDesc(Long circleId);

    List<Report> findByCircleIdAndStatusOrderByIdDesc(Long circleId, ReportStatus status);

    long countByStatus(ReportStatus status);
}
