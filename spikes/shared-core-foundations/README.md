# Disposable shared-core foundations proof harness

> **Experimental Phase 1 spike:** everything under this directory is designed
> to be disposable evidence code. It is not the production core, an application
> framework, or a production schema. Passing these proofs does not make Python,
> these data shapes, or this implementation the production stack.

This harness exercises the proof questions in GitHub issues #6 through #10
using only synthetic data and registered temporary roots. The indexed evidence
reports live in
[`docs/spikes/phase-1/shared-core-foundations/`](../../docs/spikes/phase-1/shared-core-foundations/README.md).

## Why Python for this experiment

CPython 3.12 was already installed on the VPS, is available in Android Termux,
and has a supported Windows 11 distribution. Its standard library supplies
Unicode normalization, JSON, UUIDs, checksums, temporary directories, atomic
rename primitives, and tests, so the harness has no third-party dependencies.
The recorded VPS experimental baseline is CPython 3.12.13. The Android evidence
records a successful CPython 3.14.6 run, and the Windows evidence records
CPython 3.14.3. Target runs must record the exact version; these compatibility
observations do not turn any version into a production runtime requirement.

This is a convenience choice for reproducible experimentation only. It does
not select the application language, shared-core packaging, persistence layer,
sync implementation, or final hash policy. The entire harness may be discarded
after Phase 1.

## One-command verification

From the repository root on Linux or Android Termux:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py verify --seeds 7,20260723
```

That command runs path vectors, identity-sidecar round trips, snapshot fault
injection and recovery, event merge/permutation tests, hash correctness tests,
and a 256 KiB non-destructive hash smoke probe. It exits nonzero on any failure.
Large benchmarks are never part of this command or normal CI.

On Windows 11 PowerShell, run the same harness and vectors with the Windows
Python launcher:

```powershell
py -3 .\spikes\shared-core-foundations\scripts\harness.py verify --seeds 7,20260723
```

The path-only command is:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py paths
```

```powershell
py -3 .\spikes\shared-core-foundations\scripts\harness.py paths
```

An Android or Windows pass must be recorded as target-device evidence; the
current Linux pass only proves VPS behavior and simulated input handling.

## Seeded event runs

Run additional recorded permutations explicitly:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py events --seed 7 --iterations 100
python3 spikes/shared-core-foundations/scripts/harness.py events --seed 20260723 --iterations 100
```

Change only the Python launcher to `py -3` on Windows. Every command prints the
seed and iteration count for reproduction.

## Hash benchmarks

The smoke probe is safe and small:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py hash-smoke
```

The full VPS command used for the committed results was:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py hash-benchmark \
  --sizes-mib 1,16,64,256 --repeats 3 --chunk-mib 1 \
  --device-label vps-linux-x86_64 \
  --platform vps \
  --json-output spikes/shared-core-foundations/results/vps-sha256.json \
  --markdown-output spikes/shared-core-foundations/results/vps-sha256.md
```

Run on the primary Android device in Termux from a clean checkout:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py hash-benchmark \
  --sizes-mib 1,16,64,256 --repeats 3 --chunk-mib 1 \
  --device-label android-galaxy-tab-s10-fe-5g \
  --platform android \
  --os-label "Android 16" --architecture aarch64 \
  --runtime-label "Python 3.14.6" \
  --json-output spikes/shared-core-foundations/results/local-android-sha256.json \
  --markdown-output spikes/shared-core-foundations/results/local-android-sha256.md
```

Run on the primary Windows device from PowerShell:

```powershell
py -3 .\spikes\shared-core-foundations\scripts\harness.py hash-benchmark `
  --sizes-mib 1,16,64,256 --repeats 3 --chunk-mib 1 `
  --device-label windows-surface-book-3 `
  --platform windows `
  --os-label "Microsoft Windows 11 Pro <version/build>" `
  --architecture "64-bit OS; X64 process" `
  --runtime-label "Python <exact-version>" `
  --json-output .\spikes\shared-core-foundations\results\local-windows-sha256.json `
  --markdown-output .\spikes\shared-core-foundations\results\local-windows-sha256.md
```

`local-*` results are ignored so measurements can be reviewed and sanitized
before deliberately adding evidence. Record exact OS/build, Python version,
storage location/type, power source, battery delta where meaningful, and any
thermal or cancellation observation alongside target results. Do not use real
media.

The platform is an explicit input; reports never infer it from a device label.
Use `--os-label`, `--architecture`, and `--runtime-label` when automatic runtime
values do not contain the exact environment string needed for evidence. Missing
memory, battery/power, thermal, and cancellation measurements are recorded as
`not captured by this benchmark` unless an explicit observation option is
provided.

After sanitizing a target result, regenerate its Markdown without rerunning the
benchmark:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py hash-report \
  --json-input spikes/shared-core-foundations/results/android-internal-sha256.json \
  --markdown-output spikes/shared-core-foundations/results/local-regenerated.md
