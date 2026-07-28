# Android targeted physical retest — 2026-07-28

This additive evidence note connects the preserved 2026-07-24 physical run with
the successful 2026-07-28 targeted retest. It does not replace or rewrite the
earlier report.

## Evidence artifacts

- Historical sanitized report:
  [`evidence/android-2026-07-24-sanitized.json`](evidence/android-2026-07-24-sanitized.json)
- Targeted sanitized report:
  [`evidence/android-2026-07-28-targeted-retest-sanitized.json`](evidence/android-2026-07-28-targeted-retest-sanitized.json)
- Raw targeted ZIP: preserved unchanged outside Git
- Raw targeted ZIP SHA-256:
  `593b28964854248ccb0b5177d6b27c2c26c43429b08ca3fdd8cecfc437c9d6d2`
- Source commit:
  `295849d09f2b709728c4aff980f7b7eac3d9eba5`

The targeted archive passed the exact seven-member allowlist, all internal
checksums, fixture-manifest and build-metadata checks, schema 1.1 validation,
and the privacy boundary.

## Screen-off playback

The guided screen-off workflow completed with continuous playback for
`888433` ms against a required minimum of `300000` ms. The independent
monotonic interval is authoritative; the retained cross-boot session aggregate
is zero because Android elapsed-realtime resets across reboot.

The targeted workflow did not repeat system controls. The earlier physical run
already acknowledged notification play/pause, lock-screen play/pause and
metadata, hardware media-button behavior, audio-focus interruption, and
becoming-noisy behavior.

## Android required formats

The preserved 2026-07-24 report establishes Android passes for MP3 V0, MP3 320,
FLAC, AAC, and Ogg Vorbis. The targeted retest establishes WAV open, prepare,
playback start, position advancement, seek, duration tolerance, and
end-of-track, with final disposition `passed`.

Optional WAV metadata did not match and is explicitly not required by PB-01.

Combined Android result: all six amended v1 required formats pass.

ALAC and AIFF remain parseable nonrequired historical observations. Retained
AIFF parser errors are dated 2026-07-24 and are not targeted-retest failures.

## Issue disposition

- Issue #2 remains open; this retest adds no unavailable/removal/relink evidence.
- Issue #3's existing background/screen-off and system-control criteria are
  satisfied by the combined physical evidence.
- Issue #5 remains open because all six Windows format results are still
  required.

Media3 remains a disposable Phase 1 candidate. No production playback engine or
architecture has been selected.
