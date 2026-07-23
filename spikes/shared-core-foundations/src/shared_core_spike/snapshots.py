"""Experimental atomic snapshot and recovery proof for issue #8."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
from typing import Any, Callable


SCHEMA_VERSION = 1
LATEST = "snapshot.latest.json"
PREVIOUS = "snapshot.previous.json"
TEMPORARY = ".snapshot.latest.tmp"
PREVIOUS_TEMPORARY = ".snapshot.previous.tmp"


class SnapshotError(ValueError):
    """No safe snapshot operation or recovery result is available."""


def _canonical(value: Any) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def envelope(state: dict[str, Any]) -> dict[str, Any]:
    protected = {"schema_version": SCHEMA_VERSION, "state": state}
    return {**protected, "checksum": {"algorithm": "sha256", "digest": hashlib.sha256(_canonical(protected)).hexdigest()}}


def serialize(state: dict[str, Any]) -> bytes:
    return json.dumps(
        envelope(state), ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False
    ).encode("utf-8") + b"\n"


def deserialize(payload: bytes) -> dict[str, Any]:
    try:
        document = json.loads(payload)
    except (json.JSONDecodeError, UnicodeError) as error:
        raise SnapshotError("snapshot is truncated or malformed") from error
    if not isinstance(document, dict) or document.get("schema_version") != SCHEMA_VERSION:
        raise SnapshotError("snapshot schema version is unsupported")
    state = document.get("state")
    checksum = document.get("checksum")
    if not isinstance(state, dict) or not isinstance(checksum, dict) or checksum.get("algorithm") != "sha256":
        raise SnapshotError("snapshot envelope is incomplete")
    protected = {"schema_version": document["schema_version"], "state": state}
    actual = hashlib.sha256(_canonical(protected)).hexdigest()
    if checksum.get("digest") != actual:
        raise SnapshotError("snapshot checksum mismatch")
    return state


def _registered_root(root: Path) -> Path:
    resolved = root.resolve(strict=True)
    if not resolved.is_dir():
        raise SnapshotError("registered snapshot root must be a directory")
    return resolved


def _sync_file(path: Path) -> None:
    # Windows maps os.fsync() to the writable-descriptor _commit() API and
    # returns EBADF for a read-only descriptor. The file was just created by
    # this harness, so reopen it read/write without changing its bytes.
    with path.open("r+b") as stream:
        os.fsync(stream.fileno())


def _sync_directory(path: Path) -> None:
    if os.name == "nt":
        return
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def write(root: Path, state: dict[str, Any], fault: Callable[[str], None] | None = None) -> Path:
    """Write a checksummed latest snapshot while retaining one known-good prior."""

    directory = _registered_root(root)
    latest = directory / LATEST
    previous = directory / PREVIOUS
    temporary = directory / TEMPORARY
    previous_temporary = directory / PREVIOUS_TEMPORARY

    temporary.write_bytes(serialize(state))
    _sync_file(temporary)
    if fault:
        fault("after_temp_sync")

    if latest.exists():
        try:
            deserialize(latest.read_bytes())
        except SnapshotError as error:
            raise SnapshotError("refusing to retain a corrupt latest snapshot") from error
        shutil.copyfile(latest, previous_temporary)
        _sync_file(previous_temporary)
        os.replace(previous_temporary, previous)
        _sync_directory(directory)
        if fault:
            fault("after_prior_retained")

    if fault:
        fault("before_atomic_promotion")
    os.replace(temporary, latest)
    if fault:
        fault("after_atomic_promotion")
    _sync_directory(directory)
    return latest


def recover(root: Path) -> tuple[dict[str, Any], str]:
    directory = _registered_root(root)
    failures: list[str] = []
    for name in (LATEST, PREVIOUS):
        path = directory / name
        if not path.exists():
            failures.append(f"{name}: missing")
            continue
        try:
            return deserialize(path.read_bytes()), name
        except (OSError, SnapshotError) as error:
            failures.append(f"{name}: {error}")
    raise SnapshotError("no valid snapshot exists (" + "; ".join(failures) + ")")


def rebuild_from_events(root: Path, event_documents: list[dict[str, Any]]) -> dict[str, Any]:
    """Model a rebuild from validated events and persist the rebuilt state."""

    from .events import merge, public_state

    merged = merge(event_documents)
    rebuilt = {
        "event_state": public_state(merged),
        "applied_event_ids": merged["applied_event_ids"],
    }
    write(root, rebuilt)
    return rebuilt
