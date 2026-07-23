"""Experimental portable logical-path contract for issue #6."""

from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import re
import unicodedata


MAX_COMPONENT_UTF8_BYTES = 255
MAX_PATH_UTF8_BYTES = 1024
_DRIVE_PATH = re.compile(r"^[A-Za-z]:")
_WINDOWS_INVALID = frozenset('<>:"|?*')
_WINDOWS_RESERVED = {
    "CON",
    "PRN",
    "AUX",
    "NUL",
    *(f"COM{number}" for number in range(1, 10)),
    *(f"LPT{number}" for number in range(1, 10)),
}


class PathContractError(ValueError):
    """A logical path violates the experimental shared-path contract."""


class RootUnavailableError(PathContractError):
    """A device-local root is currently missing or unmounted."""


@dataclass(frozen=True)
class CanonicalPath:
    display: str
    comparison_key: str


def _component_key(component: str) -> str:
    return unicodedata.normalize("NFC", component).casefold()


def canonicalize(raw: str) -> CanonicalPath:
    """Return a deterministic logical path or fail closed.

    The result never contains a device-local physical root.
    """

    if not isinstance(raw, str) or not raw:
        raise PathContractError("path must be a non-empty string")
    if raw.startswith(("/", "\\")):
        raise PathContractError("absolute and UNC paths are device-local")
    if _DRIVE_PATH.match(raw):
        raise PathContractError("drive-letter paths are device-local")

    normalized_separators = raw.replace("\\", "/")
    components = normalized_separators.split("/")
    if any(component == "" for component in components):
        raise PathContractError("empty path components are unsafe")

    canonical_components: list[str] = []
    comparison_components: list[str] = []
    for component in components:
        if component in {".", ".."}:
            raise PathContractError("dot traversal components are forbidden")
        canonical = unicodedata.normalize("NFC", component)
        if canonical.endswith((".", " ")):
            raise PathContractError("trailing dots or spaces are not portable")
        if any(character in _WINDOWS_INVALID or ord(character) < 32 for character in canonical):
            raise PathContractError("component contains a Windows-invalid character")
        if canonical.split(".", 1)[0].upper() in _WINDOWS_RESERVED:
            raise PathContractError("component uses a Windows-reserved name")
        if len(canonical.encode("utf-8")) > MAX_COMPONENT_UTF8_BYTES:
            raise PathContractError("component exceeds the experimental UTF-8 byte policy")
        canonical_components.append(canonical)
        comparison_components.append(_component_key(canonical))

    display = "/".join(canonical_components)
    if len(display.encode("utf-8")) > MAX_PATH_UTF8_BYTES:
        raise PathContractError("path exceeds the experimental UTF-8 byte policy")
    return CanonicalPath(display=display, comparison_key="/".join(comparison_components))


def detect_case_collisions(paths: list[str]) -> dict[str, list[str]]:
    """Return comparison-key collisions without selecting a winner."""

    grouped: dict[str, list[str]] = {}
    for path in paths:
        value = canonicalize(path)
        grouped.setdefault(value.comparison_key, []).append(value.display)
    return {key: values for key, values in grouped.items() if len(values) > 1}


def rename(path: str, new_name: str) -> CanonicalPath:
    current = canonicalize(path)
    if "/" in new_name or "\\" in new_name:
        raise PathContractError("rename accepts one component")
    parent, separator, _ = current.display.rpartition("/")
    return canonicalize(f"{parent}{separator}{new_name}" if separator else new_name)


def move(path: str, new_parent: str) -> CanonicalPath:
    current = canonicalize(path)
    parent = canonicalize(new_parent)
    name = current.display.rsplit("/", 1)[-1]
    return canonicalize(f"{parent.display}/{name}")


class DeviceLocalRoot:
    """Resolve logical paths beneath one registered disposable physical root."""

    def __init__(self, registered_root: Path):
        self._root = registered_root.resolve(strict=True)
        if not self._root.is_dir():
            raise PathContractError("registered root must be a directory")

    @property
    def physical_root(self) -> Path:
        return self._root

    @property
    def available(self) -> bool:
        return self._root.is_dir()

    def relink(self, changed_root: Path) -> None:
        candidate = changed_root.resolve(strict=True)
        if not candidate.is_dir():
            raise PathContractError("relinked root must be a directory")
        self._root = candidate

    def resolve(self, logical_path: str) -> Path:
        if not self.available:
            raise RootUnavailableError("registered storage is unavailable; no deletion is implied")
        logical = canonicalize(logical_path)
        candidate = Path(os.path.abspath(self._root.joinpath(*logical.display.split("/"))))
        if os.path.commonpath((self._root, candidate)) != str(self._root):
            raise PathContractError("logical path escaped its registered root")
        return candidate
