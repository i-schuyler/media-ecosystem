package org.mediaecosystem.experimental.platformproof.model;

import java.util.EnumMap;
import java.util.Map;

public final class StorageStateMachine {
    private static final Map<ProofState, Map<StorageEvent, ProofState>> TRANSITIONS = buildTransitions();

    private StorageStateMachine() {}

    public static ProofState transition(ProofState state, StorageEvent event) {
        ProofState next = TRANSITIONS.getOrDefault(state, Map.of()).get(event);
        if (next == null) {
            throw new IllegalStateException("Invalid storage transition: " + state + " + " + event);
        }
        return next;
    }

    public static boolean impliesDeletion(ProofState state) {
        return false;
    }

    private static Map<ProofState, Map<StorageEvent, ProofState>> buildTransitions() {
        EnumMap<ProofState, Map<StorageEvent, ProofState>> transitions = new EnumMap<>(ProofState.class);
        put(transitions, ProofState.NEVER_SELECTED, StorageEvent.SELECT_WITH_PERMISSION, ProofState.PERMISSION_GRANTED);
        put(transitions, ProofState.PERMISSION_GRANTED, StorageEvent.ACCESS_CONFIRMED, ProofState.ACCESSIBLE);
        put(transitions, ProofState.ACCESSIBLE, StorageEvent.PROCESS_RELAUNCH_CONFIRMED, ProofState.PROCESS_RESTARTED_AND_STILL_ACCESSIBLE);
        put(transitions, ProofState.PROCESS_RESTARTED_AND_STILL_ACCESSIBLE, StorageEvent.REBOOT_RELAUNCH_CONFIRMED, ProofState.REBOOTED_AND_STILL_ACCESSIBLE);

        for (ProofState accessible : new ProofState[] {
                ProofState.PERMISSION_GRANTED,
                ProofState.ACCESSIBLE,
                ProofState.PROCESS_RESTARTED_AND_STILL_ACCESSIBLE,
                ProofState.REBOOTED_AND_STILL_ACCESSIBLE,
                ProofState.VOLUME_REINSERTED,
                ProofState.EXPLICIT_RELINK_SUCCEEDED
        }) {
            put(transitions, accessible, StorageEvent.ACCESS_UNAVAILABLE, ProofState.TEMPORARILY_UNAVAILABLE);
            put(transitions, accessible, StorageEvent.GRANT_REVOKED, ProofState.PERMISSION_REVOKED);
            put(transitions, accessible, StorageEvent.RELINK_REQUIRED, ProofState.EXPLICIT_RELINK_REQUIRED);
            put(transitions, accessible, StorageEvent.CLEANUP_VALIDATED_AND_COMPLETE, ProofState.CLEANUP_COMPLETE);
        }

        put(transitions, ProofState.TEMPORARILY_UNAVAILABLE, StorageEvent.ACCESS_RESTORED, ProofState.VOLUME_REINSERTED);
        put(transitions, ProofState.TEMPORARILY_UNAVAILABLE, StorageEvent.GRANT_REVOKED, ProofState.PERMISSION_REVOKED);
        put(transitions, ProofState.TEMPORARILY_UNAVAILABLE, StorageEvent.RELINK_REQUIRED, ProofState.EXPLICIT_RELINK_REQUIRED);
        put(transitions, ProofState.PERMISSION_REVOKED, StorageEvent.RELINK_REQUIRED, ProofState.EXPLICIT_RELINK_REQUIRED);
        put(transitions, ProofState.EXPLICIT_RELINK_REQUIRED, StorageEvent.EXPLICIT_RELINK_VALIDATED, ProofState.EXPLICIT_RELINK_SUCCEEDED);
        put(transitions, ProofState.VOLUME_REINSERTED, StorageEvent.ACCESS_CONFIRMED, ProofState.ACCESSIBLE);
        put(transitions, ProofState.EXPLICIT_RELINK_SUCCEEDED, StorageEvent.ACCESS_CONFIRMED, ProofState.ACCESSIBLE);
        return transitions;
    }

    private static void put(
            EnumMap<ProofState, Map<StorageEvent, ProofState>> transitions,
            ProofState from,
            StorageEvent event,
            ProofState to
    ) {
        EnumMap<StorageEvent, ProofState> events = new EnumMap<>(StorageEvent.class);
        events.putAll(transitions.getOrDefault(from, Map.of()));
        events.put(event, to);
        transitions.put(from, events);
    }
}
