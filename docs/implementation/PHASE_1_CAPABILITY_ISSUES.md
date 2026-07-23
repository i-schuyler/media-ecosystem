# Phase 1 Capability Issue Catalog

This catalog is the canonical planning source for Phase 1 proof-of-capability
issues. Dependency entries describe sequencing and evidence relationships;
they are not GitHub-enforced blockers. Every spike follows the
[capability-spike protocol](CAPABILITY_SPIKE_PROTOCOL.md) and the
[supported test-device matrix](SUPPORTED_TEST_DEVICE_MATRIX.md).

## Evidence in progress

The disposable
[shared-core foundations harness](../../spikes/shared-core-foundations/README.md)
and [indexed reports](../spikes/phase-1/shared-core-foundations/README.md) record
the 2026-07-23 VPS and primary-Android-device evidence for GitHub issues #6
through #10 (catalog items 5 through 9 below). Android evidence now covers the
unified reference harness in Termux-private F2FS storage, normal replacement
and case behavior through the portable-SD FUSE mount, and internal/removable
SHA-256 measurements. Windows evidence, app-level Android SAF persistence,
active removal/power-loss durability, and remaining resource observations are
still pending. Raw Termux path access is not SAF access. The event
reference-model proof is fully evidenced and issue #9 is closed, but it is not
a production synchronization design. Python 3.14.6 compatibility on Android is
experimental evidence, not a production runtime requirement. Phase 1 remains
active, and catalog item 10 / GitHub issue #11 remains pending.

## 1. Prove Android SD-card root access and persisted permission.

- **Purpose:** Establish that the Android validation device can safely retain
  user-granted access to a selected removable-media root.
- **In scope:** Root selection, persisted permission, process restart, device
  reboot, card removal/reinsertion, unavailable-state handling, and relink
  observations.
- **Out of scope:** Production file management, library scanning, destructive
  operations, and choosing the application framework.
- **Relevant DoD sections:** Identity and paths; Availability; Restore and
  migration.
- **Relevant acceptance IDs:** FS-01, FS-02.
- **Dependencies:** None.
- **Required evidence:** Exact Android environment, APIs tried, reproduction
  commands/steps, permission state across lifecycle events, and sanitized logs.
- **Exit criteria:** Persisted access works across restart and reboot; removal
  is reported as unavailable rather than deleted; reinsertion or explicit
  relink restores access without unsafe inference.
- **Expected ADR or follow-up artifact:** Storage-access evidence report and a
  storage API input to the later stack-selection ADR.

## 2. Prove Android background playback and system media controls.

- **Purpose:** Prove the Android playback lifecycle and required system-control
  integration on the primary Android validation device.
- **In scope:** Background and screen-off playback, lock-screen metadata and
  controls, media buttons, interruption behavior, app restart observations,
  and sanitized lifecycle diagnostics.
- **Out of scope:** Production UI, queue implementation, final playback-engine
  selection, and complete format certification.
- **Relevant DoD sections:** Playback; Persistent queue.
- **Relevant acceptance IDs:** PB-02.
- **Dependencies:** Tiny redistributable playback fixture availability; may run
  in parallel with the Android storage-access proof.
- **Required evidence:** Exact environment and candidate versions, lifecycle
  steps, control-event results, duration of background/screen-off runs, and
  failure observations.
- **Exit criteria:** Playback remains active as required and system controls
  reliably operate the candidate during the tested lifecycle transitions.
- **Expected ADR or follow-up artifact:** Android playback evidence report and
  candidate comparison input for the stack-selection ADR.

## 3. Prove Windows playback and system media controls.

- **Purpose:** Prove playback lifecycle behavior and Windows system-media
  integration on the primary Windows validation device.
- **In scope:** Playback, Windows media controls and metadata, sleep/wake,
  process restart observations, and sanitized diagnostics.
- **Out of scope:** Production UI, installer design, final playback-engine
  selection, and complete format certification.
- **Relevant DoD sections:** Playback; Persistent queue.
- **Relevant acceptance IDs:** PB-05; PB-01 is exercised by the cross-platform
  format proof.
- **Dependencies:** Tiny redistributable playback fixture availability.
- **Required evidence:** Exact Windows environment, candidate versions,
  reproduction steps, system-control events, sleep/wake results, and failures.
- **Exit criteria:** The candidate plays reliably and exposes working Windows
  media controls through the required tested lifecycle transitions.
