# Windows playback and format proof plan

## Status

Tooling implementation plan for the combined Phase 1 Windows slice covering
GitHub issues #4 and the Windows half of #5. The implementation is prepared for
review; physical Surface Book 3 evidence is not yet complete.

## Questions

1. Can one disposable Windows desktop candidate play reliably through the
   required lifecycle transitions and expose working Windows System Media
   Transport Controls with expected synthetic metadata?
2. Do MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, and WAV satisfy PB-01 on the
   primary Microsoft Surface Book 3 validation device?

## Contract links

- Related DoD areas: Required formats; Playback; Persistent queue.
- Acceptance IDs: PB-01 and PB-05.
- Primary device: Microsoft Surface Book 3 on Windows 11.
- Capability issues: #4 and #5.
- Protocol: `docs/implementation/CAPABILITY_SPIKE_PROTOCOL.md`.

## Candidate boundary

Use a deliberately disposable .NET 8 Windows desktop diagnostic application
backed by `Windows.Media.Playback.MediaPlayer` and its automatic System Media
Transport Controls integration.

The implementation should use a conventional unpacked desktop executable rather
than an installer or production package. It may use Windows Forms for the proof
UI because the UI itself is not under evaluation.

This candidate is evidence only. It does not select:

- the production language or UI framework;
- the production playback engine;
- packaging or installer technology;
- the catalog, database, queue, or synchronization architecture;
- a minimum Windows version beyond the recorded proof environment.

Official Microsoft guidance records that desktop .NET applications can call
Windows Runtime APIs when they target a Windows-specific TFM, and that
`MediaPlayer` integrates automatically with the System Media Transport Controls.
The proof should prefer that built-in command-manager path instead of replacing
it with manual SMTC control unless the automatic path proves insufficient and
the limitation is recorded.

## Toolchain posture

Pin and record the exact toolchain selected by the implementation. The expected
starting posture is:

- .NET 8 SDK;
- `net8.0-windows10.0.19041.0` or a narrowly justified equivalent;
- Microsoft Windows SDK .NET reference package compatible with .NET 8;
- Windows Forms desktop UI;
- `Windows.Media.Playback.MediaPlayer`, `MediaPlaybackItem`, and
  `MediaPlaybackCommandManager`;
- GitHub Actions `windows-latest` build and test coverage;
- self-contained `win-x64` physical-test artifact for the Surface Book 3.

Do not silently float major toolchain components. Record official sources,
versions, hashes where practical, and why the version was selected.

## Fixture contract

Reuse the exact six manifest-covered fixtures already tracked under:

`spikes/android-platform-proof/app/src/main/assets/fixtures/`

Do not duplicate the audio bytes in a second tracked fixture directory. The
Windows build may copy those fixtures into its output directory from the single
canonical corpus.

The active stable IDs are exactly:

- `mp3-v0`
- `mp3-320`
- `flac`
- `aac`
- `ogg-vorbis`
- `wav`

ALAC and AIFF remain parseable historical Android observations and must not
re-enter the active Windows matrix.

Before playback, the proof must verify the fixture manifest, exact membership,
byte sizes, and SHA-256 values. Missing, extra, or modified fixtures fail closed.

## PB-01 format dimensions

For each required fixture, record separately:

- fixture identity and verified hash;
- open attempted and source accepted;
- player prepared/opened;
- playback started;
- playback position advanced;
- seek requested and completed within a documented tolerance;
- expected and actual duration plus tolerance result;
- end-of-track observed;
- optional extracted metadata observations;
- candidate-reported media properties when available;
- warnings, errors, timeouts, and elapsed monotonic duration;
- final disposition: `passed`, `failed`, `inconclusive`, or `not_run`.

PB-01 passes only when open/prepare/start, advancement, seek, duration tolerance,
and end-of-track pass. Optional metadata absence must not fail a format.

Use bounded timeouts. One failed or stalled decoder must not prevent later
fixtures from being tested or evidence from being exported.

## PB-05 and lifecycle dimensions

Use one dedicated lifecycle player with an exact synthetic fixture and explicit
metadata. Record:

- automatic SMTC integration became active after playback began;
- expected synthetic title and artist were visibly displayed in Windows system
  media UI;
