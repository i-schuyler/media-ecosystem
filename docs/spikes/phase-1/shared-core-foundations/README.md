# Phase 1 shared-core foundations evidence index

This directory indexes the disposable proof harness for GitHub issues #6
through #10. Evidence was gathered on the VPS and on the primary Android
device's Termux-private internal storage and Android-exposed portable-SD FUSE
mount on 2026-07-23 UTC. No report selects a production language, application
framework, schema, persistence layer, filesystem policy, synchronization
implementation, storage API, or hash algorithm.

| Issue | Report | Current evidence level | Issue disposition |
|---|---|---|---|
| #6 | [Portable paths](issue-6-portable-paths.md) | VPS model plus Android harness and FUSE case/path observations; Windows pending | Partially evidenced and open |
| #7 | [Identity sidecars](issue-7-identity-sidecars.md) | VPS invariants plus Android internal harness and normal replacement observations; Windows pending | Partially evidenced and open |
| #8 | [Snapshot recovery](issue-8-snapshot-recovery.md) | VPS fault model plus Android normal replacement/directory-sync observations; crash durability and Windows pending | Partially evidenced and open |
| #9 | [Event merging](issue-9-event-merging.md) | Reference-model exit criteria evidenced in merged PR #13 | Closed as fully evidenced; not production synchronization |
| #10 | [Full-file hashing](issue-10-hashing.md) | VPS plus Android internal and portable-SD measurements; Windows and resource observations pending | Partially evidenced and open |

Android environment reports:

- [Termux-private internal storage](android-internal-storage.md)
- [Portable-SD raw-path storage](android-portable-sd-storage.md)

The runnable source, fixtures, commands, and safety boundary are in the
[`spikes/shared-core-foundations/`](../../../../spikes/shared-core-foundations/README.md)
workspace. Phase 1 remains active. Raw Termux path access is not persisted
app-level Storage Access Framework access. Windows evidence, issue #11, and the
stack-selection ADR remain pending.

An [experimental decision note](harness-decision-note.md) explains why CPython
was used for this harness only.
