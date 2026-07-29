using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace MediaEcosystem.WindowsProof;

public sealed class FixtureManifest
{
    [JsonPropertyName("fixtures")]
    public List<FixtureManifestEntry> Fixtures { get; set; } = [];

    [JsonPropertyName("format_contract")]
    public FixtureFormatContract FormatContract { get; set; } = new();

    [JsonPropertyName("provenance")]
    public string Provenance { get; set; } = string.Empty;

    [JsonPropertyName("schema_version")]
    public string SchemaVersion { get; set; } = string.Empty;
}

public sealed class FixtureManifestEntry
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("filename")]
    public string Filename { get; set; } = string.Empty;

    [JsonPropertyName("required_format")]
    public string RequiredFormat { get; set; } = string.Empty;

    [JsonPropertyName("sha256")]
    public string Sha256 { get; set; } = string.Empty;

    [JsonPropertyName("size_bytes")]
    public long SizeBytes { get; set; }

    [JsonPropertyName("expected_duration_ms")]
    public long ExpectedDurationMs { get; set; }

    [JsonPropertyName("duration_tolerance_ms")]
    public int DurationToleranceMs { get; set; }

    [JsonPropertyName("mime_type")]
    public string MimeType { get; set; } = string.Empty;

    [JsonPropertyName("container")]
    public string Container { get; set; } = string.Empty;

    [JsonPropertyName("codec")]
    public string Codec { get; set; } = string.Empty;
}

public sealed class FixtureFormatContract
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("active_required_count")]
    public int ActiveRequiredCount { get; set; }

    [JsonPropertyName("active_required_format_ids")]
    public List<string> ActiveRequiredFormatIds { get; set; } = [];

    [JsonPropertyName("historical_nonrequired_format_ids")]
    public List<string> HistoricalNonrequiredFormatIds { get; set; } = [];
}

public sealed class VerifiedFixture
{
    public string Id { get; init; } = string.Empty;
    public string Filename { get; init; } = string.Empty;
    public string RequiredFormat { get; init; } = string.Empty;
    public string Sha256 { get; init; } = string.Empty;
    public long SizeBytes { get; init; }
    public long ExpectedDurationMs { get; init; }
    public int DurationToleranceMs { get; init; }
    public string MimeType { get; init; } = string.Empty;
    public string Container { get; init; } = string.Empty;
    public string Codec { get; init; } = string.Empty;

    [JsonIgnore]
    public string AbsolutePath { get; init; } = string.Empty;
}

public sealed class FixtureVerificationResult
{
    public string ManifestSha256 { get; init; } = string.Empty;
    public long TotalAudioBytes { get; init; }
    public IReadOnlyList<VerifiedFixture> Fixtures { get; init; } = [];
}

public static class FixtureVerifier
{
    public const int RequiredCount = 6;
    public const long MaximumFixtureBytes = 2_000_000;
    public const long MaximumCorpusBytes = 8_000_000;

    public static async Task<FixtureVerificationResult> VerifyAsync(
        string fixtureRoot,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(fixtureRoot);
        string root = Path.GetFullPath(fixtureRoot);
        if (!Directory.Exists(root))
        {
            throw new FixtureVerificationException("Fixture directory is missing.");
        }

        string manifestPath = Path.Combine(root, "fixture-manifest.json");
        string sumsPath = Path.Combine(root, "SHA256SUMS");
        if (!File.Exists(manifestPath) || !File.Exists(sumsPath))
        {
            throw new FixtureVerificationException("Fixture manifests are missing.");
        }

        byte[] manifestBytes = await File.ReadAllBytesAsync(manifestPath, cancellationToken);
        FixtureManifest manifest;
        try
        {
            manifest = JsonSerializer.Deserialize<FixtureManifest>(manifestBytes)
                ?? throw new JsonException("Manifest was empty.");
        }
        catch (JsonException exception)
        {
            throw new FixtureVerificationException(
                "Fixture manifest is not valid JSON.",
                exception);
        }

        ValidateContract(manifest);
        IReadOnlyDictionary<string, string> sums = ParseChecksums(
            await File.ReadAllTextAsync(sumsPath, cancellationToken));

        HashSet<string> expectedFiles = manifest.Fixtures
            .Select(item => item.Filename)
            .Append("fixture-manifest.json")
            .Append("SHA256SUMS")
            .ToHashSet(StringComparer.Ordinal);
        HashSet<string> actualFiles = Directory
            .EnumerateFiles(root, "*", SearchOption.TopDirectoryOnly)
            .Select(Path.GetFileName)
            .Where(name => name is not null)
            .Cast<string>()
            .ToHashSet(StringComparer.Ordinal);
        if (!actualFiles.SetEquals(expectedFiles))
        {
            throw new FixtureVerificationException(
                "Fixture membership mismatch; missing or extra data is rejected.");
        }

        if (actualFiles.Any(name =>
            ProofConstants.MediaExtensions.Contains(Path.GetExtension(name)) &&
            !manifest.Fixtures.Any(entry =>
                string.Equals(entry.Filename, name, StringComparison.Ordinal))))
        {
            throw new FixtureVerificationException("Unmanifested media file was found.");
        }

        List<VerifiedFixture> verified = [];
        long totalBytes = 0;
        foreach (FixtureManifestEntry entry in manifest.Fixtures)
        {
            cancellationToken.ThrowIfCancellationRequested();
            ValidateEntry(entry);
            string path = Path.Combine(root, entry.Filename);
            FileInfo info = new(path);
            if (!info.Exists || info.Length != entry.SizeBytes)
            {
                throw new FixtureVerificationException(
                    $"Fixture size mismatch for stable ID '{entry.Id}'.");
            }

            if (info.Length > MaximumFixtureBytes)
            {
                throw new FixtureVerificationException(
                    $"Fixture exceeds the bounded size limit for stable ID '{entry.Id}'.");
            }

            string actualHash = await Sha256FileAsync(path, cancellationToken);
            if (!string.Equals(actualHash, entry.Sha256, StringComparison.Ordinal) ||
                !sums.TryGetValue(entry.Filename, out string? sumHash) ||
                !string.Equals(sumHash, entry.Sha256, StringComparison.Ordinal))
            {
                throw new FixtureVerificationException(
                    $"Fixture SHA-256 mismatch for stable ID '{entry.Id}'.");
            }

            totalBytes += info.Length;
            verified.Add(new VerifiedFixture
            {
                Id = entry.Id,
                Filename = entry.Filename,
                RequiredFormat = entry.RequiredFormat,
                Sha256 = entry.Sha256,
                SizeBytes = entry.SizeBytes,
                ExpectedDurationMs = entry.ExpectedDurationMs,
                DurationToleranceMs = entry.DurationToleranceMs,
                MimeType = entry.MimeType,
                Container = entry.Container,
                Codec = entry.Codec,
                AbsolutePath = path,
            });
        }

        if (totalBytes > MaximumCorpusBytes)
        {
            throw new FixtureVerificationException("Fixture corpus exceeds the bounded size limit.");
        }

        if (!sums.Keys.ToHashSet(StringComparer.Ordinal).SetEquals(
            manifest.Fixtures.Select(entry => entry.Filename)))
        {
            throw new FixtureVerificationException("Fixture checksum membership mismatch.");
        }

        return new FixtureVerificationResult
        {
            ManifestSha256 = Convert.ToHexString(SHA256.HashData(manifestBytes)).ToLowerInvariant(),
            TotalAudioBytes = totalBytes,
            Fixtures = verified,
        };
    }

