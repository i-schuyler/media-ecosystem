using System.IO.Compression;
using System.Text;
using System.Text.Json;
using MediaEcosystem.WindowsProof;

namespace MediaEcosystem.WindowsProof.SelfTest;

internal static class Program
{
    private static readonly List<(string Name, Func<Task> Test)> Tests =
    [
        ("fixture_manifest_exact_six_and_hashes", FixtureManifestExactSixAsync),
        ("fixture_manifest_excludes_alac_aiff", FixtureManifestExcludesHistoricalAsync),
        ("fixture_modified_fails_closed", FixtureModifiedFailsAsync),
        ("fixture_extra_media_fails_closed", FixtureExtraFailsAsync),
        ("pb01_all_dimensions_pass", Pb01AllDimensionsPassAsync),
        ("pb01_missing_fixture_inconclusive", Pb01MissingFixtureInconclusiveAsync),
        ("pb01_duplicate_fixture_inconclusive", Pb01DuplicateFixtureInconclusiveAsync),
        ("wav_without_optional_metadata_passes", WavWithoutMetadataPassesAsync),
        ("failed_decoder_continues_matrix", FailedDecoderContinuesAsync),
        ("lifecycle_all_required_dimensions_pass", LifecyclePassesAsync),
        ("lifecycle_human_observation_not_automatic", LifecycleHumanObservationRequiredAsync),
        ("checkpoint_round_trip_preserves_partial_results", CheckpointRoundTripAsync),
        ("checkpoint_concurrent_saves_are_serialized", CheckpointConcurrentSavesAsync),
        ("checkpoint_invalid_primary_recovers_previous", CheckpointRecoveryAsync),
        ("restart_checkpoint_state_round_trip", RestartCheckpointRoundTripAsync),
        ("evidence_schema_accepts_valid_partial_session", EvidenceSchemaValidAsync),
        ("evidence_schema_rejects_wrong_active_format_order", EvidenceSchemaRejectsFormatAsync),
        ("evidence_zip_exact_seven_member_allowlist", EvidenceArchiveAllowlistAsync),
        ("evidence_zip_extra_member_is_rejected", EvidenceArchiveExtraMemberAsync),
        ("evidence_zip_internal_checksums_verify", EvidenceArchiveChecksumsAsync),
        ("evidence_zip_tamper_is_rejected", EvidenceArchiveTamperAsync),
        ("privacy_rejects_forbidden_field", PrivacyRejectsFieldAsync),
        ("privacy_rejects_absolute_personal_path", PrivacyRejectsPathAsync),
        ("privacy_accepts_sanitized_evidence", PrivacyAcceptsSanitizedAsync),
        ("evidence_zip_contains_no_audio", EvidenceArchiveContainsNoAudioAsync),
    ];

    private static string repositoryRoot = string.Empty;

    private static async Task<int> Main(string[] args)
    {
        repositoryRoot = args.Length == 1
            ? Path.GetFullPath(args[0])
            : DiscoverRepositoryRoot();
        int passed = 0;
        foreach ((string name, Func<Task> test) in Tests)
        {
            try
            {
                await test();
                Console.WriteLine($"PASS {name}");
                passed++;
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine(
                    $"FAIL {name}: {exception.GetType().Name}: {exception.Message}");
            }
        }

        Console.WriteLine($"{passed}/{Tests.Count} deterministic self-tests passed.");
        return passed == Tests.Count ? 0 : 1;
    }

    private static string FixtureRoot => Path.Combine(
        repositoryRoot,
        "spikes",
        "android-platform-proof",
        "app",
        "src",
        "main",
        "assets",
        "fixtures");

    private static string SchemaPath => Path.Combine(
        repositoryRoot,
        "spikes",
        "windows-platform-proof",
        "schemas",
        "windows-proof-evidence.schema.json");

    private static async Task FixtureManifestExactSixAsync()
    {
        FixtureVerificationResult result = await FixtureVerifier.VerifyAsync(FixtureRoot);
        Equal(6, result.Fixtures.Count);
        SequenceEqual(
            ProofConstants.RequiredFixtureIds,
            result.Fixtures.Select(item => item.Id));
        True(result.Fixtures.All(item => item.SizeBytes > 0));
    }

