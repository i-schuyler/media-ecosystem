using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;

namespace MediaEcosystem.WindowsProof;

public sealed class EvidenceArchiveContent
{
    public required byte[] EvidenceJson { get; init; }
    public required byte[] SummaryMarkdown { get; init; }
    public required byte[] FixtureManifestJson { get; init; }
    public required byte[] FixtureSha256Sums { get; init; }
    public required byte[] BuildMetadataJson { get; init; }
    public required byte[] DiagnosticLog { get; init; }
}

public static class EvidenceArchive
{
    private const int MaximumMemberBytes = 2_000_000;

    public static async Task<EvidenceArchiveResult> CreateVerifiedAsync(
        string destination,
        EvidenceArchiveContent content,
        CancellationToken cancellationToken = default)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(destination);
        ArgumentNullException.ThrowIfNull(content);
        string destinationFullPath = Path.GetFullPath(destination);
        string filename = Path.GetFileName(destinationFullPath);
        if (!ProofConstants.EvidenceArchiveFilenameRegex().IsMatch(filename))
        {
            throw new EvidenceValidationException("Evidence ZIP filename is not valid.");
        }

        Dictionary<string, byte[]> members = new(StringComparer.Ordinal)
        {
            ["evidence.json"] = content.EvidenceJson,
            ["summary.md"] = content.SummaryMarkdown,
            ["fixture-manifest.json"] = content.FixtureManifestJson,
            ["fixture-SHA256SUMS"] = content.FixtureSha256Sums,
            ["build-metadata.json"] = content.BuildMetadataJson,
            ["diagnostic.log"] = content.DiagnosticLog,
        };
        ValidateTextMembers(members);
        members["CHECKSUMS.sha256"] = BuildInternalChecksums(members);
        ValidateMemberContract(members);
        ValidateInternalChecksums(members);

        string parent = Path.GetDirectoryName(destinationFullPath)
            ?? throw new EvidenceValidationException("Evidence destination has no parent.");
        if (!Directory.Exists(parent))
        {
            throw new DirectoryNotFoundException("Evidence destination directory is missing.");
        }

