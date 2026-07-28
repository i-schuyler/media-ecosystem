# Phase 1 Android platform proof evidence index

This directory indexes the disposable application prepared for GitHub issues
#2, #3, and the Android half of #5.

| Issue | Report | Current evidence level | Issue disposition |
|---|---|---|---|
| #2 | [Persisted removable-storage access](issue-2-saf-storage.md) | Persisted read/write permission and marker access verified through reboot; removal/reinsertion and relink unperformed | Open |
| #3 | [Background playback and system controls](issue-3-background-playback.md) | System-control/interruption acknowledgements verified; screen-off observed for 137 ms, below the required 300,000 ms | Open; targeted screen-off retest required |
| #5 | [Android required-format matrix](issue-5-android-formats.md) | Five of six active v1 formats passed on Android; corrected WAV retest and all six Windows checks remain | Open |

The runnable source and complete proof boundary are in
[`spikes/android-platform-proof/`](../../../../spikes/android-platform-proof/README.md).
The candidate is AndroidX Media3 1.10.1 for comparison only. Nothing here
selects a production stack, application language, framework, playback engine,
storage abstraction, or UI.

The unchanged raw evidence ZIP is ignored. The reproducible
[sanitized report](evidence/android-2026-07-24-sanitized.json) records:

- whole-ZIP SHA-256
  `882dd5f54d79094021b1228c92ec08e3797c341fc995b877deb8ccd4f24069e5`;
- exact member allowlist, internal checksum, fixture-manifest, build/source,
  corrected schema-compatibility, and privacy verification;
- the measured storage, playback, and per-format observations; and
- unperformed steps and remaining gaps without inference.

The active v1 format contract was amended on 2026-07-28 to exactly MP3 V0,
MP3 320, FLAC, AAC, Ogg Vorbis, and WAV. ALAC and AIFF remain in the historical
eight-format report as `nonrequired_historical_observation`; their observations
were not rewritten or erased.

The retest APK narrows the physical follow-up to five-minute screen-off
playback, WAV, and evidence export. It does not require SD removal, another
complete format run, or repeated media-control observations.