    private static async Task FixtureManifestExcludesHistoricalAsync()
    {
        FixtureVerificationResult result = await FixtureVerifier.VerifyAsync(FixtureRoot);
        True(result.Fixtures.All(item =>
            !ProofConstants.HistoricalNonrequiredFixtureIds.Contains(item.Id)));
        string manifest = await File.ReadAllTextAsync(
            Path.Combine(FixtureRoot, "fixture-manifest.json"));
        True(manifest.Contains("\"historical_nonrequired_format_ids\"", StringComparison.Ordinal));
    }

    private static async Task FixtureModifiedFailsAsync()
    {
        using TemporaryDirectory temporary = new();
        CopyFixtureCorpus(temporary.Path);
        string wav = Path.Combine(temporary.Path, "wav.wav");
        await using (FileStream stream = new(
            wav,
            FileMode.Append,
            FileAccess.Write,
            FileShare.None))
        {
            await stream.WriteAsync(new byte[] { 0x01 });
        }
        await ThrowsAsync<FixtureVerificationException>(
            () => FixtureVerifier.VerifyAsync(temporary.Path));
    }

    private static async Task FixtureExtraFailsAsync()
    {
        using TemporaryDirectory temporary = new();
        CopyFixtureCorpus(temporary.Path);
        await File.WriteAllBytesAsync(Path.Combine(temporary.Path, "extra.aiff"), [0x00]);
        await ThrowsAsync<FixtureVerificationException>(
            () => FixtureVerifier.VerifyAsync(temporary.Path));
    }

    private static Task Pb01AllDimensionsPassAsync()
    {
        List<FormatResult> results = ProofConstants.RequiredFixtureIds
            .Select(PassingFormat)
            .ToList();
        Equal(ProofDisposition.Passed, ProofAggregators.EvaluatePb01(results));
        return Task.CompletedTask;
    }

    private static Task Pb01MissingFixtureInconclusiveAsync()
    {
        List<FormatResult> results = ProofConstants.RequiredFixtureIds
            .Take(5)
            .Select(PassingFormat)
            .ToList();
        Equal(ProofDisposition.Inconclusive, ProofAggregators.EvaluatePb01(results));
        return Task.CompletedTask;
    }

    private static Task Pb01DuplicateFixtureInconclusiveAsync()
    {
        List<FormatResult> results = ProofConstants.RequiredFixtureIds
            .Select(PassingFormat)
            .ToList();
        results.Add(PassingFormat("wav"));
        Equal(ProofDisposition.Inconclusive, ProofAggregators.EvaluatePb01(results));
        return Task.CompletedTask;
    }

    private static Task WavWithoutMetadataPassesAsync()
    {
        FormatResult wav = PassingFormat("wav");
        wav.ExtractedFileMetadata = new ExtractedMetadata();
        False(wav.ExtractedFileMetadata.AnyAvailable);
        Equal(ProofDisposition.Passed, ProofAggregators.EvaluateFormat(wav));
        return Task.CompletedTask;
    }

    private static async Task FailedDecoderContinuesAsync()
    {
        List<VerifiedFixture> fixtures = ProofConstants.RequiredFixtureIds
            .Select(FakeFixture)
            .ToList();
        List<string> attempted = [];
        IReadOnlyList<FormatResult> results = await MatrixCoordinator.RunAllAsync(
            fixtures,
            (fixture, _) =>
            {
                attempted.Add(fixture.Id);
                if (fixture.Id == "aac")
                {
                    throw new InvalidOperationException("synthetic decoder failure");
                }

                return Task.FromResult(PassingFormat(fixture.Id));
            },
            completed: null,
            CancellationToken.None);
        SequenceEqual(ProofConstants.RequiredFixtureIds, attempted);
        Equal(6, results.Count);
        Equal(
            ProofDisposition.Failed,
            results.Single(item => item.FixtureId == "aac").Disposition);
        Equal(
            ProofDisposition.Passed,
            results.Single(item => item.FixtureId == "wav").Disposition);
    }