- system play and pause commands were received and affected playback;
- any position/seek command exercised through system controls;
- playback state immediately before sleep;
- Windows suspend/resume or equivalent power-mode events observed by the app;
- playback and SMTC state after wake;
- a bounded app-restart checkpoint showing that the proof reopens cleanly,
  retains only sanitized checkpoint state, and can resume a new playback test;
- failures and limitations.

Do not claim that audio survives process termination unless that behavior is
actually implemented and observed. The app-restart observation is a lifecycle
and recovery check, not permission to invent a persistent production queue.

Manual acknowledgements must be explicit and separate from automatically
recorded command or power events. The evidence must show which facts were
observed by the app and which were confirmed by the human.

## Evidence archive

Export one sanitized ZIP with a user-selected destination and a filename
beginning `media-ecosystem-windows-proof-`.

Use exactly these seven members unless a reviewed schema amendment changes the
contract:

- `evidence.json`
- `summary.md`
- `fixture-manifest.json`
- `fixture-SHA256SUMS`
- `build-metadata.json`
- `diagnostic.log`
- `CHECKSUMS.sha256`

The app must validate the member allowlist and internal hashes before reporting
success, then hash the completed ZIP and display its filename, size, and
SHA-256. Do not export audio binaries.

The archive should record:

- evidence-schema version;
- source commit and application/build version;
- exact candidate and dependency versions;
- sanitized Windows edition/version/build, architecture, and .NET runtime;
- primary-device model as an approved non-secret validation label;
- fixture-manifest hash;
- format results;
- SMTC, sleep/wake, and restart observations;
- errors and limitations;
- session timestamps and monotonic measurements where applicable.

Do not export usernames, hostnames, account data, serial numbers, machine or
volume IDs, absolute paths, environment variables, installed-app lists,
personal filenames, media-library data, credentials, tokens, signing material,
or unrelated system diagnostics.

The unchanged raw ZIP remains outside Git. A later review imports only a
reproducible sanitized report.

## User experience

The proof UI should guide one bounded physical session:

1. Verify the packaged six-fixture corpus.
2. Run the exact six-format matrix.
3. Start the lifecycle/SMTC test.
4. Confirm Windows metadata visibility and exercise system play/pause.
5. Put the Surface Book 3 to sleep, wake it, and record the result.
6. Complete the restart checkpoint if the implementation requires one reopen.
7. Export the evidence ZIP.

The app must preserve partial and failed observations so evidence can still be
exported without repeating successful work.

## Validation and CI

The implementation PR should include, at minimum:

- deterministic fixture-manifest and exact-membership verification;
- pure logic tests for PB-01 aggregation and lifecycle disposition;
- evidence schema, allowlist, checksum, and privacy tests;
- regression coverage that optional metadata cannot fail WAV;
- regression coverage that ALAC/AIFF cannot enter the active matrix;
- Windows compilation and self-test execution;
- self-contained `win-x64` publish;
- packaged-corpus verification;
- artifact upload with bounded retention;
- repository foundation guardrails and shared-core regression checks;
- final tracked-file, secret, signing-material, media, size, and mode audits.

CI build success is tooling evidence only. PB-01 and PB-05 require the physical
Surface Book 3 session.

## Expected repository artifacts

- `spikes/windows-platform-proof/` source, tests, scripts, and toolchain record;
- `.github/workflows/windows-platform-proof.yml`;
- `docs/spikes/phase-1/windows-platform-proof/` evidence index and issue reports;
- narrowly aligned README, roadmap, capability-catalog, device-matrix, privacy,
  and acceptance references where needed;
- issue #4 and #5 updates;
- one draft PR retained until physical evidence is imported and reviewed.

## Exit boundary for this PR

The tooling checkpoint is reached when the Windows artifact is reproducibly
built, CI is green, documentation is coherent, and the smallest exact physical
session is ready.

The PR remains draft and unmerged until the Surface Book 3 evidence ZIP is
verified and sanitized results are imported. Issue #4 may close only when its
existing lifecycle/SMTC criteria are satisfied. Issue #5 may close only when
all six exact formats pass on both Android and Windows.