    private static void ValidateContract(FixtureManifest manifest)
    {
        if (manifest.Fixtures.Count != RequiredCount ||
            manifest.FormatContract.ActiveRequiredCount != RequiredCount ||
            !string.Equals(
                manifest.FormatContract.Id,
                ProofConstants.FormatContractId,
                StringComparison.Ordinal) ||
            !manifest.FormatContract.ActiveRequiredFormatIds.SequenceEqual(
                ProofConstants.RequiredFixtureIds,
                StringComparer.Ordinal) ||
            !manifest.Fixtures.Select(item => item.Id).SequenceEqual(
                ProofConstants.RequiredFixtureIds,
                StringComparer.Ordinal) ||
            !manifest.FormatContract.HistoricalNonrequiredFormatIds
                .ToHashSet(StringComparer.Ordinal)
                .SetEquals(ProofConstants.HistoricalNonrequiredFixtureIds) ||
            manifest.Fixtures.Any(item =>
                ProofConstants.HistoricalNonrequiredFixtureIds.Contains(item.Id)))
        {
            throw new FixtureVerificationException(
                "Fixture manifest does not describe the exact active six-format contract.");
        }

        if (!manifest.Provenance.Contains("synthetic", StringComparison.OrdinalIgnoreCase) ||
            !manifest.Provenance.Contains(
                "no human recording",
                StringComparison.OrdinalIgnoreCase))
        {
            throw new FixtureVerificationException("Fixture provenance is not synthetic.");
        }
    }

    private static void ValidateEntry(FixtureManifestEntry entry)
    {
        if (string.IsNullOrWhiteSpace(entry.Id) ||
            string.IsNullOrWhiteSpace(entry.Filename) ||
            Path.GetFileName(entry.Filename) != entry.Filename ||
            entry.SizeBytes <= 0 ||
            entry.ExpectedDurationMs < 5_000 ||
            entry.DurationToleranceMs is <= 0 or > 500 ||
            entry.Sha256.Length != 64 ||
            entry.Sha256.Any(character =>
                !Uri.IsHexDigit(character) || char.IsUpper(character)) ||
            !entry.MimeType.StartsWith("audio/", StringComparison.Ordinal))
        {
            throw new FixtureVerificationException(
                $"Fixture entry is invalid for stable ID '{entry.Id}'.");
        }
    }

    private static IReadOnlyDictionary<string, string> ParseChecksums(string text)
    {
        Dictionary<string, string> result = new(StringComparer.Ordinal);
        foreach (string line in text.Split(
            ['\r', '\n'],
            StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            int separator = line.IndexOf("  ", StringComparison.Ordinal);
            if (separator != 64 || line.Length <= 66)
            {
                throw new FixtureVerificationException("Fixture checksum manifest is malformed.");
            }

            string hash = line[..separator];
            string filename = line[(separator + 2)..];
            if (hash.Any(character =>
                    !Uri.IsHexDigit(character) || char.IsUpper(character)) ||
                Path.GetFileName(filename) != filename ||
                !result.TryAdd(filename, hash))
            {
                throw new FixtureVerificationException("Fixture checksum manifest is malformed.");
            }
        }

        return result;
    }

    public static async Task<string> Sha256FileAsync(
        string path,
        CancellationToken cancellationToken = default)
    {
        await using FileStream stream = new(
            path,
            FileMode.Open,
            FileAccess.Read,
            FileShare.Read,
            bufferSize: 128 * 1024,
            FileOptions.Asynchronous | FileOptions.SequentialScan);
        byte[] hash = await SHA256.HashDataAsync(stream, cancellationToken);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }
}

public sealed class FixtureVerificationException : Exception
{
    public FixtureVerificationException(string message)
        : base(message)
    {
    }

    public FixtureVerificationException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
