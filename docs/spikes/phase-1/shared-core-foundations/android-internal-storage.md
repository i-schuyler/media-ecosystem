# Capability evidence: Android Termux-private internal storage

- **Questions tested:** Does the shared-core harness execute on the primary
  Android device, and what SHA-256 performance and filesystem environment are
  observed in Termux-private internal storage?
- **Related DoD sections:** Identity and paths; Metadata synchronization; File
  operations; Audio transfer; Duplicate detection.
- **Related acceptance IDs:** ID-01, ID-02, ID-03, FS-01, FS-02, STAT-03,
  SY-01, SY-02, TR-02, OP-01, DU-02.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G; Android 16;
  build `BP4A.251205.006.X528USQU9CZE9`; aarch64; kernel
  `6.6.102-android15-8-abX528USQU9CZE9-4k`; Python 3.14.6 in Termux;
  tested 2026-07-22 local time.
- **Storage environment:** Termux-private internal storage on F2FS with 4 KiB
  filesystem blocks. This was not Android shared internal storage, removable
  SD storage, app-level SAF access, or VPS evidence.
- **Candidate approach:** Existing disposable Python shared-core harness and
  streaming SHA-256 benchmark using deterministic synthetic bytes.
- **Preconditions:** Verified transferred evidence; synthetic inputs only;
  sufficient private internal space.

## Reproduction commands

The transferred bundle contains the environment and benchmark outputs, but no
shell transcript. The completed-run summary records the unified and seeded
event commands below. The current benchmark command adds explicit platform
metadata so regenerated reports are truthful:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py verify --seeds 7,20260723
python3 spikes/shared-core-foundations/scripts/harness.py events --seed 7 --iterations 100
python3 spikes/shared-core-foundations/scripts/harness.py events --seed 20260723 --iterations 100
python3 spikes/shared-core-foundations/scripts/harness.py hash-benchmark \
  --sizes-mib 1,16,64,256 --repeats 3 --chunk-mib 1 \
  --device-label android-galaxy-tab-s10-fe-5g \
  --platform android \
  --os-label "Android 16" --architecture aarch64 \
  --runtime-label "Python 3.14.6" \
  --json-output spikes/shared-core-foundations/results/local-android-sha256.json \
  --markdown-output spikes/shared-core-foundations/results/local-android-sha256.md
```

## Criteria

- **Success:** The shared-core invariants and seeded event permutations pass;
  all repeated digests agree; a one-byte change is detected; and benchmark
  measurements contain exact environment and timing data.
- **Failure:** A shared-core invariant fails, delivery order changes supported
  state, a duplicate digest differs, a one-byte change is missed, or results
  claim resources or storage contexts that were not observed.
- **Required measurements:** Environment, filesystem, capacity, test counts,
  seeds and iterations, elapsed and CPU time, throughput, repeat digests, and
  available resource observations.

## Results and measurements

- Evidence level: **measured on the primary Android device in Termux-private
  internal storage**. This does not establish shared-storage or SAF behavior.
- The operator-supplied completed-run summary records **35 of 35 shared-core
  tests passed**, seed `7` with 100 iterations passed, and seed `20260723` with
  100 iterations passed. The transferred raw bundle does not contain the suite
  transcript, so the counts are retained as an operator observation rather
  than represented as a raw log.
- F2FS reported 27,434,496 total and 18,653,169 free 4 KiB blocks. `df`
  reported approximately 105 GB total, 33 GB used, and 71 GB available at the
  time of testing.
- Twelve SHA-256 measurements completed: three runs each for synthetic 1, 16,
  64, and 256 MiB files with 1 MiB chunks. Repeated digests agreed, and the
  unified correctness probe detected a one-byte mutation.

| Synthetic size | SHA-256 throughput range |
|---:|---:|
| 1 MiB | 409.541–509.787 MiB/s |
| 16 MiB | 595.589–639.969 MiB/s |
| 64 MiB | 635.382–654.368 MiB/s |
| 256 MiB | 601.098–601.893 MiB/s |

The complete sanitized measurements are available as
[JSON](../../../../spikes/shared-core-foundations/results/android-internal-sha256.json)
and [Markdown](../../../../spikes/shared-core-foundations/results/android-internal-sha256.md).

## Provenance, limitations, security, and privacy

- **Provenance:** Every transferred file passed its relative checksum
  manifest. The internal manifest's SHA-256 is
  `5364fa1850e96c636e00a04839fd8accee5c75c8a23121afa5c647900a015e34`.
- **Resource limitations:** The battery observation was post-run only: 43%,
  unplugged and discharging, with a battery temperature reading of 33.9 °C.
  No pre-run battery delta, peak-memory measurement, long-duration thermal
  measurement, or cancellation measurement exists.
- **Known limitations:** Python 3.14.6 compatibility is measured evidence, not
  a production runtime requirement and not a change to the experimental
  CPython 3.12 baseline. No sudden-power-loss test, physical-media removal,
  app lifecycle, SAF permission, Android peak-memory measurement, or explicit
  Android cancellation proof was tested. Windows evidence is recorded
  separately and does not fill these Android gaps.
- **Security observations:** The benchmark used deterministic synthetic bytes.
  SHA-256 remains integrity evidence and never assigns logical identity.
- **Privacy observations:** Private absolute Termux paths were removed. The
  committed artifacts contain no personal media, device identifiers, account
  data, or removable-volume identifier.

## Production suitability and disposition

- **Production suitability:** Not established. These results are target-device
  evidence for comparison only.
- **Disposition:** **retain for comparison**.
- **Required follow-up:** Complete the remaining Android resource,
  cancellation, SAF, and durability observations required by issues #8 and
  #10 and the Android storage-access issue.
- **ADR implications:** None by itself. This evidence does not select Python,
  SHA-256, a storage API, persistence design, or production architecture.
