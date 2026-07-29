using System.Text.Json.Serialization;

namespace MediaEcosystem.WindowsProof;

public enum ProofDisposition
{
    Passed,
    Failed,
    Inconclusive,
    NotRun,
}

public sealed class ExtractedMetadata
{
    public string? Title { get; set; }
    public string? Artist { get; set; }
    public string? Album { get; set; }
    public bool AnyAvailable =>
        !string.IsNullOrWhiteSpace(Title) ||
        !string.IsNullOrWhiteSpace(Artist) ||
        !string.IsNullOrWhiteSpace(Album);
}

public sealed class AppliedSystemMetadata
{
    public string Title { get; set; } = string.Empty;
    public string Artist { get; set; } = string.Empty;
    public string Source { get; set; } = "manually_applied_by_proof_app";
}

public sealed class FormatResult
{
    public string FixtureId { get; set; } = string.Empty;
    public string VerifiedFilename { get; set; } = string.Empty;
    public long VerifiedSizeBytes { get; set; }
    public string VerifiedSha256 { get; set; } = string.Empty;
    public bool OpenAttempted { get; set; }
    public bool SourceAccepted { get; set; }
    public bool MediaOpenedPrepared { get; set; }
    public bool PlaybackStarted { get; set; }
    public bool PositionAdvanced { get; set; }
    public bool SeekRequested { get; set; }
    public bool SeekCompleted { get; set; }
    public int SeekToleranceMs { get; set; }
    public long ExpectedDurationMs { get; set; }
    public long? ActualDurationMs { get; set; }
    public int DurationToleranceMs { get; set; }
    public bool DurationWithinTolerance { get; set; }
    public bool EndOfTrackObserved { get; set; }
    public ExtractedMetadata ExtractedFileMetadata { get; set; } = new();
    public AppliedSystemMetadata? AppliedSystemMetadata { get; set; }
    public Dictionary<string, string> CandidateReportedMediaProperties { get; set; } =
        new(StringComparer.Ordinal);
    public List<string> Warnings { get; set; } = [];
    public List<string> Errors { get; set; } = [];
    public List<string> Timeouts { get; set; } = [];
    public long ElapsedMonotonicMs { get; set; }
    public ProofDisposition Disposition { get; set; } = ProofDisposition.NotRun;
}

public sealed class SystemCommandObservation
{
    public string Command { get; set; } = string.Empty;
    public bool AutomaticallyObservedByCommandManager { get; set; }
    public string PlaybackStateBefore { get; set; } = string.Empty;
    public string PlaybackStateAfter { get; set; } = string.Empty;
    public bool AffectedPlayback { get; set; }
    public long? RequestedPositionMs { get; set; }
    public DateTimeOffset ObservedUtc { get; set; }
    public long ObservedMonotonicMs { get; set; }
}

public sealed class ManualAcknowledgement
{
    public string ObservationId { get; set; } = string.Empty;
    public string ObservationSource { get; set; } = "human";
    public string Expected { get; set; } = string.Empty;
    public string Observed { get; set; } = string.Empty;
    public bool Acknowledged { get; set; }
    public DateTimeOffset ObservedUtc { get; set; }
    public long ObservedMonotonicMs { get; set; }
}

public sealed class PowerObservation
{
    public string Mode { get; set; } = string.Empty;
    public string PlaybackState { get; set; } = string.Empty;
    public bool AutomaticSmtcCandidateActive { get; set; }
    public DateTimeOffset ObservedUtc { get; set; }
    public long ObservedMonotonicMs { get; set; }
}

public sealed class RestartCheckpoint
{
    public string Status { get; set; } = "not_started";
    public int Generation { get; set; }
    public DateTimeOffset? BeganUtc { get; set; }
    public DateTimeOffset? ReopenedUtc { get; set; }
    public DateTimeOffset? CompletedUtc { get; set; }
    public bool CleanReopenObserved { get; set; }
    public bool NewPlaybackStartedAfterReopen { get; set; }
    public bool AbsolutePathExported { get; set; }
    public string StorageDescription { get; set; } = "private_local_application_data";
    public List<string> Failures { get; set; } = [];
}

public sealed class LifecycleResult
{
    public string FixtureId { get; set; } = ProofConstants.LifecycleFixtureId;
    public bool FixtureVerified { get; set; }
    public bool PlaybackBegan { get; set; }
    public bool AutomaticSmtcCandidateActive { get; set; }
    public AppliedSystemMetadata AppliedSystemMetadata { get; set; } = new();
    public ExtractedMetadata ExtractedFileMetadata { get; set; } = new();
    public List<SystemCommandObservation> SystemCommands { get; set; } = [];
    public List<ManualAcknowledgement> ManualAcknowledgements { get; set; } = [];
    public bool ReadyForSleepMarked { get; set; }
    public string PlaybackStateImmediatelyBeforeSleep { get; set; } = string.Empty;
    public bool SuspendObserved { get; set; }
    public bool ResumeObserved { get; set; }
    public List<PowerObservation> PowerEvents { get; set; } = [];
    public bool WakeResultRecorded { get; set; }
    public string PlaybackStateAfterWake { get; set; } = string.Empty;
    public bool AutomaticSmtcCandidateActiveAfterWake { get; set; }
    public RestartCheckpoint RestartCheckpoint { get; set; } = new();
    public List<string> Failures { get; set; } = [];
    public List<string> Limitations { get; set; } = [];
    public ProofDisposition Disposition { get; set; } = ProofDisposition.NotRun;
}

