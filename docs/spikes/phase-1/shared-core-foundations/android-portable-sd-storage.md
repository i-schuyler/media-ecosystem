# Capability evidence: Android portable-SD raw-path storage

- **Questions tested:** What ordinary filesystem behavior and streaming
  SHA-256 performance does Termux observe through the primary Android device's
  mounted portable-SD path?
- **Related DoD sections:** Identity and paths; File operations; Metadata
  synchronization; Restore and migration; Audio transfer; Duplicate detection.
- **Related acceptance IDs:** FS-01, FS-02, ID-01, OP-01, CFG-02, REST-01,
  REST-02, TR-02, DU-02.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G; Android 16;
  build `BP4A.251205.006.X528USQU9CZE9`; aarch64; kernel
  `6.6.102-android15-8-abX528USQU9CZE9-4k`; Python 3.14.6 in Termux;
  tested 2026-07-22 local time.
- **Storage environment:** Android-exposed FUSE mount labeled
  `portable-sd-volume`; approximately 59 GB total and 24 GB available at the
  time of testing; 131,072-byte reported filesystem blocks; raw path readable
  and writable by Termux.
- **Candidate approach:** One-off bounded filesystem probe plus the existing
  deterministic streaming SHA-256 benchmark. The successful probe has been
  converted into the reusable, safer `storage-probe` harness command.
- **Preconditions:** Explicit writable removable-storage target; uniquely named
  disposable child; deterministic synthetic bytes only.

## Reproduction command

The transferred bundle contains the one-off probe result rather than its source
or shell transcript. Use this consolidated reusable command for later target
runs, replacing the placeholder with an intentionally selected parent:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py storage-probe \
  --target-root "/explicit/selected/parent" \
  --storage-context portable-sd-raw-path \
  --target-label portable-sd-volume \
  > spikes/shared-core-foundations/results/local-android-storage-probe.json
```

The current SD benchmark invocation is documented in the
[harness README](../../../../spikes/shared-core-foundations/README.md).

## Criteria

- **Success:** A disposable child is created; deterministic write/read/hash,
  file `fsync`, ordinary rename, replacement, streaming read, and cleanup pass;
  directory `fsync`, case behavior, and symlink behavior are recorded without
  treating unsupported capabilities as automatic product failures.
- **Failure:** Any operation touches unrelated files, escapes the disposable
  root, leaves unexplained probe data after a reported success, corrupts a
  repeated digest, or claims crash durability or SAF permission from raw-path
  behavior.
- **Required measurements:** Exact environment and mount, capacity, probe
  results, cleanup, battery/temperature observations, benchmark timings,
  unsupported capabilities, and unperformed failure modes.

## Results and measurements

- Evidence level: **measured raw-path behavior through the Android-exposed FUSE
  mount**. It is not app-level Storage Access Framework evidence.
- A writable uniquely named disposable child was created. Deterministic 4 MiB
  write/read/file-`fsync`/SHA-256 passed. Ordinary rename and replacement via
  `os.replace()` passed. Streaming read passed. The operator summary records
  that the disposable directory was removed and the repository remained clean.
- Directory `fsync()` returned successfully. This observes the API call in
  normal execution; it does not prove controller-level or sudden-power-loss
  durability.
- Case-distinct names could not coexist. This is evidence about the
  Android-exposed FUSE mount, not a claim about every native filesystem layer.
- Symlink creation failed with `EACCES`/permission denied. Symlink
  unavailability is expected storage behavior here and is not a product
  failure or a canonical-path-model requirement.
- Twelve SD-card SHA-256 measurements completed with repeated digests agreeing:

| Synthetic size | SHA-256 throughput range |
|---:|---:|
| 1 MiB | 109.831–149.370 MiB/s |
| 16 MiB | 335.947–513.351 MiB/s |
| 64 MiB | 513.038–547.727 MiB/s |
| 256 MiB | 531.649–589.442 MiB/s |

The sanitized filesystem result is available as
[JSON](../../../../spikes/shared-core-foundations/results/android-portable-sd-storage-probe.json).
The benchmark results are available as
[JSON](../../../../spikes/shared-core-foundations/results/android-portable-sd-sha256.json)
and [Markdown](../../../../spikes/shared-core-foundations/results/android-portable-sd-sha256.md).

## Provenance, limitations, security, and privacy

- **Provenance:** Every transferred file passed its relative checksum
  manifest. The SD manifest's SHA-256 is
  `297ab977d4968428ce5cfbac716c9b7e24c6695f3984422356334bdf16e1d6b7`.
- **Resource observations:** Battery was 40%, unplugged and discharging both
  before and after; its charge-counter reading changed from 4,096,540 to
  4,086,450. Battery temperature was 33.9 °C before and after. Peak memory,
  long-duration thermal behavior, and cancellation were not captured.
- **Durability limitations:** No sudden-power-loss test and no physical media
  removal during active writes were performed. Successful replacement proves
  normal-process replacement behavior on this mounted path only.
- **Access limitations:** No Android application or persisted SAF permission
  was tested. Raw Termux read/write access does not satisfy issue #2's app-level
  lifecycle criteria for restart, reboot, removal, reinsertion, or relink.
- **Security observations:** The one-off probe stayed in a disposable root and
  used deterministic synthetic bytes. The reusable command adds explicit
  refusal and containment guards.
- **Privacy observations:** Private absolute paths and the physical removable
  volume identifier were removed. `portable-sd-volume` is a descriptive
  placeholder. No personal media or account data is committed.

## Production suitability and disposition

- **Production suitability:** Not established. Raw-path behavior is useful
  filesystem input, not a selected Android storage API or production policy.
- **Disposition:** **retain for comparison**.
- **Required follow-up:** Prove persisted app-level SAF access, restart/reboot,
  card removal and reinsertion, unavailable-not-deleted behavior, Android
  resource/cancellation costs, and crash/power-loss durability where safely
  possible. Windows filesystem behavior is recorded separately.
- **ADR implications:** A future storage or persistence ADR must distinguish
  normal replacement from crash durability and raw mounts from provider APIs.
  No architecture is selected here.
