# Phase 1 Android platform proof evidence index

This directory indexes the disposable application prepared for GitHub issues
#2, #3, and the Android half of #5.

| Issue | Report | Current evidence level | Issue disposition |
|---|---|---|---|
| #2 | [Persisted removable-storage access](issue-2-saf-storage.md) | Persisted read/write permission and marker access verified through reboot; removal/reinsertion and relink unperformed | Open |
| #3 | [Background playback and system controls](issue-3-background-playback.md) | System-control/interruption acknowledgements plus 888,433 ms of continuous screen-off playback verified | Closed; criteria satisfied for this disposable candidate |
| #5 | [Android required-format matrix](issue-5-android-formats.md) | All six active v1 formats pass on Android; all six Windows checks remain | Open |

The runnable source and complete proof boundary are in
[`spikes/android-platform-proof/`](../../../../spikes/android-platform-proof/README.md).
The candidate is AndroidX Media3 1.10.1 for comparison only. Nothing here
selects a production stack, application language, framework, playback engine,
storage abstraction, or UI.

Raw evidence ZIPs are unchanged, ignored, and outside Git. The
[physical-evidence index](evidence/README.md) links both reproducible sanitized
reports:

- the 2026-07-24 initial session, which established persisted SAF access,
  acknowledged Android control/interruption dimensions, and passed MP3 V0,
  MP3 320, FLAC, AAC, and Ogg Vorbis; and
- the 2026-07-28 targeted session, which completed 888,433 ms of continuous
  screen-off playback and passed corrected WAV end-of-track evidence.

Both archives passed their exact member allowlists, internal checksums,
fixture-manifest and build/source validation, schema validation, and privacy
boundaries. Unperformed steps and remaining gaps are recorded without
inference.

The active v1 format contract was amended on 2026-07-28 to exactly MP3 V0,
MP3 320, FLAC, AAC, Ogg Vorbis, and WAV. ALAC and AIFF remain in the historical
eight-format report as `nonrequired_historical_observation`; their observations
were not rewritten or erased.

Android background playback and the Android six-format matrix are complete for
this candidate. Remaining related work is issue #2's unavailable/removal/relink
evidence and issue #5's exact six-format Windows matrix.
