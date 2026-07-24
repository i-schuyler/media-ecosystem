package org.mediaecosystem.experimental.platformproof;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.mediaecosystem.experimental.platformproof.evidence.EvidenceExporter;
import org.mediaecosystem.experimental.platformproof.evidence.EvidenceStore;
import org.mediaecosystem.experimental.platformproof.formats.FormatMatrixRunner;
import org.mediaecosystem.experimental.platformproof.model.Hashing;
import org.mediaecosystem.experimental.platformproof.playback.ProofPlaybackService;
import org.mediaecosystem.experimental.platformproof.storage.SafProofManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;

@SuppressLint("SetTextI18n")
@UnstableApi
public final class MainActivity extends Activity {
    private static final int PICK_ROOT = 100;
    private static final int PICK_RELINK = 101;
    private static final int CREATE_EXPORT = 102;
    private static final int NOTIFICATION_PERMISSION = 103;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EvidenceStore evidence;
    private EvidenceExporter exporter;
    private SafProofManager storage;
    private TextView storageStatus;
    private TextView playbackStatus;
    private TextView formatStatus;
    private TextView evidenceStatus;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private FormatMatrixRunner formatRunner;
    private EvidenceExporter.ExportArtifact pendingExport;

    private final Runnable playbackTick = new Runnable() {
        @Override
        public void run() {
            if (controller != null && playbackStatus != null) {
                String fixture = controller.getCurrentMediaItem() == null
                        ? "none" : controller.getCurrentMediaItem().mediaId;
                playbackStatus.setText("Controller connected\n"
                        + "fixture: " + fixture + "\n"
                        + "state: " + playerState(controller) + "\n"
                        + "position/duration: " + controller.getCurrentPosition()
                        + " / " + Math.max(0, controller.getDuration()) + " ms\n"
                        + "Media3 candidate 1.10.1; not a production selection");
            }
            handler.postDelayed(this, 1_000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        evidence = EvidenceStore.get(this);
        exporter = new EvidenceExporter(this);
        storage = new SafProofManager(this);
        buildDiagnosticUi();
        evidence.append("activity", "activity-created", "observed",
                "recreated=" + (savedInstanceState != null));
        if (savedInstanceState != null) {
            evidence.acknowledgePhysicalAction("activity_recreation", true, 0);
        }
        refreshStorageStatus();
        connectController();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onStart() {
        super.onStart();
        evidence.append("activity", "activity-foreground", "observed", "");
        refreshStorageStatus();
    }

    @Override
    protected void onStop() {
        evidence.append("activity", "activity-background", "observed",
                "changing_configuration=" + isChangingConfigurations());
        evidence.flush();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(playbackTick);
        if (formatRunner != null && isFinishing()) {
            formatRunner.cancel();
        }
        if (controller != null) {
            controller.release();
            controller = null;
        }
        if (controllerFuture != null && !controllerFuture.isDone()) {
            controllerFuture.cancel(false);
        }
        evidence.append("activity", "activity-destroyed", "observed",
                "changing_configuration=" + isChangingConfigurations());
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == PICK_ROOT || requestCode == PICK_RELINK || requestCode == CREATE_EXPORT) {
                evidence.append("interaction", "system-picker", "cancelled",
                        "request_code=" + requestCode);
            }
            return;
        }
        Uri uri = data.getData();
        try {
            if (requestCode == PICK_ROOT) {
                storage.acceptNewRoot(uri, data.getFlags());
                refreshStorageStatus();
            } else if (requestCode == PICK_RELINK) {
                storage.acceptExplicitRelink(uri, data.getFlags());
                refreshStorageStatus();
            } else if (requestCode == CREATE_EXPORT) {
                saveAndValidateExport(uri);
            }
        } catch (RuntimeException exception) {
            evidence.recordError("interaction", exception.getMessage());
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
            refreshStorageStatus();
        }
    }

    private void buildDiagnosticUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, dp(48));
        scroll.addView(root);

