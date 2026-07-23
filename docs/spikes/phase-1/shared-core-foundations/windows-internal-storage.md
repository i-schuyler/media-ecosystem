# Capability evidence: Windows shared core and internal NTFS storage

- **Questions tested:** Does the shared-core harness execute on the primary
  Windows device; what path, identity, snapshot, and event behavior is
  observed; and what storage, SHA-256, resource, and cancellation behavior is
  measurable on its primary internal volume?
- **Related DoD sections:** Identity and paths; Metadata synchronization;
  Configuration; Restore and migration; File operations; Audio transfer;
  Duplicate detection.
- **Related acceptance IDs:** ID-01, ID-02, ID-03, FS-01, FS-02, STAT-03,
  SY-01, SY-02, TR-02, OP-01, DU-02, CFG-02, REST-01, REST-02.
- **Platform and exact environment:** Microsoft Surface Book 3; Microsoft
  Windows 11 Pro 25H2; version `10.0.26200`, build `26200.8894`; 64-bit OS;
  X64/AMD64 processes; Windows PowerShell Desktop `5.1.26100.8894`; Python
  Launcher file version `3.13.14`; CPython `3.14.3` built with MSC v.1944;
  tested 2026-07-23 local time.
- **Storage environment:** Primary internal system/probe volume, NTFS,
  1,022,389,907,456 bytes capacity and 224,886,382,592 bytes available before
  capture. The selected non-elevated probe parent was under a dedicated Local
  AppData temporary subtree, not the repository or a personal media root.
- **Candidate approach:** Existing disposable shared-core harness plus a
  Windows-only evidence collector, safe storage probe, streaming SHA-256
  benchmark with Win32 peak-working-set collection, and automated cooperative
  cancellation worker.
- **Preconditions:** Clean dedicated branch; synthetic bytes and identities
  only; unique non-overwriting raw-evidence and probe roots; no personal media,
  Developer Mode change, security-policy change, system configuration change,
  or destructive power-loss test.

## Reproduction commands

The exact commands and separate stdout, stderr, exit, and duration records are
covered by the raw manifest. From the repository root, the core commands were:

```powershell
py -3 .\spikes\shared-core-foundations\scripts\harness.py verify --seeds 7,20260723
py -3 .\spikes\shared-core-foundations\scripts\harness.py events --seed 7 --iterations 100
py -3 .\spikes\shared-core-foundations\scripts\harness.py events --seed 20260723 --iterations 100
py -3 .\spikes\shared-core-foundations\scripts\harness.py storage-probe `
  --target-root "<dedicated-local-app-data-probe-parent>" `
  --storage-context windows-internal `
  --target-label windows-internal-volume
py -3 .\spikes\shared-core-foundations\scripts\harness.py hash-cancellation `
  --work-root "<dedicated-local-app-data-probe-parent>" `
  --size-mib 64 --chunk-mib 1 --cancel-after-seconds 0.5
py -3 .\spikes\shared-core-foundations\scripts\harness.py hash-benchmark `
  --sizes-mib 1,16,64,256 --repeats 3 --chunk-mib 1 `
  --device-label windows-surface-book-3 --platform windows `
  --json-output "<raw-evidence>/windows-internal-sha256.raw.json" `
  --markdown-output "<raw-evidence>/windows-internal-sha256.raw.md"
