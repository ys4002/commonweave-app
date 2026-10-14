package com.commonweave.dashboard;

import com.commonweave.circle.ActivityService;
import com.commonweave.circle.CircleAccess;
import com.commonweave.circle.Circle;
import com.commonweave.circle.CircleRepository;
import com.commonweave.handoff.Handoff;
import com.commonweave.handoff.HandoffDtos.HandoffSummary;
import com.commonweave.handoff.HandoffMapper;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.matching.MatchDtos.SuggestedMatch;
import com.commonweave.matching.MatchingService;
import com.commonweave.notification.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private static final int LIST_LIMIT = 5;
    private static final int ACTIVITY_LIMIT = 10;

    public record CircleActivityItem(String circleName, String type, String text, Instant at) {
    }

    public record DashboardResponse(List<HandoffSummary> upcomingHandoffs, List<HandoffSummary> needsAction,
                                    List<SuggestedMatch> suggestions, List<CircleActivityItem> circleActivity,
                                    long unreadNotifications) {
    }

    private final HandoffRepository handoffs;
    private final HandoffMapper handoffMapper;
    private final MatchingService matching;
    private final ActivityService activity;
    private final CircleAccess circleAccess;
    private final CircleRepository circles;
    private final NotificationService notifications;
    private final Clock clock;

    public DashboardService(HandoffRepository handoffs, HandoffMapper handoffMapper, MatchingService matching,
                            ActivityService activity, CircleAccess circleAccess, CircleRepository circles,
                            NotificationService notifications, Clock clock) {
        this.handoffs = handoffs;
        this.handoffMapper = handoffMapper;
        this.matching = matching;
        this.activity = activity;
        this.circleAccess = circleAccess;
        this.circles = circles;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(Long userId) {
        Instant now = clock.instant();
        List<Handoff> upcoming = handoffs.findMineByStatus(userId, HandoffStatus.SCHEDULED).stream()
                .sorted(Comparator.comparing(Handoff::getScheduledAt)).limit(LIST_LIMIT).toList();
        List<Handoff> needs = new ArrayList<>(handoffs.findMineByStatus(userId, HandoffStatus.PROPOSED).stream()
                .filter(h -> h.ownerId().equals(userId)).toList());
        handoffs.findMineByStatus(userId, HandoffStatus.HANDED_OFF).stream()
                .filter(h -> h.isReturnExpected() && h.getReturnBy() != null && h.getReturnBy().isBefore(now))
                .forEach(needs::add);
        needs.sort(Comparator.comparing(Handoff::getUpdatedAt).reversed());
        return new DashboardResponse(handoffMapper.summaries(upcoming, userId),
                handoffMapper.summaries(needs.stream().limit(LIST_LIMIT).toList(), userId),
                matching.suggested(userId, MatchingService.SUGGESTION_LIMIT), circleActivity(userId),
                notifications.unreadCount(userId));
    }

    private List<CircleActivityItem> circleActivity(Long userId) {
        Set<Long> circleIds = circleAccess.activeCircleIds(userId);
        Map<Long, String> names = circles.findAllById(circleIds).stream()
                .collect(Collectors.toMap(Circle::getId, Circle::getName));
        return activity.recent(circleIds, ACTIVITY_LIMIT).stream()
                .map(e -> new CircleActivityItem(names.get(e.circleId()), e.item().type(), e.item().text(),
                        e.item().at())).toList();
    }
}