        TextView warning = text(
                "EXPERIMENTAL PHASE 1 PROOF\nSynthetic data only\n\n"
                        + "This disposable diagnostic app proves platform capabilities. "
                        + "It is not the production player, UI, catalog, queue, storage abstraction, "
                        + "package identity, language decision, or playback-engine selection.",
                18, Color.rgb(120, 0, 0));
        warning.setPadding(dp(12), dp(12), dp(12), dp(12));
        warning.setBackgroundColor(Color.rgb(255, 235, 235));
        root.addView(warning, matchWrap());

        addSection(root, "1. Storage access");
        storageStatus = text("", 14, Color.DKGRAY);
        root.addView(storageStatus, matchWrap());
        root.addView(button("Select removable SD root", view ->
                startActivityForResult(storage.rootPickerIntent(), PICK_ROOT)), matchWrap());
        root.addView(button("Recheck persisted access / reinsertion", view ->
                refreshStorageStatus()), matchWrap());
        root.addView(button("Explicit relink: select existing proof directory", view -> {
            Toast.makeText(this,
                    "Select the existing Media-Ecosystem-Phase1-Proof-v1 directory itself.",
                    Toast.LENGTH_LONG).show();
            storage.beginExplicitRelink();
            startActivityForResult(storage.relinkPickerIntent(), PICK_RELINK);
        }), matchWrap());
        root.addView(button("Safely revoke this proof grant", view ->
                confirm("Revoke only this proof's persisted URI grant? "
                        + "Remembered state and marker data will be retained for explicit relink.", () -> {
                    storage.revokePersistedGrantForProof();
                    refreshStorageStatus();
                })), matchWrap());
        root.addView(button("Prepare safe SD unmount/removal", view ->
                prepareSafeRemoval()), matchWrap());
        root.addView(button("Intentional process termination check", view ->
                terminateForRestartProof()), matchWrap());

        addSection(root, "2. Playback lifecycle");
        playbackStatus = text("Connecting to MediaSessionService…", 14, Color.DKGRAY);
        root.addView(playbackStatus, matchWrap());
        root.addView(button("Start guided playback evidence session", view -> {
            evidence.append("playback", "guided-session-start", "started",
                    "required_screen_off_duration_ms=" + ProofPlaybackService.REQUIRED_SCREEN_OFF_MS);
            play();
        }), matchWrap());
        LinearLayout controls = horizontal();
        controls.addView(button("Play", view -> play()));
        controls.addView(button("Pause", view -> withController(Player::pause)));
        controls.addView(button("Stop", view -> withController(Player::stop)));
        root.addView(controls, matchWrap());
        LinearLayout navigation = horizontal();
        navigation.addView(button("Previous", view -> withController(Player::seekToPreviousMediaItem)));
        navigation.addView(button("Seek +2s", view -> withController(
                player -> player.seekTo(Math.min(
                        Math.max(0, player.getDuration()), player.getCurrentPosition() + 2_000)))));
        navigation.addView(button("Next", view -> withController(Player::seekToNextMediaItem)));
        root.addView(navigation, matchWrap());
        root.addView(button("Acknowledge notification play/pause worked", view ->
                evidence.acknowledgePhysicalAction("notification_play_pause", true, 0)), matchWrap());
        root.addView(button("Acknowledge lock-screen controls + metadata worked", view -> {
            evidence.acknowledgePhysicalAction("lock_screen_play_pause", true, 0);
            evidence.acknowledgePhysicalAction("lock_screen_metadata", true, 0);
        }), matchWrap());
        root.addView(button("Acknowledge Bluetooth/hardware media button worked", view ->
                evidence.acknowledgePhysicalAction("hardware_media_button", true, 0)), matchWrap());
        root.addView(button("Acknowledge audio-focus interruption was tested", view ->
                evidence.acknowledgePhysicalAction("audio_focus_interruption", true, 0)), matchWrap());
        root.addView(button("Acknowledge becoming-noisy behavior was tested", view ->
                evidence.acknowledgePhysicalAction("becoming_noisy", true, 0)), matchWrap());

