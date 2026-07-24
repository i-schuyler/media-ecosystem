package org.mediaecosystem.experimental.platformproof.model;

import java.util.Set;

public final class ZipContract {
    public static final Set<String> REQUIRED_ENTRIES = Set.of(
            "evidence.json",
            "summary.md",
            "fixture-manifest.json",
            "fixture-SHA256SUMS",
            "build-metadata.json",
            "diagnostic.log",
            "CHECKSUMS.sha256"
    );

    private ZipContract() {}

    public static boolean complete(Set<String> entries) {
        return entries.equals(REQUIRED_ENTRIES);
    }
}
