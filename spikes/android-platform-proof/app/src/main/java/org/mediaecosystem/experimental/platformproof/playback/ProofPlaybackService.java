package org.mediaecosystem.experimental.platformproof.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import org.json.JSONException;
import org.json.JSONObject;
import org.mediaecosystem.experimental.platformproof.evidence.EvidenceStore;
import org.mediaecosystem.experimental.platformproof.fixtures.FixtureCatalog;
import org.mediaecosystem.experimental.platformproof.model.MonotonicDuration;
import org.mediaecosystem.experimental.platformproof.model.PlaybackStateTranslator;
import org.mediaecosystem.experimental.platformproof.model.ScreenOffProof;

import java.util.ArrayList;
import java.util.List;

public final class ProofPlaybackService extends MediaSessionService {
    public static final long REQUIRED_SCREEN_OFF_MS = 5 * 60 * 1000L;
    public static final String ACTION_START_SCREEN_OFF_PROOF =
            "org.mediaecosystem.experimental.platformproof.START_SCREEN_OFF_PROOF";
    public static final String ACTION_COMPLETE_SCREEN_OFF_PROOF =
            "org.mediaecosystem.experimental.platformproof.COMPLETE_SCREEN_OFF_PROOF";
    public static final String ACTION_CONTROLS_EXERCISED =
            "org.mediaecosystem.experimental.platformproof.CONTROLS_EXERCISED";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private MediaSession mediaSession;
    private EvidenceStore evidence;
    private long screenOffStartedMs = -1;
    private boolean screenOffPlaybackStayedActive;
    private ScreenOffProof.Status screenOffProof = ScreenOffProof.notStarted();
    private boolean minimumReachedLogged;

