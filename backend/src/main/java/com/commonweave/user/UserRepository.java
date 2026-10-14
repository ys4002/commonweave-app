package com.commonweave.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Locks every enabled admin row so concurrent demotions serialise and the last-admin check is race-safe. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.platformRole = :role and u.enabled = true order by u.id")
    List<User> lockEnabledByRole(@Param("role") PlatformRole role);

    long countByEnabled(boolean enabled);

    long countByPlatformRole(PlatformRole role);

    long countByPlatformRoleAndEnabled(PlatformRole role, boolean enabled);
}
