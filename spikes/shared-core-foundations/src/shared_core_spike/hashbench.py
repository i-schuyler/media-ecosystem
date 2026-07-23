"""Safe synthetic streaming hash benchmark for issue #10."""

from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import multiprocessing
import os
from pathlib import Path
import platform
import shutil
import tempfile
import time
from typing import Any
import uuid


ALGORITHM = "sha256"
DEFAULT_CHUNK_SIZE = 1024 * 1024
MISSING_OBSERVATION = "not captured by this benchmark"
PLATFORM_KINDS = {"android", "vps", "windows", "other"}
CANCELLATION_PREFIX = "media-ecosystem-hash-cancellation-"
CANCELLATION_EXIT_CODE = 130


class BenchmarkError(RuntimeError):
    pass


class CancellationProbeError(BenchmarkError):
    def __init__(self, message: str, disposable_child: str):
        super().__init__(message)
        self.disposable_child = disposable_child


def generate_synthetic(path: Path, size_bytes: int, chunk_size: int = DEFAULT_CHUNK_SIZE) -> None:
    """Generate deterministic non-media bytes without retaining the file in Git."""

    if size_bytes < 1 or chunk_size < 1:
        raise BenchmarkError("size and chunk size must be positive")
    seed = hashlib.sha256(b"media-ecosystem-synthetic-hash-fixture-v1").digest()
    pattern = (seed * ((chunk_size // len(seed)) + 1))[:chunk_size]
    remaining = size_bytes
    with path.open("wb") as stream:
        while remaining:
            portion = pattern[: min(remaining, len(pattern))]
            stream.write(portion)
            remaining -= len(portion)
        stream.flush()
        os.fsync(stream.fileno())


def stream_hash(path: Path, chunk_size: int = DEFAULT_CHUNK_SIZE) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(chunk_size):
            digest.update(chunk)
    return digest.hexdigest()


def _process_peak_working_set() -> dict[str, Any]:
    if os.name != "nt":
        return {
            "available": False,
            "reason": "peak process working set is collected by this harness on Windows only",
        }
    try:
        import ctypes
        from ctypes import wintypes

        class ProcessMemoryCounters(ctypes.Structure):
            _fields_ = [
                ("cb", wintypes.DWORD),
                ("PageFaultCount", wintypes.DWORD),
                ("PeakWorkingSetSize", ctypes.c_size_t),
                ("WorkingSetSize", ctypes.c_size_t),
                ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
                ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                ("PagefileUsage", ctypes.c_size_t),
                ("PeakPagefileUsage", ctypes.c_size_t),
            ]

        counters = ProcessMemoryCounters()
        counters.cb = ctypes.sizeof(counters)
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        psapi = ctypes.WinDLL("psapi", use_last_error=True)
        kernel32.GetCurrentProcess.argtypes = []
        kernel32.GetCurrentProcess.restype = wintypes.HANDLE
        psapi.GetProcessMemoryInfo.argtypes = [
            wintypes.HANDLE,
            ctypes.POINTER(ProcessMemoryCounters),
            wintypes.DWORD,
        ]
        psapi.GetProcessMemoryInfo.restype = wintypes.BOOL
        process = kernel32.GetCurrentProcess()
        succeeded = psapi.GetProcessMemoryInfo(
            process, ctypes.byref(counters), counters.cb
        )
        if not succeeded:
            return {
                "available": False,
                "reason": "GetProcessMemoryInfo returned failure",
            }
        return {
            "available": True,
            "bytes": int(counters.PeakWorkingSetSize),
            "metric": "PeakWorkingSetSize for the benchmark process",
        }
    except (AttributeError, OSError, TypeError, ValueError) as error:
        return {
            "available": False,
            "reason": f"Windows peak working-set query failed: {type(error).__name__}",
        }


def correctness_probe(size_bytes: int = 256 * 1024, chunk_size: int = 64 * 1024) -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="media-ecosystem-hash-smoke-") as temporary:
        path = Path(temporary) / "synthetic.bin"
        generate_synthetic(path, size_bytes, chunk_size)
        first = stream_hash(path, chunk_size)
        second = stream_hash(path, chunk_size)
        with path.open("r+b") as stream:
            stream.seek(size_bytes // 2)
            original = stream.read(1)
            stream.seek(size_bytes // 2)
            stream.write(bytes([original[0] ^ 0x01]))
            stream.flush()
            os.fsync(stream.fileno())
        mutated = stream_hash(path, chunk_size)
        if first != second or first == mutated:
            raise BenchmarkError("hash repeatability or mutation detection failed")
        return {
            "algorithm": ALGORITHM,
            "size_bytes": size_bytes,
            "chunk_size_bytes": chunk_size,
            "repeated_digest_equal": True,
            "one_byte_mutation_detected": True,
        }


def environment(
    device_label: str,
    platform_kind: str,
    *,
    os_label: str | None = None,
    architecture: str | None = None,
    runtime_label: str | None = None,
) -> dict[str, str]:
    if platform_kind not in PLATFORM_KINDS:
        raise BenchmarkError(f"unsupported platform kind: {platform_kind}")
    return {
        "device_label": device_label,
        "platform": platform_kind,
        "runtime": runtime_label or f"Python {platform.python_version()}",
        "os": os_label or f"{platform.system()} {platform.release()}",
        "architecture": architecture or platform.machine(),
    }


def benchmark(
    *,
    sizes_bytes: list[int],
    repeats: int,
    chunk_size: int,
    device_label: str,
    platform_kind: str,
    os_label: str | None = None,
    architecture: str | None = None,
    runtime_label: str | None = None,
    resource_observations: dict[str, str] | None = None,
) -> dict[str, Any]:
    if repeats < 2:
        raise BenchmarkError("at least two runs are required to verify digest repeatability")
    measurements: list[dict[str, Any]] = []
    with tempfile.TemporaryDirectory(prefix="media-ecosystem-hash-benchmark-") as temporary:
        for size_bytes in sizes_bytes:
            path = Path(temporary) / f"synthetic-{size_bytes}.bin"
            generate_synthetic(path, size_bytes, chunk_size)
            expected: str | None = None
            for run_index in range(1, repeats + 1):
                process_start = time.process_time()
                wall_start = time.perf_counter()
                digest = stream_hash(path, chunk_size)
                elapsed = time.perf_counter() - wall_start
                cpu_elapsed = time.process_time() - process_start
                if expected is None:
                    expected = digest
                elif digest != expected:
                    raise BenchmarkError("repeated hash run returned a different digest")
                measurements.append(
                    {
                        "algorithm": ALGORITHM,
                        "file_size_bytes": size_bytes,
                        "chunk_size_bytes": chunk_size,
                        "run_index": run_index,
                        "elapsed_seconds": round(elapsed, 6),
                        "cpu_seconds": round(cpu_elapsed, 6),
                        "throughput_mib_per_second": round((size_bytes / (1024 * 1024)) / elapsed, 3),
                        "digest": digest,
                    }
                )
            path.unlink()
    memory_measurement = _process_peak_working_set()
    observations = resource_observations or {
        "cpu": "process CPU time recorded per run; utilization not instrumented",
        "memory": MISSING_OBSERVATION,
        "battery_or_power": MISSING_OBSERVATION,
        "thermal": MISSING_OBSERVATION,
        "cancellation": MISSING_OBSERVATION,
    }
    if memory_measurement["available"] and observations.get("memory") == MISSING_OBSERVATION:
        observations = dict(observations)
        observations["memory"] = (
            "peak process working set recorded in resource_measurements"
        )
    return {
        "schema_version": 2,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "environment": environment(
            device_label,
            platform_kind,
            os_label=os_label,
            architecture=architecture,
            runtime_label=runtime_label,
        ),
        "generator": "deterministic repeated SHA-256 seed bytes, streamed to a temporary file",
        "identity_warning": "A full-file hash is integrity evidence and is never a logical Track ID.",
        "resource_observations": observations,
        "resource_measurements": {
            "peak_process_working_set": memory_measurement,
        },
        "measurements": measurements,
    }


def _validate_work_root(work_root: Path) -> Path:
    try:
        resolved = work_root.expanduser().resolve(strict=True)
    except OSError as error:
        raise CancellationProbeError(
            "cancellation work root must exist and be accessible", "not-created"
        ) from error
    repository_root = Path(__file__).resolve().parents[4]
    if not resolved.is_dir():
        raise CancellationProbeError(
            "cancellation work root must be a directory", "not-created"
        )
    if resolved == Path(resolved.anchor):
        raise CancellationProbeError(
            "filesystem roots are not valid cancellation work roots", "not-created"
        )
    if resolved == Path.home().resolve():
        raise CancellationProbeError(
            "the user home directory is not a valid cancellation work root",
            "not-created",
        )
    if resolved == repository_root:
        raise CancellationProbeError(
            "the repository root is not a valid cancellation work root",
            "not-created",
        )
    return resolved


def _cancellation_worker(
    path: Path,
    final_artifact: Path,
    ready: Any,
    cancel: Any,
    size_bytes: int,
    chunk_size: int,
    operation_timeout_seconds: float,
) -> None:
    generate_synthetic(path, size_bytes, chunk_size)
    ready.set()
    started = time.perf_counter()
    while time.perf_counter() - started < operation_timeout_seconds:
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            while chunk := stream.read(chunk_size):
                if cancel.is_set():
                    raise SystemExit(CANCELLATION_EXIT_CODE)
                digest.update(chunk)
    atomic_output(
        final_artifact,
        json.dumps(
            {
                "result": "completed",
                "digest": digest.hexdigest(),
            },
            sort_keys=True,
        )
        + "\n",
    )


def cancellation_probe(
    *,
    work_root: Path,
    size_bytes: int = 64 * 1024 * 1024,
    chunk_size: int = DEFAULT_CHUNK_SIZE,
    cancel_after_seconds: float = 0.5,
    operation_timeout_seconds: float = 120.0,
) -> dict[str, Any]:
    """Cancel a long synthetic hash worker and verify fail-clean behavior."""

    if size_bytes < 1 or chunk_size < 1:
        raise CancellationProbeError(
            "size and chunk size must be positive", "not-created"
        )
    if cancel_after_seconds <= 0 or operation_timeout_seconds <= cancel_after_seconds:
        raise CancellationProbeError(
            "cancellation timing must be positive and shorter than the operation timeout",
            "not-created",
        )
    root = _validate_work_root(work_root)
    disposable = Path(tempfile.mkdtemp(prefix=CANCELLATION_PREFIX, dir=root))
    if disposable.parent != root or disposable.resolve() != disposable:
        raise CancellationProbeError(
            "cancellation probe directory escaped the selected root",
            disposable.name,
        )
    synthetic_path = disposable / "synthetic.bin"
    final_artifact = disposable / f"completed-{uuid.uuid4().hex}.json"
    context = multiprocessing.get_context("spawn")
    ready = context.Event()
    cancel = context.Event()
    process = context.Process(
        target=_cancellation_worker,
        args=(
            synthetic_path,
            final_artifact,
            ready,
            cancel,
            size_bytes,
            chunk_size,
            operation_timeout_seconds,
        ),
    )
    wall_start = time.perf_counter()
    try:
        process.start()
        if not ready.wait(timeout=30):
            process.terminate()
            process.join(timeout=10)
            raise CancellationProbeError(
                "hash worker did not become ready", disposable.name
            )
        time.sleep(cancel_after_seconds)
        cancel.set()
        process.join(timeout=30)
        if process.is_alive():
            process.terminate()
            process.join(timeout=10)
            raise CancellationProbeError(
                "hash worker did not honor cancellation", disposable.name
            )
        elapsed = time.perf_counter() - wall_start
        temporary_files = sorted(path.name for path in disposable.iterdir())
        final_exists = final_artifact.exists()
        if process.exitcode != CANCELLATION_EXIT_CODE or final_exists:
            raise CancellationProbeError(
                "cancellation worker reported an unexpected exit or final artifact",
                disposable.name,
            )
        shutil.rmtree(disposable)
        return {
            "schema_version": 1,
            "probe": "automated-hash-cancellation",
            "result": "passed",
            "synthetic_size_bytes": size_bytes,
            "chunk_size_bytes": chunk_size,
            "cancel_after_seconds": cancel_after_seconds,
            "operation_timeout_seconds": operation_timeout_seconds,
            "wall_seconds": round(elapsed, 6),
            "worker_exit_code": process.exitcode,
            "expected_cancellation_exit_code": CANCELLATION_EXIT_CODE,
            "finalized_artifact_reported": False,
            "finalized_artifact_exists": False,
            "temporary_files_before_cleanup": temporary_files,
            "cleanup": {
                "passed": not disposable.exists(),
                "disposable_root_removed": not disposable.exists(),
            },
        }
    except BaseException:
        if process.is_alive():
            process.terminate()
            process.join(timeout=10)
        raise


def render_markdown(result: dict[str, Any]) -> str:
    env = result["environment"]
    scope = {
        "android": "Android",
        "vps": "VPS",
        "windows": "Windows",
    }.get(env.get("platform"), "recorded-environment")
    lines = [
        "# Sanitized full-file hashing measurements",
        "",
        f"> Experimental {scope} evidence only. This does not select a production hash or scan policy.",
        "",
        f"- Device label: `{env['device_label']}`",
        f"- Runtime: `{env['runtime']}`",
        f"- OS: `{env['os']}`",
        f"- Architecture: `{env['architecture']}`",
    ]
    for key, label in (
        ("device_model", "Device model"),
        ("os_build", "OS build"),
        ("kernel", "Kernel"),
        ("storage_context", "Storage context"),
        ("filesystem", "Filesystem"),
    ):
        if key in env:
            lines.append(f"- {label}: `{env[key]}`")
    lines.extend(
        [
            f"- Generated at: `{result['generated_at_utc']}`",
            f"- Logical identity: {result['identity_warning']}",
            "",
            "| Size (bytes) | Chunk (bytes) | Run | Elapsed (s) | CPU (s) | MiB/s | SHA-256 |",
            "|---:|---:|---:|---:|---:|---:|---|",
        ]
    )
    for item in result["measurements"]:
        lines.append(
            f"| {item['file_size_bytes']} | {item['chunk_size_bytes']} | {item['run_index']} | "
            f"{item['elapsed_seconds']:.6f} | {item['cpu_seconds']:.6f} | "
            f"{item['throughput_mib_per_second']:.3f} | `{item['digest']}` |"
        )
    lines.extend(
        [
            "",
            "## Resource observations",
            "",
            *[
                f"- {key.replace('_', ' ').title()}: {result['resource_observations'][key]}"
                for key in (
                    "cpu",
                    "memory",
                    "battery_or_power",
                    "thermal",
                    "cancellation",
                )
                if key in result["resource_observations"]
            ],
        ]
    )
    peak = result.get("resource_measurements", {}).get(
        "peak_process_working_set"
    )
    if peak:
        if peak.get("available"):
            lines.append(
                f"- Peak Process Working Set: {peak['bytes']} bytes "
                f"({peak['metric']})"
            )
        else:
            lines.append(
                f"- Peak Process Working Set: unavailable ({peak['reason']})"
            )
    lines.append("")
    return "\n".join(lines)


def atomic_output(path: Path, payload: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def write_results(result: dict[str, Any], json_path: Path, markdown_path: Path) -> None:
    atomic_output(json_path, json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
    atomic_output(markdown_path, render_markdown(result))