    private static Task LifecyclePassesAsync()
    {
        LifecycleResult lifecycle = PassingLifecycle();
        Equal(ProofDisposition.Passed, ProofAggregators.EvaluateLifecycle(lifecycle));
        return Task.CompletedTask;
    }

    private static Task LifecycleHumanObservationRequiredAsync()
    {
        LifecycleResult lifecycle = PassingLifecycle();
        lifecycle.ManualAcknowledgements.Clear();
        Equal(ProofDisposition.Inconclusive, ProofAggregators.EvaluateLifecycle(lifecycle));
        return Task.CompletedTask;
    }

    private static async Task CheckpointRoundTripAsync()
    {
        using TemporaryDirectory temporary = new();
        CheckpointStore store = new(temporary.Path);
        ProofSessionState state = ProofSessionState.CreateNew();
        state.FormatResults.Add(PassingFormat("mp3-v0"));
        await store.SaveAsync(state);
        CheckpointLoadResult loaded = await store.LoadAsync();
        True(loaded.Found);
        Equal(1, loaded.State.FormatResults.Count);
        Equal("mp3-v0", loaded.State.FormatResults[0].FixtureId);
    }

    private static async Task CheckpointConcurrentSavesAsync()
    {
        using TemporaryDirectory temporary = new();
        CheckpointStore store = new(temporary.Path);
        List<ProofSessionState> states = Enumerable.Range(0, 8)
            .Select(index =>
            {
                ProofSessionState state = ProofSessionState.CreateNew();
                state.Failures.Add($"save-{index}");
                return state;
            })
            .ToList();

        await Task.WhenAll(states.Select(state => store.SaveAsync(state)));
        CheckpointLoadResult loaded = await store.LoadAsync();
        True(loaded.Found);
        Equal(1, loaded.State.Failures.Count);
        True(loaded.State.Failures[0].StartsWith("save-", StringComparison.Ordinal));
    }

    private static async Task CheckpointRecoveryAsync()
    {
        using TemporaryDirectory temporary = new();
        CheckpointStore store = new(temporary.Path);
        ProofSessionState first = ProofSessionState.CreateNew();
        first.Failures.Add("first");
        await store.SaveAsync(first);
        ProofSessionState second = ProofSessionState.CreateNew();
        second.Failures.Add("second");
        await store.SaveAsync(second);
        await File.WriteAllTextAsync(
            Path.Combine(temporary.Path, "proof-checkpoint.json"),
            "{invalid");
        CheckpointLoadResult loaded = await store.LoadAsync();
        True(loaded.Found);
        True(loaded.RecoveredFromPrevious);
        True(loaded.State.Failures.Contains("first"));
    }

    private static async Task RestartCheckpointRoundTripAsync()
    {
        using TemporaryDirectory temporary = new();
        CheckpointStore store = new(temporary.Path);
        ProofSessionState state = ProofSessionState.CreateNew();
        state.Lifecycle.RestartCheckpoint.Status = "awaiting_reopen";
        state.Lifecycle.RestartCheckpoint.Generation = 1;
        await store.SaveAsync(state);
        CheckpointLoadResult loaded = await store.LoadAsync();
        Equal("awaiting_reopen", loaded.State.Lifecycle.RestartCheckpoint.Status);
        Equal(1, loaded.State.Lifecycle.RestartCheckpoint.Generation);
        False(loaded.State.Lifecycle.RestartCheckpoint.AbsolutePathExported);
    }

    private static async Task EvidenceSchemaValidAsync()
    {
        string schema = await File.ReadAllTextAsync(SchemaPath);
        string evidence = JsonSerializer.Serialize(
            ValidEvidence(),
            JsonDefaults.Create(indented: true));
        EvidenceContractValidator.Validate(evidence, schema);
    }

