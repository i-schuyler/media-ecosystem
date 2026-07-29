using System.Text.Json;

namespace MediaEcosystem.WindowsProof;

public static class EvidenceContractValidator
{
    private static readonly IReadOnlySet<string> RequiredRootFields =
        new HashSet<string>(StringComparer.Ordinal)
        {
            "schema_version",
            "build",
            "environment",
            "fixture_manifest_sha256",
            "format_contract_id",
            "active_required_format_ids",
            "historical_nonrequired_format_ids",
            "format_results",
            "pb01_windows_disposition",
            "lifecycle",
            "pb05_disposition",
            "session_started_utc",
            "session_ended_utc",
            "session_started_monotonic_ms",
            "session_ended_monotonic_ms",
            "session_monotonic_duration_ms",
            "failures",
            "limitations",
            "privacy",
        };

    public static void Validate(string evidenceJson, string schemaJson)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(evidenceJson);
        ArgumentException.ThrowIfNullOrWhiteSpace(schemaJson);
        using JsonDocument schema = JsonDocument.Parse(schemaJson);
        JsonElement schemaRoot = schema.RootElement;
        if (schemaRoot.GetProperty("$schema").GetString() !=
                "https://json-schema.org/draft/2020-12/schema" ||
            schemaRoot.GetProperty("properties")
                .GetProperty("schema_version")
                .GetProperty("const")
                .GetString() != ProofConstants.EvidenceSchemaVersion)
        {
            throw new EvidenceValidationException("Unexpected evidence schema identity.");
        }

        using JsonDocument document = JsonDocument.Parse(evidenceJson);
        JsonElement root = document.RootElement;
        if (root.ValueKind != JsonValueKind.Object)
        {
            throw new EvidenceValidationException("Evidence root must be an object.");
        }

        HashSet<string> actualFields = root.EnumerateObject()
            .Select(property => property.Name)
            .ToHashSet(StringComparer.Ordinal);
        if (!actualFields.SetEquals(RequiredRootFields))
        {
            throw new EvidenceValidationException("Evidence root field contract mismatch.");
        }