        string partial = destinationFullPath + ".partial";
        try
        {
            await WriteArchiveAsync(partial, members, cancellationToken);
            File.Move(partial, destinationFullPath, overwrite: true);
            Dictionary<string, byte[]> reopened = await ReadArchiveAsync(
                destinationFullPath,
                cancellationToken);
            ValidateMemberContract(reopened);
            ValidateInternalChecksums(reopened);
            foreach ((string name, byte[] expected) in members)
            {
                if (!reopened.TryGetValue(name, out byte[]? actual) ||
                    !actual.AsSpan().SequenceEqual(expected))
                {
                    throw new EvidenceValidationException(
                        "Completed evidence ZIP bytes did not reopen identically.");
                }
            }

            string wholeZipHash = await FixtureVerifier.Sha256FileAsync(
                destinationFullPath,
                cancellationToken);
            long size = new FileInfo(destinationFullPath).Length;
            return new EvidenceArchiveResult
            {
                Filename = filename,
                SizeBytes = size,
                Sha256 = wholeZipHash,
                Members = reopened.Keys.Order(StringComparer.Ordinal).ToArray(),
            };
        }
        finally
        {
            if (File.Exists(partial))
            {
                File.Delete(partial);
            }
        }
    }

    public static async Task<IReadOnlyDictionary<string, byte[]>> VerifyAsync(
        string archivePath,
        CancellationToken cancellationToken = default)
    {
        Dictionary<string, byte[]> members = await ReadArchiveAsync(
            archivePath,
            cancellationToken);
        ValidateMemberContract(members);
        ValidateInternalChecksums(members);
        ValidateTextMembers(members.Where(pair => pair.Key != "CHECKSUMS.sha256")
            .ToDictionary(pair => pair.Key, pair => pair.Value, StringComparer.Ordinal));
        return members;
    }

    private static byte[] BuildInternalChecksums(IReadOnlyDictionary<string, byte[]> members)
    {
        string text = string.Join(
            "\n",
            members.Keys.Order(StringComparer.Ordinal).Select(name =>
                $"{Sha256(members[name])}  {name}")) + "\n";
        return Encoding.UTF8.GetBytes(text);
    }

    private static void ValidateTextMembers(IReadOnlyDictionary<string, byte[]> members)
    {
        foreach ((string name, byte[] bytes) in members)
        {
            if (bytes.Length > MaximumMemberBytes)
            {
                throw new EvidenceValidationException($"Evidence member is oversized: {name}.");
            }

            string text = Encoding.UTF8.GetString(bytes);
            PrivacyValidator.ValidateText(text);
            if (name.EndsWith(".json", StringComparison.Ordinal))
            {
                PrivacyValidator.ValidateJson(text);
            }
        }
    }

    private static void ValidateMemberContract(IReadOnlyDictionary<string, byte[]> members)
    {
        if (members.Count != ProofConstants.EvidenceArchiveMembers.Count ||
            !members.Keys.ToHashSet(StringComparer.Ordinal)
                .SetEquals(ProofConstants.EvidenceArchiveMembers) ||
            members.Keys.Any(name => name.Contains('/') || name.Contains('\\')) ||
            members.Keys.Any(name =>
                ProofConstants.MediaExtensions.Contains(Path.GetExtension(name))))
        {
            throw new EvidenceValidationException("Evidence ZIP member allowlist mismatch.");
        }
    }

    private static void ValidateInternalChecksums(
        IReadOnlyDictionary<string, byte[]> members)
    {
        string checksumText = Encoding.UTF8.GetString(members["CHECKSUMS.sha256"]);
        Dictionary<string, string> parsed = new(StringComparer.Ordinal);
        foreach (string line in checksumText.Split(
            ['\r', '\n'],
            StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            int separator = line.IndexOf("  ", StringComparison.Ordinal);
            if (separator != 64 || line.Length <= 66)
            {
                throw new EvidenceValidationException("Internal checksum manifest is malformed.");
            }

            string hash = line[..separator];
            string name = line[(separator + 2)..];
            if (!parsed.TryAdd(name, hash))
            {
                throw new EvidenceValidationException("Duplicate internal checksum entry.");
            }
        }

        HashSet<string> expected = ProofConstants.EvidenceArchiveMembers
            .Where(name => name != "CHECKSUMS.sha256")
            .ToHashSet(StringComparer.Ordinal);
        if (!parsed.Keys.ToHashSet(StringComparer.Ordinal).SetEquals(expected))
        {
            throw new EvidenceValidationException("Internal checksum allowlist mismatch.");
        }

        foreach ((string name, string expectedHash) in parsed)
        {
            if (!members.TryGetValue(name, out byte[]? value) ||
                !string.Equals(Sha256(value), expectedHash, StringComparison.Ordinal))
            {
                throw new EvidenceValidationException(
                    $"Internal checksum mismatch for '{name}'.");
            }
        }
    }

    private static async Task WriteArchiveAsync(
        string path,
        IReadOnlyDictionary<string, byte[]> members,
        CancellationToken cancellationToken)
    {
        await using FileStream output = new(
            path,
            FileMode.Create,
            FileAccess.Write,
            FileShare.None,
            bufferSize: 128 * 1024,
            FileOptions.Asynchronous | FileOptions.WriteThrough);
        using ZipArchive archive = new(output, ZipArchiveMode.Create, leaveOpen: true);
        foreach (string name in members.Keys.Order(StringComparer.Ordinal))
        {
            ZipArchiveEntry entry = archive.CreateEntry(name, CompressionLevel.Optimal);
            entry.LastWriteTime = new DateTimeOffset(1980, 1, 1, 0, 0, 0, TimeSpan.Zero);
            await using Stream stream = entry.Open();
            await stream.WriteAsync(members[name], cancellationToken);
        }
    }

    private static async Task<Dictionary<string, byte[]>> ReadArchiveAsync(
        string path,
        CancellationToken cancellationToken)
    {
        Dictionary<string, byte[]> members = new(StringComparer.Ordinal);
        await using FileStream input = new(
            path,
            FileMode.Open,
            FileAccess.Read,
            FileShare.Read,
            bufferSize: 128 * 1024,
            FileOptions.Asynchronous | FileOptions.SequentialScan);
        using ZipArchive archive = new(input, ZipArchiveMode.Read, leaveOpen: true);
        foreach (ZipArchiveEntry entry in archive.Entries)
        {
            if (entry.FullName.EndsWith("/", StringComparison.Ordinal) ||
                entry.Length > MaximumMemberBytes ||
                !members.TryAdd(entry.FullName, []))
            {
                throw new EvidenceValidationException(
                    "Evidence ZIP contains a directory, duplicate, or oversized member.");
            }

            await using Stream stream = entry.Open();
            using MemoryStream buffer = new((int)entry.Length);
            await stream.CopyToAsync(buffer, cancellationToken);
            members[entry.FullName] = buffer.ToArray();
        }

        return members;
    }

    private static string Sha256(byte[] bytes) =>
        Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
}
