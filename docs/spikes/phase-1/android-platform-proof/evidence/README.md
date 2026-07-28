# Android physical evidence index

Raw physical evidence archives remain unchanged, ignored, and outside Git.
Only sanitized reports and explanatory links are tracked here.

## 2026-07-24 initial physical run

- Sanitized report:
  [`android-2026-07-24-sanitized.json`](android-2026-07-24-sanitized.json)
- Raw ZIP SHA-256:
  `882dd5f54d79094021b1228c92ec08e3797c341fc995b877deb8ccd4f24069e5`
- Source commit:
  `a5fa2f657d63b5e89491562456744513266ba861`
- Established persisted SAF permission and marker access after reboot,
  acknowledged Android system-control dimensions, and Android passes for MP3
  V0, MP3 320, FLAC, AAC, and Ogg Vorbis.
- Left the five-minute screen-off interval and corrected WAV end-of-track
  evidence incomplete.
- Preserved ALAC and AIFF as nonrequired historical observations after the
  2026-07-28 product-contract amendment.

## 2026-07-28 targeted retest

- Sanitized report:
  [`android-2026-07-28-targeted-retest-sanitized.json`](android-2026-07-28-targeted-retest-sanitized.json)
- Combined evidence note:
  [`../TARGETED_RETEST_2026-07-28.md`](../TARGETED_RETEST_2026-07-28.md)
- Raw ZIP SHA-256:
  `593b28964854248ccb0b5177d6b27c2c26c43429b08ca3fdd8cecfc437c9d6d2`
- Source commit:
  `295849d09f2b709728c4aff980f7b7eac3d9eba5`
- Completed continuous screen-off playback for `888433` ms against the
  required `300000` ms minimum.
- Passed WAV open, prepare, play, advancement, seek, duration tolerance, and
  end-of-track under PB-01; optional metadata remained a separate nonrequired
  dimension.

## Combined disposition

- All six amended v1 required formats pass on Android.
- Android background/screen-off playback and required system-control evidence
  satisfy issue #3 for this disposable candidate.
- Windows validation remains required for issue #5.
- Storage unavailable/removal/relink gaps remain open in issue #2.
- Media3 remains a disposable Phase 1 candidate and is not a production
  architecture selection.