    private final Runnable diagnosticTick = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                updateScreenOffProgress();
                writePlaybackSnapshot("periodic-service-state");
                handler.postDelayed(this, 1_000);
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (!screenOffProof.testStarted()) {
                    evidence.append("playback", "screen-off", "ignored",
                            "guided five-minute test was not started");
                    return;
                }
                screenOffStartedMs = SystemClock.elapsedRealtime();
                screenOffPlaybackStayedActive = player.isPlaying();
                screenOffProof = screenOffProof.screenOff(player.isPlaying());
                evidence.recordScreenOffWorkflow(screenOffProof);
                evidence.append("playback", "screen-off", "observed",
                        "phase=" + screenOffProof.phase() + ",playback_state=" + player.isPlaying());
                writePlaybackSnapshot("screen-off");
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (screenOffStartedMs < 0 || !screenOffProof.testStarted()) {
                    evidence.append("playback", "screen-on", "ignored",
                            "no active guided screen-off interval");
                    return;
                }
                long duration = MonotonicDuration.between(
                        screenOffStartedMs, SystemClock.elapsedRealtime());
                screenOffProof = screenOffProof.screenOn(
                        Math.max(0, duration),
                        REQUIRED_SCREEN_OFF_MS,
                        screenOffPlaybackStayedActive,
                        player.isPlaying());
                evidence.recordScreenOffWorkflow(screenOffProof);
                evidence.acknowledgePhysicalAction(
                        "screen_off_playback",
                        screenOffProof.minimumDurationReached(),
                        Math.max(0, duration));
                evidence.append("playback", "screen-on",
                        screenOffProof.minimumDurationReached()
                                ? "minimum-duration-reached"
                                : "incomplete-duration-not-met",
                        "monotonic_duration_ms=" + duration
                                + ",continuous_playback="
                                + screenOffProof.playbackContinuedWhileScreenOff()
                                + ",is_playing_on_return=" + player.isPlaying()
                                + ",test_completed=" + screenOffProof.testCompleted());
                screenOffStartedMs = -1;
                screenOffPlaybackStayedActive = false;
                writePlaybackSnapshot("screen-on");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        evidence = EvidenceStore.get(this);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build();
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(audioAttributes, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (screenOffStartedMs >= 0 && !isPlaying) {
                    screenOffPlaybackStayedActive = false;
                    screenOffProof = screenOffProof.playbackInterrupted(
                            MonotonicDuration.between(
                                    screenOffStartedMs, SystemClock.elapsedRealtime()));
                    evidence.recordScreenOffWorkflow(screenOffProof);
                } else if (isPlaying && screenOffProof.testStarted()
                        && screenOffStartedMs < 0
                        && !screenOffProof.minimumDurationReached()
                        && !screenOffProof.testCompleted()) {
                    screenOffProof = screenOffProof.playbackReady(true);
                    evidence.recordScreenOffWorkflow(screenOffProof);
                    evidence.append("playback", "screen-off-proof-ready", "ready",
                            "playback is active; turn the screen off and leave it off for five minutes");
                }
                evidence.append("playback", "is-playing-changed", "observed",
                        "is_playing=" + isPlaying);
                writePlaybackSnapshot("is-playing-changed");
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                evidence.append("playback", "player-state-changed", "observed",
                        "media3_state=" + playbackState);
                writePlaybackSnapshot("player-state-changed");
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                String observation = switch (reason) {
                    case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ->
                            "audio-focus-interruption";
                    case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                            "becoming-noisy";
                    case Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE ->
                            "system-or-hardware-control";
                    default -> "play-when-ready-change";
                };
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        && screenOffProof.testStarted()) {
                    screenOffProof = screenOffProof.markControlsExercised();
                    evidence.recordScreenOffWorkflow(screenOffProof);
                }
                evidence.append("playback", observation, "observed",
                        "play_when_ready=" + playWhenReady + ",reason=" + reason);
                writePlaybackSnapshot(observation);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                evidence.recordError("playback", error.getErrorCodeName() + ": " + error.getMessage());
                writePlaybackSnapshot("player-error");
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                evidence.append("playback", "media-item-transition", "observed",
                        mediaItem == null ? "no current item" : "fixture_id=" + mediaItem.mediaId);
                writePlaybackSnapshot("media-item-transition");
            }
        });
        List<MediaItem> mediaItems = new ArrayList<>();
        for (FixtureCatalog.Fixture fixture : FixtureCatalog.load(this).fixtures()) {
            mediaItems.add(mediaItem(fixture));
        }
        player.setMediaItems(mediaItems);
        mediaSession = new MediaSession.Builder(this, player).build();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED);
        evidence.append("playback", "media-session-service-created", "passed",
                "foreground media playback candidate ready");
        handler.post(diagnosticTick);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_SCREEN_OFF_PROOF.equals(intent.getAction())) {
            startScreenOffProof();
        } else if (intent != null && ACTION_COMPLETE_SCREEN_OFF_PROOF.equals(intent.getAction())) {
            completeScreenOffProof();
        } else if (intent != null && ACTION_CONTROLS_EXERCISED.equals(intent.getAction())) {
            screenOffProof = screenOffProof.markControlsExercised();
            evidence.recordScreenOffWorkflow(screenOffProof);
            evidence.append("playback", "screen-off-controls", "observed",
                    "controls_exercised=true");
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        evidence.append("playback", "activity-task-removed", "observed",
                "service_is_playing=" + (player != null && player.isPlaying()));
        writePlaybackSnapshot("activity-task-removed");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        evidence.append("playback", "media-session-service-destroyed", "observed", "");
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(screenReceiver);
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private void writePlaybackSnapshot(String observation) {
        if (player == null) {
            return;
        }
        try {
            MediaMetadata metadata = player.getMediaMetadata();
            JSONObject snapshot = new JSONObject()
                    .put("candidate", "AndroidX Media3 ExoPlayer + MediaSessionService")
                    .put("candidate_version", "1.10.1")
                    .put("disposable_candidate_only", true)
                    .put("production_engine_selected", false)
                    .put("observation", observation)
                    .put("state", PlaybackStateTranslator.translate(
                            player.getPlaybackState(), player.isPlaying(), player.getPlayerError() != null).name())
                    .put("is_playing", player.isPlaying())
                    .put("play_when_ready", player.getPlayWhenReady())
                    .put("current_fixture_id", player.getCurrentMediaItem() == null
                            ? "" : player.getCurrentMediaItem().mediaId)
                    .put("title", metadata.title == null ? "" : metadata.title.toString())
                    .put("artist", metadata.artist == null ? "" : metadata.artist.toString())
                    .put("album", metadata.albumTitle == null ? "" : metadata.albumTitle.toString())
                    .put("position_ms", Math.max(0, player.getCurrentPosition()))
                    .put("duration_ms", Math.max(0, player.getDuration()))
                    .put("service_present", true)
                    .put("required_screen_off_duration_ms", REQUIRED_SCREEN_OFF_MS)
                    .put("screen_off_workflow_phase", screenOffProof.phase().name())
                    .put("elapsed_realtime_ms", SystemClock.elapsedRealtime());
            evidence.recordPlayback(snapshot);
        } catch (JSONException exception) {
            evidence.recordError("playback", "unable to serialize playback snapshot");
        }
    }

    private void startScreenOffProof() {
        screenOffStartedMs = -1;
        screenOffPlaybackStayedActive = false;
        minimumReachedLogged = false;
        screenOffProof = ScreenOffProof.notStarted().start();
        evidence.recordScreenOffWorkflow(screenOffProof);
        evidence.append("playback", "screen-off-proof-start", "test-started",
                "minimum_duration_ms=" + REQUIRED_SCREEN_OFF_MS
                        + "; waiting for playback to become active");
        player.prepare();
        player.play();
        if (player.isPlaying()) {
            screenOffProof = screenOffProof.playbackReady(true);
            evidence.recordScreenOffWorkflow(screenOffProof);
        }
    }

    private void updateScreenOffProgress() {
        if (screenOffStartedMs < 0
                || screenOffProof.phase() != ScreenOffProof.Phase.SCREEN_OFF_PLAYING) {
            return;
        }
        long duration = MonotonicDuration.between(
                screenOffStartedMs, SystemClock.elapsedRealtime());
        if (!player.isPlaying()) {
            screenOffPlaybackStayedActive = false;
            screenOffProof = screenOffProof.playbackInterrupted(duration);
            evidence.recordScreenOffWorkflow(screenOffProof);
            evidence.append("playback", "screen-off-proof", "incomplete",
                    "playback stopped before the five-minute minimum");
            return;
        }
        ScreenOffProof.Status updated = screenOffProof.minimumReached(
                duration, REQUIRED_SCREEN_OFF_MS);
        if (updated.minimumDurationReached() && !minimumReachedLogged) {
            minimumReachedLogged = true;
            screenOffProof = updated;
            evidence.recordScreenOffWorkflow(screenOffProof);
            evidence.append("playback", "screen-off-proof", "minimum-duration-reached",
                    "monotonic_duration_ms=" + duration
                            + "; turn the screen on and complete the test in the app");
        }
    }

    private void completeScreenOffProof() {
        try {
            screenOffProof = screenOffProof.complete();
            evidence.recordScreenOffWorkflow(screenOffProof);
            evidence.append("playback", "screen-off-proof", "test-completed",
                    "minimum duration reached; controls remain an independent dimension");
        } catch (IllegalStateException exception) {
            evidence.recordScreenOffWorkflow(screenOffProof);
            evidence.append("playback", "screen-off-proof", "completion-refused",
                    exception.getMessage());
        }
    }

    private static MediaItem mediaItem(FixtureCatalog.Fixture fixture) {
        return new MediaItem.Builder()
                .setMediaId(fixture.id())
                .setUri(fixture.assetUri())
                .setMimeType(fixture.mimeType())
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(fixture.title())
                        .setArtist(fixture.artist())
                        .setAlbumTitle(fixture.album())
                        .setSubtitle(fixture.requiredFormat())
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build())
                .build();
    }
}
