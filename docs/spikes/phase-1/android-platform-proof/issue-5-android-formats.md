# Capability spike: Android required-format matrix

- **Question being tested:** Can the same disposable Android playback
  candidate open, prepare, play, advance, seek, report duration, reach
  end-of-track, and expose basic metadata for all eight required Bandcamp
  formats on the primary Android device?
- **Related DoD sections:** Required formats; Playback.
- **Related acceptance IDs:** PB-01.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G, Android
  16. Exact runtime build/model/architecture and decoder results are pending.
- **Candidate approach:** AndroidX Media3 ExoPlayer 1.10.1 against eight
  deterministic bundled synthetic fixtures.
- **Preconditions:** Installed proof APK and sufficient time for the bounded
  automated runner.

## Reproduction

The **Run all 8 formats with bounded timeouts** action runs MP3 V0, MP3 320,
FLAC, AAC-LC in M4A, Ogg Vorbis, ALAC in M4A, WAV, and AIFF sequentially. It
initializes every result as `not run`, records each independent disposition,
and continues after a failure where safe. The fixture generator, manifest,
hashes, and exact arguments are in the
[proof workspace](../../../../spikes/android-platform-proof/README.md).

## Criteria

- **Success:** Every Android fixture records successful open/prepare/start,
  position advancement, bounded seek completion, duration within tolerance,
  extracted metadata match, and end-of-track.
- **Failure:** Any required check fails or errors. A hang becomes a bounded
  failed or inconclusive result and must not skip later formats.
- **Required measurements:** Manifest entry/hash, expected duration and
  metadata, MIME/container/codec, candidate track format, exposed decoder,
  all operation results, warning/error, monotonic test duration, and final
  disposition for every fixture.

## Fixture corpus

All fixtures derive from the same 6-second integer-generated stereo PCM source.
The corpus totals 3,336,159 bytes. MP3 modes are distinct LAME encodings; AAC
and ALAC containers are explicit. The repository guardrail allows only the
exact manifest-covered directory and enforces hashes, provenance, membership,
and strict size limits.

## Results and measurements

| Required format | Android disposition |
|---|---|
| MP3 V0 | Not run |
| MP3 320 | Not run |
| FLAC | Not run |
| AAC | Not run |
| Ogg Vorbis | Not run |
| ALAC | Not run |
| WAV | Not run |
| AIFF | Not run |

- **Tooling:** Ready. Fixture verification, bounded runner, decoder/format
  diagnostics, independent dispositions, ZIP export, host unit tests, lint,
  and APK packaging are implemented.
- **Physical results:** **Pending.** The table is not prefilled from codec
  documentation or JVM tests.
- **Issue #5:** Must remain open after Android evidence because the same
  required matrix still must pass or receive explicit dispositions on Windows.
- **PB-01:** Unchanged and not satisfied by build success.

## Limitations, security, and privacy

- Candidate-reported decoder information is recorded only when Media3 exposes
  it; absence is explicit.
- Fixture metadata support may vary by container/decoder and is evaluated, not
  assumed.
- The app uses no personal media or network input. Evidence normally exports
  hashes/manifests, not audio binaries.

## Production suitability and disposition

- **Production suitability:** Not established; one candidate and one device
  half are insufficient for selection.
- **Disposition:** **inconclusive** until Android physical results arrive.
- **Required follow-up:** Verify and sanitize Android evidence, then complete
  the Windows half without weakening PB-01 before evaluating issue #5.
