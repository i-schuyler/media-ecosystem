# Capability spike: exact six-format Windows PB-01 matrix

- **Question:** Do MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, and WAV satisfy every
  required PB-01 playback dimension through the disposable Windows candidate on
  the Microsoft Surface Book 3?
- **Related DoD sections:** Required formats; Playback.
- **Related acceptance ID:** PB-01.
- **Candidate:** the same pinned automatic-`MediaPlayer` candidate documented
  in [TOOLCHAIN.md](../../../../spikes/windows-platform-proof/TOOLCHAIN.md).
- **Current disposition:** **inconclusive — tooling prepared, physical matrix
  not run**.

## Fixture contract

The Windows project does not track another audio corpus. Build and publish copy
the exact existing binaries from:

`spikes/android-platform-proof/app/src/main/assets/fixtures/`

Before playback, both pure C# logic and PowerShell packaging verification
require:

- exactly six active entries and files;
- exact ordered stable IDs `mp3-v0`, `mp3-320`, `flac`, `aac`,
  `ogg-vorbis`, and `wav`;
- matching filenames, sizes, SHA-256 values, expected durations, and
  tolerances;
- matching `SHA256SUMS`;
- no extra media;
- ALAC and AIFF only in the historical-nonrequired declaration.

Missing, modified, or extra data fails closed.

## Required result matrix

No physical rows have run yet:

| Stable fixture ID | Open/prepare | Start | Advance | Seek | Duration | Natural end | Windows disposition |
|---|---|---|---|---|---|---|---|
| `mp3-v0` | not run | not run | not run | not run | not run | not run | `not_run` |
| `mp3-320` | not run | not run | not run | not run | not run | not run | `not_run` |
| `flac` | not run | not run | not run | not run | not run | not run | `not_run` |
| `aac` | not run | not run | not run | not run | not run | not run | `not_run` |
| `ogg-vorbis` | not run | not run | not run | not run | not run | not run | `not_run` |
| `wav` | not run | not run | not run | not run | not run | not run | `not_run` |

PB-01 Windows passes only if every row passes every required dimension.
Optional title/artist/album extraction and candidate-reported properties are
separate observations. Missing WAV metadata cannot fail the row.

## Runner behavior

The matrix creates a fresh `MediaPlayer` per verified fixture and records:

- stable ID, verified filename/size/SHA-256;
- open attempt and source acceptance;
- opened/prepared and playback start;
- position advancement;
- seek request, completion, and tolerance;
- expected/actual duration and tolerance result;
- natural end;
- optional file-extracted metadata;
- candidate-reported seek/duration/track properties;
- warnings, errors, timeouts, monotonic elapsed duration, and disposition.

Open, start, advancement, seek, and end waits are finite. A failed decoder is
disposed, recorded, and followed by the next stable fixture. Partial results
are checkpointed, remain exportable, and are not silently converted to passes.
The matrix never applies SMTC display metadata.

## Tooling result and pending evidence

The canonical corpus verifies and the runner compiles with the pinned Windows
SDK. Deterministic tests cover exact membership, ALAC/AIFF exclusion,
fail-closed mutation/extra-file behavior, PB-01 aggregation, WAV without
optional metadata, and continuation after a failed decoder.

These are tooling results. The smallest remaining work is the one six-format
run in [PHYSICAL_SESSION_PROTOCOL.md](PHYSICAL_SESSION_PROTOCOL.md), followed
by independent raw-ZIP verification and a reviewed sanitized report. Issue #5
stays open until the existing Android evidence and all six Windows rows pass.
