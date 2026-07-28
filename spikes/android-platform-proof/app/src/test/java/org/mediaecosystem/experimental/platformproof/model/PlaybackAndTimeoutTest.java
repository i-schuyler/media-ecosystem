package org.mediaecosystem.experimental.platformproof.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mediaecosystem.experimental.platformproof.evidence.ExportDestination;

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

    @Test
    public void screenOffProofCannotCompleteBeforeFiveMinutes() {
        ScreenOffProof.Status status = ScreenOffProof.notStarted()
                .start()
                .playbackReady(true)
                .screenOff(true)
                .screenOn(137, 300_000, true, true);
        assertEquals(ScreenOffProof.Phase.INCOMPLETE, status.phase());
        assertFalse(status.minimumDurationReached());
        assertFalse(status.testCompleted());
        assertThrows(IllegalStateException.class, status::complete);
    }

    @Test
    public void unrelatedScreenEventsCannotStartOrCompleteTheProof() {
        ScreenOffProof.Status status = ScreenOffProof.notStarted()
                .screenOn(300_000, 300_000, true, true);
        assertEquals(ScreenOffProof.Phase.NOT_STARTED, status.phase());
        assertFalse(status.testStarted());
        assertFalse(status.minimumDurationReached());
    }

    @Test
    public void screenOffProofSeparatesDurationControlsAndCompletion() {
        ScreenOffProof.Status status = ScreenOffProof.notStarted()
                .start()
                .playbackReady(true)
                .screenOff(true)
                .minimumReached(300_000, 300_000)
                .screenOn(301_250, 300_000, true, true);
        assertTrue(status.playbackContinuedWhileScreenOff());
        assertTrue(status.minimumDurationReached());
        assertFalse(status.controlsExercised());
        assertFalse(status.testCompleted());
        status = status.markControlsExercised().complete();
        assertTrue(status.controlsExercised());
        assertTrue(status.testCompleted());
        assertEquals(ScreenOffProof.Phase.COMPLETED, status.phase());
    }

    @Test
    public void exportDestinationReportsProviderCategoryWithoutRawUri() {
        assertEquals("Android Downloads document provider",
                ExportDestination.providerCategory(
                        "com.android.providers.downloads.documents", true));
        assertEquals("Android device-storage document provider",
                ExportDestination.providerCategory(
                        "com.android.externalstorage.documents", true));
        assertTrue(ExportDestination.isProofFilename(
                "media-ecosystem-android-proof-20260728T120000Z.zip"));
        assertFalse(ExportDestination.isProofFilename("personal-name.zip"));
    }
}
