# Disposable Windows proof toolchain record

- **Verified:** 2026-07-29 UTC
- **Scope:** one Phase 1 disposable candidate for PB-01 and PB-05; not a
  production architecture, packaging, or compatibility-floor decision

| Component | Exact selection | Source and rationale |
|---|---|---|
| .NET SDK | 8.0.423 | [Official .NET 8 download](https://dotnet.microsoft.com/en-us/download/dotnet/8.0) and [release index](https://github.com/dotnet/core/blob/main/release-notes/8.0/README.md); current supported .NET 8 servicing SDK on 2026-07-29 |
| .NET runtime | 8.0.29 | Included by SDK 8.0.423 and the self-contained publish; the official download page records .NET, Windows Desktop, and ASP.NET Core runtime 8.0.29 |
| Language | C# 12 | Language version shipped with .NET 8 and pinned explicitly by `Directory.Build.props` |
| Desktop UI | Windows Forms from `Microsoft.WindowsDesktop.App` 8.0.29 | Proof-only readable UI; it is not the capability under evaluation |
| Target framework | `net8.0-windows10.0.19041.0` | [Microsoft desktop WinRT guidance](https://learn.microsoft.com/windows/apps/desktop/modernize/winrt-apis-desktop-apps) shows this exact .NET 8 Windows TFM; it adds Windows SDK projections without selecting Windows App SDK or MSIX |
| Supported/target minimum | Windows `10.0.19041.0` | A narrow proof compilation boundary. The physical claim remains limited to the recorded Surface Book 3 Windows 11 environment. |
| Windows SDK .NET reference | `Microsoft.Windows.SDK.NET.Ref` 10.0.26100.84 | [Official NuGet package](https://www.nuget.org/packages/Microsoft.Windows.SDK.NET.Ref/10.0.26100.84); latest stable package on the verified feed that includes a `net8.0` reference surface. Newer 10.0.26100.87 targets .NET 9 and later, so it is not silently substituted. |
| Windows SDK package integrity | SHA-256 `2eff36accb9d76466d36e27e7199d375c662818d2c1376e65125d48ba4bd1c96`; NuGet SHA-512 content hash `kdW+7YTJUAmxepIMS0oD2by4LoCwjXARnby+y0iKRrEa6WkMQf+/oYgDe/67zrsxZLi2tGYPx0A/XKTLdyyLsQ==` | Calculated from the restored official 10.0.26100.84 `.nupkg` and its NuGet content-hash file during the tooling validation |
| Playback API | `Windows.Media.Playback.MediaPlayer`, `MediaPlaybackItem`, `MediaPlaybackSession` | Direct Windows SDK API surface; no alternate playback engine or codec package |
| System controls | Enabled `MediaPlaybackCommandManager` and `MediaPlayer.SystemMediaTransportControls` | [Microsoft automatic SMTC integration](https://learn.microsoft.com/windows/apps/develop/media-playback/integrate-with-systemmediatransportcontrols) documents that assigning a media source/item to `MediaPlayer` automatically integrates with SMTC. `PlayReceived`, `PauseReceived`, and optional `PositionReceived` distinguish system commands from in-app actions. |
| Display metadata | `MediaPlaybackItem.GetDisplayProperties` / `ApplyDisplayProperties` | The same Microsoft SMTC guidance documents display-property application. Evidence labels this as app-applied system metadata and keeps it separate from file-extracted metadata. |
| File metadata | `StorageFile.Properties.GetMusicPropertiesAsync` | [Microsoft file-property API](https://learn.microsoft.com/windows/apps/develop/files/file-properties); optional extracted title/artist/album observations cannot fail WAV |
| Power events | `Microsoft.Win32.SystemEvents.PowerModeChanged` | Desktop event observation only. The app never initiates suspend, restart, or shutdown. |
| Deployment | Unpacked, self-contained `win-x64`; not trimmed; not single-file | Straightforward unzip-and-run handoff with no installed .NET requirement, installer, package identity, deployment framework, signing key, or secret |
| CI | GitHub-hosted `windows-latest`; `actions/setup-dotnet@v5`; SDK 8.0.423 | Tooling build/test/publish evidence only; it does not prove Surface Book 3 behavior |
| CI structured-file parser | CPython 3.14.3 and PyYAML 6.0.3 | CI-only pinned JSON/XML/YAML validation; not an application or production dependency |

## Candidate boundary

The expected candidate compiled with zero warnings using the automatic
command-manager path. No manual `SystemMediaTransportControls.ButtonPressed`
implementation, WinUI, Windows App SDK, package identity, or alternate playback
engine was introduced.

Compilation proves only that the selected SDK surface is available. Whether an
unpackaged process exposes usable automatic SMTC on the actual Surface Book 3,
whether commands affect playback, and whether all six Windows decoders satisfy
PB-01 remain physical observations.

.NET 8 remains supported through 2026-11-10 according to the
[official .NET support policy](https://dotnet.microsoft.com/platform/support/policy/dotnet-core).
That nearby end-of-support date is a production-stack comparison input, not a
reason to change this already planned disposable proof to another major
runtime.

## Version controls

- `global.json` refuses SDK roll-forward from 8.0.423.
- `WindowsSdkPackageVersion` pins 10.0.26100.84.
- restore uses the repository-local `NuGet.Config` and committed lock files.
- the app embeds the source commit supplied as `SourceCommit`.
- publish is explicitly self-contained for `win-x64`.
- no dependency is permitted to choose a production architecture.
