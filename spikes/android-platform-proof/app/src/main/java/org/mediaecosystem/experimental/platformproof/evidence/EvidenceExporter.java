package org.mediaecosystem.experimental.platformproof.evidence;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediaecosystem.experimental.platformproof.BuildConfig;
import org.mediaecosystem.experimental.platformproof.model.Hashing;
import org.mediaecosystem.experimental.platformproof.model.ZipContract;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class EvidenceExporter {
    private static final String TEMP_PREFIX = "media-ecosystem-android-proof-";
    private final Context context;
    private final EvidenceStore evidence;

    public EvidenceExporter(Context context) {
        this.context = context.getApplicationContext();
        evidence = EvidenceStore.get(context);
    }

    public ExportArtifact createAndValidate() {
        String stem = TEMP_PREFIX + timestamp();
        File output = new File(context.getCacheDir(), stem + ".zip");
        Map<String, byte[]> files = buildFiles();
        files.put("CHECKSUMS.sha256", checksumManifest(files).getBytes(StandardCharsets.UTF_8));
        if (!files.keySet().equals(ZipContract.REQUIRED_ENTRIES)) {
            throw new IllegalStateException("Evidence export contract is incomplete");
        }
        writeZip(output, files);
        validateZip(output, files);
        evidence.append("evidence", "export-validated", "passed", "filename=" + output.getName());
        return new ExportArtifact(output, output.getName(), sha256(output));
    }

    public int cleanupTemporaryExports() {
        File[] candidates = context.getCacheDir().listFiles(
                file -> file.isFile() && file.getName().startsWith(TEMP_PREFIX) && file.getName().endsWith(".zip"));
        int removed = 0;
        if (candidates != null) {
            for (File candidate : candidates) {
                if (candidate.delete()) {
                    removed++;
                }
            }
        }
        evidence.recordCleanup("temporary_exports", true, "removed=" + removed);
        return removed;
    }

    private Map<String, byte[]> buildFiles() {
        JSONObject primary = evidence.exportEvidence();
        Map<String, byte[]> files = new LinkedHashMap<>();
        try {
            files.put("evidence.json", (primary.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to serialize evidence", exception);
        }
        files.put("summary.md", summary(primary).getBytes(StandardCharsets.UTF_8));
        files.put("fixture-manifest.json", evidence.fixtureManifestText().getBytes(StandardCharsets.UTF_8));
        files.put("fixture-SHA256SUMS", evidence.fixtureSumsText().getBytes(StandardCharsets.UTF_8));
        files.put("build-metadata.json", buildMetadata().getBytes(StandardCharsets.UTF_8));
        files.put("diagnostic.log", evidence.diagnosticLog().getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private String summary(JSONObject primary) {
        return "# Android Phase 1 platform proof evidence\n\n"
                + "- Proof application: " + BuildConfig.VERSION_NAME + "\n"
                + "- Source commit: `" + BuildConfig.SOURCE_COMMIT + "`\n"
                + "- Build variant: `" + BuildConfig.BUILD_TYPE + "`\n"
                + "- Media3 candidate: `" + BuildConfig.MEDIA3_VERSION + "`\n"
                + "- Evidence schema: `" + BuildConfig.EVIDENCE_SCHEMA_VERSION + "`\n"
                + "- Export generated: " + primary.optJSONObject("export").optString("generated_wall_time_utc") + "\n\n"
                + "This archive contains sanitized evidence from a disposable proof application. "
                + "It contains no audio binaries and does not select a production architecture or playback engine.\n";
    }

    private String buildMetadata() {
        try {
            return new JSONObject()
                    .put("application_id", BuildConfig.APPLICATION_ID)
                    .put("version_name", BuildConfig.VERSION_NAME)
                    .put("version_code", BuildConfig.VERSION_CODE)
                    .put("source_commit", BuildConfig.SOURCE_COMMIT)
                    .put("build_type", BuildConfig.BUILD_TYPE)
                    .put("media3", BuildConfig.MEDIA3_VERSION)
                    .put("android_gradle_plugin", "9.3.0")
                    .put("gradle", "9.5.0")
                    .put("compile_sdk", 36)
                    .put("target_sdk", 36)
                    .put("minimum_sdk", 33)
                    .put("java_source_level", 17)
                    .put("kotlin_application_source", "not used")
                    .toString(2) + "\n";
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to serialize build metadata", exception);
        }
    }

    private static String checksumManifest(Map<String, byte[]> files) {
        List<String> names = new ArrayList<>(files.keySet());
        names.sort(String::compareTo);
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            result.append(sha256(files.get(name))).append("  ").append(name).append('\n');
        }
        return result.toString();
    }

    private static void writeZip(File output, Map<String, byte[]> files) {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create evidence ZIP", exception);
        }
    }

    private static void validateZip(File output, Map<String, byte[]> expected) {
        try (ZipFile zip = new ZipFile(output)) {
            Set<String> names = new TreeSet<>();
            zip.stream().forEach(entry -> names.add(entry.getName()));
            if (!ZipContract.complete(names)) {
                throw new IllegalStateException("Evidence ZIP entries are incomplete: " + names);
            }
            for (Map.Entry<String, byte[]> item : expected.entrySet()) {
                byte[] actual = zip.getInputStream(zip.getEntry(item.getKey())).readAllBytes();
                if (!sha256(actual).equals(sha256(item.getValue()))) {
                    throw new IllegalStateException("Evidence ZIP checksum mismatch: " + item.getKey());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to validate evidence ZIP", exception);
        }
    }

    private static String sha256(File file) {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
            return Hashing.hex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash export", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return Hashing.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    public record ExportArtifact(File file, String suggestedName, String sha256) {}
}