public sealed class DiagnosticEntry
{
    public DateTimeOffset Utc { get; set; }
    public long MonotonicMs { get; set; }
    public string Event { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public string Details { get; set; } = string.Empty;
}

public sealed class ProofSessionState
{
    public string StateSchemaVersion { get; set; } = "1.0.0";
    public DateTimeOffset SessionStartedUtc { get; set; } = DateTimeOffset.UtcNow;
    public long SessionStartedMonotonicMs { get; set; } = Environment.TickCount64;
    public DateTimeOffset LastUpdatedUtc { get; set; } = DateTimeOffset.UtcNow;
    public bool FixturesVerified { get; set; }
    public string FixtureManifestSha256 { get; set; } = string.Empty;
    public List<FormatResult> FormatResults { get; set; } = [];
    public LifecycleResult Lifecycle { get; set; } = new();
    public List<DiagnosticEntry> DiagnosticLog { get; set; } = [];
    public List<string> Failures { get; set; } = [];
    public List<string> Limitations { get; set; } =
    [
        "Build success is tooling evidence only.",
        "Physical PB-01 and PB-05 disposition requires the Microsoft Surface Book 3 session.",
        "The restart checkpoint does not claim playback survives process termination.",
    ];

    [JsonIgnore]
    public bool LoadedFromPrivateCheckpoint { get; set; }

    public static ProofSessionState CreateNew() => new();
}

public sealed class BuildMetadata
{
    public string ApplicationVersion { get; set; } = ProofConstants.ApplicationVersion;
    public string SourceCommit { get; set; } = string.Empty;
    public string BuildConfiguration { get; set; } = "Release";
    public string Candidate { get; set; } = ProofConstants.CandidateName;
    public string TargetFramework { get; set; } = ProofConstants.TargetFramework;
    public string RuntimeIdentifier { get; set; } = ProofConstants.RuntimeIdentifier;
    public string DotnetSdk { get; set; } = ProofConstants.DotNetSdkVersion;
    public string DotnetRuntime { get; set; } = ProofConstants.DotNetRuntimeVersion;
    public string WindowsSdkReferencePackage { get; set; } =
        $"Microsoft.Windows.SDK.NET.Ref/{ProofConstants.WindowsSdkReferenceVersion}";
    public string Packaging { get; set; } = "unpacked_self_contained";
    public bool PackageIdentity { get; set; }
    public bool Installer { get; set; }
    public bool SigningMaterialRequired { get; set; }
}

public sealed class SanitizedWindowsEnvironment
{
    public string Edition { get; set; } = string.Empty;
    public string DisplayVersion { get; set; } = string.Empty;
    public string Build { get; set; } = string.Empty;
    public string Architecture { get; set; } = string.Empty;
    public string DotnetRuntime { get; set; } = string.Empty;
    public string ApprovedDeviceLabel { get; set; } = ProofConstants.DeviceLabel;
}

public sealed class EvidenceDocument
{
    public string SchemaVersion { get; set; } = ProofConstants.EvidenceSchemaVersion;
    public BuildMetadata Build { get; set; } = new();
    public SanitizedWindowsEnvironment Environment { get; set; } = new();
    public string FixtureManifestSha256 { get; set; } = string.Empty;
    public string FormatContractId { get; set; } = ProofConstants.FormatContractId;
    public IReadOnlyList<string> ActiveRequiredFormatIds { get; set; } =
        ProofConstants.RequiredFixtureIds;
    public IReadOnlyList<string> HistoricalNonrequiredFormatIds { get; set; } =
        ProofConstants.HistoricalNonrequiredFixtureIds.Order(StringComparer.Ordinal).ToArray();
    public List<FormatResult> FormatResults { get; set; } = [];
    public ProofDisposition Pb01WindowsDisposition { get; set; } = ProofDisposition.NotRun;
    public LifecycleResult Lifecycle { get; set; } = new();
    public ProofDisposition Pb05Disposition { get; set; } = ProofDisposition.NotRun;
    public DateTimeOffset SessionStartedUtc { get; set; }
    public DateTimeOffset SessionEndedUtc { get; set; }
    public long SessionStartedMonotonicMs { get; set; }
    public long SessionEndedMonotonicMs { get; set; }
    public long SessionMonotonicDurationMs { get; set; }
    public List<string> Failures { get; set; } = [];
    public List<string> Limitations { get; set; } = [];
    public Dictionary<string, bool> Privacy { get; set; } = new(StringComparer.Ordinal);
}

public sealed class EvidenceArchiveResult
{
    public string Filename { get; init; } = string.Empty;
    public long SizeBytes { get; init; }
    public string Sha256 { get; init; } = string.Empty;
    public IReadOnlyList<string> Members { get; init; } = [];
}
