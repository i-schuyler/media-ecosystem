package org.mediaecosystem.experimental.platformproof.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public final class PrivacySanitizer {
    private static final Pattern DOCUMENT_URI = Pattern.compile("content://[^\\s\"']+");
    private static final Pattern STORAGE_PATH = Pattern.compile("(?:/storage|/mnt|/media)/[^\\s\"']+");
    private static final Pattern VOLUME_LIKE = Pattern.compile("(?i)\\b[0-9a-f]{4}-[0-9a-f]{4}\\b");
    private static final Pattern ACCOUNT_LIKE = Pattern.compile("[\\w.+-]+@[\\w.-]+");

    private PrivacySanitizer() {}

    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String result = DOCUMENT_URI.matcher(value).replaceAll("<redacted-document-uri>");
        result = STORAGE_PATH.matcher(result).replaceAll("<redacted-storage-path>");
        result = VOLUME_LIKE.matcher(result).replaceAll("<redacted-volume-token>");
        result = ACCOUNT_LIKE.matcher(result).replaceAll("<redacted-account>");
        return result;
    }

    public static String nonReversibleToken(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("media-ecosystem-phase1-proof:" + value).getBytes(StandardCharsets.UTF_8));
            return Hashing.hex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
