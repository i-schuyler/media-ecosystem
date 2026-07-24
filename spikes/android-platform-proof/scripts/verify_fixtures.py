#!/usr/bin/env python3
"""Validate membership, provenance, hashes, and limits of the proof corpus."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_DIR = ROOT / "app" / "src" / "main" / "assets" / "fixtures"
REQUIRED = {
    "mp3-v0": "MP3 V0",
    "mp3-320": "MP3 320",
    "flac": "FLAC",
    "aac": "AAC",
    "ogg-vorbis": "Ogg Vorbis",
    "alac": "ALAC",
    "wav": "WAV",
    "aiff": "AIFF",
}
MAX_FILE_BYTES = 2_000_000
MAX_TOTAL_BYTES = 8_000_000


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    manifest_path = FIXTURE_DIR / "fixture-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert "synthetic" in manifest["provenance"].lower()
    assert "no human recording" in manifest["provenance"].lower()
    entries = manifest["fixtures"]
    assert len(entries) == 8
    assert {entry["id"]: entry["required_format"] for entry in entries} == REQUIRED
    assert len({entry["filename"] for entry in entries}) == 8
    expected_files = {entry["filename"] for entry in entries} | {
        "fixture-manifest.json",
        "SHA256SUMS",
    }
    actual_files = {path.name for path in FIXTURE_DIR.iterdir() if path.is_file()}
    assert actual_files == expected_files, (
        f"fixture directory membership mismatch: "
        f"missing={sorted(expected_files - actual_files)}, "
        f"unexpected={sorted(actual_files - expected_files)}"
    )

    expected_sums = []
    total = 0
    for entry in entries:
        path = FIXTURE_DIR / entry["filename"]
        assert path.is_file(), path
        assert path.stat().st_size == entry["size_bytes"]
        assert entry["size_bytes"] <= MAX_FILE_BYTES
        assert digest(path) == entry["sha256"]
        assert entry["expected_duration_ms"] >= 5_000
        assert entry["duration_tolerance_ms"] <= 500
        assert set(entry["expected_metadata"]) == {"title", "artist", "album"}
        assert entry["generator_arguments"]
        assert entry["container"]
        assert entry["codec"]
        assert entry["mime_type"].startswith("audio/")
        total += entry["size_bytes"]
        expected_sums.append(f"{entry['sha256']}  {entry['filename']}")
    assert total <= MAX_TOTAL_BYTES

    sums = (FIXTURE_DIR / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
    assert sums == expected_sums
    print(f"Verified 8 synthetic fixtures ({total} bytes), manifest, provenance, and hashes.")


if __name__ == "__main__":
    main()
