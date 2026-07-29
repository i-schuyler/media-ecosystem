using System.Text;
using System.Text.Json;

namespace MediaEcosystem.WindowsProof;

internal static class EvidenceExporter
{
    public static async Task<EvidenceArchiveResult> ExportAsync(
        ProofSessionState state,
        string destination,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(state);
        BuildMetadata build = ProofRuntime.BuildMetadata();
        long endedMonotonicMs = Environment.TickCount64;
        DateTimeOffset endedUtc = DateTimeOffset.UtcNow;
        state.Lifecycle.Disposition = ProofAggregators.EvaluateLifecycle(state.Lifecycle);
        ProofDisposition pb01 = ProofAggregators.EvaluatePb01(state.FormatResults);
        EvidenceDocument evidence = new()
        {
            Build = build,
            Environment = ProofRuntime.EnvironmentMetadata(),
            FixtureManifestSha256 = state.FixtureManifestSha256,
            FormatResults = state.FormatResults,
            Pb01WindowsDisposition = pb01,
            Lifecycle = state.Lifecycle,
            Pb05Disposition = state.Lifecycle.Disposition,
            SessionStartedUtc = state.SessionStartedUtc,
            SessionEndedUtc = endedUtc,
            SessionStartedMonotonicMs = state.SessionStartedMonotonicMs,
            SessionEndedMonotonicMs = endedMonotonicMs,
            SessionMonotonicDurationMs =
                Math.Max(0, endedMonotonicMs - state.SessionStartedMonotonicMs),
            Failures = state.Failures
                .Concat(state.FormatResults.SelectMany(item => item.Errors))
                .Concat(state.Lifecycle.Failures)
                .Distinct(StringComparer.Ordinal)
                .ToList(),
            Limitations = state.Limitations
                .Concat(state.Lifecycle.Limitations)
                .Distinct(StringComparer.Ordinal)
                .ToList(),
            Privacy = PrivacyFlags(),
        };

        JsonSerializerOptions indented = JsonDefaults.Create(indented: true);
        string evidenceJson = JsonSerializer.Serialize(evidence, indented);
        string buildJson = JsonSerializer.Serialize(build, indented);
        string schemaJson = await File.ReadAllTextAsync(
            ProofRuntime.SchemaPath,
            cancellationToken);
        EvidenceContractValidator.Validate(evidenceJson, schemaJson);

        string diagnostic = string.Join(
            "\n",
            state.DiagnosticLog.Select(item =>
                JsonSerializer.Serialize(item, JsonDefaults.Create()))) + "\n";
        string summary = BuildSummary(evidence);
        PrivacyValidator.ValidateText(summary);
        PrivacyValidator.ValidateText(diagnostic);

        EvidenceArchiveContent content = new()
        {
            EvidenceJson = Encoding.UTF8.GetBytes(evidenceJson + "\n"),
            SummaryMarkdown = Encoding.UTF8.GetBytes(summary),
            FixtureManifestJson = await File.ReadAllBytesAsync(
                Path.Combine(ProofRuntime.FixtureRoot, "fixture-manifest.json"),
                cancellationToken),
            FixtureSha256Sums = await File.ReadAllBytesAsync(
                Path.Combine(ProofRuntime.FixtureRoot, "SHA256SUMS"),
                cancellationToken),
            BuildMetadataJson = Encoding.UTF8.GetBytes(buildJson + "\n"),
            DiagnosticLog = Encoding.UTF8.GetBytes(diagnostic),
        };
        return await EvidenceArchive.CreateVerifiedAsync(
            destination,
            content,
            cancellationToken);
    }

    private static Dictionary<string, bool> PrivacyFlags() =>
        new(StringComparer.Ordinal)
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
        };

    private static string BuildSummary(EvidenceDocument evidence)
    {
        StringBuilder summary = new();
        summary.AppendLine("# Media Ecosystem disposable Windows proof");
        summary.AppendLine();
        summary.AppendLine(
            $"Source commit: `{evidence.Build.SourceCommit}`  ");
        summary.AppendLine(
            $"Candidate: {evidence.Build.Candidate}  ");
        summary.AppendLine(
            $"Device label: {evidence.Environment.ApprovedDeviceLabel}  ");
        summary.AppendLine(
            $"Windows PB-01 disposition: **{Wire(evidence.Pb01WindowsDisposition)}**  ");
        summary.AppendLine(
            $"PB-05 disposition: **{Wire(evidence.Pb05Disposition)}**");
        summary.AppendLine();
        summary.AppendLine("## Exact six-format matrix");
        summary.AppendLine();
        summary.AppendLine("| Fixture ID | Disposition | Required playback dimensions |");
        summary.AppendLine("|---|---|---|");
        Dictionary<string, FormatResult> byId = evidence.FormatResults.ToDictionary(
            item => item.FixtureId,
            StringComparer.Ordinal);
        foreach (string id in ProofConstants.RequiredFixtureIds)
        {
            if (!byId.TryGetValue(id, out FormatResult? result))
            {
                summary.AppendLine($"| {id} | not_run | no result recorded |");
                continue;
            }

            bool dimensions =
                result.SourceAccepted &&
                result.MediaOpenedPrepared &&
                result.PlaybackStarted &&
                result.PositionAdvanced &&
                result.SeekCompleted &&
                result.DurationWithinTolerance &&
                result.EndOfTrackObserved;
            summary.AppendLine(
                $"| {id} | {Wire(result.Disposition)} | " +
                $"{(dimensions ? "passed" : "incomplete or failed")} |");
        }

        summary.AppendLine();
        summary.AppendLine("## Lifecycle and SMTC");
        summary.AppendLine();
        summary.AppendLine(
            $"Lifecycle disposition: **{Wire(evidence.Lifecycle.Disposition)}**. " +
            "Command-manager observations are automatic application events; " +
            "metadata visibility acknowledgements are explicitly human observations.");
        summary.AppendLine();
        summary.AppendLine("## Evidence boundary");
        summary.AppendLine();
        summary.AppendLine(
            "This archive contains no audio, personal paths, account data, machine " +
            "identifiers, credentials, or signing material. Build success by itself " +
            "is tooling evidence, not physical-device evidence.");
        return summary.ToString();
    }

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
