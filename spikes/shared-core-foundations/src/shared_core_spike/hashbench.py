"""Safe synthetic streaming hash benchmark for issue #10."""

from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import tempfile
import time
from typing import Any


ALGORITHM = "sha256"
DEFAULT_CHUNK_SIZE = 1024 * 1024
MISSING_OBSERVATION = "not captured by this benchmark"
PLATFORM_KINDS = {"android", "vps", "windows", "other"}


class BenchmarkError(RuntimeError):
    pass


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
        "resource_observations": resource_observations
        or {
            "cpu": "process CPU time recorded per run; utilization not instrumented",
            "memory": MISSING_OBSERVATION,
            "battery_or_power": MISSING_OBSERVATION,
            "thermal": MISSING_OBSERVATION,
            "cancellation": MISSING_OBSERVATION,
        },
        "measurements": measurements,
    }


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
            "",
        ]
    )
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
