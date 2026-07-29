using System.Diagnostics;
using Windows.Media;
using Windows.Media.Playback;
using Windows.Media.Core;
using Windows.Storage;
using Windows.Storage.FileProperties;

namespace MediaEcosystem.WindowsProof;

internal static class WindowsFormatRunner
{
    private const int OpenTimeoutMs = 10_000;
    private const int PlaybackStartTimeoutMs = 10_000;
    private const int AdvancementTimeoutMs = 10_000;
    private const int SeekTimeoutMs = 8_000;
    private const int EndTimeoutMs = 15_000;
    private const int SeekToleranceMs = 750;

    public static async Task<FormatResult> RunOneAsync(
        VerifiedFixture fixture,
        CancellationToken cancellationToken)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        FormatResult result = NewResult(fixture);
        MediaPlayer? player = null;
        MediaSource? source = null;
        try
        {
            result.OpenAttempted = true;
            StorageFile file = await StorageFile.GetFileFromPathAsync(fixture.AbsolutePath);
            result.ExtractedFileMetadata = await ExtractMetadataAsync(file, result);
            source = MediaSource.CreateFromStorageFile(file);
            MediaPlaybackItem item = new(source);
            player = new MediaPlayer();
            result.SourceAccepted = true;

            TaskCompletionSource<bool> opened = NewCompletionSource<bool>();
            TaskCompletionSource<string> failed = NewCompletionSource<string>();
            TaskCompletionSource<bool> ended = NewCompletionSource<bool>();
            player.MediaOpened += (_, _) => opened.TrySetResult(true);
            player.MediaFailed += (_, args) => failed.TrySetResult(args.Error.ToString());
            player.MediaEnded += (_, _) => ended.TrySetResult(true);
            player.Source = item;

            string? openFailure = await AwaitSignalAsync(
                opened.Task,
                failed.Task,
                OpenTimeoutMs,
                cancellationToken);
            if (openFailure == "timeout")
            {
                result.Timeouts.Add("media_open_timeout");
                return Finalize(result, stopwatch);
            }

            if (openFailure is not null)
            {
                result.Errors.Add($"media_open_failed:{openFailure}");
                return Finalize(result, stopwatch);
            }

            result.MediaOpenedPrepared = true;
            MediaPlaybackSession session = player.PlaybackSession;
            result.ActualDurationMs = (long)Math.Round(session.NaturalDuration.TotalMilliseconds);
            result.DurationWithinTolerance =
                Math.Abs(result.ActualDurationMs.Value - result.ExpectedDurationMs) <=
                result.DurationToleranceMs;
            result.CandidateReportedMediaProperties["can_seek"] =
                session.CanSeek.ToString().ToLowerInvariant();
            result.CandidateReportedMediaProperties["natural_duration_ms"] =
                result.ActualDurationMs.Value.ToString(
                    System.Globalization.CultureInfo.InvariantCulture);
            result.CandidateReportedMediaProperties["audio_track_count"] =
                item.AudioTracks.Count.ToString(System.Globalization.CultureInfo.InvariantCulture);

            player.Play();
            result.PlaybackStarted = await WaitUntilAsync(
                () => session.PlaybackState == MediaPlaybackState.Playing,
                PlaybackStartTimeoutMs,
                cancellationToken);
            if (!result.PlaybackStarted)
            {
                result.Timeouts.Add("playback_start_timeout");
                return Finalize(result, stopwatch);
            }

            long startPositionMs = (long)session.Position.TotalMilliseconds;
            result.PositionAdvanced = await WaitUntilAsync(
                () => session.Position.TotalMilliseconds >= startPositionMs + 300,
                AdvancementTimeoutMs,
                cancellationToken);
            if (!result.PositionAdvanced)
            {
                result.Timeouts.Add("position_advancement_timeout");
                return Finalize(result, stopwatch);
            }

            result.SeekRequested = true;
            result.SeekToleranceMs = SeekToleranceMs;
            TimeSpan seekTarget = TimeSpan.FromMilliseconds(2_000);
            TaskCompletionSource<bool> seekCompleted = NewCompletionSource<bool>();
            session.SeekCompleted += (_, _) => seekCompleted.TrySetResult(true);
            session.Position = seekTarget;
            Task seekWinner = await Task.WhenAny(
                seekCompleted.Task,
                Task.Delay(SeekTimeoutMs, cancellationToken));
            if (seekWinner != seekCompleted.Task)
            {
                await seekWinner;
                result.Timeouts.Add("seek_timeout");
                return Finalize(result, stopwatch);
            }

            result.SeekCompleted =
                Math.Abs(session.Position.TotalMilliseconds - seekTarget.TotalMilliseconds) <=
                SeekToleranceMs;
            if (!result.SeekCompleted)
            {
                result.Errors.Add("seek_outside_tolerance");
                return Finalize(result, stopwatch);
            }

            player.Play();
            Task endWinner = await Task.WhenAny(
                ended.Task,
                failed.Task,
                Task.Delay(EndTimeoutMs, cancellationToken));
            if (endWinner == ended.Task)
            {
                result.EndOfTrackObserved = true;
            }
            else if (endWinner == failed.Task)
            {
                result.Errors.Add($"playback_failed:{await failed.Task}");
            }
            else
            {
                await endWinner;
                result.Timeouts.Add("end_of_track_timeout");
            }

            result.CandidateReportedMediaProperties["final_playback_state"] =
                session.PlaybackState.ToString();
            return Finalize(result, stopwatch);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            result.Timeouts.Add("fixture_run_cancelled");
            return Finalize(result, stopwatch);
        }
        catch (Exception exception)
        {
            result.Errors.Add($"fixture_runtime_error:{exception.GetType().Name}");
            return Finalize(result, stopwatch);
        }
        finally
        {
            player?.Dispose();
            source?.Dispose();
        }
    }

    private static FormatResult NewResult(VerifiedFixture fixture) => new()
    {
        FixtureId = fixture.Id,
        VerifiedFilename = fixture.Filename,
        VerifiedSizeBytes = fixture.SizeBytes,
        VerifiedSha256 = fixture.Sha256,
        ExpectedDurationMs = fixture.ExpectedDurationMs,
        DurationToleranceMs = fixture.DurationToleranceMs,
    };

    private static FormatResult Finalize(FormatResult result, Stopwatch stopwatch)
    {
        stopwatch.Stop();
        result.ElapsedMonotonicMs = stopwatch.ElapsedMilliseconds;
        result.Disposition = ProofAggregators.EvaluateFormat(result);
        return result;
    }

    private static async Task<ExtractedMetadata> ExtractMetadataAsync(
        StorageFile file,
        FormatResult result)
    {
        try
        {
            MusicProperties properties = await file.Properties.GetMusicPropertiesAsync();
            return new ExtractedMetadata
            {
                Title = EmptyToNull(properties.Title),
                Artist = EmptyToNull(properties.Artist),
                Album = EmptyToNull(properties.Album),
            };
        }
        catch (Exception exception)
        {
            result.Warnings.Add($"optional_metadata_unavailable:{exception.GetType().Name}");
            return new ExtractedMetadata();
        }
    }

    private static string? EmptyToNull(string? value) =>
        string.IsNullOrWhiteSpace(value) ? null : value;

    private static async Task<string?> AwaitSignalAsync(
        Task<bool> success,
        Task<string> failure,
        int timeoutMs,
        CancellationToken cancellationToken)
    {
        Task timeout = Task.Delay(timeoutMs, cancellationToken);
        Task winner = await Task.WhenAny(success, failure, timeout);
        if (winner == success)
        {
            return null;
        }

        if (winner == failure)
        {
            return await failure;
        }

        await timeout;
        return "timeout";
    }

    private static async Task<bool> WaitUntilAsync(
        Func<bool> condition,
        int timeoutMs,
        CancellationToken cancellationToken)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (condition())
            {
                return true;
            }

            await Task.Delay(50, cancellationToken);
        }

        return false;
    }

    private static TaskCompletionSource<T> NewCompletionSource<T>() =>
        new(TaskCreationOptions.RunContinuationsAsynchronously);
}

