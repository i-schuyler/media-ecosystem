# Capability spike: atomic snapshots, recovery, and rebuild (#8)

- **Proof question:** Can one candidate human-readable snapshot strategy retain
  a prior valid state, detect corruption, recover after injected interruption,
  and rebuild equivalent modeled event state?
- **Related DoD sections:** Metadata synchronization; Configuration; Restore
  and migration.
- **Related acceptance IDs:** CFG-02, REST-01, REST-02, STAT-03.
- **Platform and exact environment:** VPS; Linux 5.15.0-185-generic x86_64;
  repository and `/tmp` reported as ext2/ext3-family filesystems by `stat`;
  CPython 3.12.13; 2026-07-23. No Android or Windows filesystem was tested.
- **Candidate approach:** Deterministic JSON envelope, schema version, SHA-256
  checksum, same-root temporary file, file flush and `fsync`, prior snapshot
  retention, `os.replace` promotion, directory `fsync` on POSIX, newest-first
  validation with prior fallback, and rebuild from validated events.
- **Preconditions:** A registered disposable directory on the recorded VPS
  filesystem and entirely synthetic state/events.

## Reproduction commands

```sh
python3 spikes/shared-core-foundations/scripts/harness.py verify --seeds 7,20260723
```

## Criteria

- **Success:** Interruptions before promotion preserve the previous valid
  latest; interruption after atomic promotion exposes a complete new snapshot;
  corruption is visible and falls back to a valid prior; no valid copy produces
  explicit failure; rebuild matches the reference event state.
- **Failure:** Partial bytes become accepted latest state, corruption is silent,
  recovery guesses, prior valid state is lost before a safe replacement, or
  rebuild changes identities/statistics.
- **Required measurements:** Exact write/fault points, before/after states,
  checksum results, recovery source, filesystem/runtime, and target-device
  behavior.

## Results and measurements

- Evidence level: **Proven on VPS** for the modeled write/recovery protocol;
  **harness ready but target-device run pending** for Android and Windows.
- Seven snapshot tests passed. Equivalent dictionaries serialized to identical
  bytes and validated through the versioned checksum envelope.
- Injected faults at `after_temp_sync`, `after_prior_retained`, and
  `before_atomic_promotion` repeatedly recovered generation 1 from the latest
  snapshot. A fault at `after_atomic_promotion` recovered the complete
  generation 2 snapshot.
- After two successful generations, truncating/corrupting latest caused visible
  validation failure and fallback to prior generation 1. A corrupt latest with
  no valid prior raised `no valid snapshot exists`.
- Rebuilding from the valid synthetic event corpus and writing a fresh snapshot
  reproduced the directly merged event state and applied Event IDs.
- Every snapshot filename is fixed beneath the registered disposable root.

## Language/runtime guarantees and filesystem assumptions

- CPython exposes `os.replace` as a replace/rename primitive and `os.fsync` for
  open descriptors. The harness flushes Python buffers before file `fsync`.
- On this POSIX VPS run, the harness also opened and synchronized the containing
  directory after retaining prior and after promotion.
- Atomicity and durability are filesystem, mount, device-cache, and crash-mode
  dependent. The harness can inject immediately before and after `os.replace`;
  it cannot interrupt inside the kernel rename operation or simulate sudden
  power loss.
- Windows directory synchronization is deliberately not claimed. Android
  removable storage may expose non-atomic or differently durable behavior.

## Limitations, security, and privacy

- **Known limitations:** No real process kill, kernel crash, power loss,
  removable-media removal, FAT/exFAT behavior, Windows filesystem, or Android
  SD-card provider behavior was measured. The rebuild model covers this
  synthetic event envelope, not production migrations or a database.
- **Security observations:** Checksums detect accidental corruption, not
  adversarial tampering; no key or authentication scheme is implied. Invalid
  latest state is never used merely because its filename is newest.
- **Privacy observations:** Snapshots and event state are invented and exist
  only inside disposable roots.

## Production suitability and disposition

- **Production suitability:** Not established. The candidate identifies
  requirements for a later durability design but cannot support target claims.
- **Disposition:** **retain for comparison**.
- **Required target-device follow-up:** Execute fault tests on the primary
  Windows filesystem and Android internal/removable storage; add process-kill,
  storage-removal, full-volume, and where safely possible power-loss evidence;
  record filesystem/mount details and actual directory durability APIs.
- **ADR implications:** The later persistence/stack ADR must compare atomic
  replace, prior retention, directory synchronization, removable-media risks,
  corruption visibility, and rebuild semantics. This report is not that ADR.

Issue #8 is partially evidenced and remains open pending both target filesystem
proofs.

