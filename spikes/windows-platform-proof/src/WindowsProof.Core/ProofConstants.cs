using System.Text.RegularExpressions;

namespace MediaEcosystem.WindowsProof;

public static partial class ProofConstants
{
    public const string EvidenceSchemaVersion = "1.0.0";
    public const string ApplicationVersion = "0.1.0";
    public const string CandidateName = "Windows.Media.Playback.MediaPlayer automatic SMTC";
    public const string TargetFramework = "net8.0-windows10.0.19041.0";
    public const string DotNetSdkVersion = "8.0.423";
    public const string DotNetRuntimeVersion = "8.0.29";
    public const string WindowsSdkReferenceVersion = "10.0.26100.84";
    public const string RuntimeIdentifier = "win-x64";
    public const string DeviceLabel = "Microsoft Surface Book 3";
    public const string FormatContractId = "v1-required-formats-2026-07-28";
    public const string LifecycleFixtureId = "mp3-v0";

    public static readonly IReadOnlyList<string> RequiredFixtureIds =
    [
        "mp3-v0",
        "mp3-320",
        "flac",
        "aac",
        "ogg-vorbis",
        "wav",
    ];

    public static readonly IReadOnlySet<string> HistoricalNonrequiredFixtureIds =
        new HashSet<string>(StringComparer.Ordinal) { "alac", "aiff" };

    public static readonly IReadOnlySet<string> EvidenceArchiveMembers =
        new HashSet<string>(StringComparer.Ordinal)
        {
            "evidence.json",
            "summary.md",
            "fixture-manifest.json",
            "fixture-SHA256SUMS",
            "build-metadata.json",
            "diagnostic.log",
            "CHECKSUMS.sha256",
        };

    public static readonly IReadOnlySet<string> MediaExtensions =
        new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".mp3", ".flac", ".m4a", ".aac", ".ogg", ".oga", ".wav",
            ".aiff", ".aif", ".alac", ".wma", ".opus",
        };

    [GeneratedRegex(
        "^media-ecosystem-windows-proof-[0-9]{8}T[0-9]{6}Z\\.zip$",
        RegexOptions.CultureInvariant)]
    public static partial Regex EvidenceArchiveFilenameRegex();
}
