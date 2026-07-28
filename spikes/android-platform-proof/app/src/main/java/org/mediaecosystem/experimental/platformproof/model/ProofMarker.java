package org.mediaecosystem.experimental.platformproof.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ProofMarker(String schemaVersion, String sessionId, String markerHash) {
    public static final String VERSION = "1";
    public static final String DIRECTORY_NAME = "Media-Ecosystem-Phase1-Proof-v1";
    public static final String MARKER_NAME = "proof-marker-v1.json";
    private static final Pattern SESSION = Pattern.compile("\"session_id\"\\s*:\\s*\"([0-9a-f-]{36})\"");
    private static final Pattern VERSION_FIELD = Pattern.compile("\"schema_version\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HASH = Pattern.compile("\"marker_hash\"\\s*:\\s*\"([0-9a-f]{64})\"");

    public static ProofMarker create(String sessionId) {
        return new ProofMarker(VERSION, sessionId, calculateHash(VERSION, sessionId));
    }

    public boolean valid() {
        return VERSION.equals(schemaVersion) && markerHash.equals(calculateHash(schemaVersion, sessionId));
    }

    public String toJson() {
        return "{\n"
                + "  \"schema_version\": \"" + schemaVersion + "\",\n"
                + "  \"proof_kind\": \"disposable-android-platform-proof\",\n"
                + "  \"session_id\": \"" + sessionId + "\",\n"
                + "  \"marker_hash\": \"" + markerHash + "\"\n"
                + "}\n";
    }

    public static ProofMarker parse(String json) {
        Matcher version = VERSION_FIELD.matcher(json);
        Matcher session = SESSION.matcher(json);
        Matcher hash = HASH.matcher(json);
        if (!version.find() || !session.find() || !hash.find()) {
            throw new IllegalArgumentException("Missing or malformed proof marker fields");
        }
        ProofMarker marker = new ProofMarker(version.group(1), session.group(1), hash.group(1));
        if (!marker.valid()) {
            throw new IllegalArgumentException("Proof marker hash mismatch");
        }
        return marker;
    }

    public static boolean cleanupAllowed(String directoryName, String markerJson, String expectedSessionId) {
        if (!DIRECTORY_NAME.equals(directoryName)) {
            return false;
        }
        try {
            ProofMarker marker = parse(markerJson);
            return marker.sessionId.equals(expectedSessionId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String calculateHash(String version, String sessionId) {
        try {
            byte[] bytes = ("media-ecosystem-proof-marker|" + version + "|" + sessionId)
                    .getBytes(StandardCharsets.UTF_8);
            return Hashing.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
