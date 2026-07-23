# Phase 1 shared-core foundations evidence index

This directory indexes the disposable proof harness for GitHub issues #6
through #10. Evidence was gathered on the VPS on 2026-07-23. No report selects
a production language, application framework, schema, persistence layer,
filesystem policy, synchronization implementation, or hash algorithm.

| Issue | Report | Current evidence level | Issue disposition |
|---|---|---|---|
| #6 | [Portable paths](issue-6-portable-paths.md) | Proven on VPS; simulated platform inputs; Android and Windows runs pending | Partially evidenced |
| #7 | [Identity sidecars](issue-7-identity-sidecars.md) | Round trips and safety proven on VPS; target filesystem behavior pending | Partially evidenced |
| #8 | [Snapshot recovery](issue-8-snapshot-recovery.md) | Fault model proven on VPS ext-family filesystem; both target filesystems pending | Partially evidenced |
| #9 | [Event merging](issue-9-event-merging.md) | Reference-model criteria proven on VPS with recorded seeds | Fully evidenced as a spike model; production policy gaps remain |
| #10 | [Full-file hashing](issue-10-hashing.md) | VPS measurements recorded; Android and Windows measurements pending | Partially evidenced and open |

The runnable source, fixtures, commands, and safety boundary are in the
[`spikes/shared-core-foundations/`](../../../../spikes/shared-core-foundations/README.md)
workspace. Phase 1 remains active. Issue #11 and the stack-selection ADR remain
pending until all required capability evidence is available.

An [experimental decision note](harness-decision-note.md) explains why CPython
was used for this harness only.