    private static async Task EvidenceSchemaRejectsFormatAsync()
    {
        string schema = await File.ReadAllTextAsync(SchemaPath);
        EvidenceDocument evidence = ValidEvidence();
        evidence.ActiveRequiredFormatIds =
        [
            "mp3-320", "mp3-v0", "flac", "aac", "ogg-vorbis", "wav",
        ];
        string json = JsonSerializer.Serialize(evidence, JsonDefaults.Create());
        Throws<EvidenceValidationException>(() =>
            EvidenceContractValidator.Validate(json, schema));
    }

    private static async Task EvidenceArchiveAllowlistAsync()
    {
        using TemporaryDirectory temporary = new();
        string path = await CreateArchiveAsync(temporary.Path);
        IReadOnlyDictionary<string, byte[]> members = await EvidenceArchive.VerifyAsync(path);
        Equal(7, members.Count);
        True(members.Keys.ToHashSet(StringComparer.Ordinal)
            .SetEquals(ProofConstants.EvidenceArchiveMembers));
    }

    private static async Task EvidenceArchiveChecksumsAsync()
    {
        using TemporaryDirectory temporary = new();
        string path = await CreateArchiveAsync(temporary.Path);
        IReadOnlyDictionary<string, byte[]> members = await EvidenceArchive.VerifyAsync(path);
        True(members["CHECKSUMS.sha256"].Length > 0);
    }

    private static async Task EvidenceArchiveExtraMemberAsync()
    {
        using TemporaryDirectory temporary = new();
        string path = await CreateArchiveAsync(temporary.Path);
        using (ZipArchive archive = ZipFile.Open(path, ZipArchiveMode.Update))
        {
            ZipArchiveEntry extra = archive.CreateEntry("extra.txt");
            await using StreamWriter writer = new(extra.Open(), Encoding.UTF8);
            await writer.WriteAsync("unexpected");
        }

        await ThrowsAsync<EvidenceValidationException>(() => EvidenceArchive.VerifyAsync(path));
    }

    private static async Task EvidenceArchiveTamperAsync()
    {
        using TemporaryDirectory temporary = new();
        string path = await CreateArchiveAsync(temporary.Path);
        using (ZipArchive archive = ZipFile.Open(path, ZipArchiveMode.Update))
        {
            archive.GetEntry("summary.md")!.Delete();
            ZipArchiveEntry replacement = archive.CreateEntry("summary.md");
            await using StreamWriter writer = new(replacement.Open(), Encoding.UTF8);
            await writer.WriteAsync("tampered");
        }

        await ThrowsAsync<EvidenceValidationException>(() => EvidenceArchive.VerifyAsync(path));
    }

    private static Task PrivacyRejectsFieldAsync()
    {
        Throws<PrivacyValidationException>(() =>
            PrivacyValidator.ValidateJson("{\"username\":\"private\"}"));
        return Task.CompletedTask;
    }

    private static Task PrivacyRejectsPathAsync()
    {
        var forbiddenPath = string.Join(
            Path.DirectorySeparatorChar,
            "C:",
            "Users",
            "synthetic-user",
            "Music",
            "private.mp3");
        Throws<PrivacyValidationException>(() =>
            PrivacyValidator.ValidateText(forbiddenPath));
        return Task.CompletedTask;
    }

    private static Task PrivacyAcceptsSanitizedAsync()
    {
        PrivacyValidator.ValidateJson(
            "{\"approved_device_label\":\"Microsoft Surface Book 3\",\"audio_exported\":false}");
        return Task.CompletedTask;
    }

    private static async Task EvidenceArchiveContainsNoAudioAsync()
    {
        using TemporaryDirectory temporary = new();
        string path = await CreateArchiveAsync(temporary.Path);
        IReadOnlyDictionary<string, byte[]> members = await EvidenceArchive.VerifyAsync(path);
        False(members.Keys.Any(name =>
            ProofConstants.MediaExtensions.Contains(Path.GetExtension(name))));
    }

