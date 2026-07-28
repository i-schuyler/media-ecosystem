# Capability spike: Android required-format matrix

- **Question being tested:** Can the same disposable Android playback
  candidate open, prepare, play, advance, seek, report duration, reach
  end-of-track, and separately report optional metadata for all six active v1
  formats on the primary Android device?
- **Related DoD sections:** Required formats; Playback.
- **Related acceptance IDs:** PB-01.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G, Android
  16 build `BP4A.251205.006.X528USQU9CZE9`, model `samsung SM-X528U`,
  `arm64-v8a`.
- **Candidate approach:** AndroidX Media3 ExoPlayer 1.10.1 against six
  deterministic bundled synthetic fixtures.
- **Preconditions:** Installed proof APK and sufficient time for the bounded
  automated runner.

## Reproduction

The active corpus and runner use stable IDs for MP3 V0, MP3 320, FLAC, AAC-LC
in M4A, Ogg Vorbis, and WAV. The hardened app exposes a **Run targeted WAV
check with bounded timeouts** action so the five already-valid Android formats
need not be repeated. It initializes all six active results as `not run` and
updates WAV by ID, not positional index. The fixture generator, manifest,
hashes, and exact arguments are in the
[proof workspace](../../../../spikes/android-platform-proof/README.md).

The combined disposition preserves the five successful 2026-07-24 format
observations and adds the corrected 2026-07-28 WAV result. Targeted `not run`
rows do not erase prior verified passes.

## Criteria

- **Success:** Every Android fixture records successful open/prepare/start,
  position advancement, bounded seek completion, duration within tolerance,
  and end-of-track. Optional metadata is recorded independently and does not
  fail PB-01.
- **Failure:** Any required check fails or errors. A hang becomes a bounded
  failed or inconclusive result and must not skip later formats.
- **Required measurements:** Manifest entry/hash, expected duration and
  metadata, MIME/container/codec, candidate track format, exposed decoder,
  all operation results, warning/error, monotonic test duration, and final
  disposition for every fixture.

## Fixture corpus

All fixtures derive from the same 6-second integer-generated stereo PCM source.
The six-file corpus totals 1,948,811 bytes, uses distinct LAME MP3 modes, and
uses an explicit AAC M4A container. The repository guardrail allows only the
exact manifest-covered directory and enforces the active count, stable IDs,
hashes, provenance, membership, and strict size limits.

## Results and measurements

| Required format | Android disposition |
|---|---|
| MP3 V0 | Passed |
| MP3 320 | Passed |
| FLAC | Passed |
| AAC | Passed |
| Ogg Vorbis | Passed |
| WAV | Passed in corrected targeted retest |

- **Initial archive:** The ignored 2026-07-24 raw ZIP SHA-256 is
  `882dd5f54d79094021b1228c92ec08e3797c341fc995b877deb8ccd4f24069e5`.
  Its allowlist, internal hashes, historical manifest, build/source, corrected
  v1 compatibility schema, and privacy checks passed. See the
  [initial sanitized report](evidence/android-2026-07-24-sanitized.json).
- **Targeted archive:** The ignored 2026-07-28 raw ZIP SHA-256 is
  `593b28964854248ccb0b5177d6b27c2c26c43429b08ca3fdd8cecfc437c9d6d2`.
  Its exact seven-member allowlist, internal checksums, fixture manifest,
  build metadata, source commit, schema 1.1, and privacy boundary passed. See
  the [targeted sanitized report](evidence/android-2026-07-28-targeted-retest-sanitized.json).
- **Initial physical results:** MP3 V0, MP3 320, FLAC, AAC, and Ogg Vorbis
  passed the complete original checks.
- **Corrected WAV result:** WAV opened, prepared, started playback, advanced,
  completed a bounded seek, reported 6,000 ms within tolerance, and reached
  end-of-track. Optional metadata remained false and is explicitly not a PB-01
  requirement.
- **Combined Android result:** All six amended v1 required formats pass on the
  primary Android validation device.
- **Historical nonrequired observations:** ALAC and AIFF remain in the
  2026-07-24 sanitized report as `nonrequired_historical_observation`. ALAC
  opened/played/advanced/sought/reported duration but failed the original
  aggregate criteria; AIFF failed container parsing. Neither is an active v1
  requirement after the 2026-07-28 product-priority amendment.
- **Issue #5:** Remains open because the same six required formats still need
  Windows validation.
- **PB-01:** Android is complete under the exact six-format v1 contract;
  cross-platform PB-01 remains open until Windows evidence is complete. The
  amendment does not convert the original eight-format run into a pass.

## Limitations, security, and privacy

- Candidate-reported decoder information is recorded only when Media3 exposes
  it; absence is explicit.
- Fixture metadata support may vary by container/decoder and is recorded
  separately; PB-01 does not require optional WAV tags.
- The app uses no personal media or network input. Evidence exports
  hashes/manifests and structured observations, not audio binaries.
- One Android candidate and one device do not establish production suitability
  or broad device compatibility.

## Production suitability and disposition

- **Production suitability:** Not established; the Android half is complete but
  cross-platform evidence and candidate comparison remain.
- **Disposition:** **retain for comparison**; the Android half of issue #5 is
  complete and all six Windows checks remain.
- **Required follow-up:** Complete the exact six-format Windows matrix without
  weakening PB-01, then use the combined platform evidence in the later
  stack-selection ADR.
