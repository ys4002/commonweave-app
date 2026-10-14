package com.commonweave.notification;

import com.commonweave.common.ApiException;
import com.commonweave.common.PageResponse;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public record NotificationDto(Long id, String type, String title, String body, String link,
                                  java.time.Instant readAt, java.time.Instant createdAt) {
    }

    @Transactional
    public void notify(Long userId, String type, String title, String body, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(truncate(title, 160));
        n.setBody(truncate(body, 500));
        n.setLink(link);
        n.setCreatedAt(clock.instant());
        repo.save(n);
    }

    /** Returns true if a new notification was created, false if this dedupe key was already used. */
    @Transactional
    public boolean notifyOnce(Long userId, String type, String title, String body, String link, String dedupeKey) {
        return repo.insertIgnore(userId, type, truncate(title, 160), truncate(body, 500), link, dedupeKey,
                clock.instant()) > 0;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long userId, boolean unreadOnly, Integer page, Integer size) {
        Pageable pageable = PageResponse.pageable(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> result = unreadOnly ? repo.findByUserIdAndReadAtIsNull(userId, pageable)
                : repo.findByUserId(userId, pageable);
        return PageResponse.of(result, items -> items.stream().map(NotificationService::toDto).toList());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Notification"));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId, clock.instant());
    }

    private static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt(),
                n.getCreatedAt());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
