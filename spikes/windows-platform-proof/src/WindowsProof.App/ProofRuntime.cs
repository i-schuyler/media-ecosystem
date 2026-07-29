using System.Reflection;
using System.Runtime.InteropServices;
using System.Security;
using Microsoft.Win32;

namespace MediaEcosystem.WindowsProof;

internal static class ProofRuntime
{
    public static string FixtureRoot => Path.Combine(AppContext.BaseDirectory, "fixtures");
    public static string SchemaPath => Path.Combine(
        AppContext.BaseDirectory,
        "schemas",
        "windows-proof-evidence.schema.json");

    public static string PrivateCheckpointRoot => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "MediaEcosystem",
        "DisposableWindowsPlatformProof");

    public static BuildMetadata BuildMetadata()
    {
        Assembly assembly = typeof(ProofRuntime).Assembly;
        Dictionary<string, string> metadata = assembly
            .GetCustomAttributes<AssemblyMetadataAttribute>()
            .ToDictionary(item => item.Key, item => item.Value ?? string.Empty);
        return new BuildMetadata
        {
            SourceCommit = metadata.GetValueOrDefault("SourceCommit", "unknown-local-build"),
            BuildConfiguration = metadata.GetValueOrDefault("BuildConfiguration", "Release"),
        };
    }

    public static SanitizedWindowsEnvironment EnvironmentMetadata()
    {
        string edition = "unavailable";
        string displayVersion = "unavailable";
        string build = Environment.OSVersion.Version.ToString();
        try
        {
            using RegistryKey? key = Registry.LocalMachine.OpenSubKey(
                @"SOFTWARE\Microsoft\Windows NT\CurrentVersion",
                writable: false);
            if (key is not null)
            {
                edition = key.GetValue("ProductName") as string ?? edition;
                displayVersion = key.GetValue("DisplayVersion") as string ?? displayVersion;
                string? currentBuild = key.GetValue("CurrentBuildNumber") as string;
                object? updateBuildRevision = key.GetValue("UBR");
                if (!string.IsNullOrWhiteSpace(currentBuild))
                {
                    build = updateBuildRevision is int revision
                        ? $"{currentBuild}.{revision}"
                        : currentBuild;
                }
            }
        }
        catch (Exception exception) when (
            exception is SecurityException or UnauthorizedAccessException)
        {
            // Sanitized fallbacks above are sufficient for a partial evidence export.
        }

        return new SanitizedWindowsEnvironment
        {
            Edition = edition,
            DisplayVersion = displayVersion,
            Build = build,
            Architecture = RuntimeInformation.OSArchitecture.ToString(),
            DotnetRuntime = RuntimeInformation.FrameworkDescription,
        };
    }
}
