# Disposable Windows Phase 1 platform proof

This workspace contains one deliberately disposable Windows desktop diagnostic
application for GitHub issue #4 and the Windows half of issue #5. It covers
PB-05 and the Windows side of PB-01. It uses synthetic data only and does not
select a production framework, language, playback engine, UI, installer,
package identity, queue, catalog, or persistence design.

Build success is tooling evidence only. The application has not produced
physical Windows playback, codec, SMTC, sleep/wake, or restart evidence until a
human completes the documented Microsoft Surface Book 3 protocol.

## Candidate

The candidate is:

- .NET SDK 8.0.423 and runtime 8.0.29;
- `net8.0-windows10.0.19041.0`;
- `Microsoft.Windows.SDK.NET.Ref` 10.0.26100.84;
- Windows Forms for the diagnostic UI;
- `Windows.Media.Playback.MediaPlayer`, `MediaPlaybackItem`, and
  `MediaPlaybackCommandManager`;
- automatic System Media Transport Controls integration;
- unpacked, untrimmed, self-contained `win-x64` publishing.

Exact sources, package integrity values, API boundaries, and selection
rationale are in [TOOLCHAIN.md](TOOLCHAIN.md).

## Source layout

- `src/WindowsProof.Core/` contains pure fixture, aggregation, checkpoint,
  schema, privacy, and evidence-ZIP logic.
- `src/WindowsProof.App/` contains the Windows Forms guide, Windows playback
  runner, dedicated lifecycle player, power observation, and export assembly.
- `tests/WindowsProof.SelfTest/` is a dependency-free deterministic headless
  test executable.
- `schemas/` contains evidence schema 1.0.0.
- `scripts/` verifies fixtures, published output, PowerShell syntax, and the
  final tooling artifact ZIP.

The application copies fixtures at build/publish time from the single canonical
tracked corpus:

`../android-platform-proof/app/src/main/assets/fixtures/`

It does not track a second copy of the six audio binaries.

## Fixture and format behavior

Before any player is created, verification requires:

- the exact ordered stable IDs `mp3-v0`, `mp3-320`, `flac`, `aac`,
  `ogg-vorbis`, and `wav`;
- exactly six active entries and exactly six media files;
- the manifest and checksum file, with no missing or additional file;
- matching filenames, sizes, SHA-256 values, expected durations, and
  tolerances;
- explicit historical exclusion of ALAC and AIFF;
- the existing per-file and total corpus bounds.

Any mismatch fails closed.

The matrix uses a fresh `MediaPlayer` per fixture. It records source
acceptance, opened/prepared, playback start, position advancement, seek request
and completion, duration/tolerance, natural end, optional extracted file
metadata, candidate properties, warnings, errors, timeouts, monotonic elapsed
time, and disposition. All phases have finite timeouts. A decoder error is
recorded and the next stable fixture still runs.

PB-01 passes only if every exact fixture passes all required playback
dimensions. File metadata is optional and cannot fail WAV. The matrix never
applies system display metadata.

## Lifecycle and SMTC workflow

A separate long-lived `MediaPlayer` loops the verified `mp3-v0` fixture. The
app applies the synthetic title `Synthetic Windows Lifecycle Proof` and artist
`Media Ecosystem Synthetic Lab` to the `MediaPlaybackItem` display properties.
Those values are exported as **applied system metadata**, not extracted file
metadata.

The guided UI keeps these stages visible:

1. Verify packaged fixtures.
2. Run the exact six-format matrix.
3. Start lifecycle/SMTC playback.
4. Record the human metadata-visibility observation.
5. Exercise Windows system play/pause; command-manager events record the real
   system commands and resulting playback state.
6. Mark ready for sleep without initiating sleep.
7. Observe suspend/resume and record the wake result.
8. Save, reopen, and complete one bounded restart checkpoint after a new
   playback starts.
9. Export evidence.

The optional system position command is recorded when Windows exposes and the
human exercises it. In-app buttons do not stand in for Windows system
commands. Human acknowledgements and application events remain different model
types.

## Restart checkpoint

The app writes one versioned checkpoint beneath its private local application
data. It stores only the proof session, partial matrix results, sanitized
diagnostics, lifecycle state, and restart status. A prior generation permits
recovery from a malformed primary checkpoint.

The absolute checkpoint path is neither displayed nor exported. The checkpoint
shows only that the app reopened cleanly, recovered bounded proof state, and
could start a new playback. It does not claim that playback survives process
termination or implement a production persistent queue.

## Evidence ZIP

The user chooses the destination for
`media-ecosystem-windows-proof-<UTC timestamp>.zip`. The archive contains
exactly:

- `evidence.json`
- `summary.md`
- `fixture-manifest.json`
- `fixture-SHA256SUMS`
- `build-metadata.json`
- `diagnostic.log`
- `CHECKSUMS.sha256`

The exporter validates the schema and privacy boundary, computes the six
internal content hashes, validates the exact seven-member allowlist, writes an
intermediate archive next to the selected destination, promotes it, reopens it,
compares the completed bytes, revalidates all internal hashes, then calculates
the whole-ZIP SHA-256. Only filename, byte size, and SHA-256 are displayed.
Audio is never exported.

The raw ZIP stays outside Git. Only a later reviewed, reproducible sanitized
report belongs in the durable evidence directory.

## Build and validate

Run these commands in Windows PowerShell from this directory with .NET SDK
8.0.423 installed:

```powershell
dotnet restore .\MediaEcosystem.WindowsProof.sln --locked-mode --configfile .\NuGet.Config
dotnet build .\MediaEcosystem.WindowsProof.sln --configuration Release --no-restore
dotnet run --project .\tests\WindowsProof.SelfTest\WindowsProof.SelfTest.csproj `
  --configuration Release --no-build --no-restore -- ..\..
.\scripts\Verify-Fixtures.ps1 `
  -FixtureRoot ..\android-platform-proof\app\src\main\assets\fixtures
```

Publish and verify an unpacked handoff:

```powershell
$sourceCommit = git rev-parse HEAD
dotnet publish .\src\WindowsProof.App\WindowsProof.App.csproj `
  --configuration Release --runtime win-x64 --self-contained true `
  --no-restore --output .\out\publish /p:SourceCommit=$sourceCommit
.\scripts\Verify-Publish.ps1 -PublishRoot .\out\publish
.\scripts\Package-Artifact.ps1 -PublishRoot .\out\publish `
  -OutputZip .\out\disposable-windows-platform-proof-$sourceCommit.zip
```

The resulting tooling artifact is intended to be unpacked on the Surface Book
3 and run directly. Do not launch it during automated tooling validation.

## Physical boundary

Follow the durable
[physical-session protocol](../../docs/spikes/phase-1/windows-platform-proof/PHYSICAL_SESSION_PROTOCOL.md).
The app never sleeps, restarts, shuts down, or relaunches the computer. The
human performs those lifecycle actions. Issue #4 and issue #5 stay open until
the raw evidence archive is independently verified and sanitized results are
reviewed.
