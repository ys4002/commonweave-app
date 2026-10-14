package com.commonweave.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commonweave.common.ApiException;
import com.commonweave.common.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandoffStateMachineTest {
    @Test
    void happyPathLoan() {
        assertEquals(HandoffStatus.SCHEDULED, HandoffStateMachine.next(HandoffStatus.PROPOSED, TransitionAction.CONFIRM, true));
        assertEquals(HandoffStatus.HANDED_OFF, HandoffStateMachine.next(HandoffStatus.SCHEDULED, TransitionAction.MARK_HANDED_OFF, true));
        assertEquals(HandoffStatus.RETURNED, HandoffStateMachine.next(HandoffStatus.HANDED_OFF, TransitionAction.MARK_RETURNED, true));
    }

    @Test
    void happyPathGift() {
        assertEquals(HandoffStatus.COMPLETED, HandoffStateMachine.next(HandoffStatus.HANDED_OFF, TransitionAction.COMPLETE, false));
    }

    @Test
    void returnAndCompleteDependOnReturnExpected() {
        assertThrows(ApiException.class, () -> HandoffStateMachine.next(HandoffStatus.HANDED_OFF, TransitionAction.COMPLETE, true));
        assertThrows(ApiException.class, () -> HandoffStateMachine.next(HandoffStatus.HANDED_OFF, TransitionAction.MARK_RETURNED, false));
    }

    @Test
    void cancelOnlyFromProposedOrScheduled() {
        assertEquals(HandoffStatus.CANCELLED, HandoffStateMachine.next(HandoffStatus.PROPOSED, TransitionAction.CANCEL, false));
        assertEquals(HandoffStatus.CANCELLED, HandoffStateMachine.next(HandoffStatus.SCHEDULED, TransitionAction.CANCEL, false));
        for (HandoffStatus s : List.of(HandoffStatus.HANDED_OFF, HandoffStatus.RETURNED, HandoffStatus.COMPLETED, HandoffStatus.CANCELLED)) {
            assertThrows(ApiException.class, () -> HandoffStateMachine.next(s, TransitionAction.CANCEL, false));
        }
    }

    @Test
    void invalidTransitionIsReportedWithCode() {
        ApiException e = assertThrows(ApiException.class,
                () -> HandoffStateMachine.next(HandoffStatus.COMPLETED, TransitionAction.CONFIRM, false));
        assertEquals(ErrorCode.INVALID_TRANSITION, e.code());
    }

    @Test
    void terminalStatesAllowNothing() {
        for (HandoffStatus s : List.of(HandoffStatus.RETURNED, HandoffStatus.COMPLETED, HandoffStatus.CANCELLED)) {
            assertEquals(List.of(), HandoffStateMachine.allowed(s, true, true));
            assertEquals(List.of(), HandoffStateMachine.allowed(s, false, true));
        }
    }

    @Test
    void allowedActionsOnlyOfferConfirmToOwner() {
        assertEquals(List.of(TransitionAction.CONFIRM, TransitionAction.CANCEL),
                HandoffStateMachine.allowed(HandoffStatus.PROPOSED, true, true));
        assertEquals(List.of(TransitionAction.CANCEL), HandoffStateMachine.allowed(HandoffStatus.PROPOSED, true, false));
        assertEquals(List.of(TransitionAction.MARK_HANDED_OFF, TransitionAction.CANCEL),
                HandoffStateMachine.allowed(HandoffStatus.SCHEDULED, true, false));
        assertEquals(List.of(TransitionAction.MARK_RETURNED), HandoffStateMachine.allowed(HandoffStatus.HANDED_OFF, true, true));
        assertEquals(List.of(TransitionAction.COMPLETE), HandoffStateMachine.allowed(HandoffStatus.HANDED_OFF, false, true));
    }
}
