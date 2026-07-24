#!/usr/bin/env python3
"""Verify that the disposable debug APK packages the complete proof corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    required = {
        "assets/fixtures/mp3-v0.mp3",
        "assets/fixtures/mp3-320.mp3",
        "assets/fixtures/flac.flac",
        "assets/fixtures/aac.m4a",
        "assets/fixtures/ogg-vorbis.ogg",
        "assets/fixtures/alac.m4a",
        "assets/fixtures/wav.wav",
        "assets/fixtures/aiff.aiff",
        "assets/fixtures/fixture-manifest.json",
        "assets/fixtures/SHA256SUMS",
        "assets/evidence/evidence-schema-v1.json",
    }
    with zipfile.ZipFile(args.apk) as archive:
        names = set(archive.namelist())
        missing = required - names
        assert not missing, f"APK missing entries: {sorted(missing)}"
        manifest = json.loads(archive.read("assets/fixtures/fixture-manifest.json"))
        assert len(manifest["fixtures"]) == 8
        expected_audio = {
            f"assets/fixtures/{entry['filename']}" for entry in manifest["fixtures"]
        }
        actual_audio = {
            name for name in names
            if name.startswith("assets/fixtures/")
            and name.endswith((".mp3", ".flac", ".m4a", ".ogg", ".wav", ".aiff"))
        }
        assert actual_audio == expected_audio
        for entry in manifest["fixtures"]:
            packaged = archive.read(f"assets/fixtures/{entry['filename']}")
            assert hashlib.sha256(packaged).hexdigest() == entry["sha256"]
            assert len(packaged) == entry["size_bytes"]
        assert not any(name.endswith((".p12", ".pfx", ".pem", ".key")) for name in names)
    print(f"Verified proof APK contents: {args.apk}")


if __name__ == "__main__":
    main()
