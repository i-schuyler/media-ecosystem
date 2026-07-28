package org.mediaecosystem.experimental.platformproof.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Test;

public final class SafetyAndPrivacyTest {
    @Test
    public void persistedPermissionRepresentationUsesOnlyFlags() {
        assertEquals(new PersistedPermission(true, true, true),
                PersistedPermission.fromFlags(3, true));
        assertEquals("read=true,write=false,persisted=false",
                PersistedPermission.fromFlags(1, false).sanitizedDescription());
    }

    @Test
    public void rawUrisPathsAccountsAndVolumeTokensAreRedacted() {
        String sanitized = PrivacySanitizer.sanitize(
                "content://provider/tree/ABCD-1234%3A path=/storage/ABCD-1234/Music "
                        + "volume=ABCD-1234 account=proof@example.invalid");
        assertFalse(sanitized.contains("content://"));
        assertFalse(sanitized.contains("/storage/"));
        assertFalse(sanitized.contains("ABCD-1234"));
        assertFalse(sanitized.contains("proof@example.invalid"));
    }

    @Test
    public void rootTokenIsStableAndNonReversible() {
        String raw = "content://provider/tree/private";
        String firstSessionValue = "session-one|" + raw;
        assertEquals(PrivacySanitizer.nonReversibleToken(firstSessionValue),
                PrivacySanitizer.nonReversibleToken(firstSessionValue));
        assertNotEquals(
                PrivacySanitizer.nonReversibleToken(firstSessionValue),
                PrivacySanitizer.nonReversibleToken("session-two|" + raw));
        assertNotEquals(raw, PrivacySanitizer.nonReversibleToken(firstSessionValue));
        assertEquals(24, PrivacySanitizer.nonReversibleToken(firstSessionValue).length());
    }

    @Test
    public void markerValidationAndCleanupFailClosed() {
        String session = UUID.randomUUID().toString();
        ProofMarker marker = ProofMarker.create(session);
        assertEquals(marker, ProofMarker.parse(marker.toJson()));
        assertTrue(ProofMarker.cleanupAllowed(
                ProofMarker.DIRECTORY_NAME, marker.toJson(), session));
        assertFalse(ProofMarker.cleanupAllowed("Music", marker.toJson(), session));
        assertFalse(ProofMarker.cleanupAllowed(
                ProofMarker.DIRECTORY_NAME, marker.toJson(), UUID.randomUUID().toString()));
        assertFalse(ProofMarker.cleanupAllowed(
                ProofMarker.DIRECTORY_NAME, marker.toJson().replace(marker.markerHash(), "0".repeat(64)),
                session));
        assertThrows(IllegalArgumentException.class, () -> ProofMarker.parse("{}"));
    }

    @Test
    public void schemaProhibitsPersonalDataFieldNames() {
        assertTrue(EvidencePolicy.PROHIBITED_FIELDS.contains("raw_document_uri"));
        assertTrue(EvidencePolicy.PROHIBITED_FIELDS.contains("volume_uuid"));
        assertTrue(EvidencePolicy.PROHIBITED_FIELDS.contains("account_name"));
        assertTrue(EvidencePolicy.PROHIBITED_FIELDS.contains("serial_number"));
        assertTrue(EvidencePolicy.PROHIBITED_FIELDS.contains("personal_path"));
    }
}
