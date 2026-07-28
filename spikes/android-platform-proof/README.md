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
3. **Format matrix** uses stable fixture IDs and packages the six active v1
   formats. The current handoff exposes a WAV-only targeted retest with bounded
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
entry point. In a dedicated Heartloom VPS session, from a clean checkout with
Java and SDK paths supplied explicitly:

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
AAC-LC uses an explicit ISO BMFF/M4A container. MP3 V0 uses LAME
`-V 0 --vbr-new`; MP3 320 uses distinct `-b 320 --cbr` settings.

The active v1 corpus is exactly MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, and
WAV. The original 2026-07-24 physical run also contained ALAC and AIFF. Those
two observations remain preserved in the sanitized historical report but the
files are not active v1 fixtures or packaged in the retest APK.

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

`scripts/verify_fixtures.py` rejects missing or extra corpus files, a count
other than exactly six active formats, active ALAC/AIFF entries, provenance
gaps, hash or size mismatches, fixtures above 2 MB, and a corpus above 8 MB. It
runs before every Android build and from the foundation guardrail. The narrow
repository exception is documented in the
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
`SystemClock.elapsedRealtime()`. The guided workflow now distinguishes test
started, playback ready, screen-off playback active, minimum reached, controls
exercised, and test completed. Completion is refused until continuous playback
has reached 300,000 ms. The earlier 137 ms off/on event therefore remains
incomplete rather than appearing equivalent to the required interval.

The format runner uses a separate instance of the same candidate. Each fixture
records manifest identity/hash, expected and actual duration, MIME/container,
candidate track format, exposed decoder name, open/prepare/start/position
advancement/seek/end results, extracted basic metadata, warnings/errors,
monotonic total time, and one of `passed`, `failed`, `inconclusive`, or
`not run`. PB-01 disposition requires open/prepare/start, position advancement,
seek, duration tolerance, and end-of-track. Metadata remains a separate
optional dimension because WAV does not reliably carry the synthetic tag set.
A failure advances to the next targeted fixture. Timeouts are finite so one
decoder cannot stall the matrix.

Trying this candidate does not select it. The later Phase 1 architecture ADR
must compare it with all other completed or unresolved evidence.

## Evidence ZIP and privacy

The corrected historical schema is
[`evidence-schema-v1.json`](app/src/main/assets/evidence/evidence-schema-v1.json);
the retest app emits schema
[`1.1`](app/src/main/assets/evidence/evidence-schema-v1.1.json).
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
ZIP through the user-selected Android provider, then reopens and hashes the
saved ZIP before reporting success. It displays the returned filename and a
sanitized provider category, explicitly says that no raw URI is exported, and
can open Android's share sheet for the last validated saved ZIP. This avoids
assuming that a provider exposes the file through Termux's Downloads
projection. Audio binaries are not exported.

Exported evidence includes app/source/build/dependency versions; sanitized
Android environment; fixture-manifest hash; app-generated wall times and
monotonic duration; SAF state, permission flags, and no-deletion assertions;
playback observations; all format dispositions; acknowledged physical
actions; errors; and cleanup state.

It excludes raw document URIs, removable-volume identifiers, account or Wi-Fi
data, installed-app lists, personal filenames and paths, library contents,
serial numbers, advertising IDs, credentials, and authentication material.

## Verified physical evidence and targeted retest

The unchanged raw 2026-07-24 archive is ignored. Its whole-ZIP SHA-256 is
`882dd5f54d79094021b1228c92ec08e3797c341fc995b877deb8ccd4f24069e5`.
The reproducible sanitized report is
[`android-2026-07-24-sanitized.json`](../../docs/spikes/phase-1/android-platform-proof/evidence/android-2026-07-24-sanitized.json).

That run verified persisted SAF read/write permission and marker access after
reboot; acknowledged notification, lock-screen, hardware-button, audio-focus,
and becoming-noisy observations; and passed MP3 V0, MP3 320, FLAC, AAC, and
Ogg Vorbis. It did not complete the five-minute screen-off interval. WAV needs
a corrected end-of-track retest. Removal/reinsertion, revocation, and explicit
relink were not performed.

The smallest follow-up on the same Samsung tablet is:

1. install/update and open the new debug APK;
2. start the five-minute screen-off retest, wait for
   `READY_TO_TURN_SCREEN_OFF`, turn the screen off for at least five minutes,
   return, verify `MINIMUM_REACHED`, and tap Complete;
3. run the targeted WAV check;
4. export the ZIP to any user-selected provider and either locate the displayed
   filename/provider category or use **Share last saved evidence ZIP**.

Do not repeat the storage sequence, the five already-valid required formats,
or the already-acknowledged media controls. The tablet's shared SIM/microSD
tray is effectively permanent in ordinary use, so this slice does not ask for
physical removal. Future storage evidence may use a safe Android unmount/eject,
persisted-permission revocation, provider-unavailability simulation, or a
secondary device. Missing or revoked access still never means deletion.

Uninstalling this application removes application-private state; it is not
described as deleting user media, and its isolated SD proof directory has a
separate explicit cleanup control.
