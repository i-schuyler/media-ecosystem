"""Disposable, bounded filesystem capability probe for explicitly selected roots."""

from __future__ import annotations

import errno
import hashlib
import os
from pathlib import Path
import re
import tempfile
from typing import Any


PROBE_PREFIX = "media-ecosystem-storage-probe-"
PROBE_SIZE_BYTES = 4 * 1024 * 1024
REPOSITORY_ROOT = Path(__file__).resolve().parents[4]
STORAGE_CONTEXTS = {
    "termux-private-internal": "Termux-private internal storage",
    "android-shared-internal": "Android shared internal storage",
    "portable-sd-raw-path": "portable SD raw-path access",
    "app-saf": "future app-level Storage Access Framework access",
}


class StorageProbeError(RuntimeError):
    pass


class StorageProbeRunError(StorageProbeError):
    def __init__(self, message: str, disposable_child: str):
        super().__init__(message)
        self.disposable_child = disposable_child


def _safe_label(value: str) -> str:
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", value):
        raise StorageProbeError(
            "target label must contain only lowercase letters, digits, and hyphens"
        )
    return value


def _validate_target_root(target_root: Path) -> Path:
    try:
        resolved = target_root.expanduser().resolve(strict=True)
    except OSError as error:
        raise StorageProbeError("target root must exist and be accessible") from error
    if not resolved.is_dir():
        raise StorageProbeError("target root must be a directory")
    if resolved == Path(resolved.anchor):
        raise StorageProbeError("filesystem roots are not valid probe targets")
    if resolved == Path.home().resolve():
        raise StorageProbeError("the user home directory is not a valid probe target")
    if resolved == REPOSITORY_ROOT:
        raise StorageProbeError("the repository root is not a valid probe target")
    return resolved


