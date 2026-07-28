#!/usr/bin/env python3
"""Regression coverage for combined Android physical evidence."""

from __future__ import annotations

import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]
HISTORICAL = ROOT / "docs/spikes/phase-1/android-platform-proof/evidence/android-2026-07-24-sanitized.json"
TARGETED = ROOT / "docs/spikes/phase-1/android-platform-proof/evidence/android-2026-07-28-targeted-retest-sanitized.json"
REQUIRED_IDS = ("mp3-v0", "mp3-320", "flac", "aac", "ogg-vorbis", "wav")


class CombinedAndroidEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.historical = json.loads(HISTORICAL.read_text(encoding="utf-8"))
        cls.targeted = json.loads(TARGETED.read_text(encoding="utf-8"))

    def test_targeted_not_run_rows_do_not_erase_prior_passes(self) -> None:
        historical = {
            row["fixture_id"]: row["active_contract_evaluation"]
            for row in self.historical["format_observations"]
        }
        self.assertEqual(
            {fixture_id for fixture_id, result in historical.items() if result == "passed"},
            {"mp3-v0", "mp3-320", "flac", "aac", "ogg-vorbis"},
        )
        combined = self.targeted["combined_android_format_result"]
        self.assertTrue(combined["all_six_required_formats_pass"])
        self.assertEqual(
            {fixture_id for fixture_id in REQUIRED_IDS if combined[fixture_id] == "passed"},
            set(REQUIRED_IDS),
        )
        self.assertTrue(
            self.targeted["preserved_prior_evidence"][
                "targeted_not_run_rows_do_not_revoke_prior_passes"
            ]
        )

    def test_wav_passes_without_optional_metadata(self) -> None:
        wav = self.targeted["wav_targeted_result"]
        self.assertEqual(wav["disposition"], "passed")
        self.assertTrue(wav["required_playback_contract_result"])
        self.assertTrue(wav["end_of_track_observed"])
        self.assertFalse(wav["basic_metadata_result"])
        self.assertFalse(wav["basic_metadata_required_for_pb01"])

    def test_screen_off_interval_satisfies_minimum(self) -> None:
        proof = self.targeted["screen_off_playback"]
        self.assertEqual(proof["phase"], "COMPLETED")
        self.assertTrue(proof["playback_continued_while_screen_off"])
        self.assertTrue(proof["minimum_duration_reached"])
        self.assertTrue(proof["test_completed"])
        self.assertGreaterEqual(
            proof["observed_monotonic_duration_ms"],
            proof["required_duration_ms"],
        )

    def test_historical_nonrequired_observations_remain_nonrequired(self) -> None:
        contract = self.targeted["active_v1_format_contract"]
        self.assertEqual(contract["required_count"], 6)
        self.assertEqual(set(contract["required_format_ids"]), set(REQUIRED_IDS))
        self.assertEqual(
            set(contract["historical_nonrequired_format_ids"]),
            {"alac", "aiff"},
        )
        historical = self.targeted["historical_nonrequired_observations"]
        self.assertTrue(historical["retained_aiff_errors_are_historical"])
        self.assertEqual(historical["historical_error_date"], "2026-07-24")

    def test_raw_evidence_remains_untracked_by_contract(self) -> None:
        source = self.targeted["source_evidence"]
        self.assertFalse(source["raw_archive_tracked"])
        self.assertTrue(source["exact_member_allowlist_verified"])
        self.assertTrue(source["internal_checksums_verified"])
        self.assertTrue(source["privacy_scan_passed"])


if __name__ == "__main__":
    unittest.main()