```

The benchmark streams SHA-256 input in bounded chunks. Files are generated one
at a time under an automatically disposed system temporary directory and are
deleted before the next size. Output files are atomically promoted only after
all runs succeed. `Ctrl+C` may stop the run without modifying tracked evidence.
A digest is integrity evidence; it is never used as a Track ID or as automatic
logical-identity evidence.

On Windows, the benchmark also records `PeakWorkingSetSize` for the benchmark
process through `GetProcessMemoryInfo`. Unsupported resource APIs remain
explicitly unavailable rather than fabricated. The committed Windows result
is under `results/windows-internal-sha256.*`.

## Automated cancellation proof

The cancellation proof starts a bounded child process, generates a synthetic
file under an explicit safe work parent, begins a long repeated hash operation,
requests cancellation after a deterministic interval, verifies worker exit
`130`, verifies that no final artifact exists, and removes the unique
disposable child:

```powershell
py -3 .\spikes\shared-core-foundations\scripts\harness.py hash-cancellation `
  --work-root "<dedicated-local-app-data-probe-parent>" `
  --size-mib 64 --chunk-mib 1 --cancel-after-seconds 0.5
```

The operation timeout is deliberately much longer than the cancellation
interval, so a successful result proves cancellation handling rather than
normal completion. A failed or interrupted proof identifies its disposable
child instead of touching unrelated data.

## Safe storage capability probe

The storage probe is a separate, explicit target command. It is not included in
`verify`, `run-all.sh`, or CI. Run this consolidated form only after replacing
the target-root placeholder with an intentionally selected parent directory:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py storage-probe \
  --target-root "/explicit/selected/parent" \
  --storage-context portable-sd-raw-path \
  --target-label portable-sd-volume \
  > spikes/shared-core-foundations/results/local-android-storage-probe.json
```

Choose the explicit context from `termux-private-internal`,
`android-shared-internal`, `portable-sd-raw-path`, `app-saf`, or
`windows-internal`. These names deliberately distinguish Termux-private
storage, Android shared internal storage, raw mounted-card access, a future
app-level Storage Access Framework run, and Windows internal storage. Raw-path
success is not evidence of persisted SAF permission.

The selected root is used only as a parent. The probe refuses a filesystem
root, the user's home directory, and the repository root; creates a uniquely
named empty child; never enumerates or modifies other entries in the selected
parent; and writes deterministic synthetic bytes only within that child. It
tests write/read/hash, file and directory `fsync`, ordinary rename,
`os.replace`, case-distinct names, symlinks, streaming reads, and cleanup.
Unavailable filesystem capabilities are observations rather than automatic
product failures. On success the child is removed. On failure or interruption,
stderr identifies its sanitized child name so it can be removed manually from
the already known selected root.

On Windows the probe additionally records NFC-equivalent name coexistence,
trailing dot/space behavior, 255/256-character components, and one bounded path
beyond 260 characters. It does not physically open Win32 reserved device
aliases; the shared path corpus rejects them before filesystem access.

## Windows raw-evidence collector

`scripts/collect-windows-evidence.ps1` records the exact safe environment,
commands, stdout, stderr, exit codes, and durations for the unified suite,
event permutations, storage probe, cancellation proof, and benchmark. It
requires unique evidence and probe directories beneath dedicated Local AppData
roots and refuses to overwrite an existing evidence directory. It can ingest a
separately verified non-elevated storage-probe bundle when the host collector
itself is high-integrity.

The collector writes a relative SHA-256 manifest last and verifies complete
coverage immediately. Independently verify that manifest before consuming or
packaging the raw bundle. Raw directories and ZIPs remain outside Git; only
small sanitized results and reports belong here.

## Layout

- `fixtures/`: machine-readable, entirely synthetic path and event corpora
- `src/shared_core_spike/`: experimental reference implementations
- `tests/`: invariants, failure tests, fault injection, and randomized tests
- `scripts/`: dependency-free entry points
- `results/`: small sanitized measurements only; no generated benchmark files

The experimental path contract is documented in
[`EXPERIMENTAL_PATH_CONTRACT.md`](EXPERIMENTAL_PATH_CONTRACT.md), and the
sidecar shape in [`EXPERIMENTAL_SIDECAR_FORMAT.md`](EXPERIMENTAL_SIDECAR_FORMAT.md).

## Safety boundary

- All fixtures are invented and contain no audio or personal listening data.
- Sidecar and snapshot write tests require a registered directory created by
  the test and never accept an output path outside it.
- Missing roots produce an unavailable result, never a deletion decision.
- Large generated files, local measurements, Python caches, and partial output
  are ignored by Git.
- The Ubuntu CI job is evidence about Ubuntu only. It makes no Android or
  Windows compatibility claim.