internal sealed class LifecyclePlayer : IDisposable
{
    private const string AppliedTitle = "Synthetic Windows Lifecycle Proof";
    private const string AppliedArtist = "Media Ecosystem Synthetic Lab";
    private readonly ProofSessionState state;
    private MediaPlayer? player;
    private MediaSource? source;
    private bool disposed;

    public LifecyclePlayer(ProofSessionState state)
    {
        this.state = state;
    }

    public event EventHandler? ObservationRecorded;

    public string PlaybackState =>
        player?.PlaybackSession.PlaybackState.ToString() ?? "not_initialized";

    public bool AutomaticSmtcCandidateActive =>
        player is not null &&
        player.CommandManager.IsEnabled &&
        player.SystemMediaTransportControls.IsEnabled;

    public async Task StartAsync(
        VerifiedFixture fixture,
        CancellationToken cancellationToken = default)
    {
        ObjectDisposedException.ThrowIf(disposed, this);
        DisposePlayer();
        if (!string.Equals(
                fixture.Id,
                ProofConstants.LifecycleFixtureId,
                StringComparison.Ordinal))
        {
            throw new InvalidOperationException("Unexpected lifecycle fixture.");
        }

        StorageFile file = await StorageFile.GetFileFromPathAsync(fixture.AbsolutePath);
        ExtractedMetadata extracted;
        try
        {
            MusicProperties metadata = await file.Properties.GetMusicPropertiesAsync();
            extracted = new ExtractedMetadata
            {
                Title = EmptyToNull(metadata.Title),
                Artist = EmptyToNull(metadata.Artist),
                Album = EmptyToNull(metadata.Album),
            };
        }
        catch (Exception exception)
        {
            state.Lifecycle.Limitations.Add(
                $"optional_extracted_metadata_unavailable:{exception.GetType().Name}");
            extracted = new ExtractedMetadata();
        }

        source = MediaSource.CreateFromStorageFile(file);
        MediaPlaybackItem item = new(source);
        MediaItemDisplayProperties display = item.GetDisplayProperties();
        display.Type = MediaPlaybackType.Music;
        display.MusicProperties.Title = AppliedTitle;
        display.MusicProperties.Artist = AppliedArtist;
        item.ApplyDisplayProperties(display);

        player = new MediaPlayer { IsLoopingEnabled = true };
        TaskCompletionSource<bool> opened = NewCompletionSource<bool>();
        TaskCompletionSource<string> failed = NewCompletionSource<string>();
        player.MediaOpened += (_, _) => opened.TrySetResult(true);
        player.MediaFailed += (_, args) => failed.TrySetResult(args.Error.ToString());
        SubscribeCommandManager(player.CommandManager);
        player.Source = item;
        player.Play();

        Task timeout = Task.Delay(10_000, cancellationToken);
        Task winner = await Task.WhenAny(opened.Task, failed.Task, timeout);
        if (winner == failed.Task)
        {
            string code = await failed.Task;
            state.Lifecycle.Failures.Add($"lifecycle_media_failed:{code}");
            throw new InvalidOperationException("Lifecycle media failed.");
        }

        if (winner == timeout)
        {
            await timeout;
            state.Lifecycle.Failures.Add("lifecycle_media_open_timeout");
            throw new TimeoutException("Lifecycle media open timed out.");
        }

        bool playing = await WaitUntilAsync(
            () => player.PlaybackSession.PlaybackState == MediaPlaybackState.Playing,
            10_000,
            cancellationToken);
        LifecycleResult lifecycle = state.Lifecycle;
        lifecycle.FixtureId = fixture.Id;
        lifecycle.FixtureVerified = true;
        lifecycle.PlaybackBegan = playing;
        lifecycle.ExtractedFileMetadata = extracted;
        lifecycle.AppliedSystemMetadata = new AppliedSystemMetadata
        {
            Title = AppliedTitle,
            Artist = AppliedArtist,
        };
        lifecycle.AutomaticSmtcCandidateActive = AutomaticSmtcCandidateActive;
        if (!playing)
        {
            lifecycle.Failures.Add("lifecycle_playback_start_timeout");
        }

        if (!lifecycle.AutomaticSmtcCandidateActive)
        {
            lifecycle.Failures.Add("automatic_smtc_candidate_not_active");
        }

        if (lifecycle.RestartCheckpoint.Status == "reopened" &&
            lifecycle.RestartCheckpoint.CleanReopenObserved &&
            playing)
        {
            lifecycle.RestartCheckpoint.NewPlaybackStartedAfterReopen = true;
        }

        lifecycle.Disposition = ProofAggregators.EvaluateLifecycle(lifecycle);
        OnObservationRecorded();
    }