    private static EvidenceDocument ValidEvidence() => new()
    {
        Build = new BuildMetadata
        {
            SourceCommit = new string('a', 40),
            BuildConfiguration = "Release",
        },
        Environment = new SanitizedWindowsEnvironment
        {
            Edition = "Microsoft Windows 11 Pro",
            DisplayVersion = "25H2",
            Build = "26200.8894",
            Architecture = "X64",
            DotnetRuntime = ".NET 8.0.29",
        },
        FixtureManifestSha256 = new string('b', 64),
        FormatResults = [],
        Pb01WindowsDisposition = ProofDisposition.NotRun,
        Lifecycle = new LifecycleResult(),
        Pb05Disposition = ProofDisposition.NotRun,
        SessionStartedUtc = DateTimeOffset.Parse("2026-07-29T20:00:00Z"),
        SessionEndedUtc = DateTimeOffset.Parse("2026-07-29T20:00:01Z"),
        SessionStartedMonotonicMs = 100,
        SessionEndedMonotonicMs = 1_100,
        SessionMonotonicDurationMs = 1_000,
        Privacy = new Dictionary<string, bool>(StringComparer.Ordinal)
        {
            ["username_included"] = false,
            ["hostname_included"] = false,
            ["account_data_included"] = false,
            ["serial_number_included"] = false,
            ["machine_or_volume_identifier_included"] = false,
            ["absolute_path_included"] = false,
            ["environment_variables_included"] = false,
            ["installed_apps_included"] = false,
            ["personal_media_data_included"] = false,
            ["credentials_or_signing_material_included"] = false,
            ["audio_exported"] = false,
        },
    };

    private static async Task<string> CreateArchiveAsync(string root)
    {
        string path = Path.Combine(
            root,
            "media-ecosystem-windows-proof-20260729T200000Z.zip");
        EvidenceDocument evidence = ValidEvidence();
        JsonSerializerOptions options = JsonDefaults.Create(indented: true);
        EvidenceArchiveContent content = new()
        {
            EvidenceJson = Encoding.UTF8.GetBytes(
                JsonSerializer.Serialize(evidence, options)),
            SummaryMarkdown = Encoding.UTF8.GetBytes("# Synthetic proof\n"),
            FixtureManifestJson = Encoding.UTF8.GetBytes(
                "{\"provenance\":\"synthetic no human recording\"}"),
            FixtureSha256Sums = Encoding.UTF8.GetBytes(
                $"{new string('a', 64)}  synthetic.bin\n"),
            BuildMetadataJson = Encoding.UTF8.GetBytes(
                JsonSerializer.Serialize(evidence.Build, options)),
            DiagnosticLog = Encoding.UTF8.GetBytes(
                "{\"event\":\"synthetic\",\"status\":\"passed\"}\n"),
        };
        EvidenceArchiveResult result = await EvidenceArchive.CreateVerifiedAsync(path, content);
        Equal(Path.GetFileName(path), result.Filename);
        Equal(64, result.Sha256.Length);
        return path;
    }

    private static FormatResult PassingFormat(string id) => new()
    {
        FixtureId = id,
        VerifiedFilename = $"{id}.synthetic",
        VerifiedSizeBytes = 100,
        VerifiedSha256 = new string('a', 64),
        OpenAttempted = true,
        SourceAccepted = true,
        MediaOpenedPrepared = true,
        PlaybackStarted = true,
        PositionAdvanced = true,
        SeekRequested = true,
        SeekCompleted = true,
        SeekToleranceMs = 750,
        ExpectedDurationMs = 6_000,
        ActualDurationMs = 6_000,
        DurationToleranceMs = 350,
        DurationWithinTolerance = true,
        EndOfTrackObserved = true,
        Disposition = ProofDisposition.Passed,
    };

    private static VerifiedFixture FakeFixture(string id) => new()
    {
        Id = id,
        Filename = $"{id}.synthetic",
        RequiredFormat = id,
        Sha256 = new string('a', 64),
        SizeBytes = 100,
        ExpectedDurationMs = 6_000,
        DurationToleranceMs = 350,
    };

