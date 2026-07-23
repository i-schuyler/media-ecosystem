from __future__ import annotations

import json
from pathlib import Path


SPIKE_ROOT = Path(__file__).resolve().parents[1]
TRACK_ID = "11111111-1111-4111-8111-111111111111"
FOLDER_ID = "44444444-4444-4444-8444-444444444444"
INSTANCE_A = "55555555-5555-4555-8555-555555555551"
INSTANCE_B = "55555555-5555-4555-8555-555555555552"
DEVICE_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
DEVICE_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"


def load_json(name: str):
    return json.loads((SPIKE_ROOT / "fixtures" / name).read_text(encoding="utf-8"))


def file_sidecar(
    *,
    instance_id: str = INSTANCE_A,
    device_id: str = DEVICE_A,
    logical_path: str = "Synthetic/Proof.flac",
):
    return {
        "schema_version": 1,
        "sidecar_kind": "file_identity",
        "track_id": TRACK_ID,
        "file_instance_id": instance_id,
        "device_id": device_id,
        "folder_id": FOLDER_ID,
        "logical_path": logical_path,
        "expected_content_hash": {"algorithm": "sha256", "digest": "ab" * 32},
        "extensions": {"synthetic_proof": {"retained": True}},
        "future_top_level_field": {"must_survive": True},
    }
