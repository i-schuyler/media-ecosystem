using Microsoft.Win32;

namespace MediaEcosystem.WindowsProof;

internal sealed class MainForm : Form
{
    private readonly SessionController session = new();
    private readonly Label fixtureStatus = StatusLabel();
    private readonly Label matrixStatus = StatusLabel();
    private readonly Label lifecycleStatus = StatusLabel();
    private readonly Label metadataStatus = StatusLabel();
    private readonly Label commandStatus = StatusLabel();
    private readonly Label sleepStatus = StatusLabel();
    private readonly Label restartStatus = StatusLabel();
    private readonly Label exportStatus = StatusLabel();
    private readonly TextBox diagnosticStatus = new()
    {
        Multiline = true,
        ReadOnly = true,
        ScrollBars = ScrollBars.Vertical,
        Height = 150,
        Dock = DockStyle.Fill,
        BackColor = SystemColors.Window,
    };
    private readonly Button verifyButton = ActionButton("Verify packaged fixtures");
    private readonly Button matrixButton = ActionButton("Run exact six-format matrix");
    private readonly Button lifecycleButton = ActionButton("Start lifecycle / SMTC playback");
    private readonly Button metadataVisibleButton =
        ActionButton("Human confirmation: metadata is visible");
    private readonly Button metadataNotVisibleButton =
        ActionButton("Human observation: metadata is not visible");
    private readonly Button readyForSleepButton =
        ActionButton("Mark ready for sleep (does not sleep this PC)");
    private readonly Button recordWakeButton = ActionButton("Record wake result");
    private readonly Button beginRestartButton =
        ActionButton("Begin app-restart checkpoint");
    private readonly Button completeRestartButton =
        ActionButton("Complete checkpoint after reopen + new playback");
    private readonly Button exportButton = ActionButton("Export verified evidence ZIP");
    private FixtureVerificationResult? fixtureVerification;
    private LifecyclePlayer? lifecyclePlayer;
    private bool closing;

    public MainForm()
    {
        Text = "Disposable Windows Phase 1 Playback Proof";
        MinimumSize = new Size(920, 760);
        Size = new Size(1080, 900);
        AutoScaleMode = AutoScaleMode.Dpi;
        Font = new Font("Segoe UI", 10F);

        FlowLayoutPanel flow = new()
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false,
            AutoScroll = true,
            Padding = new Padding(14),
        };
        Controls.Add(flow);
        flow.SizeChanged += (_, _) =>
        {
            foreach (Control control in flow.Controls)
            {
                control.Width = Math.Max(760, flow.ClientSize.Width - 42);
            }
        };