    private static LifecycleResult PassingLifecycle() => new()
    {
        FixtureVerified = true,
        PlaybackBegan = true,
        AutomaticSmtcCandidateActive = true,
        ManualAcknowledgements =
        [
            new ManualAcknowledgement
            {
                ObservationId = "smtc_metadata_visible",
                ObservationSource = "human",
                Acknowledged = true,
            },
        ],
        SystemCommands =
        [
            new SystemCommandObservation
            {
                Command = "play",
                AutomaticallyObservedByCommandManager = true,
                AffectedPlayback = true,
            },
            new SystemCommandObservation
            {
                Command = "pause",
                AutomaticallyObservedByCommandManager = true,
                AffectedPlayback = true,
            },
        ],
        ReadyForSleepMarked = true,
        PlaybackStateImmediatelyBeforeSleep = "Playing",
        SuspendObserved = true,
        ResumeObserved = true,
        WakeResultRecorded = true,
        PlaybackStateAfterWake = "Playing",
        AutomaticSmtcCandidateActiveAfterWake = true,
        RestartCheckpoint = new RestartCheckpoint
        {
            Status = "completed",
            CleanReopenObserved = true,
            NewPlaybackStartedAfterReopen = true,
            AbsolutePathExported = false,
        },
    };

    private static void CopyFixtureCorpus(string destination)
    {
        foreach (string path in Directory.EnumerateFiles(FixtureRoot))
        {
            File.Copy(path, Path.Combine(destination, Path.GetFileName(path)));
        }
    }

    private static string DiscoverRepositoryRoot()
    {
        DirectoryInfo? directory = new(Environment.CurrentDirectory);
        while (directory is not null)
        {
            if (File.Exists(Path.Combine(directory.FullName, "AGENTS.md")) &&
                Directory.Exists(Path.Combine(directory.FullName, ".github")))
            {
                return directory.FullName;
            }

            directory = directory.Parent;
        }

        throw new DirectoryNotFoundException("Repository root was not found.");
    }

    private static void True(bool condition)
    {
        if (!condition)
        {
            throw new TestFailureException("Expected true.");
        }
    }

    private static void False(bool condition) => True(!condition);

    private static void Equal<T>(T expected, T actual)
        where T : notnull
    {
        if (!EqualityComparer<T>.Default.Equals(expected, actual))
        {
            throw new TestFailureException($"Expected '{expected}', actual '{actual}'.");
        }
    }

    private static void SequenceEqual<T>(
        IEnumerable<T> expected,
        IEnumerable<T> actual)
    {
        if (!expected.SequenceEqual(actual))
        {
            throw new TestFailureException("Sequences differ.");
        }
    }

    private static void Throws<T>(Action action)
        where T : Exception
    {
        try
        {
            action();
        }
        catch (T)
        {
            return;
        }

        throw new TestFailureException($"Expected {typeof(T).Name}.");
    }

    private static async Task ThrowsAsync<T>(Func<Task> action)
        where T : Exception
    {
        try
        {
            await action();
        }
        catch (T)
        {
            return;
        }

        throw new TestFailureException($"Expected {typeof(T).Name}.");
    }
}

internal sealed class TemporaryDirectory : IDisposable
{
    public TemporaryDirectory()
    {
        Path = System.IO.Path.Combine(
            System.IO.Path.GetTempPath(),
            $"media-ecosystem-windows-proof-test-{Guid.NewGuid():N}");
        Directory.CreateDirectory(Path);
    }

    public string Path { get; }

    public void Dispose()
    {
        string fullPath = System.IO.Path.GetFullPath(Path);
        string tempPath = System.IO.Path.GetFullPath(System.IO.Path.GetTempPath());
        if (!fullPath.StartsWith(tempPath, StringComparison.OrdinalIgnoreCase) ||
            !System.IO.Path.GetFileName(fullPath).StartsWith(
                "media-ecosystem-windows-proof-test-",
                StringComparison.Ordinal))
        {
            throw new InvalidOperationException("Refusing unsafe test cleanup.");
        }

        if (Directory.Exists(fullPath))
        {
            Directory.Delete(fullPath, recursive: true);
        }
    }
}

internal sealed class TestFailureException : Exception
{
    public TestFailureException(string message)
        : base(message)
    {
    }
}
