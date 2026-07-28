package org.mediaecosystem.experimental.platformproof.model;

public record PersistedPermission(boolean read, boolean write, boolean persisted) {
    public static PersistedPermission fromFlags(int flags, boolean persisted) {
        return new PersistedPermission((flags & 1) != 0, (flags & 2) != 0, persisted);
    }

    public String sanitizedDescription() {
        return "read=" + read + ",write=" + write + ",persisted=" + persisted;
    }
}
