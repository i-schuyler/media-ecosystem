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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final List<FixtureCatalog.Fixture> allFixtures;
    private final List<FixtureCatalog.Fixture> fixtures;
    private final List<String> targetedFixtureIds;
    private final Map<String, Integer> resultIndexById = new LinkedHashMap<>();
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
    private boolean openAttempted;
    private boolean prepared;
    private boolean playbackStarted;
    private boolean durationWithinTolerance;
    private boolean endOfTrackObserved;
    private String decoderName = "not exposed";
    private String reportedFormat = "not exposed";
    private Runnable timeout;

    public FormatMatrixRunner(
            Context context,
            List<String> fixtureIdsToRun,
            Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        targetedFixtureIds = List.copyOf(fixtureIdsToRun);
        evidence = EvidenceStore.get(context);
        FixtureCatalog catalog = FixtureCatalog.load(context);
        allFixtures = catalog.fixtures();
        List<FixtureCatalog.Fixture> selectedFixtures = new ArrayList<>();
        for (String fixtureId : fixtureIdsToRun) {
            selectedFixtures.add(catalog.byId(fixtureId));
        }
        fixtures = List.copyOf(selectedFixtures);
        if (fixtures.isEmpty()
                || fixtures.size() != new HashSet<>(fixtureIdsToRun).size()) {
            throw new IllegalArgumentException("Targeted format run must contain unique fixture IDs");
        }
        for (int manifestIndex = 0; manifestIndex < allFixtures.size(); manifestIndex++) {
            FixtureCatalog.Fixture fixture = allFixtures.get(manifestIndex);
            resultIndexById.put(fixture.id(), manifestIndex);
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
                "active_required_fixtures=" + allFixtures.size()
                        + ",targeted_fixture_ids="
                        + targetedFixtureIds
                        + ",bounded_timeouts=true");
        runCurrent();
    }

    public void cancel() {
        if (running.getAndSet(false)) {
            finishCurrent(
                    FormatDisposition.INCONCLUSIVE,
                    "runner cancelled before completion",
                    false);
            releasePlayer();
            evidence.recordFormatMatrix(results);
        }
    }

    private void runCurrent() {
        if (index >= fixtures.size()) {
            running.set(false);
            evidence.recordFormatMatrix(results);
            evidence.append("formats", "matrix-complete", "complete",
                    "all six active dispositions recorded; targeted execution complete");
            listener.onComplete(results);
            return;
        }
        FixtureCatalog.Fixture fixture = fixtures.get(index);
        listener.onProgress("Preparing " + fixture.requiredFormat()
                + " (" + (index + 1) + "/" + fixtures.size() + ")");
        fixtureStarted = SystemClock.elapsedRealtime();
        positionAdvanced = false;
        seekCompleted = false;
        seekTarget = 0;
        openAttempted = false;
        prepared = false;
        playbackStarted = false;
        durationWithinTolerance = false;
        endOfTrackObserved = false;
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
                    endOfTrackObserved = true;
                    boolean passed = requiredPlaybackDimensionsPassed();
                    boolean metadataMatched = metadataMatches(
                            player.getMediaMetadata(), fixtures.get(index));
                    finishCurrent(
                            passed ? FormatDisposition.PASSED : FormatDisposition.FAILED,
                            passed
                                    ? "PB-01 playback dimensions passed; optional metadata match="
                                            + metadataMatched
                                    : "end observed but one or more required playback dimensions failed");
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
        openAttempted = true;
        player.prepare();
        scheduleTimeout(timeouts.prepareMs(), "open/prepare timeout");
    }

    private void onPrepared() {
        cancelTimeout();
        FixtureCatalog.Fixture fixture = fixtures.get(index);
        prepared = true;
        long duration = player.getDuration();
        durationWithinTolerance = duration > 0
                && Math.abs(duration - fixture.expectedDurationMs())
                <= fixture.durationToleranceMs();
        if (!durationWithinTolerance) {
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
        playbackStarted = true;
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
        finishCurrent(disposition, details, true);
    }

    private void finishCurrent(
            FormatDisposition disposition,
            String details,
            boolean continueRunner
    ) {
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
                    .put("open_result", openAttempted ? "asset submitted" : "not run")
                    .put("prepare_result", prepared ? "ready" : "not ready")
                    .put("playback_start_result", playbackStarted ? "started" : "not confirmed")
                    .put("position_advancement", positionAdvanced)
                    .put("seek_request_ms", seekTarget)
                    .put("seek_completion", seekCompleted)
                    .put("duration_result_ms", actualDuration)
                    .put("duration_within_tolerance", durationWithinTolerance)
                    .put("end_of_track_result", endOfTrackObserved)
                    .put("basic_metadata_result", metadataMatched)
                    .put("basic_metadata_required_for_pb01", false)
                    .put("required_playback_contract_result",
                            requiredPlaybackDimensionsPassed())
                    .put("warning_or_error", details)
                    .put("total_test_duration_ms", total)
                    .put("bounded_timeouts", timeouts.allBounded())
                    .put("disposition", disposition.wireValue());
            results.put(resultIndexById.get(fixture.id()), result);
            evidence.recordFormatMatrix(results);
            evidence.append("formats", "fixture-complete", disposition.wireValue(),
                    "fixture_id=" + fixture.id() + ",details=" + details);
        } catch (JSONException exception) {
            evidence.recordError("formats", "Unable to serialize " + fixture.id() + " result");
        }
        releasePlayer();
        index++;
        if (continueRunner) {
            handler.post(this::runCurrent);
        }
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

    private boolean requiredPlaybackDimensionsPassed() {
        return openAttempted
                && prepared
                && playbackStarted
                && positionAdvanced
                && seekCompleted
                && durationWithinTolerance
                && endOfTrackObserved;
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
