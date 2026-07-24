# Disposable Android Phase 1 platform proof

This directory contains a deliberately disposable Android application for
GitHub issues #2, #3, and the Android half of #5. It uses synthetic data only.
It does not establish the production framework, language, playback engine,
user interface, catalog, queue, storage abstraction, package structure, or
application identity.

The diagnostic application is named **Disposable Phase 1 Platform Proof** and
the installable debug application ID is
`org.mediaecosystem.experimental.phase1platformproof.debug`.

## Proof areas

The single scrolling diagnostic screen keeps five areas visibly separate:

1. **Storage access** launches the system directory-tree picker, takes and
   records a persistable read/write grant, creates only
   `Media-Ecosystem-Phase1-Proof-v1`, validates its versioned marker, guides
   restart/reboot/removal/reinsertion/relink/revocation observations, and never
   scans siblings.
2. **Playback lifecycle** controls a foreground `MediaSessionService` backed
   by the disposable Media3 candidate, records monotonic screen-off time, and
   exposes explicit acknowledgements for system-control observations.
3. **Format matrix** runs all eight fixtures independently with bounded
   prepare, start, seek, and end timeouts.
4. **Evidence** writes one validated, sanitized ZIP through Android's document
   creation flow.
5. **Cleanup** separately removes only a marker-validated SD proof directory,
   temporary export ZIPs, or app-internal evidence.

Build success is tooling evidence only. SAF persistence, background playback,
system controls, and decoder behavior require the physical primary tablet.

## Toolchain and build

Exact versions, checksums, official sources, and selection rationale are in
[TOOLCHAIN.md](TOOLCHAIN.md). The committed wrapper is the supported Gradle
entry point. From a clean checkout with Java and SDK paths supplied explicitly:

```sh
export JAVA_HOME=/absolute/path/to/temurin-17
export ANDROID_HOME=/absolute/path/to/android-sdk
export GRADLE_USER_HOME=/absolute/path/to/disposable-gradle-cache
export SOURCE_COMMIT="$(git rev-parse HEAD)"
./gradlew --no-daemon --no-configuration-cache \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

No signing secret is needed. Android's ordinary generated debug key signs the
installable debug APK; no keystore is tracked or exported.

For an ADB-equipped development host, installation is:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The physical handoff uses Android's package installer instead and does not
require ADB.

## Deterministic fixtures

The corpus is generated from a 6,000 ms, stereo, 48,000 Hz, signed 16-bit PCM
integer triangle-tone sequence at 220, 330, and 440 Hz. Left and right samples
have opposite polarity and a peak magnitude of 1,200, keeping the fixture
obviously synthetic and low amplitude. No human recording, copyrighted music,
personal media, or anonymized library data is used.

The exact generator, source versions, arguments, containers, codecs, expected
duration and metadata, byte sizes, source PCM hash, and fixture SHA-256 values
are in
[`fixture-manifest.json`](app/src/main/assets/fixtures/fixture-manifest.json).
AAC-LC and ALAC use explicit ISO BMFF/M4A containers. MP3 V0 uses LAME
`-V 0 --vbr-new`; MP3 320 uses distinct `-b 320 --cbr` settings.

Generation and reproducibility verification:

```sh
python3 scripts/generate_fixtures.py \
  --ffmpeg /absolute/path/to/ffmpeg-8.1.2 \
  --lame /absolute/path/to/lame-4.0 \
  --output app/src/main/assets/fixtures

python3 scripts/generate_fixtures.py \
  --ffmpeg /absolute/path/to/ffmpeg-8.1.2 \
  --lame /absolute/path/to/lame-4.0 \
  --output app/src/main/assets/fixtures \
  --verify
