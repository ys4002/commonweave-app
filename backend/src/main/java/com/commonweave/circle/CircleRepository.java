package com.commonweave.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CircleRepository extends JpaRepository<Circle, Long>, JpaSpecificationExecutor<Circle> {
    boolean existsBySlug(String slug);
}
