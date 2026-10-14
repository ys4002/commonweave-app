package com.commonweave.notification;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNull(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    java.util.Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    /** Race-free dedupe: the unique (user_id, dedupe_key) index decides, INSERT IGNORE swallows the loser. */
    @Modifying
    @Query(value = "INSERT IGNORE INTO notifications (user_id, type, title, body, link, dedupe_key, created_at) "
            + "VALUES (:userId, :type, :title, :body, :link, :dedupeKey, :now)", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("type") String type, @Param("title") String title,
                     @Param("body") String body, @Param("link") String link, @Param("dedupeKey") String dedupeKey,
                     @Param("now") Instant now);
}
