package com.commonweave.handoff;

import com.commonweave.common.ApiException;
import com.commonweave.common.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Pure transition rules. Authorisation and side effects live in {@link HandoffService}. */
public final class HandoffStateMachine {
    private HandoffStateMachine() {
    }

    public static Optional<HandoffStatus> target(HandoffStatus from, TransitionAction action, boolean returnExpected) {
        return Optional.ofNullable(switch (action) {
            case CONFIRM -> from == HandoffStatus.PROPOSED ? HandoffStatus.SCHEDULED : null;
            case CANCEL -> from == HandoffStatus.PROPOSED || from == HandoffStatus.SCHEDULED
                    ? HandoffStatus.CANCELLED : null;
            case MARK_HANDED_OFF -> from == HandoffStatus.SCHEDULED ? HandoffStatus.HANDED_OFF : null;
            case MARK_RETURNED -> from == HandoffStatus.HANDED_OFF && returnExpected ? HandoffStatus.RETURNED : null;
            case COMPLETE -> from == HandoffStatus.HANDED_OFF && !returnExpected ? HandoffStatus.COMPLETED : null;
        });
    }

    public static HandoffStatus next(HandoffStatus from, TransitionAction action, boolean returnExpected) {
        return target(from, action, returnExpected).orElseThrow(() -> new ApiException(
                ErrorCode.INVALID_TRANSITION, "Cannot apply " + action + " to a handoff that is " + from));
    }

    public static List<TransitionAction> allowed(HandoffStatus from, boolean returnExpected, boolean isOwner) {
        return Arrays.stream(TransitionAction.values())
                .filter(a -> target(from, a, returnExpected).isPresent())
                .filter(a -> a != TransitionAction.CONFIRM || isOwner)
                .toList();
    }
}
