package org.mediaecosystem.experimental.platformproof.evidence;

import java.util.Locale;

public final class ExportDestination {
    private ExportDestination() {}

    public static String providerCategory(String authority, boolean documentUri) {
        String normalized = authority == null ? "" : authority.toLowerCase(Locale.ROOT);
        if (normalized.contains("downloads")) {
            return "Android Downloads document provider";
        }
        if (normalized.contains("externalstorage")) {
            return "Android device-storage document provider";
        }
        if (normalized.contains("drive") || normalized.contains("cloud")) {
            return "user-selected cloud document provider";
        }
        return documentUri ? "user-selected Android document provider"
                : "user-selected content provider";
    }

    public static boolean isProofFilename(String name) {
        return name != null
                && name.startsWith("media-ecosystem-android-proof-")
                && name.endsWith(".zip");
    }
}
