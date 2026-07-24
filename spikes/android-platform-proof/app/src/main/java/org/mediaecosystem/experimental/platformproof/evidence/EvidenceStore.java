package org.mediaecosystem.experimental.platformproof.evidence;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mediaecosystem.experimental.platformproof.BuildConfig;
import org.mediaecosystem.experimental.platformproof.model.Hashing;
import org.mediaecosystem.experimental.platformproof.model.PrivacySanitizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class EvidenceStore {
    private static final String LOG_NAME = "sanitized-diagnostic.jsonl";
    private static final String STATE_NAME = "structured-evidence-state.json";
    private static volatile EvidenceStore instance;

    private final Context context;
    private final File logFile;
    private final File stateFile;

    private EvidenceStore(Context context) {
        this.context = context.getApplicationContext();
        logFile = new File(this.context.getFilesDir(), LOG_NAME);
        stateFile = new File(this.context.getFilesDir(), STATE_NAME);
        if (!stateFile.exists()) {
            try {
                writeState(new JSONObject().put("session_timing", new JSONObject()
                        .put("started_wall_time_utc", utcNow())
                        .put("started_elapsed_realtime_ms", SystemClock.elapsedRealtime())));
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to initialize evidence session timing", exception);
            }
        }
    }

    public static EvidenceStore get(Context context) {
        if (instance == null) {
            synchronized (EvidenceStore.class) {
                if (instance == null) {
                    instance = new EvidenceStore(context);
                }
            }
        }
        return instance;
    }

    public synchronized void append(String area, String event, String status, String details) {
        try {
            JSONObject record = new JSONObject()
                    .put("wall_time_utc", utcNow())
                    .put("elapsed_realtime_ms", SystemClock.elapsedRealtime())
                    .put("area", safe(area))
                    .put("event", safe(event))
                    .put("status", safe(status))
                    .put("details", PrivacySanitizer.sanitize(details));
            try (FileOutputStream output = new FileOutputStream(logFile, true)) {
                output.write((record + "\n").getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
        } catch (IOException | JSONException exception) {
            throw new IllegalStateException("Unable to persist sanitized evidence event", exception);
        }
    }

    public synchronized void recordStorage(JSONObject storage) {
        mutateState("storage", storage);
    }

    public synchronized void recordPlayback(JSONObject playback) {
        mutateState("playback", playback);
    }

    public synchronized void recordFormatMatrix(JSONArray results) {
        mutateState("format_matrix", results);
    }

    public synchronized void acknowledgePhysicalAction(String action, boolean completed, long monotonicDurationMs) {
        JSONObject state = readState();
        JSONArray actions = state.optJSONArray("physical_actions");
        if (actions == null) {
            actions = new JSONArray();
        }
        try {
            actions.put(new JSONObject()
                    .put("action", safe(action))
                    .put("acknowledged", completed)
                    .put("wall_time_utc", utcNow())
                    .put("elapsed_realtime_ms", SystemClock.elapsedRealtime())
                    .put("monotonic_duration_ms", monotonicDurationMs));
            state.put("physical_actions", actions);
            writeState(state);
            append("physical", action, completed ? "acknowledged" : "not-complete", "");
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to record physical action", exception);
        }
    }

    public synchronized void recordError(String area, String message) {
        JSONObject state = readState();
        JSONArray errors = state.optJSONArray("errors");
        if (errors == null) {
            errors = new JSONArray();
        }
        try {
            errors.put(new JSONObject()
                    .put("area", safe(area))
                    .put("message", PrivacySanitizer.sanitize(message))
                    .put("wall_time_utc", utcNow())
                    .put("elapsed_realtime_ms", SystemClock.elapsedRealtime()));
            state.put("errors", errors);
            writeState(state);
            append(area, "error", "failed", message);
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to record sanitized error", exception);
        }
    }

    public synchronized void recordCleanup(String kind, boolean completed, String details) {
        JSONObject state = readState();
        JSONObject cleanup = state.optJSONObject("cleanup");
        if (cleanup == null) {
            cleanup = new JSONObject();
        }
        try {
            cleanup.put(safe(kind), new JSONObject()
                    .put("completed", completed)
                    .put("details", PrivacySanitizer.sanitize(details))
                    .put("wall_time_utc", utcNow()));
            state.put("cleanup", cleanup);
            writeState(state);
            append("cleanup", kind, completed ? "complete" : "refused", details);
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to record cleanup state", exception);
        }
    }

    public synchronized JSONObject exportEvidence() {
        JSONObject state = readState();
        try {
            long endedElapsed = SystemClock.elapsedRealtime();
            JSONObject timing = state.optJSONObject("session_timing");
            if (timing == null) {
                timing = new JSONObject()
                        .put("started_wall_time_utc", utcNow())
                        .put("started_elapsed_realtime_ms", endedElapsed);
            }
            long startedElapsed = timing.getLong("started_elapsed_realtime_ms");
            timing.put("ended_wall_time_utc", utcNow())
                    .put("ended_elapsed_realtime_ms", endedElapsed)
                    .put("total_monotonic_duration_ms",
                            Math.max(0, endedElapsed - startedElapsed));
            JSONObject result = new JSONObject()
                    .put("schema_version", BuildConfig.EVIDENCE_SCHEMA_VERSION)
                    .put("proof_app", new JSONObject()
                            .put("version", BuildConfig.VERSION_NAME)
                            .put("source_commit", BuildConfig.SOURCE_COMMIT)
                            .put("build_variant", BuildConfig.BUILD_TYPE)
                            .put("dependencies", new JSONObject()
                                    .put("android_gradle_plugin", "9.3.0")
                                    .put("gradle", "9.5.0")
                                    .put("java_toolchain", "17")
                                    .put("kotlin_application_source", "not used")
                                    .put("compile_sdk", 36)
                                    .put("target_sdk", 36)
                                    .put("minimum_sdk", 33)
                                    .put("media3", BuildConfig.MEDIA3_VERSION)))
                    .put("environment", new JSONObject()
                            .put("android_release", Build.VERSION.RELEASE)
                            .put("android_build", Build.DISPLAY)
                            .put("device_model", Build.MANUFACTURER + " " + Build.MODEL)
                            .put("architecture", primaryAbi()))
                    .put("fixture_manifest_sha256", fixtureManifestHash())
                    .put("session_timing", timing)
                    .put("storage", state.optJSONObject("storage") == null
                            ? new JSONObject().put("status", "not run") : state.getJSONObject("storage"))
                    .put("playback", state.optJSONObject("playback") == null
                            ? new JSONObject().put("status", "not run") : state.getJSONObject("playback"))
                    .put("format_matrix", state.optJSONArray("format_matrix") == null
                            ? new JSONArray() : state.getJSONArray("format_matrix"))
                    .put("physical_actions", state.optJSONArray("physical_actions") == null
                            ? new JSONArray() : state.getJSONArray("physical_actions"))
                    .put("errors", state.optJSONArray("errors") == null
                            ? new JSONArray() : state.getJSONArray("errors"))
                    .put("cleanup", state.optJSONObject("cleanup") == null
                            ? new JSONObject() : state.getJSONObject("cleanup"))
                    .put("export", new JSONObject()
                            .put("generated_wall_time_utc", utcNow())
                            .put("generated_elapsed_realtime_ms", endedElapsed)
                            .put("privacy", "sanitized; no raw URI, volume identifier, account, path, serial, or library data"));
            assertNoProhibitedFields(result);
            return result;
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to assemble evidence", exception);
        }
    }

    public synchronized String diagnosticLog() {
        if (!logFile.exists()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(logFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(PrivacySanitizer.sanitize(line)).append('\n');
            }
            return builder.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read sanitized diagnostics", exception);
        }
    }

    public synchronized void flush() {
        append("evidence", "flush-checkpoint", "complete", "");
    }

    public synchronized void clearInternalEvidence() {
        boolean logRemoved = !logFile.exists() || logFile.delete();
        boolean stateRemoved = !stateFile.exists() || stateFile.delete();
        if (!logRemoved || !stateRemoved) {
            throw new IllegalStateException("Unable to remove app-internal evidence");
        }
    }

    public String fixtureManifestText() {
        return readAsset("fixtures/fixture-manifest.json");
    }

    public String fixtureSumsText() {
        return readAsset("fixtures/SHA256SUMS");
    }

    private void mutateState(String key, Object value) {
        JSONObject state = readState();
        try {
            state.put(key, value);
            writeState(state);
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to update structured evidence", exception);
        }
    }

    private JSONObject readState() {
        if (!stateFile.exists()) {
            return new JSONObject();
        }
        try (FileInputStream input = new FileInputStream(stateFile)) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | JSONException exception) {
            throw new IllegalStateException("Structured evidence is unreadable", exception);
        }
    }

    private void writeState(JSONObject state) {
        File temporary = new File(context.getFilesDir(), STATE_NAME + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write((state.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (IOException | JSONException exception) {
            throw new IllegalStateException("Unable to write structured evidence", exception);
        }
        if (!temporary.renameTo(stateFile)) {
            throw new IllegalStateException("Unable to atomically replace structured evidence");
        }
    }

    private String fixtureManifestHash() {
        try {
            byte[] bytes = fixtureManifestText().getBytes(StandardCharsets.UTF_8);
            return Hashing.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String readAsset(String path) {
        try (InputStream input = context.getAssets().open(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read packaged asset " + path, exception);
        }
    }

    private static String primaryAbi() {
        return Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0];
    }

    private static String safe(String value) {
        return PrivacySanitizer.sanitize(value == null ? "" : value);
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static void assertNoProhibitedFields(JSONObject value) {
        String serialized = value.toString().toLowerCase(Locale.ROOT);
        String[] prohibited = {
                "raw_document_uri", "volume_uuid", "volume_id", "account_name",
                "wifi_ssid", "installed_applications", "serial_number",
                "advertising_id", "personal_path", "media_library"
        };
        for (String key : prohibited) {
            if (serialized.contains("\"" + key + "\"")) {
                throw new IllegalStateException("Prohibited evidence field: " + key);
            }
        }
    }
}
