package org.mediaecosystem.experimental.platformproof.model;

public final class PlaybackStateTranslator {
    public enum ProofPlaybackState { IDLE, BUFFERING, READY_PAUSED, PLAYING, ENDED, FAILED }

    private PlaybackStateTranslator() {}

    public static ProofPlaybackState translate(int media3State, boolean isPlaying, boolean failed) {
        if (failed) {
            return ProofPlaybackState.FAILED;
        }
        return switch (media3State) {
            case 1 -> ProofPlaybackState.IDLE;
            case 2 -> ProofPlaybackState.BUFFERING;
            case 3 -> isPlaying ? ProofPlaybackState.PLAYING : ProofPlaybackState.READY_PAUSED;
            case 4 -> ProofPlaybackState.ENDED;
            default -> throw new IllegalArgumentException("Unknown Media3 state: " + media3State);
        };
    }
}