    public void MarkReadyForSleep()
    {
        LifecycleResult lifecycle = state.Lifecycle;
        lifecycle.ReadyForSleepMarked = true;
        lifecycle.PlaybackStateImmediatelyBeforeSleep = PlaybackState;
        OnObservationRecorded();
    }

    public void ObservePowerMode(string mode)
    {
        LifecycleResult lifecycle = state.Lifecycle;
        if (string.Equals(mode, "suspend", StringComparison.Ordinal))
        {
            lifecycle.SuspendObserved = true;
        }
        else if (string.Equals(mode, "resume", StringComparison.Ordinal))
        {
            lifecycle.ResumeObserved = true;
        }

        lifecycle.PowerEvents.Add(new PowerObservation
        {
            Mode = mode,
            PlaybackState = PlaybackState,
            AutomaticSmtcCandidateActive = AutomaticSmtcCandidateActive,
            ObservedUtc = DateTimeOffset.UtcNow,
            ObservedMonotonicMs = Environment.TickCount64,
        });
        OnObservationRecorded();
    }

    public void RecordWakeResult()
    {
        LifecycleResult lifecycle = state.Lifecycle;
        lifecycle.WakeResultRecorded = true;
        lifecycle.PlaybackStateAfterWake = PlaybackState;
        lifecycle.AutomaticSmtcCandidateActiveAfterWake =
            AutomaticSmtcCandidateActive;
        if (!lifecycle.ResumeObserved)
        {
            lifecycle.Failures.Add("wake_result_recorded_without_resume_event");
        }

        lifecycle.Disposition = ProofAggregators.EvaluateLifecycle(lifecycle);
        OnObservationRecorded();
    }