        Label boundary = new()
        {
            AutoSize = true,
            MaximumSize = new Size(960, 0),
            Text = "Disposable evidence candidate only. Build success is tooling evidence. " +
                "The application never sleeps, restarts, or shuts down the computer, and " +
                "it never exports audio or a private checkpoint path.",
            ForeColor = Color.DarkRed,
            Padding = new Padding(2, 0, 2, 8),
        };
        flow.Controls.Add(boundary);
        flow.Controls.Add(Stage(
            "1. Verify packaged fixtures",
            "Fail closed unless the output contains exactly the six active fixtures, " +
                "the two manifests, and matching filename, size, and SHA-256 data.",
            fixtureStatus,
            verifyButton));
        flow.Controls.Add(Stage(
            "2. Run the exact six-format PB-01 matrix",
            "Runs MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, and WAV with finite " +
                "timeouts. A failed decoder is recorded and the next fixture runs.",
            matrixStatus,
            matrixButton));
        flow.Controls.Add(Stage(
            "3. Start dedicated lifecycle / automatic SMTC playback",
            "Uses verified mp3-v0 with manually applied system display metadata: " +
                "\"Synthetic Windows Lifecycle Proof\" by \"Media Ecosystem Synthetic Lab\".",
            lifecycleStatus,
            lifecycleButton));
        flow.Controls.Add(Stage(
            "4. Confirm metadata visibility (human observation)",
            "Open Windows Quick Settings / system media UI and record what you see. " +
                "This acknowledgement is never converted into an app-observed event.",
            metadataStatus,
            metadataVisibleButton,
            metadataNotVisibleButton));
        flow.Controls.Add(Stage(
            "5. Exercise Windows system play / pause",
            "Use only Windows system media controls. Do not use an in-app playback " +
                "button. The application records PlayReceived / PauseReceived and the " +
                "resulting playback state automatically. Position is optional.",
            commandStatus));
        flow.Controls.Add(Stage(
            "6–7. Sleep / wake observation",
            "First mark ready. Then put the Surface Book 3 to sleep yourself. After " +
                "wake, return here and record the result. The app only observes power events.",
            sleepStatus,
            readyForSleepButton,
            recordWakeButton));
        flow.Controls.Add(Stage(
            "8. Bounded app-restart checkpoint",
            "Begin the checkpoint, close this app yourself, reopen it, verify fixtures, " +
                "start a new lifecycle playback, then complete. This is recovery evidence; " +
                "it does not claim playback survives process termination.",
            restartStatus,
            beginRestartButton,
            completeRestartButton));
        flow.Controls.Add(Stage(
            "9. Export sanitized evidence",
            "Writes exactly seven members, validates internal checksums, reopens the " +
                "completed ZIP, and displays only its filename, byte size, and SHA-256.",
            exportStatus,
            exportButton));
        flow.Controls.Add(Stage(
            "Progress, failures, and exact remaining steps",
            "This view is derived from the private bounded checkpoint. Absolute paths " +
                "are not displayed or exported.",
            diagnosticStatus));

