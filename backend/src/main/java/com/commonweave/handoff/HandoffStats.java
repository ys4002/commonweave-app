package com.commonweave.handoff;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Trust record: a plain count of finished exchanges. Never a rating or a ranking input. */
@Component
public class HandoffStats {
    public static final List<HandoffStatus> FINISHED = List.of(HandoffStatus.COMPLETED, HandoffStatus.RETURNED);

    private final HandoffRepository handoffs;

    public HandoffStats(HandoffRepository handoffs) {
        this.handoffs = handoffs;
    }

    public long completedExchanges(Long userId) {
        return completedExchanges(List.of(userId)).getOrDefault(userId, 0L);
    }

    public Map<Long, Long> completedExchanges(Collection<Long> userIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (userIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : handoffs.countAsProvider(userIds, FINISHED)) {
            counts.merge((Long) row[0], (Long) row[1], Long::sum);
        }
        for (Object[] row : handoffs.countAsRecipient(userIds, FINISHED)) {
            counts.merge((Long) row[0], (Long) row[1], Long::sum);
        }
        return counts;
    }
}
