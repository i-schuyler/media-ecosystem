#!/usr/bin/env python3
"""Parse the proof's JSON/XML and all repository workflow YAML files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

import yaml


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("repository_root", type=Path)
    args = parser.parse_args()
    root = args.repository_root.resolve()
    proof = root / "spikes" / "windows-platform-proof"

    ignored_parts = {"bin", "obj", "out"}
    json_files = sorted(
        path
        for path in proof.rglob("*.json")
        if not (set(path.relative_to(proof).parts) & ignored_parts)
    )
    xml_files = sorted(
        path
        for path in proof.rglob("*")
        if path.is_file()
        and path.suffix.lower() in {".csproj", ".props", ".config", ".manifest"}
        and not (set(path.relative_to(proof).parts) & ignored_parts)
    )
    yaml_files = sorted((root / ".github" / "workflows").glob("*.y*ml"))
    if not json_files or not xml_files or not yaml_files:
        raise AssertionError("Expected JSON, XML, and workflow YAML inputs.")

    for path in json_files:
        json.loads(path.read_text(encoding="utf-8-sig"))
    for path in xml_files:
        ET.parse(path)
    for path in yaml_files:
        parsed = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(parsed, dict):
            raise AssertionError(f"Workflow root is not a mapping: {path.name}")

    print(
        f"Parsed {len(json_files)} JSON, {len(xml_files)} XML, "
        f"and {len(yaml_files)} YAML files."
    )


if __name__ == "__main__":
    main()