```

`scripts/verify_fixtures.py` rejects missing or extra corpus files, missing
formats, provenance gaps, hash or size mismatches, fixtures above 2 MB, and a
corpus above 8 MB. It runs before every Android build and from the foundation
guardrail. The narrow repository exception is documented in the
[privacy and fixture policy](../../docs/privacy/PRIVACY_AND_FIXTURE_POLICY.md).

## SAF state and safety model

The explicit states are:

- never selected;
- permission granted;
- accessible;
- process restarted and still accessible;
- rebooted and still accessible;
- temporarily unavailable;
- permission revoked;
- volume reinserted;
- explicit relink required;
- explicit relink succeeded; and
- cleanup complete.

The raw root URI, document URIs, and proof-session UUID stay in private
application state. Export uses a 24-character non-reversible root token,
permission flags, provider-neutral state, and sanitized equality observations.
Display name, capacity, path, and volume identifier are never identity inputs.

An unavailable provider changes the proof state to unavailable, retains the
remembered state, records that no deletion intent was generated, and does not
attempt cleanup. Explicit relink always opens a user picker and accepts only a
directory containing the valid versioned marker for the existing private proof
session.

Cleanup refuses a selected root, an unknown directory, missing ownership
state, duplicate or malformed markers, and a marker for another session. It
passes exactly the isolated proof-directory document to the provider; it never
recursively deletes the selected root or enumerates siblings.

## Playback and format candidate

The one disposable candidate is AndroidX Media3 ExoPlayer plus
`MediaSessionService` 1.10.1. The service supplies the foreground media
notification and system session, automatic audio-focus handling,
becoming-noisy handling, wake mode, repeat-all synthetic playback, metadata,
play/pause/seek/previous/next/stop, and structured state observations.

The screen-off threshold is five minutes, measured with
`SystemClock.elapsedRealtime()`. The threshold is long enough to expose a
simple lifecycle stop while keeping the manual session bounded. A shorter
observation is recorded as not meeting the duration; the application never
fills it in as passed.

The format runner uses a separate instance of the same candidate. Each fixture
records manifest identity/hash, expected and actual duration, MIME/container,
candidate track format, exposed decoder name, open/prepare/start/position
advancement/seek/end results, extracted basic metadata, warnings/errors,
monotonic total time, and one of `passed`, `failed`, `inconclusive`, or
`not run`. A failure advances to the next fixture. Timeouts are finite so one
decoder cannot stall the matrix.

Trying this candidate does not select it. The later Phase 1 architecture ADR
must compare it with all other completed or unresolved evidence.

## Evidence ZIP and privacy

The versioned primary schema is
[`evidence-schema-v1.json`](app/src/main/assets/evidence/evidence-schema-v1.json).
One **Export evidence ZIP** action recommends a filename beginning
`media-ecosystem-android-proof-` and writes:

- `evidence.json`;
- `summary.md`;
- `fixture-manifest.json`;
- `fixture-SHA256SUMS`;
- `build-metadata.json`;
- `diagnostic.log`; and
- `CHECKSUMS.sha256`.

The app validates the in-memory entry contract and every entry hash, writes the
ZIP through the chosen Android provider, then reopens and hashes the saved ZIP
before reporting success. Audio binaries are not exported.

Exported evidence includes app/source/build/dependency versions; sanitized
Android environment; fixture-manifest hash; app-generated wall times and
monotonic duration; SAF state, permission flags, and no-deletion assertions;
playback observations; all format dispositions; acknowledged physical
actions; errors; and cleanup state.

It excludes raw document URIs, removable-volume identifiers, account or Wi-Fi
data, installed-app lists, personal filenames and paths, library contents,
serial numbers, advertising IDs, credentials, and authentication material.

## Physical protocol

The app itself labels and records each unavoidable action. On the Samsung
Galaxy Tab S10 FE 5G:

1. install/open and grant notification permission if requested;
2. select the removable SD root and run the immediate and guided process-stop
   check;
3. reboot normally and reopen;
4. prepare safe removal in the app, use Android's eject/unmount control,
   remove/reinsert the card, and recheck access;
5. run the guided revocation and explicit marker-validated relink check;
6. run the five-minute screen-off playback sequence and exercise notification,
   lock-screen, hardware/Bluetooth (when available), interruption, and
   becoming-noisy controls;
7. run all eight format checks;
8. export the ZIP; and
9. upload that ZIP unchanged for host verification and sanitization.

The host-side reports remain pending until the exported physical evidence is
received. Uninstalling this application removes application-private state; it
is not described as deleting user media, and its isolated SD proof directory
has a separate explicit cleanup control.
