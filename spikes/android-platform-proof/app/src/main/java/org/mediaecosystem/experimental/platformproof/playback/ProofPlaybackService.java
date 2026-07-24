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

import java.util.ArrayList;
import java.util.List;

public final class ProofPlaybackService extends MediaSessionService {
    public static final long REQUIRED_SCREEN_OFF_MS = 5 * 60 * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private MediaSession mediaSession;
    private EvidenceStore evidence;
    private long screenOffStartedMs = -1;
    private boolean screenOffPlaybackStayedActive;

    private final Runnable diagnosticTick = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                writePlaybackSnapshot("periodic-service-state");
                handler.postDelayed(this, 1_000);
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenOffStartedMs = SystemClock.elapsedRealtime();
                screenOffPlaybackStayedActive = player.isPlaying();
                evidence.append("playback", "screen-off", "observed",
                        "playback_state=" + player.isPlaying());
                writePlaybackSnapshot("screen-off");
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                long duration = screenOffStartedMs < 0 ? -1
                        : MonotonicDuration.between(screenOffStartedMs, SystemClock.elapsedRealtime());
                boolean met = duration >= REQUIRED_SCREEN_OFF_MS
                        && screenOffPlaybackStayedActive
                        && player.isPlaying();
                evidence.acknowledgePhysicalAction("screen_off_playback", met, Math.max(0, duration));
                evidence.append("playback", "screen-on", met ? "duration-met" : "duration-not-met",
                        "monotonic_duration_ms=" + duration
                                + ",continuous_playback=" + screenOffPlaybackStayedActive
                                + ",is_playing_on_return=" + player.isPlaying());
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
                    .put("elapsed_realtime_ms", SystemClock.elapsedRealtime());
            evidence.recordPlayback(snapshot);
        } catch (JSONException exception) {
            evidence.recordError("playback", "unable to serialize playback snapshot");
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
