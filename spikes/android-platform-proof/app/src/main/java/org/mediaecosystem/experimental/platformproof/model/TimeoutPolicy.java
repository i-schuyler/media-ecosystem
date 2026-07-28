package org.mediaecosystem.experimental.platformproof.model;

public record TimeoutPolicy(long prepareMs, long playbackStartMs, long seekMs, long endMs) {
    public static TimeoutPolicy boundedDefaults() {
        return new TimeoutPolicy(10_000, 10_000, 8_000, 15_000);
    }

    public boolean allBounded() {
        return prepareMs > 0 && playbackStartMs > 0 && seekMs > 0 && endMs > 0
                && prepareMs <= 30_000 && playbackStartMs <= 30_000
                && seekMs <= 30_000 && endMs <= 30_000;
    }
}
