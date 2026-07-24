package org.mediaecosystem.experimental.platformproof.formats;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mediaecosystem.experimental.platformproof.evidence.EvidenceStore;
import org.mediaecosystem.experimental.platformproof.fixtures.FixtureCatalog;
import org.mediaecosystem.experimental.platformproof.model.FormatDisposition;
import org.mediaecosystem.experimental.platformproof.model.MonotonicDuration;
import org.mediaecosystem.experimental.platformproof.model.TimeoutPolicy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@UnstableApi
public final class FormatMatrixRunner {
    public interface Listener {
        void onProgress(String message);
        void onComplete(JSONArray results);
    }

    private enum Phase { PREPARING, STARTING, SEEKING, ENDING, COMPLETE }

    private final Context context;
    private final EvidenceStore evidence;
    private final List<FixtureCatalog.Fixture> fixtures;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TimeoutPolicy timeouts = TimeoutPolicy.boundedDefaults();
    private final Listener listener;
    private final JSONArray results = new JSONArray();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExoPlayer player;
    private int index;
    private Phase phase;
    private long fixtureStarted;
    private long advanceBaseline;
    private long seekTarget;
    private boolean positionAdvanced;
    private boolean seekCompleted;
    private String decoderName = "not exposed";
    private String reportedFormat = "not exposed";
    private Runnable timeout;