- **Expected ADR or follow-up artifact:** Windows playback evidence report and
  candidate comparison input for the stack-selection ADR.

## 4. Validate all required Bandcamp formats across Android and Windows.

- **Purpose:** Verify that every v1 required audio format plays on both primary
  validation devices.
- **In scope:** MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, ALAC, WAV, and AIFF;
  open, play, seek, duration, end-of-track, and basic metadata observations.
- **Out of scope:** Codec implementation, transcoding, streaming services,
  gapless playback, ReplayGain, and unlisted formats.
- **Relevant DoD sections:** Required formats; Playback.
- **Relevant acceptance IDs:** PB-01.
- **Dependencies:** Android and Windows playback harnesses from issues 2 and 3;
  tiny generated or clearly redistributable fixtures for every format.
- **Required evidence:** Fixture provenance and hashes, exact environments,
  per-format result table, reproduction steps, measurements, and failures.
- **Exit criteria:** Every required format passes the documented checks on both
  devices, or the issue records a failed/inconclusive disposition without
  weakening PB-01.
- **Expected ADR or follow-up artifact:** Cross-platform format report and any
  codec/playback limitations for the stack-selection ADR.

## 5. Prove portable root-relative path normalization.

- **Purpose:** Establish deterministic shared paths across Android and Windows
  while keeping absolute roots device-local.
- **In scope:** Separator normalization, Unicode and case observations,
  traversal rejection, reserved names, root containment, round trips, and
  removable/unavailable root representation.
- **Out of scope:** Production catalog schema, automatic rename policy, and
  broad filesystem abstraction design.
- **Relevant DoD sections:** Identity and paths; File operations.
- **Relevant acceptance IDs:** FS-01, FS-02, OP-01.
- **Dependencies:** Android root-access evidence from issue 1 for device checks;
  shared synthetic path corpus.
- **Required evidence:** Path corpus, expected normalized values, commands,
  platform results, rejected unsafe inputs, and known filesystem differences.
- **Exit criteria:** The same valid root-relative representation round-trips on
  both platforms, remains inside its registered root, and never turns missing
  storage into deletion.
- **Expected ADR or follow-up artifact:** Portable-path evidence report and a
  path-model input to the stack-selection or later storage ADR.

## 6. Prove Track ID, Folder ID, and File Instance ID sidecar round trips.

- **Purpose:** Demonstrate that immutable identities survive supported
  file/folder operations and deterministic sidecar serialization.
- **In scope:** ID generation, human-readable sidecar write/read, validation,
  rename/move round trips, multiple physical instances, missing/corrupt
  sidecars, and explicit non-guessing behavior.
- **Out of scope:** Production schema, embedded-tag policy, catalog database,
  automatic reconciliation, and media replacement workflow.
- **Relevant DoD sections:** Identity and paths; Human-readable and AI-friendly
  interfaces.
- **Relevant acceptance IDs:** ID-01, ID-02, ID-03, PL-01.
- **Dependencies:** Portable path representation from issue 5; synthetic files
  and folders only.
- **Required evidence:** Proposed sidecar samples, schema/version assumptions,
  round-trip results, mutation cases, corruption cases, and deterministic IDs.
- **Exit criteria:** All three ID types round-trip without mutation; moves and
  renames preserve identity; ambiguous or invalid evidence never causes a
  silent assignment.
- **Expected ADR or follow-up artifact:** Identity-sidecar evidence report and
  a follow-up ADR for the selected sidecar/embedded-ID policy when appropriate.

## 7. Prove atomic snapshots, interrupted-write recovery, and rebuild behavior.

- **Purpose:** Prove that human-readable canonical state can survive interrupted
  writes and be rebuilt without identity or event loss.
- **In scope:** Atomic replacement approaches, flush/sync behavior, deliberate
  interruption points, last-valid-state recovery, corruption detection,
  deterministic rebuild, and schema-version observations.
- **Out of scope:** Production database selection, Google Drive integration,
  full migration implementation, and backup scheduling.
- **Relevant DoD sections:** Metadata synchronization; Configuration; Restore
  and migration.
- **Relevant acceptance IDs:** CFG-02, REST-01, REST-02, STAT-03.
- **Dependencies:** Identity serialization evidence from issue 6 and a small
  synthetic state/event corpus.
- **Required evidence:** Exact filesystem/environment, interruption method,
  before/after hashes, recovery logs, rebuild comparisons, timings, and
  documented failure cases.
