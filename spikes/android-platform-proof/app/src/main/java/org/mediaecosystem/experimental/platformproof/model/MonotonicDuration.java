package org.mediaecosystem.experimental.platformproof.model;

public final class MonotonicDuration {
    private MonotonicDuration() {}

    public static long between(long startElapsedRealtimeMs, long endElapsedRealtimeMs) {
        if (startElapsedRealtimeMs < 0 || endElapsedRealtimeMs < startElapsedRealtimeMs) {
            throw new IllegalArgumentException("Invalid monotonic endpoints");
        }
        return endElapsedRealtimeMs - startElapsedRealtimeMs;
    }
}
