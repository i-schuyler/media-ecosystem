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
The recorded VPS baseline is CPython 3.12.13; target runs should use CPython
3.12.x and record the exact patch version.

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
  --json-output spikes/shared-core-foundations/results/vps-sha256.json \
  --markdown-output spikes/shared-core-foundations/results/vps-sha256.md
```

Run on the primary Android device in Termux from a clean checkout:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py hash-benchmark \
  --sizes-mib 1,16,64,256 --repeats 3 --chunk-mib 1 \
  --device-label android-galaxy-tab-s10-fe-5g \
  --json-output spikes/shared-core-foundations/results/local-android-sha256.json \
  --markdown-output spikes/shared-core-foundations/results/local-android-sha256.md
```

Run on the primary Windows device from PowerShell:

```powershell
py -3 .\spikes\shared-core-foundations\scripts\harness.py hash-benchmark `
  --sizes-mib 1,16,64,256 --repeats 3 --chunk-mib 1 `
  --device-label windows-surface-book-3 `
  --json-output .\spikes\shared-core-foundations\results\local-windows-sha256.json `
  --markdown-output .\spikes\shared-core-foundations\results\local-windows-sha256.md
```

`local-*` results are ignored so measurements can be reviewed and sanitized
before deliberately adding evidence. Record exact OS/build, Python version,
storage location/type, power source, battery delta where meaningful, and any
thermal or cancellation observation alongside target results. Do not use real
media.

The benchmark streams SHA-256 input in bounded chunks. Files are generated one
at a time under an automatically disposed system temporary directory and are
deleted before the next size. Output files are atomically promoted only after
all runs succeed. `Ctrl+C` may stop the run without modifying tracked evidence.
A digest is integrity evidence; it is never used as a Track ID or as automatic
logical-identity evidence.

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