        addSection(root, "3. Format matrix");
        formatStatus = text(
                "Not run. Build success is not codec evidence; run this on the physical tablet.",
                14, Color.DKGRAY);
        root.addView(formatStatus, matchWrap());
        root.addView(button("Run all 8 formats with bounded timeouts", view ->
                runFormatMatrix()), matchWrap());

        addSection(root, "4. Evidence");
        evidenceStatus = text(
                "Evidence remains app-private until you deliberately export one validated ZIP.",
                14, Color.DKGRAY);
        root.addView(evidenceStatus, matchWrap());
        root.addView(button("Export evidence ZIP", view -> beginExport()), matchWrap());

        addSection(root, "5. Cleanup");
        root.addView(text(
                "Cleanup is optional until after export. SD cleanup validates the versioned marker "
                        + "and refuses unknown directories. It never deletes the selected root or siblings.",
                14, Color.DKGRAY), matchWrap());
        root.addView(button("Remove validated SD proof directory", view ->
                confirm("Remove only the validated proof directory?", () -> {
                    storage.cleanupProofDirectory();
                    refreshStorageStatus();
                })), matchWrap());
        root.addView(button("Remove temporary export ZIPs", view -> {
            int removed = exporter.cleanupTemporaryExports();
            evidenceStatus.setText("Removed " + removed + " app-cache export ZIP(s).");
        }), matchWrap());
        root.addView(button("Remove app-internal evidence", view ->
                confirm("Remove app-internal evidence? Export first if it is needed.", () -> {
                    evidence.clearInternalEvidence();
                    evidenceStatus.setText("App-internal evidence removed.");
                })), matchWrap());

