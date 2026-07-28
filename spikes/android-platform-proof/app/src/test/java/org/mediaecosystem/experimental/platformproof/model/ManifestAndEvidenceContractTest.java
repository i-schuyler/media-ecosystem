package org.mediaecosystem.experimental.platformproof.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.mediaecosystem.experimental.platformproof.fixtures.FormatContract;

public final class ManifestAndEvidenceContractTest {
    private static final Path FIXTURES = Paths.get("src/main/assets/fixtures");
    private static final Pattern ENTRY = Pattern.compile(
            "\\\"filename\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[\\s\\S]*?"
                    + "\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[\\s\\S]*?"
                    + "\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"");

    @Test
    public void manifestContainsExactlySixActiveFormatsWithMatchingHashes() throws Exception {
        String manifest = new String(
                Files.readAllBytes(FIXTURES.resolve("fixture-manifest.json")),
                StandardCharsets.UTF_8);
        Matcher matcher = ENTRY.matcher(manifest);
        Map<String, String> filenames = new HashMap<>();
        Map<String, String> hashes = new HashMap<>();
        while (matcher.find()) {
            filenames.put(matcher.group(2), matcher.group(1));
            hashes.put(matcher.group(2), matcher.group(3));
        }
        assertEquals(Set.copyOf(FormatContract.REQUIRED_IDS), filenames.keySet());
        assertEquals(6, new HashSet<>(filenames.values()).size());
        assertFalse(filenames.keySet().contains("alac"));
        assertFalse(filenames.keySet().contains("aiff"));
        for (String id : filenames.keySet()) {
            assertEquals(hashes.get(id), sha256(FIXTURES.resolve(filenames.get(id))));
        }
        assertTrue(manifest.contains("no human recording or personal media"));
        assertTrue(manifest.contains("\"expected_duration_ms\": 6000"));
    }

    @Test
    public void evidenceSchemaHasVersionedRequiredSectionsAndNoPersonalFields() throws IOException {
        String historicalSchema = new String(
                Files.readAllBytes(Paths.get("src/main/assets/evidence/evidence-schema-v1.json")),
                StandardCharsets.UTF_8);
        String currentSchema = new String(
                Files.readAllBytes(Paths.get("src/main/assets/evidence/evidence-schema-v1.1.json")),
                StandardCharsets.UTF_8);
        for (String required : new String[] {
                "proof_app", "environment", "fixture_manifest_sha256", "session_timing", "storage",
                "playback", "format_matrix", "physical_actions", "errors", "cleanup"
        }) {
            assertTrue(required, historicalSchema.contains("\"" + required + "\""));
            assertTrue(required, currentSchema.contains("\"" + required + "\""));
        }
        assertTrue(historicalSchema.contains("\"export\""));
        assertTrue(currentSchema.contains("\"format_contract\""));
        assertTrue(currentSchema.contains("\"active_required_count\": { \"const\": 6 }"));
        assertTrue(currentSchema.contains("\"screen_off_workflow\""));
        assertTrue(currentSchema.contains("\"export_handoff\""));
        for (String prohibited : EvidencePolicy.PROHIBITED_FIELDS) {
            assertFalse(prohibited, historicalSchema.contains("\"" + prohibited + "\""));
            assertFalse(prohibited, currentSchema.contains("\"" + prohibited + "\""));
        }
        for (FormatDisposition disposition : FormatDisposition.values()) {
            assertTrue(disposition.wireValue(),
                    currentSchema.contains("\"" + disposition.wireValue() + "\""));
        }
    }

    @Test
    public void formatContractUsesStableIdentifiersAndClassifiesHistoricalFormats() {
        assertEquals(6, FormatContract.REQUIRED_IDS.size());
        assertEquals(Set.of("alac", "aiff"),
                Set.copyOf(FormatContract.HISTORICAL_NONREQUIRED_IDS));
        assertTrue(FormatContract.isRequired("wav"));
        assertFalse(FormatContract.isRequired("alac"));
        assertFalse(FormatContract.isRequired("aiff"));
    }

    @Test
    public void partialAndFailedSessionsCannotBecomePassed() {
        assertEquals(FormatDisposition.NOT_RUN,
                EvidencePolicy.sessionDisposition(false, false, false, false));
        assertEquals(FormatDisposition.INCONCLUSIVE,
                EvidencePolicy.sessionDisposition(true, false, false, true));
        assertEquals(FormatDisposition.INCONCLUSIVE,
                EvidencePolicy.sessionDisposition(true, true, false, false));
        assertEquals(FormatDisposition.FAILED,
                EvidencePolicy.sessionDisposition(true, true, true, true));
        assertEquals(FormatDisposition.PASSED,
                EvidencePolicy.sessionDisposition(true, true, false, true));
    }

    @Test
    public void zipManifestRequiresExactCompleteSet() {
        assertTrue(ZipContract.complete(ZipContract.REQUIRED_ENTRIES));
        Set<String> incomplete = new HashSet<>(ZipContract.REQUIRED_ENTRIES);
        incomplete.remove("diagnostic.log");
        assertFalse(ZipContract.complete(incomplete));
        Set<String> unexpected = new HashSet<>(ZipContract.REQUIRED_ENTRIES);
        unexpected.add("raw-uri.txt");
        assertFalse(ZipContract.complete(unexpected));
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return Hashing.hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