    public FormatMatrixRunner(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        evidence = EvidenceStore.get(context);
        fixtures = FixtureCatalog.load(context).fixtures();
        for (FixtureCatalog.Fixture fixture : fixtures) {
            results.put(notRun(fixture));
        }
        evidence.recordFormatMatrix(results);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Format matrix is already running");
        }
        index = 0;
        evidence.append("formats", "matrix-start", "started",
                "fixtures=8,bounded_timeouts=true");
        runCurrent();
    }

    public void cancel() {
        if (running.getAndSet(false)) {
            finishCurrent(FormatDisposition.INCONCLUSIVE, "runner cancelled before completion");
            releasePlayer();
            evidence.recordFormatMatrix(results);
        }
    }

    private void runCurrent() {
        if (index >= fixtures.size()) {
            running.set(false);
            evidence.recordFormatMatrix(results);
            evidence.append("formats", "matrix-complete", "complete", "all eight dispositions recorded");
            listener.onComplete(results);
            return;
        }
        FixtureCatalog.Fixture fixture = fixtures.get(index);
        listener.onProgress("Preparing " + fixture.requiredFormat() + " (" + (index + 1) + "/8)");
        fixtureStarted = SystemClock.elapsedRealtime();
        positionAdvanced = false;
        seekCompleted = false;
        decoderName = "not exposed";
        reportedFormat = "not exposed";
        phase = Phase.PREPARING;

        player = new ExoPlayer.Builder(context).build();
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onAudioDecoderInitialized(
                    EventTime eventTime,
                    String name,
                    long initializationDurationMs
            ) {
                decoderName = name;
            }
        });
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (!running.get()) {
                    return;
                }
                if (playbackState == Player.STATE_READY && phase == Phase.PREPARING) {
                    onPrepared();
                } else if (playbackState == Player.STATE_ENDED && phase == Phase.ENDING) {
                    boolean metadataMatched = metadataMatches(
                            player.getMediaMetadata(), fixtures.get(index));
                    finishCurrent(positionAdvanced && seekCompleted && metadataMatched
                                    ? FormatDisposition.PASSED : FormatDisposition.FAILED,
                            positionAdvanced && seekCompleted && metadataMatched
                                    ? "open, prepare, start, advancement, seek, duration, metadata, and end observed"
                                    : "track ended without every required intermediate or metadata observation");
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (running.get() && isPlaying && phase == Phase.STARTING) {
                    onPlaybackStarted();
                }
            }

            @Override
            public void onPositionDiscontinuity(
                    Player.PositionInfo oldPosition,
                    Player.PositionInfo newPosition,
                    int reason
            ) {
                if (running.get() && phase == Phase.SEEKING
                        && reason == Player.DISCONTINUITY_REASON_SEEK) {
                    seekCompleted = Math.abs(newPosition.positionMs - seekTarget) <= 750;
                    phase = Phase.ENDING;
                    scheduleTimeout(timeouts.endMs(), "end-of-track timeout");
                    listener.onProgress("Seek observed for " + fixture.requiredFormat() + "; waiting for end");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (running.get()) {
                    finishCurrent(FormatDisposition.FAILED,
                            error.getErrorCodeName() + ": " + error.getMessage());
                }
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                for (Tracks.Group group : tracks.getGroups()) {
                    for (int track = 0; track < group.length; track++) {
                        Format format = group.getTrackFormat(track);
                        if (format.sampleMimeType != null && format.sampleMimeType.startsWith("audio/")) {
                            reportedFormat = "mime=" + text(format.sampleMimeType)
                                    + ",codecs=" + text(format.codecs)
                                    + ",sample_rate_hz=" + format.sampleRate
                                    + ",channels=" + format.channelCount;
                            return;
                        }
                    }
                }
            }
        });

        player.setMediaItem(mediaItem(fixture));
        player.prepare();
        scheduleTimeout(timeouts.prepareMs(), "open/prepare timeout");
    }

    private void onPrepared() {
        cancelTimeout();
        FixtureCatalog.Fixture fixture = fixtures.get(index);
        long duration = player.getDuration();
        if (duration <= 0
                || Math.abs(duration - fixture.expectedDurationMs()) > fixture.durationToleranceMs()) {
            finishCurrent(FormatDisposition.FAILED,
                    "duration mismatch: expected=" + fixture.expectedDurationMs() + ",actual=" + duration);
            return;
        }
        phase = Phase.STARTING;
        player.play();
        scheduleTimeout(timeouts.playbackStartMs(), "playback-start timeout");
    }

    private void onPlaybackStarted() {
        cancelTimeout();
        FixtureCatalog.Fixture fixture = fixtures.get(index);
        advanceBaseline = player.getCurrentPosition();
        listener.onProgress("Playback started for " + fixture.requiredFormat());
        handler.postDelayed(() -> {
            if (!running.get() || phase != Phase.STARTING) {
                return;
            }
            positionAdvanced = player.getCurrentPosition() >= advanceBaseline + 250;
            if (!positionAdvanced) {
                finishCurrent(FormatDisposition.FAILED, "position did not advance");
                return;
            }
            seekTarget = Math.max(1_000, player.getDuration() / 2);
            phase = Phase.SEEKING;
            player.seekTo(seekTarget);
            scheduleTimeout(timeouts.seekMs(), "seek-completion timeout");
        }, 750);
    }

    private void finishCurrent(FormatDisposition disposition, String details) {
        if (index >= fixtures.size()) {
            return;
        }
        cancelTimeout();
        FixtureCatalog.Fixture fixture = fixtures.get(index);
        long actualDuration = player == null ? -1 : player.getDuration();
        long total = MonotonicDuration.between(fixtureStarted, SystemClock.elapsedRealtime());
        MediaMetadata metadata = player == null ? MediaMetadata.EMPTY : player.getMediaMetadata();
        boolean metadataMatched = metadataMatches(metadata, fixture);
        try {
            JSONObject result = new JSONObject()
                    .put("fixture_id", fixture.id())
                    .put("required_format", fixture.requiredFormat())
                    .put("fixture_sha256", fixture.sha256())
                    .put("expected_duration_ms", fixture.expectedDurationMs())
                    .put("detected_mime", fixture.mimeType())
                    .put("expected_container", fixture.container())
                    .put("expected_codec", fixture.codec())
                    .put("candidate_reported_format", reportedFormat)
                    .put("decoder", decoderName)
                    .put("open_result", disposition == FormatDisposition.NOT_RUN ? "not run" : "attempted")
                    .put("prepare_result", actualDuration > 0 ? "ready" : "not ready")
                    .put("playback_start_result", positionAdvanced ? "started and advanced" : "not confirmed")
                    .put("position_advancement", positionAdvanced)
                    .put("seek_request_ms", seekTarget)
                    .put("seek_completion", seekCompleted)
                    .put("duration_result_ms", actualDuration)
                    .put("end_of_track_result", disposition == FormatDisposition.PASSED)
                    .put("basic_metadata_result", metadataMatched)
                    .put("warning_or_error", details)
                    .put("total_test_duration_ms", total)
                    .put("bounded_timeouts", timeouts.allBounded())
                    .put("disposition", disposition.wireValue());
            results.put(index, result);
            evidence.recordFormatMatrix(results);
            evidence.append("formats", "fixture-complete", disposition.wireValue(),
                    "fixture_id=" + fixture.id() + ",details=" + details);
        } catch (JSONException exception) {
            evidence.recordError("formats", "Unable to serialize " + fixture.id() + " result");
        }
        releasePlayer();
        index++;
        handler.post(this::runCurrent);
    }

    private void scheduleTimeout(long milliseconds, String failure) {
        cancelTimeout();
        timeout = () -> {
            if (running.get()) {
                finishCurrent(FormatDisposition.FAILED, failure);
            }
        };
        handler.postDelayed(timeout, milliseconds);
    }

    private void cancelTimeout() {
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
    }

    private void releasePlayer() {
        cancelTimeout();
        if (player != null) {
            player.release();
            player = null;
        }
        phase = Phase.COMPLETE;
    }

    private static JSONObject notRun(FixtureCatalog.Fixture fixture) {
        try {
            return new JSONObject()
                    .put("fixture_id", fixture.id())
                    .put("required_format", fixture.requiredFormat())
                    .put("fixture_sha256", fixture.sha256())
                    .put("expected_duration_ms", fixture.expectedDurationMs())
                    .put("detected_mime", fixture.mimeType())
                    .put("disposition", FormatDisposition.NOT_RUN.wireValue());
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to initialize format result", exception);
        }
    }

    private static MediaItem mediaItem(FixtureCatalog.Fixture fixture) {
        return new MediaItem.Builder()
                .setMediaId(fixture.id())
                .setUri(fixture.assetUri())
                .setMimeType(fixture.mimeType())
                .build();
    }

    private static boolean metadataMatches(
            MediaMetadata metadata,
            FixtureCatalog.Fixture fixture
    ) {
        return text(metadata.title).equals(fixture.title())
                && text(metadata.artist).equals(fixture.artist())
                && text(metadata.albumTitle).equals(fixture.album());
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
