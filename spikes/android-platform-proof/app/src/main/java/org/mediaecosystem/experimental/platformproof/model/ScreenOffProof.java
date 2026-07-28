package org.mediaecosystem.experimental.platformproof.model;

public final class ScreenOffProof {
    public enum Phase {
        NOT_STARTED,
        TEST_STARTED,
        READY_TO_TURN_SCREEN_OFF,
        SCREEN_OFF_PLAYING,
        MINIMUM_REACHED,
        INCOMPLETE,
        COMPLETED
    }

    private ScreenOffProof() {}

    public static Status notStarted() {
        return new Status(Phase.NOT_STARTED, false, false, false, false, false, 0);
    }

    public record Status(
            Phase phase,
            boolean testStarted,
            boolean playbackContinuedWhileScreenOff,
            boolean minimumDurationReached,
            boolean controlsExercised,
            boolean testCompleted,
            long monotonicDurationMs
    ) {
        public Status start() {
            return new Status(Phase.TEST_STARTED, true, false, false,
                    false, false, 0);
        }

        public Status playbackReady(boolean isPlaying) {
            if (!testStarted || !isPlaying) {
                return this;
            }
            return new Status(Phase.READY_TO_TURN_SCREEN_OFF, true, false, false,
                    controlsExercised, false, 0);
        }

        public Status screenOff(boolean isPlaying) {
            if (phase != Phase.READY_TO_TURN_SCREEN_OFF || !isPlaying) {
                return new Status(Phase.INCOMPLETE, testStarted, false, false,
                        controlsExercised, false, 0);
            }
            return new Status(Phase.SCREEN_OFF_PLAYING, true, true, false,
                    controlsExercised, false, 0);
        }

        public Status playbackInterrupted(long durationMs) {
            return new Status(Phase.INCOMPLETE, testStarted, false, false,
                    controlsExercised, false, Math.max(0, durationMs));
        }

        public Status minimumReached(long durationMs, long requiredDurationMs) {
            if (phase != Phase.SCREEN_OFF_PLAYING
                    || !playbackContinuedWhileScreenOff
                    || durationMs < requiredDurationMs) {
                return this;
            }
            return new Status(Phase.MINIMUM_REACHED, true, true, true,
                    controlsExercised, false, durationMs);
        }

        public Status screenOn(
                long durationMs,
                long requiredDurationMs,
                boolean continuousPlayback,
                boolean isPlayingOnReturn
        ) {
            if (!testStarted) {
                return this;
            }
            boolean continued = playbackContinuedWhileScreenOff
                    && continuousPlayback
                    && isPlayingOnReturn;
            boolean reached = continued && durationMs >= requiredDurationMs;
            return new Status(reached ? Phase.MINIMUM_REACHED : Phase.INCOMPLETE,
                    testStarted, continued, reached, controlsExercised, false,
                    Math.max(0, durationMs));
        }

        public Status markControlsExercised() {
            return new Status(phase, testStarted, playbackContinuedWhileScreenOff,
                    minimumDurationReached, true, testCompleted, monotonicDurationMs);
        }

        public Status complete() {
            if (!minimumDurationReached || !playbackContinuedWhileScreenOff) {
                throw new IllegalStateException(
                        "Five-minute continuous screen-off playback has not been reached");
            }
            return new Status(Phase.COMPLETED, true, true, true,
                    controlsExercised, true, monotonicDurationMs);
        }
    }
}