```

The non-elevated storage observation used the exact selected CPython executable
inside the existing non-admin Codex sandbox because the `py` registry view was
not exposed there. The bundle records `is_admin=false`, the command, and its
own verified manifest. No privilege or policy was changed.

## Criteria

- **Success:** Shared path/identity/snapshot/event invariants and explicit
  event permutations pass; the bounded storage probe verifies deterministic
  bytes, synchronization calls, rename, replacement, streaming reads, and
  cleanup while treating unavailable optional capabilities as observations;
  benchmark digests repeat; practical CPU/memory/power/thermal observations
  are explicit; and cancellation cannot report a partial result as complete.
- **Failure:** An invariant is weakened, malformed identity is guessed,
  interrupted snapshot bytes become accepted, a probe touches unrelated data,
  repeated digests differ, a private path enters committed evidence, or
  cancellation leaves an unexplained final or temporary artifact.
- **Required measurements:** Exact environment, filesystems and capacity,
  suite count and duration, event seeds/iterations, storage observations,
  benchmark elapsed/CPU/throughput/digests, peak working set, battery/power,
  thermal availability, cancellation exit/cleanup, and raw provenance.

## Environment and provenance

The authoritative capture ran from harness commit
`664124dbd3cce9d6049b0381d6bb16540b5bb6e1` over 36.354944 seconds. Its relative
SHA-256 manifest covers all 45 raw files and has digest
`9c9c2a2f3de5fe0a4cc3ef967e74f9307001d1b133320b671fac8640658c44fb`.
The incorporated non-elevated sub-bundle manifest has digest
`a48db9fb3667febdd3e8f00392724abceb042923f52cc347934c8f06ebd0b7af`.
Both manifests were verified before consumption. The unchanged raw directory
was packaged into one untracked ZIP with digest
`d8e6be2f53662067ae369de6a76c33a4c53e743eb5a26908335d3edca0a3694a`;
the raw manifest verified again after packaging.

The host-side collector process was high-integrity and recorded that fact.
The storage result committed below is from the separate non-elevated sandbox
process. That distinction prevents the high-integrity file-symlink success
seen in a supplemental raw comparison from being presented as ordinary-user
capability.

## Shared-core results

- Final verification: **46 tests run in 3.182 seconds; 45 passed and one
  skipped**. The full command took 5.220881 seconds and exited zero.
- The skipped test was the existing Windows-only skip for constructing a
  directory-symlink parent in the sidecar containment test. The separate
  storage probe measured file-symlink availability rather than inferring it.
- Path vectors, identity sidecars, snapshot recovery/fault injection, event
  merging, benchmark regression tests, storage-probe tests, hash repeatability,
  and one-byte mutation detection passed.
- Seed `7`, 100 event delivery permutations: passed, exit zero, 2.224663-second
  command duration.
- Seed `20260723`, 100 event delivery permutations: passed, exit zero,
  1.579470-second command duration.

Before the authoritative capture, Windows exposed a genuine disposable-tool
portability defect: reopening a just-written snapshot read-only and passing
that descriptor to `os.fsync()` produced `EBADF` on Windows. Reopening the
harness-owned temporary file read/write, without changing bytes or fault
points, fixed the platform call. The complete final suite then passed. No
invariant or durability claim was reduced.

## Path and filesystem semantics

The shared representation continued to use `/` separators. The model rejected
drive-letter and UNC inputs, absolute paths, traversal, reserved names,
trailing dot/space components, and invalid characters before device-root
resolution. Display paths normalized to NFC; comparison keys remained
casefolded and collision-aware.

Measured non-elevated NTFS behavior was:

| Observation | Result |
|---|---|
| Deterministic 4 MiB write/read/SHA-256 | Passed |
| File `fsync()` | Returned successfully |
| Ordinary rename | Passed |
| `os.replace()` replacement | Passed; replacement digest verified |
| Directory `fsync()` through Python | Unavailable, `EACCES` |
| Case-distinct names | Could not coexist, `EEXIST` |
| File symlink | Unavailable, `EINVAL` |
| Streaming read | Passed with 32 KiB chunks |
| NFC-equivalent composed/decomposed names | Both names coexisted as distinct entries |
| Requested trailing dot | Call succeeded but stored name omitted the dot |
| Requested trailing space | Call succeeded but stored name omitted the space |
| 255-character component | Succeeded and name was preserved |
| 256-character component | Failed with `EINVAL` |
| 323-character complete path | Succeeded |
| Cleanup | Disposable child removed; selected parent empty |

Reserved Win32 device aliases such as `CON` were not opened because they may
address devices rather than ordinary files. Their rejection was demonstrated
by the portable path corpus. The successful 323-character path is one
CPython/NTFS observation on this machine, not a claim about every Win32 API,
application manifest, filesystem, or Windows installation.

## Identity, snapshot, and event findings

- Track, Folder, and File Instance IDs serialized and round-tripped without
  mutation. Rename/move retained identity, intended cross-device copies kept
  distinct File Instance IDs, missing/corrupt evidence failed closed, and
  explicit media replacement required authorization.
- File synchronization and normal `os.replace()` promotion succeeded on the
  internal NTFS probe.
- Snapshot tests retained the prior valid generation at three pre-promotion
  fault points, exposed a complete new generation after post-promotion fault
  injection, detected corruption/truncation, fell back to prior, failed
  explicitly when no valid state existed, and rebuilt equivalent event state.
- Directory `fsync()` was unavailable through this Python/Windows path. Normal
  replacement success plus model fault injection does not establish storage
  cache, kernel-crash, process-kill, controller, or sudden-power-loss
  durability.
- Both dedicated event permutation runs converged. Duplicate imports,
  device-sequence validation, clock skew, additive play/time events, causal
  ratings/resume updates, and explicit conflicts remained portable reference
  model behavior. This is not production synchronization.

## Hashing and resource measurements

Twelve SHA-256 measurements completed over deterministic synthetic 1, 16, 64,
and 256 MiB files with 1 MiB chunks. All three digests per size agreed.

| Synthetic size | Elapsed range | Process CPU range | SHA-256 throughput range |
|---:|---:|---:|---:|
| 1 MiB | 0.006000–0.041271 s | 0–0.015625 s | 24.230–166.658 MiB/s |
| 16 MiB | 0.071939–0.119363 s | 0.046875–0.078125 s | 134.045–222.411 MiB/s |
| 64 MiB | 0.364113–0.470796 s | 0.328125–0.421875 s | 135.940–175.770 MiB/s |
| 256 MiB | 1.101029–1.680415 s | 1.078125–1.531250 s | 152.343–232.510 MiB/s |

Peak benchmark-process working set was 27,963,392 bytes. Per-run CPU time is
recorded, but utilization and system-wide energy were not instrumented.
These are uncontrolled-cache observations; neither warm-cache nor cold-cache
labels are claimed. Complete sanitized measurements are available as
[JSON](../../../../spikes/shared-core-foundations/results/windows-internal-sha256.json)
and reproducible
[Markdown](../../../../spikes/shared-core-foundations/results/windows-internal-sha256.md).

Power was online and both reported batteries remained at 100% before and after
the 36-second capture, so no battery delta was resolvable. Windows reported
both `Win32_Battery` records as status `OK`, availability `2`, battery status
`2`, and raw estimated runtime `71582788`; that sentinel-like raw value is not
interpreted as a real duration. The WMI `BatteryStatus` property projection was
unavailable. ACPI returned one record whose exposed temperature/trip fields
were all zero before and after, and `BatteryTemperature` returned no records.
Consequently, **no usable thermal measurement was available**.

## Cancellation result

The automated proof generated a 64 MiB synthetic file, started a worker with a
120-second completion window, requested cancellation after 0.5 seconds, and
observed worker exit code `130`. The worker ran for 2.289384 seconds including
startup/generation/cancellation handling. No final benchmark artifact existed
or was reported, the only temporary file was identified as `synthetic.bin`,
and the disposable root was removed. The sanitized result is
[JSON](../../../../spikes/shared-core-foundations/results/windows-hash-cancellation.json).

## Limitations, security, and privacy

- **Demonstrated on this Surface Book 3:** Final harness and event passes;
  non-elevated NTFS name/case/symlink/rename/replace/sync observations; bounded
  hashing performance, CPU time, peak working set, power/battery availability,
  thermal unavailability, and automated cancellation cleanup.
- **Reference-model proof:** Portable path rejection/normalization, immutable
  sidecar behavior, snapshot fault points/recovery, and event convergence.
- **Simulated behavior:** Fault callbacks before/after promotion and missing
  root representation. They are not process kill, controller reset, or sudden
  power loss.
- **Unsupported or unavailable:** Non-elevated file symlink, directory
  `fsync()` through this runtime, usable temperature, and WMI battery-status
  detail.
- **Untested:** Sudden power loss, kernel crash, forced process kill during
  snapshot promotion, active storage removal, other Windows filesystems and
  APIs, Explorer monitoring, playback, codecs, and system media controls.
- **Security observations:** Absolute/drive/UNC/traversal input fails before
  root resolution. All destructive cleanup was restricted to fresh disposable
  children. Checksums detect accidental corruption, not malicious tampering.
- **Privacy observations:** Raw evidence and ZIP remain outside Git. Committed
  JSON removes the Windows username, absolute paths, executable locations,
  machine/volume identifiers, account data, and unrelated environment detail.
  No personal files, media, credentials, runtime databases, or private state
  were inspected or committed.

## Production suitability and disposition

- **Production suitability:** Not established. These results are Phase 1
  evidence for later comparison only. Windows execution of a disposable Python
  harness does not make Python, these schemas, SHA-256, or these filesystem
  calls production requirements.
- **Disposition:** **retain for comparison**.
- **Issue implications:** The Windows target gap for #6 and #7 is resolved and
  their existing exit criteria are now evidenced across the recorded Android
  and Windows runs. Issue #8 remains open for process-kill, sudden-power-loss,
  removal, and provider/filesystem durability. Issue #10 remains open because
  Android peak-memory and explicit cancellation/resource evidence are still
  incomplete.
- **Required follow-up:** App-level Android SAF lifecycle, crash/power/removal
  durability, remaining Android resources, Android and Windows playback/system
  controls, required codec proof, issue #11, and the later evidence-based
  architecture ADR.

No architecture, production persistence design, storage API, sidecar schema,
hash algorithm, scan schedule, or playback stack is selected by this report.
