package org.mediaecosystem.experimental.platformproof.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class StorageStateMachineTest {
    @Test
    public void fullPersistedLifecycleUsesExplicitStates() {
        ProofState state = ProofState.NEVER_SELECTED;
        state = StorageStateMachine.transition(state, StorageEvent.SELECT_WITH_PERMISSION);
        assertEquals(ProofState.PERMISSION_GRANTED, state);
        state = StorageStateMachine.transition(state, StorageEvent.ACCESS_CONFIRMED);
        assertEquals(ProofState.ACCESSIBLE, state);
        state = StorageStateMachine.transition(state, StorageEvent.PROCESS_RELAUNCH_CONFIRMED);
        assertEquals(ProofState.PROCESS_RESTARTED_AND_STILL_ACCESSIBLE, state);
        state = StorageStateMachine.transition(state, StorageEvent.REBOOT_RELAUNCH_CONFIRMED);
        assertEquals(ProofState.REBOOTED_AND_STILL_ACCESSIBLE, state);
        state = StorageStateMachine.transition(state, StorageEvent.ACCESS_UNAVAILABLE);
        assertEquals(ProofState.TEMPORARILY_UNAVAILABLE, state);
        state = StorageStateMachine.transition(state, StorageEvent.ACCESS_RESTORED);
        assertEquals(ProofState.VOLUME_REINSERTED, state);
    }

    @Test
    public void unavailableNeverImpliesDeletion() {
        assertFalse(StorageStateMachine.impliesDeletion(ProofState.TEMPORARILY_UNAVAILABLE));
        assertFalse(StorageStateMachine.impliesDeletion(ProofState.PERMISSION_REVOKED));
        assertFalse(StorageStateMachine.impliesDeletion(ProofState.EXPLICIT_RELINK_REQUIRED));
        assertEquals(ProofState.PERMISSION_REVOKED,
                StorageStateMachine.transition(
                        ProofState.ACCESSIBLE, StorageEvent.GRANT_REVOKED));
        assertEquals(ProofState.EXPLICIT_RELINK_REQUIRED,
                StorageStateMachine.transition(
                        ProofState.PERMISSION_REVOKED, StorageEvent.RELINK_REQUIRED));
    }

    @Test
    public void relinkRequiresExplicitValidatedPickerPath() {
        assertThrows(IllegalStateException.class, () ->
                StorageStateMachine.transition(
                        ProofState.EXPLICIT_RELINK_REQUIRED,
                        StorageEvent.ACCESS_RESTORED));
        assertEquals(ProofState.EXPLICIT_RELINK_SUCCEEDED,
                StorageStateMachine.transition(
                        ProofState.EXPLICIT_RELINK_REQUIRED,
                        StorageEvent.EXPLICIT_RELINK_VALIDATED));
        assertFalse(EvidencePolicy.relinkMaySucceed(false, true));
        assertFalse(EvidencePolicy.relinkMaySucceed(true, false));
    }
}
