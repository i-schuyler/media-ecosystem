"""Experimental JSON identity-sidecar proof for issue #7."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import tempfile
from typing import Any, Callable
import uuid

from .paths import canonicalize


SCHEMA_VERSION = 1


class SidecarError(ValueError):
    """Identity evidence is missing, malformed, ambiguous, or unsafe."""


@dataclass(frozen=True)
class Unidentified:
    reason: str


@dataclass
class Sidecar:
    document: dict[str, Any]

    @property
    def kind(self) -> str:
        return self.document["sidecar_kind"]

    def update_logical_path(self, logical_path: str) -> None:
        self.document["logical_path"] = canonicalize(logical_path).display


def _object_without_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise SidecarError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _valid_uuid(value: Any, field: str) -> None:
    if not isinstance(value, str):
        raise SidecarError(f"{field} must be a UUID string")
    try:
        parsed = uuid.UUID(value)
    except (ValueError, AttributeError) as error:
        raise SidecarError(f"{field} must be a valid UUID") from error
    if str(parsed) != value.lower():
        raise SidecarError(f"{field} must use canonical lowercase UUID text")


def validate(document: dict[str, Any]) -> Sidecar:
    if type(document.get("schema_version")) is not int or document["schema_version"] != SCHEMA_VERSION:
        raise SidecarError("unsupported sidecar schema_version")
    kind = document.get("sidecar_kind")
    if kind not in {"file_identity", "folder_identity"}:
        raise SidecarError("unsupported sidecar_kind")
    if "logical_path" in document:
        canonical = canonicalize(document["logical_path"])
        if canonical.display != document["logical_path"]:
            raise SidecarError("logical_path must already be canonical")
    if kind == "file_identity":
        for field in ("track_id", "file_instance_id", "device_id"):
            _valid_uuid(document.get(field), field)
        if "folder_id" in document:
            _valid_uuid(document["folder_id"], "folder_id")
        identifiers = [document["track_id"], document["file_instance_id"]]
        if "folder_id" in document:
            identifiers.append(document["folder_id"])
        if len(identifiers) != len(set(identifiers)):
            raise SidecarError("identifier fields within one sidecar must be distinct")
        expected_hash = document.get("expected_content_hash")
        if expected_hash is not None:
            if not isinstance(expected_hash, dict) or expected_hash.get("algorithm") != "sha256":
                raise SidecarError("expected_content_hash must identify sha256")
            digest = expected_hash.get("digest")
            if not isinstance(digest, str) or len(digest) != 64:
                raise SidecarError("expected_content_hash digest must be 64 hexadecimal characters")
            try:
                bytes.fromhex(digest)
            except ValueError as error:
                raise SidecarError("expected_content_hash digest is not hexadecimal") from error
            if digest != digest.lower():
                raise SidecarError("expected_content_hash digest must be lowercase")
    else:
        _valid_uuid(document.get("folder_id"), "folder_id")
    extensions = document.get("extensions", {})
    if not isinstance(extensions, dict):
        raise SidecarError("extensions must be an object")
    return Sidecar(document=document)


def loads(payload: str) -> Sidecar:
    try:
        document = json.loads(payload, object_pairs_hook=_object_without_duplicates)
    except (json.JSONDecodeError, TypeError) as error:
        raise SidecarError("malformed sidecar JSON") from error
    if not isinstance(document, dict):
        raise SidecarError("sidecar must be a JSON object")
    return validate(document)


def dumps(sidecar: Sidecar) -> str:
    validate(sidecar.document)
    return json.dumps(sidecar.document, ensure_ascii=False, sort_keys=True, indent=2) + "\n"


def read(path: Path) -> Sidecar | Unidentified:
    if not path.exists():
        return Unidentified("identity sidecar is missing; no identity was guessed")
    try:
        return loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError) as error:
        raise SidecarError("identity sidecar could not be read") from error


def _safe_target(registered_root: Path, relative_target: str) -> Path:
    root = registered_root.resolve(strict=True)
    relative = canonicalize(relative_target)
    target = root.joinpath(*relative.display.split("/"))
    parent = target.parent.resolve(strict=True)
    if os.path.commonpath((root, parent)) != str(root):
        raise SidecarError("sidecar target escaped the registered root")
    return target


def atomic_write(
    registered_root: Path,
    relative_target: str,
    sidecar: Sidecar,
    fault: Callable[[str], None] | None = None,
) -> Path:
    """Atomically replace a sidecar beneath a registered disposable root."""

    target = _safe_target(registered_root, relative_target)
    payload = dumps(sidecar).encode("utf-8")
    temporary: Path | None = None
    try:
        descriptor, temporary_name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".tmp", dir=target.parent)
        temporary = Path(temporary_name)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        if fault:
            fault("after_temp_sync")
        os.replace(temporary, target)
        temporary = None
        if fault:
            fault("after_promotion")
        _sync_directory(target.parent)
        return target
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def _sync_directory(directory: Path) -> None:
    if os.name == "nt":
        return
    descriptor = os.open(directory, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def duplicate_track_ids_on_device(sidecars: list[Sidecar], device_id: str) -> dict[str, list[str]]:
    _valid_uuid(device_id, "device_id")
    grouped: dict[str, list[str]] = {}
    for sidecar in sidecars:
        if sidecar.kind != "file_identity" or sidecar.document["device_id"] != device_id:
            continue
        grouped.setdefault(sidecar.document["track_id"], []).append(sidecar.document["file_instance_id"])
    return {track_id: instances for track_id, instances in grouped.items() if len(instances) > 1}


def validate_inventory(sidecars: list[Sidecar]) -> None:
    """Reject physical/folder identifier reuse while allowing Track-ID review."""

    owners: dict[tuple[str, str], int] = {}
    for index, sidecar in enumerate(sidecars):
        fields = ("file_instance_id",) if sidecar.kind == "file_identity" else ("folder_id",)
        for field in fields:
            key = (field, sidecar.document[field])
            if key in owners:
                raise SidecarError(f"duplicate {field} in device inventory")
            owners[key] = index


def model_explicit_replacement(
    original: Sidecar,
    *,
    new_file_instance_id: str,
    new_content: bytes,
    explicit: bool,
) -> Sidecar:
    if original.kind != "file_identity":
        raise SidecarError("only file identities can model media replacement")
    if not explicit:
        raise SidecarError("replacement may retain Track ID only after explicit authorization")
    document = dict(original.document)
    document["file_instance_id"] = new_file_instance_id
    document["expected_content_hash"] = {
        "algorithm": "sha256",
        "digest": hashlib.sha256(new_content).hexdigest(),
    }
    return validate(document)