        setContentView(scroll);
    }

    private void connectController() {
        SessionToken token = new SessionToken(this,
                new ComponentName(this, ProofPlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                evidence.append("playback", "media-controller-connected", "passed", "");
                handler.post(playbackTick);
            } catch (ExecutionException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                evidence.recordError("playback", "MediaController connection failed: " + exception);
                playbackStatus.setText("MediaController connection failed; see sanitized diagnostics.");
            }
        }, getMainExecutor());
    }

    private void play() {
        withController(player -> {
            player.prepare();
            player.play();
        });
    }

    private void runFormatMatrix() {
        withController(Player::pause);
        if (formatRunner != null) {
            Toast.makeText(this, "A format matrix is already active or completed.", Toast.LENGTH_SHORT).show();
            return;
        }
        formatRunner = new FormatMatrixRunner(this, new FormatMatrixRunner.Listener() {
            @Override
            public void onProgress(String message) {
                formatStatus.setText(message);
            }

            @Override
            public void onComplete(JSONArray results) {
                int passed = 0;
                for (int index = 0; index < results.length(); index++) {
                    if ("passed".equals(results.optJSONObject(index).optString("disposition"))) {
                        passed++;
                    }
                }
                formatStatus.setText("Physical-device format matrix complete: "
                        + passed + "/8 passed. Export evidence for full dispositions.");
                formatRunner = null;
            }
        });
        formatRunner.start();
    }

    private void beginExport() {
        try {
            pendingExport = exporter.createAndValidate();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, pendingExport.suggestedName());
            startActivityForResult(intent, CREATE_EXPORT);
        } catch (RuntimeException exception) {
            evidence.recordError("evidence", exception.getMessage());
            evidenceStatus.setText("Export preparation failed; see sanitized diagnostics.");
        }
    }

    private void saveAndValidateExport(Uri destination) {
        if (pendingExport == null) {
            throw new IllegalStateException("No validated export is pending");
        }
        try (InputStream input = new BufferedInputStream(
                new java.io.FileInputStream(pendingExport.file()));
             OutputStream output = new BufferedOutputStream(
                     getContentResolver().openOutputStream(destination, "wt"))) {
            input.transferTo(output);
            output.flush();
        } catch (IOException | NullPointerException exception) {
            throw new IllegalStateException("Unable to save evidence ZIP", exception);
        }
        String destinationHash;
        try (InputStream input = new BufferedInputStream(
                getContentResolver().openInputStream(destination))) {
            if (input == null) {
                throw new IllegalStateException("Provider returned no saved evidence stream");
            }
            destinationHash = sha256(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to validate saved evidence ZIP", exception);
        }
        if (!pendingExport.sha256().equals(destinationHash)) {
            throw new IllegalStateException("Saved evidence ZIP checksum mismatch");
        }
        evidence.append("evidence", "saved-export-validated", "passed",
                "filename=" + pendingExport.suggestedName() + ",sha256=" + destinationHash);
        evidenceStatus.setText("Export saved and checksum-validated:\n"
                + pendingExport.suggestedName() + "\nSHA-256 " + destinationHash);
        pendingExport = null;
    }

    private void refreshStorageStatus() {
        try {
            SafProofManager.StorageSnapshot snapshot = storage.observeRememberedAccess();
            storageStatus.setText("state: " + snapshot.state() + "\n"
                    + "sanitized root token: " + snapshot.rootToken() + "\n"
                    + "permission: " + snapshot.permission().sanitizedDescription() + "\n"
                    + "accessible: " + snapshot.accessible() + "\n"
                    + "unavailable never means deleted: "
                    + snapshot.unavailableNotDeletedAssertion() + "\n"
                    + snapshot.detail());
        } catch (RuntimeException exception) {
            evidence.recordError("storage", exception.getMessage());
            storageStatus.setText("Storage observation failed closed: " + exception.getMessage());
        }
    }

    private void prepareSafeRemoval() {
        if (formatRunner != null) {
            formatRunner.cancel();
            formatRunner = null;
        }
        withController(player -> {
            player.pause();
            player.stop();
        });
        evidence.flush();
        evidence.append("storage", "safe-removal-checkpoint", "ready",
                "all proof handles are closed; safe to use Android eject/unmount UI");
        storageStatus.setText(storageStatus.getText()
                + "\n\nSAFE TO UNMOUNT: proof handles are closed. Use Android storage eject, "
                + "then physically remove the card.");
    }

    private void terminateForRestartProof() {
        storage.prepareIntentionalProcessTermination();
        Toast.makeText(this,
                "Evidence flushed. The proof process will stop; reopen the app.",
                Toast.LENGTH_LONG).show();
        handler.postDelayed(() -> {
            finishAndRemoveTask();
            Process.killProcess(Process.myPid());
        }, 1_500);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            evidence.append("playback", "notification-permission",
                    granted ? "granted" : "not-granted",
                    "System media controls may be limited if not granted");
        }
    }

    private void withController(java.util.function.Consumer<Player> action) {
        if (controller == null) {
            Toast.makeText(this, "Media controller is still connecting.", Toast.LENGTH_SHORT).show();
            return;
        }
        action.accept(controller);
    }

    private void confirm(String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        evidence.recordError("cleanup", exception.getMessage());
                        Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void addSection(LinearLayout root, String title) {
        TextView heading = text(title, 20, Color.BLACK);
        heading.setPadding(0, dp(28), 0, dp(8));
        heading.setAccessibilityHeading(true);
        root.addView(heading, matchWrap());
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setContentDescription(label);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(0, 1.1f);
        return text;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String playerState(Player player) {
        return switch (player.getPlaybackState()) {
            case Player.STATE_IDLE -> "idle";
            case Player.STATE_BUFFERING -> "buffering";
            case Player.STATE_READY -> player.isPlaying() ? "playing" : "ready/paused";
            case Player.STATE_ENDED -> "ended";
            default -> "unknown";
        };
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
            return Hashing.hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