        RequireString(root, "schema_version", ProofConstants.EvidenceSchemaVersion);
        RequireString(root, "format_contract_id", ProofConstants.FormatContractId);
        RequireSha256(root, "fixture_manifest_sha256");
        RequireDisposition(root, "pb01_windows_disposition");
        RequireDisposition(root, "pb05_disposition");
        ValidateExactIds(root.GetProperty("active_required_format_ids"));
        ValidateHistoricalIds(root.GetProperty("historical_nonrequired_format_ids"));
        ValidateBuild(root.GetProperty("build"));
        ValidateEnvironment(root.GetProperty("environment"));
        ValidateFormats(root.GetProperty("format_results"));
        ValidateLifecycle(root.GetProperty("lifecycle"));
        ValidateTiming(root);
        ValidatePrivacy(root.GetProperty("privacy"));
        PrivacyValidator.ValidateJson(evidenceJson);
    }

    private static void ValidateBuild(JsonElement build)
    {
        string sourceCommit = RequireString(build, "source_commit");
        if (sourceCommit != "unknown-local-build" &&
            (sourceCommit.Length != 40 ||
             sourceCommit.Any(character =>
                 !Uri.IsHexDigit(character) || char.IsUpper(character))))
        {
            throw new EvidenceValidationException("Source commit is not a valid immutable SHA.");
        }
        RequireString(build, "application_version");
        RequireString(build, "candidate", ProofConstants.CandidateName);
        RequireString(build, "target_framework", ProofConstants.TargetFramework);
        RequireString(build, "runtime_identifier", ProofConstants.RuntimeIdentifier);
        RequireString(
            build,
            "windows_sdk_reference_package",
            $"Microsoft.Windows.SDK.NET.Ref/{ProofConstants.WindowsSdkReferenceVersion}");
        RequireFalse(build, "package_identity");
        RequireFalse(build, "installer");
        RequireFalse(build, "signing_material_required");
    }

    private static void ValidateEnvironment(JsonElement environment)
    {
        RequireString(environment, "edition");
        RequireString(environment, "display_version");
        RequireString(environment, "build");
        RequireString(environment, "architecture");
        RequireString(environment, "dotnet_runtime");
        RequireString(
            environment,
            "approved_device_label",
            ProofConstants.DeviceLabel);
    }

    private static void ValidateFormats(JsonElement formats)
    {
        if (formats.ValueKind != JsonValueKind.Array ||
            formats.GetArrayLength() > ProofConstants.RequiredFixtureIds.Count)
        {
            throw new EvidenceValidationException("Format result count is invalid.");
        }

        HashSet<string> ids = new(StringComparer.Ordinal);
        foreach (JsonElement result in formats.EnumerateArray())
        {
            string id = RequireString(result, "fixture_id");
            if (!ProofConstants.RequiredFixtureIds.Contains(id, StringComparer.Ordinal) ||
                !ids.Add(id))
            {
                throw new EvidenceValidationException("Format result ID is invalid.");
            }

            RequireSha256(result, "verified_sha256");
            RequireDisposition(result, "disposition");
            if (result.TryGetProperty("applied_system_metadata", out JsonElement applied) &&
                applied.ValueKind != JsonValueKind.Null)
            {
                throw new EvidenceValidationException(
                    "The format matrix must not apply SMTC metadata.");
            }
        }
    }

    private static void ValidateLifecycle(JsonElement lifecycle)
    {
        RequireString(lifecycle, "fixture_id", ProofConstants.LifecycleFixtureId);
        RequireDisposition(lifecycle, "disposition");
        JsonElement checkpoint = lifecycle.GetProperty("restart_checkpoint");
        RequireFalse(checkpoint, "absolute_path_exported");
        RequireString(checkpoint, "storage_description", "private_local_application_data");
        foreach (JsonElement acknowledgement in
            lifecycle.GetProperty("manual_acknowledgements").EnumerateArray())
        {
            RequireString(acknowledgement, "observation_source", "human");
        }
    }

    private static void ValidateTiming(JsonElement root)
    {
        long start = root.GetProperty("session_started_monotonic_ms").GetInt64();
        long end = root.GetProperty("session_ended_monotonic_ms").GetInt64();
        long duration = root.GetProperty("session_monotonic_duration_ms").GetInt64();
        if (start < 0 || end < start || duration != end - start)
        {
            throw new EvidenceValidationException("Session monotonic timing is invalid.");
        }
    }

    private static void ValidatePrivacy(JsonElement privacy)
    {
        foreach (string field in new[]
        {
            "username_included",
            "hostname_included",
            "account_data_included",
            "serial_number_included",
            "machine_or_volume_identifier_included",
            "absolute_path_included",
            "environment_variables_included",
            "installed_apps_included",
            "personal_media_data_included",
            "credentials_or_signing_material_included",
            "audio_exported",
        })
        {
            RequireFalse(privacy, field);
        }
    }

    private static void ValidateExactIds(JsonElement ids)
    {
        string[] actual = ids.EnumerateArray()
            .Select(item => item.GetString() ?? string.Empty)
            .ToArray();
        if (!actual.SequenceEqual(ProofConstants.RequiredFixtureIds, StringComparer.Ordinal))
        {
            throw new EvidenceValidationException("Active format IDs are not exact.");
        }
    }

    private static void ValidateHistoricalIds(JsonElement ids)
    {
        HashSet<string> actual = ids.EnumerateArray()
            .Select(item => item.GetString() ?? string.Empty)
            .ToHashSet(StringComparer.Ordinal);
        if (!actual.SetEquals(ProofConstants.HistoricalNonrequiredFixtureIds))
        {
            throw new EvidenceValidationException("Historical format exclusions are not exact.");
        }
    }

    private static void RequireDisposition(JsonElement parent, string property)
    {
        string value = RequireString(parent, property);
        if (value is not ("passed" or "failed" or "inconclusive" or "not_run"))
        {
            throw new EvidenceValidationException($"Invalid disposition at '{property}'.");
        }
    }

    private static void RequireSha256(JsonElement parent, string property)
    {
        string value = RequireString(parent, property);
        if (value.Length != 64 ||
            value.Any(character => !Uri.IsHexDigit(character) || char.IsUpper(character)))
        {
            throw new EvidenceValidationException($"Invalid SHA-256 at '{property}'.");
        }
    }

    private static string RequireString(
        JsonElement parent,
        string property,
        string? expected = null)
    {
        if (!parent.TryGetProperty(property, out JsonElement element) ||
            element.ValueKind != JsonValueKind.String ||
            string.IsNullOrWhiteSpace(element.GetString()))
        {
            throw new EvidenceValidationException($"Missing string at '{property}'.");
        }

        string value = element.GetString()!;
        if (expected is not null && !string.Equals(value, expected, StringComparison.Ordinal))
        {
            throw new EvidenceValidationException($"Unexpected value at '{property}'.");
        }

        return value;
    }

    private static void RequireFalse(JsonElement parent, string property)
    {
        if (!parent.TryGetProperty(property, out JsonElement element) ||
            element.ValueKind != JsonValueKind.False)
        {
            throw new EvidenceValidationException($"Expected false at '{property}'.");
        }
    }
}

public sealed class EvidenceValidationException : Exception
{
    public EvidenceValidationException(string message)
        : base(message)
    {
    }
}
