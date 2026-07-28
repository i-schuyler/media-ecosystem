package org.mediaecosystem.experimental.platformproof.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediaecosystem.experimental.platformproof.evidence.EvidenceStore;
import org.mediaecosystem.experimental.platformproof.model.PersistedPermission;
import org.mediaecosystem.experimental.platformproof.model.PrivacySanitizer;
import org.mediaecosystem.experimental.platformproof.model.ProofMarker;
import org.mediaecosystem.experimental.platformproof.model.ProofState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class SafProofManager {
    private static final String PREFS = "private-saf-proof";
    private static final String ROOT_URI = "root-uri";
    private static final String ACTIVE_GRANT_URI = "active-grant-uri";
    private static final String PROOF_DIR_URI = "proof-dir-uri";
    private static final String MARKER_URI = "marker-uri";
    private static final String SESSION_ID = "session-id";
    private static final String FLAGS = "flags";
    private static final String STATE = "state";
    private static final String RESTART_CHECKPOINT = "restart-checkpoint";
    private static final String BOOT_COUNT = "boot-count";

    private final Context context;
    private final ContentResolver resolver;
    private final SharedPreferences preferences;
    private final EvidenceStore evidence;

    public SafProofManager(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        evidence = EvidenceStore.get(context);
    }

    public Intent rootPickerIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
    }

    public Intent relinkPickerIntent() {
        return rootPickerIntent();
    }

    public void beginExplicitRelink() {
        if (!preferences.contains(SESSION_ID)) {
            throw new IllegalStateException("No remembered proof session is available to relink");
        }
        setState(ProofState.EXPLICIT_RELINK_REQUIRED);
        evidence.append("storage", "explicit-relink-required", "awaiting-user-picker",
                "no identity inference attempted; select the existing proof directory");
    }

    public StorageSnapshot revokePersistedGrantForProof() {
        String activeGrant = preferences.getString(
                ACTIVE_GRANT_URI, preferences.getString(ROOT_URI, ""));
        if (activeGrant.isEmpty()) {
            throw new IllegalStateException("No active proof grant exists");
        }
        int flags = preferences.getInt(FLAGS, 0)
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        resolver.releasePersistableUriPermission(Uri.parse(activeGrant), flags);
        setState(ProofState.PERMISSION_REVOKED);
        evidence.append("storage", "persisted-permission-revoked", "observed",
                "remembered private state retained; no deletion intent generated");
        return snapshot(ProofState.PERMISSION_REVOKED, false, false, true,
                "proof grant intentionally revoked; explicit relink is required");
    }

    public StorageSnapshot acceptNewRoot(Uri selectedRoot, int resultFlags) {
        int takeFlags = resultFlags
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0
                || (takeFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            throw new SecurityException("The selected tree did not provide both read and write access");
        }
        resolver.takePersistableUriPermission(selectedRoot, takeFlags);

        String sessionId = UUID.randomUUID().toString();
        ProofMarker marker = ProofMarker.create(sessionId);
        Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(
                selectedRoot, DocumentsContract.getTreeDocumentId(selectedRoot));
        Uri proofDirectory = createDocument(
                rootDocument, DocumentsContract.Document.MIME_TYPE_DIR, ProofMarker.DIRECTORY_NAME);
        if (proofDirectory == null || proofDirectory.equals(selectedRoot)) {
            throw new IllegalStateException("Provider did not create the isolated proof directory");
        }
        if (!ProofMarker.DIRECTORY_NAME.equals(displayName(proofDirectory))) {
            throw new IllegalStateException("Provider changed the isolated proof directory name");
        }
        Uri markerUri = createDocument(proofDirectory, "application/json", ProofMarker.MARKER_NAME);
        if (markerUri == null) {
            throw new IllegalStateException("Provider did not create the proof marker");
        }
        writeMarker(markerUri, marker);

        int bootCount = currentBootCount();
        preferences.edit()
                .putString(ROOT_URI, selectedRoot.toString())
                .putString(ACTIVE_GRANT_URI, selectedRoot.toString())
                .putString(PROOF_DIR_URI, proofDirectory.toString())
                .putString(MARKER_URI, markerUri.toString())
                .putString(SESSION_ID, sessionId)
                .putInt(FLAGS, takeFlags)
                .putString(STATE, ProofState.ACCESSIBLE.name())
                .putInt(BOOT_COUNT, bootCount)
                .putBoolean(RESTART_CHECKPOINT, false)
                .apply();
        evidence.append("storage", "persistable-permission-taken", "passed",
                PersistedPermission.fromFlags(takeFlags, true).sanitizedDescription());
        return snapshot(ProofState.ACCESSIBLE, true, true, false, "immediate marker access confirmed");
    }

    public StorageSnapshot observeRememberedAccess() {
        if (!preferences.contains(ROOT_URI)) {
            return snapshot(ProofState.NEVER_SELECTED, false, false, false, "no root selected");
        }
        Uri activeGrant = Uri.parse(preferences.getString(
                ACTIVE_GRANT_URI, preferences.getString(ROOT_URI, "")));
        boolean grant = hasPersistedGrant(activeGrant);
        if (!grant) {
            setState(ProofState.PERMISSION_REVOKED);
            return snapshot(ProofState.PERMISSION_REVOKED, false, false, false,
                    "remembered root retained privately; persisted grant absent");
        }
        boolean markerAccessible;
        try {
            markerAccessible = readAndValidateStoredMarker().valid();
        } catch (RuntimeException exception) {
            setState(ProofState.TEMPORARILY_UNAVAILABLE);
            evidence.append("storage", "remembered-root-probe", "unavailable",
                    "provider inaccessible; no deletion intent generated");
            return snapshot(ProofState.TEMPORARILY_UNAVAILABLE, true, false, true,
                    "provider unavailable; remembered state retained");
        }

        ProofState previous = currentState();
        ProofState observed = ProofState.ACCESSIBLE;
        int oldBootCount = preferences.getInt(BOOT_COUNT, -1);
        int newBootCount = currentBootCount();
        if (oldBootCount >= 0 && newBootCount >= 0 && oldBootCount != newBootCount) {
            observed = ProofState.REBOOTED_AND_STILL_ACCESSIBLE;
            preferences.edit().putInt(BOOT_COUNT, newBootCount).apply();
        } else if (preferences.getBoolean(RESTART_CHECKPOINT, false)) {
            observed = ProofState.PROCESS_RESTARTED_AND_STILL_ACCESSIBLE;
            preferences.edit().putBoolean(RESTART_CHECKPOINT, false).apply();
        } else if (previous == ProofState.TEMPORARILY_UNAVAILABLE) {
            observed = ProofState.VOLUME_REINSERTED;
        }
        setState(observed);
        evidence.append("storage", "remembered-root-probe", "passed", observed.name());
        return snapshot(observed, true, markerAccessible, true, "persisted marker validated");
    }

    public StorageSnapshot acceptExplicitRelink(Uri selectedProofDirectory, int resultFlags) {
        int takeFlags = resultFlags
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0
                || (takeFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            throw new SecurityException("The relink selection did not provide both read and write access");
        }
        resolver.takePersistableUriPermission(selectedProofDirectory, takeFlags);
        String expectedSession = preferences.getString(SESSION_ID, "");
        Uri markerUri = findMarkerInsideExplicitlySelectedProofDirectory(selectedProofDirectory);
        ProofMarker marker = readMarker(markerUri);
        if (!marker.valid() || !marker.sessionId().equals(expectedSession)) {
            throw new SecurityException("Explicit relink marker does not match this proof session");
        }
        preferences.edit()
                .putString(PROOF_DIR_URI, selectedProofDirectory.toString())
                .putString(MARKER_URI, markerUri.toString())
                .putString(ACTIVE_GRANT_URI, selectedProofDirectory.toString())
                .putInt(FLAGS, takeFlags)
                .putString(STATE, ProofState.EXPLICIT_RELINK_SUCCEEDED.name())
                .apply();
        evidence.append("storage", "explicit-relink", "passed",
                "user-selected proof directory marker validated; URI equality="
                        + selectedProofDirectory.toString().equals(preferences.getString(ROOT_URI, "")));
        return snapshot(ProofState.EXPLICIT_RELINK_SUCCEEDED, true, true, true,
                "explicit user selection and marker validation succeeded");
    }

    public void prepareIntentionalProcessTermination() {
        if (currentState() == ProofState.NEVER_SELECTED) {
            throw new IllegalStateException("Select and validate the removable root first");
        }
        preferences.edit().putBoolean(RESTART_CHECKPOINT, true).apply();
        evidence.append("storage", "intentional-process-termination", "checkpoint",
                "evidence flushed; reopen application to validate persisted marker");
        evidence.flush();
    }

    public StorageSnapshot cleanupProofDirectory() {
        String directoryValue = preferences.getString(PROOF_DIR_URI, "");
        String markerValue = preferences.getString(MARKER_URI, "");
        String sessionId = preferences.getString(SESSION_ID, "");
        if (directoryValue.isEmpty() || markerValue.isEmpty() || sessionId.isEmpty()) {
            evidence.recordCleanup("sd_proof_directory", false, "ownership evidence missing");
            throw new SecurityException("Cleanup refused: ownership evidence missing");
        }
        Uri directory = Uri.parse(directoryValue);
        Uri root = Uri.parse(preferences.getString(ROOT_URI, ""));
        Uri activeGrant = Uri.parse(preferences.getString(
                ACTIVE_GRANT_URI, preferences.getString(ROOT_URI, "")));
        if (directory.equals(root)) {
            evidence.recordCleanup("sd_proof_directory", false, "proof directory equals selected root");
            throw new SecurityException("Cleanup refused: selected root can never be deleted");
        }
        ProofMarker marker = readMarker(Uri.parse(markerValue));
        if (!ProofMarker.cleanupAllowed(displayName(directory), marker.toJson(), sessionId)) {
            evidence.recordCleanup("sd_proof_directory", false, "marker validation failed");
            throw new SecurityException("Cleanup refused: proof marker invalid");
        }
        try {
            if (!DocumentsContract.deleteDocument(resolver, directory)) {
                throw new IllegalStateException("Provider refused proof-directory deletion");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Provider cleanup failed", exception);
        }
        setState(ProofState.CLEANUP_COMPLETE);
        evidence.recordCleanup("sd_proof_directory", true,
                "validated isolated proof directory and marker removed; siblings untouched");
        return snapshot(ProofState.CLEANUP_COMPLETE, hasPersistedGrant(activeGrant), false, true,
                "proof directory removed after marker validation");
    }

    public ProofState currentState() {
        try {
            return ProofState.valueOf(preferences.getString(STATE, ProofState.NEVER_SELECTED.name()));
        } catch (IllegalArgumentException exception) {
            return ProofState.NEVER_SELECTED;
        }
    }

    public String rootToken() {
        String raw = preferences.getString(ROOT_URI, "");
        String session = preferences.getString(SESSION_ID, "");
        return raw.isEmpty() || session.isEmpty()
                ? "not-selected"
                : PrivacySanitizer.nonReversibleToken(session + "|" + raw);
    }

    private boolean hasPersistedGrant(Uri root) {
        List<android.content.UriPermission> permissions = resolver.getPersistedUriPermissions();
        for (android.content.UriPermission permission : permissions) {
            if (permission.getUri().equals(root)
                    && permission.isReadPermission()
                    && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    private ProofMarker readAndValidateStoredMarker() {
        String marker = preferences.getString(MARKER_URI, "");
        if (marker.isEmpty()) {
            throw new IllegalStateException("Stored proof marker URI missing");
        }
        return readMarker(Uri.parse(marker));
    }

    private ProofMarker readMarker(Uri markerUri) {
        try (InputStream input = resolver.openInputStream(markerUri)) {
            if (input == null) {
                throw new IllegalStateException("Provider returned no marker stream");
            }
            return ProofMarker.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException("Proof marker is unavailable", exception);
        }
    }

    private void writeMarker(Uri markerUri, ProofMarker marker) {
        try (OutputStream output = resolver.openOutputStream(markerUri, "wt")) {
            if (output == null) {
                throw new IllegalStateException("Provider returned no marker output stream");
            }
            output.write(marker.toJson().getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write deterministic proof marker", exception);
        }
        if (!readMarker(markerUri).equals(marker)) {
            throw new IllegalStateException("Proof marker verification failed");
        }
    }

    private Uri createDocument(Uri parent, String mimeType, String displayName) {
        try {
            return DocumentsContract.createDocument(resolver, parent, mimeType, displayName);
        } catch (IOException exception) {
            throw new IllegalStateException("Provider could not create " + displayName, exception);
        }
    }

    private String displayName(Uri document) {
        try (Cursor cursor = resolver.query(
                document,
                new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IllegalStateException("Provider did not return the proof directory name");
            }
            return cursor.getString(0);
        }
    }

    private Uri findMarkerInsideExplicitlySelectedProofDirectory(Uri directoryTree) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryTree, DocumentsContract.getTreeDocumentId(directoryTree));
        try (Cursor cursor = resolver.query(children,
                new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {
            if (cursor == null) {
                throw new IllegalStateException("Provider returned no relink directory listing");
            }
            Uri match = null;
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (ProofMarker.MARKER_NAME.equals(name)) {
                    if (match != null) {
                        throw new SecurityException("Multiple proof markers found; relink refused");
                    }
                    match = DocumentsContract.buildDocumentUriUsingTree(directoryTree, cursor.getString(0));
                }
            }
            if (match == null) {
                throw new SecurityException("Selected directory does not contain the proof marker");
            }
            return match;
        }
    }

    private StorageSnapshot snapshot(
            ProofState state,
            boolean persistedGrant,
            boolean accessible,
            boolean unavailableNotDeleted,
            String detail
    ) {
        PersistedPermission permission = PersistedPermission.fromFlags(
                preferences.getInt(FLAGS, 0), persistedGrant);
        StorageSnapshot snapshot = new StorageSnapshot(
                state, rootToken(), permission, accessible, unavailableNotDeleted, detail);
        evidence.recordStorage(snapshot.toJson());
        return snapshot;
    }

    private void setState(ProofState state) {
        preferences.edit().putString(STATE, state.name()).apply();
    }

    private int currentBootCount() {
        return Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1);
    }

    public record StorageSnapshot(
            ProofState state,
            String rootToken,
            PersistedPermission permission,
            boolean accessible,
            boolean unavailableNotDeletedAssertion,
            String detail
    ) {
        public JSONObject toJson() {
            try {
                return new JSONObject()
                        .put("state", state.name())
                        .put("sanitized_root_token", rootToken)
                        .put("permission", new JSONObject()
                                .put("read", permission.read())
                                .put("write", permission.write())
                                .put("persisted", permission.persisted()))
                        .put("accessible", accessible)
                        .put("unavailable_not_deleted_assertion", unavailableNotDeletedAssertion)
                        .put("raw_uri_exported", false)
                        .put("volume_identifier_exported", false)
                        .put("detail", PrivacySanitizer.sanitize(detail));
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to serialize storage evidence", exception);
            }
        }
    }
}