def _deterministic_bytes(size_bytes: int, seed: bytes) -> bytes:
    block = hashlib.sha256(seed).digest()
    return (block * ((size_bytes // len(block)) + 1))[:size_bytes]


def _write_and_sync(path: Path, payload: bytes) -> None:
    with path.open("xb") as stream:
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())


def _stream_digest(path: Path, chunk_size: int = 64 * 1024) -> tuple[str, int]:
    digest = hashlib.sha256()
    bytes_read = 0
    with path.open("rb") as stream:
        while chunk := stream.read(chunk_size):
            digest.update(chunk)
            bytes_read += len(chunk)
    return digest.hexdigest(), bytes_read


def _unsupported_observation(error: OSError) -> dict[str, Any]:
    return {
        "supported": False,
        "returned_successfully": False,
        "error_kind": type(error).__name__,
        "errno": errno.errorcode.get(error.errno, error.errno),
    }


def _directory_fsync(directory: Path) -> dict[str, Any]:
    flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    try:
        descriptor = os.open(directory, flags)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    except OSError as error:
        return _unsupported_observation(error)
    return {"supported": True, "returned_successfully": True}


def run_probe(
    *,
    target_root: Path,
    storage_context: str,
    target_label: str,
    size_bytes: int = PROBE_SIZE_BYTES,
) -> dict[str, Any]:
    """Run only inside a fresh disposable child and remove it on success."""

    root = _validate_target_root(target_root)
    if storage_context not in STORAGE_CONTEXTS:
        raise StorageProbeError(f"unsupported storage context: {storage_context}")
    _safe_label(target_label)
    if size_bytes < 1:
        raise StorageProbeError("probe size must be positive")

    try:
        disposable = Path(tempfile.mkdtemp(prefix=PROBE_PREFIX, dir=root))
    except OSError as error:
        raise StorageProbeError(
            "could not create a unique disposable child in the selected root"
        ) from error
    if disposable.parent != root or disposable.resolve() != disposable:
        raise StorageProbeError("disposable probe directory escaped the selected root")

    data_path = disposable / "synthetic.bin"
    renamed_path = disposable / "renamed.bin"
    replacement_path = disposable / "replacement.bin"
    lower_case_path = disposable / "case-observation"
    upper_case_path = disposable / "CASE-OBSERVATION"
    symlink_path = disposable / "synthetic-link"

    try:
        original = _deterministic_bytes(
            size_bytes, b"media-ecosystem-storage-probe-original-v1"
        )
        original_digest = hashlib.sha256(original).hexdigest()
        _write_and_sync(data_path, original)
        read_digest, bytes_read = _stream_digest(data_path)
        if read_digest != original_digest or bytes_read != size_bytes:
            raise StorageProbeError("write/read/hash verification failed")

        data_path.rename(renamed_path)
        if not renamed_path.is_file() or data_path.exists():
            raise StorageProbeError("ordinary rename verification failed")

        replacement = _deterministic_bytes(
            size_bytes, b"media-ecosystem-storage-probe-replacement-v1"
        )
        replacement_digest = hashlib.sha256(replacement).hexdigest()
        _write_and_sync(replacement_path, replacement)
        os.replace(replacement_path, renamed_path)
        promoted_digest, promoted_bytes = _stream_digest(renamed_path)
        if promoted_digest != replacement_digest or promoted_bytes != size_bytes:
            raise StorageProbeError("replacement verification failed")

        directory_fsync = _directory_fsync(disposable)

        _write_and_sync(lower_case_path, b"deterministic-lower-case-marker\n")
        try:
            _write_and_sync(upper_case_path, b"deterministic-upper-case-marker\n")
        except FileExistsError:
            case_distinct = {
                "supported": False,
                "coexisted": False,
                "error_kind": "FileExistsError",
                "errno": "EEXIST",
            }
        else:
            case_distinct = {
                "supported": True,
                "coexisted": lower_case_path.read_bytes()
                != upper_case_path.read_bytes(),
            }

        try:
            os.symlink(renamed_path.name, symlink_path)
        except OSError as error:
            symlink = _unsupported_observation(error)
        else:
            symlink = {
                "supported": True,
                "returned_successfully": True,
                "target_is_relative_and_internal": os.readlink(symlink_path)
                == renamed_path.name,
            }

        stream_digest, stream_bytes = _stream_digest(renamed_path, 32 * 1024)
        if stream_digest != replacement_digest or stream_bytes != size_bytes:
            raise StorageProbeError("streaming read verification failed")

        if symlink_path.is_symlink():
            symlink_path.unlink()
        if case_distinct["supported"]:
            upper_case_path.unlink()
        lower_case_path.unlink()
        renamed_path.unlink()
        disposable.rmdir()

        return {
            "schema_version": 1,
            "probe": "safe-storage-capabilities",
            "storage_context": storage_context,
            "storage_context_description": STORAGE_CONTEXTS[storage_context],
            "target_label": target_label,
            "disposable_child": disposable.name,
            "synthetic_size_bytes": size_bytes,
            "algorithm": "sha256",
            "observations": {
                "write_read_hash": {
                    "passed": True,
                    "bytes": size_bytes,
                    "digest": original_digest,
                },
                "file_fsync": {"returned_successfully": True},
                "ordinary_rename": {"passed": True},
                "replacement": {
                    "passed": True,
                    "primitive": "os.replace",
                    "digest": replacement_digest,
                },
                "directory_fsync": directory_fsync,
                "case_distinct_names": case_distinct,
                "symlink": symlink,
                "streaming_read": {
                    "passed": True,
                    "bytes": stream_bytes,
                    "chunk_size_bytes": 32 * 1024,
                },
                "cleanup": {"passed": True, "disposable_root_removed": True},
            },
            "limits": {
                "sudden_power_loss": "not performed",
                "physical_media_removal_during_write": "not performed",
                "durability_claim": "successful fsync calls do not prove controller-level or power-loss durability",
            },
        }
    except BaseException as error:
        if isinstance(error, (KeyboardInterrupt, SystemExit)):
            message = "probe interrupted; disposable child preserved for manual cleanup"
        else:
            message = "probe failed; disposable child preserved for manual cleanup"
        raise StorageProbeRunError(message, disposable.name) from error
