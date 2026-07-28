#!/usr/bin/env python3
"""Host tests for historical evidence compatibility."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("verify_evidence.py")
SPEC = importlib.util.spec_from_file_location("verify_evidence", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)


class HistoricalEvidenceCompatibilityTest(unittest.TestCase):
    def test_exact_six_active_formats(self) -> None:
        self.assertEqual(6, len(VERIFY.ACTIVE_REQUIRED))
        self.assertEqual(
            {"mp3-v0", "mp3-320", "flac", "aac", "ogg-vorbis", "wav"},
            set(VERIFY.ACTIVE_REQUIRED),
        )

    def test_historical_eight_classify_without_positional_assumptions(self) -> None:
        historical_ids = [
            "wav",
            "aiff",
            "mp3-320",
            "alac",
            "ogg-vorbis",
            "aac",
            "mp3-v0",
            "flac",
        ]
        scopes = {item: VERIFY.classify_scope(item) for item in historical_ids}
        self.assertEqual(8, len(scopes))
        self.assertEqual("nonrequired_historical_observation", scopes["alac"])
        self.assertEqual("nonrequired_historical_observation", scopes["aiff"])
        self.assertEqual("active_v1_required", scopes["wav"])

    def test_schema_subset_rejects_wrong_required_count(self) -> None:
        schema = {
            "type": "object",
            "additionalProperties": False,
            "required": ["active_required_count"],
            "properties": {"active_required_count": {"const": 6}},
        }
        VERIFY.validate_schema({"active_required_count": 6}, schema)
        with self.assertRaises(ValueError):
            VERIFY.validate_schema({"active_required_count": 8}, schema)


if __name__ == "__main__":
    unittest.main()