- **Exit criteria:** Interrupted writes never replace the last valid state with
  partial data; corruption is visible; rebuild produces equivalent canonical
  identities and events.
- **Expected ADR or follow-up artifact:** Durability/recovery evidence report
  and persistence requirements for the stack-selection ADR.

## 8. Prove idempotent event merging under offline operation and clock skew.

- **Purpose:** Establish deterministic convergence and deduplication when
  independent devices create events offline with incorrect clocks.
- **In scope:** Event ID/device sequence ordering, duplicate imports, reordered
  delivery, offline branches, clock skew, additive play/time events, ratings,
  and explicit conflict cases.
- **Out of scope:** Google Drive transport, production synchronization service,
  every future event type, and silent resolution of review-required conflicts.
- **Relevant DoD sections:** Listening statistics; Metadata synchronization;
  Conflict behavior.
- **Relevant acceptance IDs:** STAT-03, SY-01, SY-02.
- **Dependencies:** Synthetic identity/event model from issue 6; snapshot and
  rebuild observations from issue 7 where relevant.
- **Required evidence:** Deterministic event corpus, merge permutations,
  expected results, repeated-import results, clock-skew cases, and conflicts
  that correctly stop for review.
- **Exit criteria:** Every tested delivery order converges to the same supported
  state, duplicates never double-count, and wall-clock time is not the sole
  ordering authority.
- **Expected ADR or follow-up artifact:** Event-merge evidence report and event
  semantics input to the stack-selection or later synchronization ADR.

## 9. Benchmark full-file hashing on target devices.

- **Purpose:** Measure whether full-file hashing is practical for validation,
  transfer verification, stale-target protection, and duplicate evidence.
- **In scope:** Representative synthetic file sizes, available candidate hash
  algorithms/APIs, warm/cold observations where practical, throughput, elapsed
  time, CPU, memory, battery/power observations, and cancellation behavior.
- **Out of scope:** Final hash algorithm selection, perceptual audio matching,
  production scan scheduling, and real personal media.
- **Relevant DoD sections:** Audio transfer; File operations; Duplicate
  detection.
- **Relevant acceptance IDs:** TR-02, OP-01, DU-02.
- **Dependencies:** Access to both primary validation devices; generated
  non-personal files of documented sizes.
- **Required evidence:** Device/environment, algorithm/API versions, file
  generator and hashes, repeated measurements, resource observations, and raw
  result tables.
- **Exit criteria:** Results quantify practical throughput and resource costs
  on both target devices and identify constraints for later architecture
  comparison without prematurely selecting an algorithm.
- **Expected ADR or follow-up artifact:** Hash benchmark report and performance
  input to the stack-selection ADR.

## 10. Compare candidate production stacks and record the architecture decision.

- **Purpose:** Compare viable stack candidates against the complete Phase 1
  evidence and record the production architecture decision.
- **In scope:** Evidence-based comparison of platform access, playback, formats,
  paths, identity serialization, durability/recovery, event merging, hashing,
  human-editable config validation, testing, maintainability, security, and
  packaging implications.
- **Out of scope:** Application feature implementation, selecting a winner
  before prerequisite evidence is complete, and changing DoD requirements to
  favor a candidate.
- **Relevant DoD sections:** All Phase 1-relevant sections, especially Required
  formats, Identity and paths, Playback, Metadata synchronization,
  Configuration, and Restore and migration.
- **Relevant acceptance IDs:** ID-01 through ID-03, FS-01 through FS-02, PB-01,
  PB-02, PB-05, STAT-03, SY-01 through SY-02, TR-02, OP-01, CFG-02, REST-01,
  and REST-02.
- **Dependencies:** Issues 1 through 9 have complete, linked evidence or an
  explicitly documented unresolved result. Dependency tracking is descriptive,
  not enforced automatically by GitHub.
- **Required evidence:** Comparison criteria and weights, links to every proof,
  candidate-specific limitations, rejected alternatives, security/privacy
  observations, and reproducible supporting measurements.
- **Exit criteria:** An ADR selects or explicitly declines to select a
  production stack based on the evidence, records tradeoffs and revisit
  conditions, and creates follow-up issues for unresolved risks.
- **Expected ADR or follow-up artifact:** A numbered stack-selection ADR plus
  narrowly scoped follow-up issues. This catalog does not choose the stack.