        verifyButton.Click += async (_, _) => await VerifyFixturesAsync();
        matrixButton.Click += async (_, _) => await RunMatrixAsync();
        lifecycleButton.Click += async (_, _) => await StartLifecycleAsync();
        metadataVisibleButton.Click += async (_, _) =>
            await RecordMetadataAcknowledgementAsync(visible: true);
        metadataNotVisibleButton.Click += async (_, _) =>
            await RecordMetadataAcknowledgementAsync(visible: false);
        readyForSleepButton.Click += async (_, _) => await MarkReadyForSleepAsync();
        recordWakeButton.Click += async (_, _) => await RecordWakeAsync();
        beginRestartButton.Click += async (_, _) => await BeginRestartAsync();
        completeRestartButton.Click += async (_, _) => await CompleteRestartAsync();
        exportButton.Click += async (_, _) => await ExportAsync();
        Load += async (_, _) => await LoadSessionAsync();
        FormClosing += OnFormClosing;
        SystemEvents.PowerModeChanged += OnPowerModeChanged;
    }

    private async Task LoadSessionAsync()
    {
        CheckpointLoadResult loaded = await session.LoadAsync();
        if (loaded.Found)
        {
            session.AddDiagnostic(
                "application_start",
                "checkpoint_loaded",
                loaded.RecoveredFromPrevious ? "recovered_previous" : "primary_valid");
        }
        else
        {
            session.AddDiagnostic("application_start", "new_session", "no_checkpoint");
        }

        await session.SaveAsync();
        RefreshStatus();
    }

    private async Task VerifyFixturesAsync()
    {
        await RunButtonAsync(verifyButton, async () =>
        {
            fixtureVerification = await FixtureVerifier.VerifyAsync(ProofRuntime.FixtureRoot);
            session.State.FixturesVerified = true;
            session.State.FixtureManifestSha256 = fixtureVerification.ManifestSha256;
            session.State.Lifecycle.FixtureVerified = true;
            session.AddDiagnostic(
                "fixture_verification",
                "passed",
                $"exact_six:{fixtureVerification.TotalAudioBytes}_bytes");
            await session.SaveAsync();
        });
    }

    private async Task RunMatrixAsync()
    {
        await RunButtonAsync(matrixButton, async () =>
        {
            EnsureFixturesAvailable();
            Dictionary<string, FormatResult> existing = session.State.FormatResults
                .ToDictionary(item => item.FixtureId, StringComparer.Ordinal);
            IReadOnlyList<FormatResult> results = await MatrixCoordinator.RunAllAsync(
                fixtureVerification!.Fixtures,
                async (fixture, cancellationToken) =>
                {
                    if (existing.TryGetValue(fixture.Id, out FormatResult? prior) &&
                        prior.OpenAttempted)
                    {
                        return prior;
                    }

                    return await WindowsFormatRunner.RunOneAsync(fixture, cancellationToken);
                },
                completed: result =>
                {
                    session.State.FormatResults.RemoveAll(item =>
                        string.Equals(
                            item.FixtureId,
                            result.FixtureId,
                            StringComparison.Ordinal));
                    session.State.FormatResults.Add(result);
                    session.State.FormatResults = session.State.FormatResults
                        .OrderBy(item =>
                            ProofConstants.RequiredFixtureIds.IndexOf(item.FixtureId))
                        .ToList();
                    session.AddDiagnostic(
                        "format_fixture_completed",
                        Wire(result.Disposition),
                        result.FixtureId);
                    session.SaveAsync().GetAwaiter().GetResult();
                    BeginInvoke(RefreshStatus);
                },
                CancellationToken.None);
            session.State.FormatResults = results.ToList();
            ProofDisposition aggregate = ProofAggregators.EvaluatePb01(results);
            session.AddDiagnostic("pb01_windows_matrix", Wire(aggregate), "exact_six_complete");
            await session.SaveAsync();
        });
    }

    private async Task StartLifecycleAsync()
    {
        await RunButtonAsync(lifecycleButton, async () =>
        {
            EnsureFixturesAvailable();
            VerifiedFixture fixture = fixtureVerification!.Fixtures.Single(item =>
                item.Id == ProofConstants.LifecycleFixtureId);
            lifecyclePlayer?.Dispose();
            lifecyclePlayer = new LifecyclePlayer(session.State);
            lifecyclePlayer.ObservationRecorded += OnLifecycleObservation;
            await lifecyclePlayer.StartAsync(fixture);
            session.AddDiagnostic(
                "lifecycle_playback",
                session.State.Lifecycle.PlaybackBegan ? "started" : "failed",
                "verified_mp3_v0");
            await session.SaveAsync();
        });
    }

    private async Task RecordMetadataAcknowledgementAsync(bool visible)
    {
        await RunButtonAsync(
            visible ? metadataVisibleButton : metadataNotVisibleButton,
            async () =>
            {
                if (lifecyclePlayer is null || !session.State.Lifecycle.PlaybackBegan)
                {
                    throw new InvalidOperationException(
                        "Start lifecycle playback before recording metadata visibility.");
                }

                session.State.Lifecycle.ManualAcknowledgements.RemoveAll(item =>
                    item.ObservationId == "smtc_metadata_visible");
                session.State.Lifecycle.ManualAcknowledgements.Add(new ManualAcknowledgement
                {
                    ObservationId = "smtc_metadata_visible",
                    Expected = "Synthetic Windows Lifecycle Proof — Media Ecosystem Synthetic Lab",
                    Observed = visible ? "visible_as_expected" : "not_visible_as_expected",
                    Acknowledged = visible,
                    ObservedUtc = DateTimeOffset.UtcNow,
                    ObservedMonotonicMs = Environment.TickCount64,
                });
                session.State.Lifecycle.Failures.RemoveAll(item =>
                    item == "human_observed_smtc_metadata_not_visible");
                if (!visible)
                {
                    session.State.Lifecycle.Failures.Add(
                        "human_observed_smtc_metadata_not_visible");
                }

                session.AddDiagnostic(
                    "human_smtc_metadata_observation",
                    visible ? "acknowledged" : "failed",
                    visible ? "visible_as_expected" : "not_visible");
                await session.SaveAsync();
            });
    }

    private async Task MarkReadyForSleepAsync()
    {
        await RunButtonAsync(readyForSleepButton, async () =>
        {
            if (lifecyclePlayer is null)
            {
                throw new InvalidOperationException("Start lifecycle playback first.");
            }

            lifecyclePlayer.MarkReadyForSleep();
            session.AddDiagnostic(
                "ready_for_sleep",
                "marked",
                $"playback_state:{lifecyclePlayer.PlaybackState}");
            await session.SaveAsync();
        });
    }

    private async Task RecordWakeAsync()
    {
        await RunButtonAsync(recordWakeButton, async () =>
        {
            if (lifecyclePlayer is null)
            {
                throw new InvalidOperationException(
                    "The lifecycle player is unavailable after wake.");
            }

            lifecyclePlayer.RecordWakeResult();
            session.AddDiagnostic(
                "wake_result",
                session.State.Lifecycle.ResumeObserved ? "recorded" : "failed",
                $"playback_state:{lifecyclePlayer.PlaybackState}");
            await session.SaveAsync();
        });
    }

    private async Task BeginRestartAsync()
    {
        await RunButtonAsync(beginRestartButton, async () =>
        {
            await session.BeginRestartCheckpointAsync();
            MessageBox.Show(
                this,
                "Checkpoint saved. Close this application yourself, reopen it, verify " +
                    "fixtures, and start a new lifecycle playback. The app will not close " +
                    "or restart itself.",
                "Restart checkpoint ready",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        });
    }

    private async Task CompleteRestartAsync()
    {
        await RunButtonAsync(completeRestartButton, async () =>
        {
            await session.CompleteRestartCheckpointAsync();
        });
    }

    private async Task ExportAsync()
    {
        await RunButtonAsync(exportButton, async () =>
        {
            if (!session.State.FixturesVerified ||
                string.IsNullOrWhiteSpace(session.State.FixtureManifestSha256))
            {
                throw new InvalidOperationException("Verify fixtures before export.");
            }

            string suggested =
                $"media-ecosystem-windows-proof-{DateTimeOffset.UtcNow:yyyyMMdd'T'HHmmss'Z'}.zip";
            using SaveFileDialog dialog = new()
            {
                AddExtension = true,
                CheckPathExists = true,
                DefaultExt = "zip",
                Filter = "ZIP archive (*.zip)|*.zip",
                FileName = suggested,
                OverwritePrompt = true,
                RestoreDirectory = true,
                Title = "Export sanitized Windows proof evidence",
            };
            if (dialog.ShowDialog(this) != DialogResult.OK)
            {
                return;
            }

            if (!ProofConstants.EvidenceArchiveFilenameRegex().IsMatch(
                    Path.GetFileName(dialog.FileName)))
            {
                throw new InvalidOperationException(
                    "Use the required media-ecosystem-windows-proof-<UTC timestamp>.zip name.");
            }

            session.AddDiagnostic("evidence_export", "started", "user_selected_destination");
            await session.SaveAsync();
            EvidenceArchiveResult result = await EvidenceExporter.ExportAsync(
                session.State,
                dialog.FileName);
            session.AddDiagnostic(
                "evidence_export",
                "passed",
                $"filename:{result.Filename};bytes:{result.SizeBytes};sha256:{result.Sha256}");
            await session.SaveAsync();
            exportStatus.Text =
                $"Passed — {result.Filename} — {result.SizeBytes:N0} bytes — " +
                $"SHA-256 {result.Sha256}";
            MessageBox.Show(
                this,
                $"{result.Filename}\n{result.SizeBytes:N0} bytes\nSHA-256 {result.Sha256}",
                "Verified evidence ZIP",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        });
    }

    private async Task RunButtonAsync(Button button, Func<Task> action)
    {
        button.Enabled = false;
        try
        {
            await action();
        }
        catch (Exception exception)
        {
            string code = $"{button.Name}:{exception.GetType().Name}";
            session.State.Failures.Add(code);
            session.AddDiagnostic("ui_action", "failed", code);
            await session.SaveAsync();
            MessageBox.Show(
                this,
                exception.Message,
                "Proof action failed",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        }
        finally
        {
            button.Enabled = true;
            RefreshStatus();
        }
    }

    private void EnsureFixturesAvailable()
    {
        if (fixtureVerification is null || !session.State.FixturesVerified)
        {
            throw new InvalidOperationException("Complete fixture verification first.");
        }
    }

    private void OnLifecycleObservation(object? sender, EventArgs args)
    {
        if (closing || IsDisposed)
        {
            return;
        }

        BeginInvoke(async () =>
        {
            try
            {
                await session.SaveAsync();
            }
            catch (Exception exception)
            {
                session.State.Failures.Add(
                    $"checkpoint_save_failed:{exception.GetType().Name}");
            }

            RefreshStatus();
        });
    }

    private void OnPowerModeChanged(object sender, PowerModeChangedEventArgs args)
    {
        string mode = args.Mode switch
        {
            PowerModes.Suspend => "suspend",
            PowerModes.Resume => "resume",
            _ => "status_change",
        };
        lifecyclePlayer?.ObservePowerMode(mode);
        session.AddDiagnostic("power_mode", "observed", mode);
        if (lifecyclePlayer is null)
        {
            OnLifecycleObservation(this, EventArgs.Empty);
        }
    }

    private void RefreshStatus()
    {
        ProofSessionState state = session.State;
        fixtureStatus.Text = state.FixturesVerified
            ? $"Passed — exact six; manifest SHA-256 {state.FixtureManifestSha256}"
            : "Pending — verify the packaged corpus.";

        Dictionary<string, FormatResult> byId = state.FormatResults.ToDictionary(
            item => item.FixtureId,
            StringComparer.Ordinal);
        matrixStatus.Text = string.Join(
            Environment.NewLine,
            ProofConstants.RequiredFixtureIds.Select(id =>
                $"{id}: {(byId.TryGetValue(id, out FormatResult? result) ? Wire(result.Disposition) : "not_run")}")) +
            Environment.NewLine +
            $"PB-01 Windows: {Wire(ProofAggregators.EvaluatePb01(state.FormatResults))}";

        LifecycleResult lifecycle = state.Lifecycle;
        lifecycleStatus.Text =
            $"Playback began: {lifecycle.PlaybackBegan}; automatic SMTC candidate active: " +
            $"{lifecycle.AutomaticSmtcCandidateActive}; PB-05: " +
            $"{Wire(ProofAggregators.EvaluateLifecycle(lifecycle))}";
        bool humanMetadata = lifecycle.ManualAcknowledgements.Any(item =>
            item.ObservationId == "smtc_metadata_visible" && item.Acknowledged);
        int playCount = lifecycle.SystemCommands.Count(item =>
            item.Command == "play" && item.AutomaticallyObservedByCommandManager);
        int pauseCount = lifecycle.SystemCommands.Count(item =>
            item.Command == "pause" && item.AutomaticallyObservedByCommandManager);
        int positionCount = lifecycle.SystemCommands.Count(item =>
            item.Command == "position" && item.AutomaticallyObservedByCommandManager);
        commandStatus.Text =
            $"Automatic command-manager events — " +
            $"play {playCount}, pause {pauseCount}, position {positionCount}.";
        metadataStatus.Text =
            $"Human metadata visibility acknowledgement: {humanMetadata}.";
        sleepStatus.Text =
            $"Ready: {lifecycle.ReadyForSleepMarked}; suspend observed: " +
            $"{lifecycle.SuspendObserved}; resume observed: {lifecycle.ResumeObserved}; " +
            $"wake result recorded: {lifecycle.WakeResultRecorded}.";
        RestartCheckpoint restart = lifecycle.RestartCheckpoint;
        restartStatus.Text =
            $"Status: {restart.Status}; clean reopen: {restart.CleanReopenObserved}; " +
            $"new playback after reopen: {restart.NewPlaybackStartedAfterReopen}.";

        List<string> remaining = [];
        if (!state.FixturesVerified) remaining.Add("1 verify fixtures");
        if (state.FormatResults.Count != 6) remaining.Add("2 complete matrix");
        if (!lifecycle.PlaybackBegan) remaining.Add("3 start lifecycle playback");
        if (!humanMetadata) remaining.Add("4 confirm visible metadata");
        if (playCount == 0 || pauseCount == 0) remaining.Add("5 exercise system play and pause");
        if (!lifecycle.ReadyForSleepMarked) remaining.Add("6 mark ready for sleep");
        if (!lifecycle.ResumeObserved || !lifecycle.WakeResultRecorded)
            remaining.Add("7 complete wake observation");
        if (restart.Status != "completed") remaining.Add("8 complete app restart checkpoint");
        if (remaining.Count == 0) remaining.Add("9 export evidence");
        diagnosticStatus.Text =
            $"Remaining: {string.Join("; ", remaining)}{Environment.NewLine}" +
            $"Failures: {(state.Failures.Count == 0 ? "none" : string.Join("; ", state.Failures))}";
    }

    private void OnFormClosing(object? sender, FormClosingEventArgs args)
    {
        closing = true;
        SystemEvents.PowerModeChanged -= OnPowerModeChanged;
        lifecyclePlayer?.Dispose();
        session.AddDiagnostic("application_close", "observed", "user_initiated_or_system_close");
        session.SaveAsync().GetAwaiter().GetResult();
    }

    private static GroupBox Stage(
        string title,
        string instructions,
        Control status,
        params Control[] buttons)
    {
        GroupBox group = new()
        {
            Text = title,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            Padding = new Padding(12),
            Margin = new Padding(0, 0, 0, 12),
        };
        TableLayoutPanel table = new()
        {
            Dock = DockStyle.Top,
            AutoSize = true,
            AutoSizeMode = AutoSizeMode.GrowAndShrink,
            ColumnCount = 1,
            RowCount = 2 + buttons.Length,
        };
        table.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        table.Controls.Add(new Label
        {
            AutoSize = true,
            MaximumSize = new Size(920, 0),
            Text = instructions,
            Padding = new Padding(0, 0, 0, 6),
        });
        table.Controls.Add(status);
        foreach (Control button in buttons)
        {
            table.Controls.Add(button);
        }

        group.Controls.Add(table);
        return group;
    }

    private static Label StatusLabel() => new()
    {
        AutoSize = true,
        MaximumSize = new Size(920, 0),
        Text = "Pending",
        ForeColor = Color.Navy,
        Padding = new Padding(0, 0, 0, 6),
    };

    private static Button ActionButton(string text) => new()
    {
        Text = text,
        AutoSize = true,
        MinimumSize = new Size(360, 38),
        Margin = new Padding(0, 4, 0, 4),
        Name = text.Replace(' ', '_').ToLowerInvariant(),
    };

    private static string Wire(ProofDisposition disposition) =>
        disposition switch
        {
            ProofDisposition.Passed => "passed",
            ProofDisposition.Failed => "failed",
            ProofDisposition.Inconclusive => "inconclusive",
            ProofDisposition.NotRun => "not_run",
            _ => throw new ArgumentOutOfRangeException(nameof(disposition)),
        };
}

internal static class ReadOnlyListExtensions
{
    public static int IndexOf(this IReadOnlyList<string> values, string value)
    {
        for (int index = 0; index < values.Count; index++)
        {
            if (string.Equals(values[index], value, StringComparison.Ordinal))
            {
                return index;
            }
        }

        return int.MaxValue;
    }
}
