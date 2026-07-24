# Phase 1 Android platform proof evidence index

This directory indexes the disposable application prepared for GitHub issues
#2, #3, and the Android half of #5.

| Issue | Report | Current evidence level | Issue disposition |
|---|---|---|---|
| #2 | [Persisted removable-storage access](issue-2-saf-storage.md) | Tooling and guided evidence collection ready; physical lifecycle evidence pending | Open; no exit criteria satisfied by build success |
| #3 | [Background playback and system controls](issue-3-background-playback.md) | Tooling and guided evidence collection ready; physical lifecycle/control evidence pending | Open; no exit criteria satisfied by build success |
| #5 | [Android required-format matrix](issue-5-android-formats.md) | Eight-format harness ready; Android physical decoder evidence pending, and Windows remains independently required | Open |

The runnable source and complete proof boundary are in
[`spikes/android-platform-proof/`](../../../../spikes/android-platform-proof/README.md).
The candidate is AndroidX Media3 1.10.1 for comparison only. Nothing here
selects a production stack, application language, framework, playback engine,
storage abstraction, or UI.

At the tooling checkpoint:

- deterministic synthetic fixtures are committed and verified;
- host-side unit tests, lint, APK assembly, packaging checks, and CI are
  required to pass;
- the evidence schema, ZIP exporter, safe cleanup, and device protocol are
  implemented; and
- every device result remains `not run` or pending until the Samsung Galaxy
  Tab S10 FE 5G exports a validated evidence ZIP.

The exported raw ZIP must remain ignored and unchanged. After it is received,
the host workflow verifies its checksums, produces sanitized reviewable
results, updates these reports, and evaluates existing issue exit criteria
without weakening them.