    private void SubscribeCommandManager(MediaPlaybackCommandManager commandManager)
    {
        commandManager.PlayReceived += (_, _) => ObserveCommandAsync("play", null);
        commandManager.PauseReceived += (_, _) => ObserveCommandAsync("pause", null);
        commandManager.PositionReceived += (_, args) =>
            ObserveCommandAsync("position", (long)args.Position.TotalMilliseconds);
    }

    private async void ObserveCommandAsync(string command, long? requestedPositionMs)
    {
        string before = PlaybackState;
        double? positionBefore = player?.PlaybackSession.Position.TotalMilliseconds;
        await Task.Delay(750);
        string after = PlaybackState;
        bool affected = command switch
        {
            "play" =>
                before != MediaPlaybackState.Playing.ToString() &&
                after == MediaPlaybackState.Playing.ToString(),
            "pause" =>
                before != MediaPlaybackState.Paused.ToString() &&
                after == MediaPlaybackState.Paused.ToString(),
            "position" when requestedPositionMs.HasValue && player is not null =>
                Math.Abs(
                    player.PlaybackSession.Position.TotalMilliseconds -
                    requestedPositionMs.Value) <= 1_000 &&
                (!positionBefore.HasValue ||
                    Math.Abs(positionBefore.Value - requestedPositionMs.Value) > 1_000),
            _ => false,
        };
        state.Lifecycle.SystemCommands.Add(new SystemCommandObservation
        {
            Command = command,
            AutomaticallyObservedByCommandManager = true,
            PlaybackStateBefore = before,
            PlaybackStateAfter = after,
            AffectedPlayback = affected,
            RequestedPositionMs = requestedPositionMs,
            ObservedUtc = DateTimeOffset.UtcNow,
            ObservedMonotonicMs = Environment.TickCount64,
        });
        state.Lifecycle.Disposition = ProofAggregators.EvaluateLifecycle(state.Lifecycle);
        OnObservationRecorded();
    }

    private void OnObservationRecorded() =>
        ObservationRecorded?.Invoke(this, EventArgs.Empty);

    private void DisposePlayer()
    {
        player?.Dispose();
        player = null;
        source?.Dispose();
        source = null;
    }

    public void Dispose()
    {
        if (disposed)
        {
            return;
        }

        disposed = true;
        DisposePlayer();
    }

    private static string? EmptyToNull(string? value) =>
        string.IsNullOrWhiteSpace(value) ? null : value;

    private static async Task<bool> WaitUntilAsync(
        Func<bool> condition,
        int timeoutMs,
        CancellationToken cancellationToken)
    {
        Stopwatch stopwatch = Stopwatch.StartNew();
        while (stopwatch.ElapsedMilliseconds < timeoutMs)
        {
            cancellationToken.ThrowIfCancellationRequested();
            if (condition())
            {
                return true;
            }

            await Task.Delay(50, cancellationToken);
        }

        return false;
    }

    private static TaskCompletionSource<T> NewCompletionSource<T>() =>
        new(TaskCreationOptions.RunContinuationsAsynchronously);
}
