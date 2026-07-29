using System.Text.Json;
using System.Text.RegularExpressions;

namespace MediaEcosystem.WindowsProof;

public static partial class PrivacyValidator
{
    public static readonly IReadOnlySet<string> ForbiddenFieldNames =
        new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "username",
            "user_name",
            "hostname",
            "host_name",
            "device_name",
            "account",
            "account_data",
            "email",
            "serial_number",
            "machine_id",
            "volume_id",
            "volume_uuid",
            "absolute_path",
            "environment_variables",
            "installed_apps",
            "installed_applications",
            "personal_filename",
            "personal_path",
            "library_data",
            "media_library",
            "credential",
            "credentials",
            "token",
            "tokens",
            "signing_material",
            "raw_destination_path",
        };

    public static void ValidateJson(string json)
    {
        using JsonDocument document = JsonDocument.Parse(json);
        ValidateElement(document.RootElement, "$");
        ValidateText(json);
    }

    public static void ValidateText(string text)
    {
        foreach (Regex pattern in ForbiddenTextPatterns())
        {
            if (pattern.IsMatch(text))
            {
                throw new PrivacyValidationException(
                    $"Privacy scan matched prohibited pattern '{pattern}'.");
            }
        }
    }

    private static void ValidateElement(JsonElement element, string location)
    {
        if (element.ValueKind == JsonValueKind.Object)
        {
            foreach (JsonProperty property in element.EnumerateObject())
            {
                if (ForbiddenFieldNames.Contains(property.Name))
                {
                    throw new PrivacyValidationException(
                        $"Forbidden evidence field at {location}.");
                }

                ValidateElement(property.Value, $"{location}.{property.Name}");
            }
        }
        else if (element.ValueKind == JsonValueKind.Array)
        {
            int index = 0;
            foreach (JsonElement child in element.EnumerateArray())
            {
                ValidateElement(child, $"{location}[{index++}]");
            }
        }
    }

    private static IReadOnlyList<Regex> ForbiddenTextPatterns() =>
    [
        WindowsUsersPathRegex(),
        UnixHomePathRegex(),
        EmailRegex(),
        PrivateKeyRegex(),
        GitHubTokenRegex(),
        OpenAiTokenRegex(),
        EnvironmentVariableDumpRegex(),
    ];

    [GeneratedRegex(
        @"[A-Za-z]:\\Users\\",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)]
    private static partial Regex WindowsUsersPathRegex();

    [GeneratedRegex(
        @"/(?:home|Users)/[^/\s]+/",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)]
    private static partial Regex UnixHomePathRegex();

    [GeneratedRegex(
        @"\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b",
        RegexOptions.CultureInvariant)]
    private static partial Regex EmailRegex();

    [GeneratedRegex(
        @"BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)]
    private static partial Regex PrivateKeyRegex();

    [GeneratedRegex(
        @"\bgh[pousr]_[A-Za-z0-9_]{20,}\b",
        RegexOptions.CultureInvariant)]
    private static partial Regex GitHubTokenRegex();

    [GeneratedRegex(
        @"\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\b",
        RegexOptions.CultureInvariant)]
    private static partial Regex OpenAiTokenRegex();

    [GeneratedRegex(
        @"(?im)^(?:PATH|USERPROFILE|APPDATA|LOCALAPPDATA|TEMP|TMP)=",
        RegexOptions.CultureInvariant)]
    private static partial Regex EnvironmentVariableDumpRegex();
}

public sealed class PrivacyValidationException : Exception
{
    public PrivacyValidationException(string message)
        : base(message)
    {
    }
}
