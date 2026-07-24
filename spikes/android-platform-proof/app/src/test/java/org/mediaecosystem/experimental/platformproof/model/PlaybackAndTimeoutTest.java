package org.mediaecosystem.experimental.platformproof.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlaybackAndTimeoutTest {
    @Test
    public void media3StatesTranslateWithoutClaimingPhysicalBehavior() {
        assertEquals(PlaybackStateTranslator.ProofPlaybackState.IDLE,
                PlaybackStateTranslator.translate(1, false, false));
        assertEquals(PlaybackStateTranslator.ProofPlaybackState.BUFFERING,
                PlaybackStateTranslator.translate(2, false, false));
        assertEquals(PlaybackStateTranslator.ProofPlaybackState.READY_PAUSED,
                PlaybackStateTranslator.translate(3, false, false));
        assertEquals(PlaybackStateTranslator.ProofPlaybackState.PLAYING,
                PlaybackStateTranslator.translate(3, true, false));
        assertEquals(PlaybackStateTranslator.ProofPlaybackState.ENDED,
                PlaybackStateTranslator.translate(4, false, false));
        assertEquals(PlaybackStateTranslator.ProofPlaybackState.FAILED,
                PlaybackStateTranslator.translate(3, false, true));
    }

    @Test
    public void timeoutPolicyIsFiniteForEveryDecoderPhase() {
        TimeoutPolicy policy = TimeoutPolicy.boundedDefaults();
        assertTrue(policy.allBounded());
        assertEquals(10_000, policy.prepareMs());
        assertEquals(10_000, policy.playbackStartMs());
        assertEquals(8_000, policy.seekMs());
        assertEquals(15_000, policy.endMs());
    }

    @Test
    public void monotonicDurationsRejectWallClockLikeReversal() {
        assertEquals(2_500, MonotonicDuration.between(10_000, 12_500));
        assertThrows(IllegalArgumentException.class, () ->
                MonotonicDuration.between(12_500, 10_000));
        assertThrows(IllegalArgumentException.class, () ->
                MonotonicDuration.between(-1, 10_000));
    }
}
