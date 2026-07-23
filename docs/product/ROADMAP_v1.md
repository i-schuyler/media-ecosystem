# Media Ecosystem v1.0.0 Roadmap

## Delivery rule

Build in large, reviewable vertical slices. Each slice must leave the repository coherent, documented, and testable. Identity, synchronization, deletion, and migration behavior require invariant tests.

## Phase 0 — Product and repository foundation

**Status:** Complete. The contract, licensing, guardrails, device matrix,
protocol, issue catalog, labels, milestones, and planning issues are merged.

Completed baseline:

- Frozen DoD, foundation decisions, roadmap, and acceptance matrix
- Public experimental repository
- Privacy and synthetic-data guardrails
- Issue, PR, ADR, and CI conventions

Phase 0 completion slice:

- Adopt Apache-2.0 and document contribution licensing
- Add durable repository agent instructions
- Define the supported test-device matrix and capability-spike protocol
- Publish the canonical Phase 1 issue catalog, issue form, milestones, labels,
  and planning issues
- Require the expanded foundation documents in CI

**Exit:** The completion slice is merged, its planning objects exist, the
product contract remains versioned, and CI uses synthetic data only. No
application code is required.

## Phase 1 — Technical proof-of-capability

**Status:** Active. Follow the
[capability-spike protocol](../implementation/CAPABILITY_SPIKE_PROTOCOL.md),
[test-device matrix](../implementation/SUPPORTED_TEST_DEVICE_MATRIX.md), and
[canonical issue catalog](../implementation/PHASE_1_CAPABILITY_ISSUES.md).

The [shared-core foundations evidence](../spikes/phase-1/shared-core-foundations/README.md)
records VPS proofs and primary Android measurements for issues #6 through #10.
Android evidence covers Termux-private F2FS execution, portable-SD raw-path
FUSE observations, and internal/removable hashing. It does not prove persisted
app-level SAF access. Windows runs, crash/power-loss and active-removal
durability, and remaining resource measurements are pending. The hashing issue
remains open; this evidence does not satisfy the Phase 1 exit gate or select
the production stack.

Prove the highest-risk capabilities before choosing the production stack:

- Android SD-card root access and durable permissions
- Android background/screen-off playback and media controls
- Windows playback and media controls
- Playback of all required Bandcamp formats on both platforms
- Portable path normalization
- Stable Track, Folder, and File Instance ID sidecar round trips
- Atomic state snapshots and recovery
- Idempotent events under clock skew
- Practical full-file hashing performance
- Candidate-stack comparison, including human-editable config validation

**Exit:** An Architecture Decision Record selects the stack based on evidence.

## Phase 2 — Canonical catalog and CLI

- Device and root registration
- Track, folder, and file-instance identity
- Identity sidecars and embedded-ID policy
- Scan and deterministic reconciliation
- Local SQLite catalog
- Human-readable export
- Config validation and `doctor`
- Synthetic fixture library

**Exit:** Moves and renames preserve identity; missing identity never triggers guessing; unmounted roots never become deletions.

## Phase 3 — Android listening vertical slice

- Folder and library browsing
- Playback and background controls
- Device-local persistent queue
- Clear Queue and Undo
- Ratings, Play Count, and Total Played Time
- Resume prompt using configurable 60-second/120-second defaults
- Sleep timer with Finish current track
- Static playlists from folders
- Show/hide unavailable UI against synthetic remote state

**Exit:** The tablet is usable for daily offline listening and all required formats pass.

## Phase 4 — Windows listening vertical slice

- Core playback/library parity
- Windows media controls and Explorer integration
- Persistent queue, resume, sleep timer, playlists, ratings, and statistics
- Local file operations through the shared core

**Exit:** Both platforms produce equivalent canonical state from the same synthetic library.

## Phase 5 — Metadata synchronization

- Google Drive authentication
- Per-device append-only journals
- Offline outbox and idempotent merge
- Snapshot compaction
- Sync diagnostics
- Wi-Fi-only default and mobile-data override
- Schema migration and clean-install metadata restore

**Exit:** Two offline devices merge supported changes without loss, double counting, or clock-dependent corruption.

## Phase 6 — Availability and audio transfer

- Intended versus actual device availability
- Pending transfer state and storage estimates
- Local-network discovery and resumable transfer
- Hash verification
- Manual-copy reconciliation

**Exit:** Reachable devices transfer and verify audio; unreachable sources remain pending; Drive receives no audio bytes.

## Phase 7 — Coordinated file operations, trash, and undo

- Rename and move
- Delete-to-trash and restore
- Offline pending operations
- Stale-target validation
- One-level synchronized compensating undo
- Retire Device
- Last-copy warning

**Exit:** Conflicting destructive intent never resolves silently, and unmounted storage cannot cause mass deletion.

## Phase 8 — Duplicate management

- Duplicate Track ID detection
- Byte-identical same-device duplicate detection
- Per-device intended-copy evaluation
- Review UI with every location and proposed retained copy
- `Keep at most one copy on each intended device`
- Recoverable cleanup and Undo

**Exit:** Copies on different intended devices remain valid; stale cleanup plans stop; no global-single-copy action exists.

## Phase 9 — Release hardening

- Large-library, battery, and performance tests
- SD-card removal/relink tests
- Network interruption and corruption recovery
- Accessibility and keyboard navigation
- Installer/update path
- Security and privacy review
- Migration rehearsal and documentation

**Release gate:** Every required acceptance row passes, with no known risk of silent data loss, silent identity mutation, or irreversible unintended deletion.
