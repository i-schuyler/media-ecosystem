from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from shared_core_spike import hashbench


class HashBenchmarkTests(unittest.TestCase):
    def test_streamed_hash_is_repeatable_and_detects_one_byte_mutation(self):
        result = hashbench.correctness_probe(size_bytes=64 * 1024, chunk_size=4096)
        self.assertTrue(result["repeated_digest_equal"])
        self.assertTrue(result["one_byte_mutation_detected"])

    def test_smoke_benchmark_records_required_metadata(self):
        result = hashbench.benchmark(
            sizes_bytes=[32 * 1024],
            repeats=2,
            chunk_size=4096,
            device_label="synthetic-ci",
            platform_kind="other",
        )
        self.assertEqual("sha256", result["measurements"][0]["algorithm"])
        self.assertEqual(2, len(result["measurements"]))
        self.assertEqual(
            result["measurements"][0]["digest"], result["measurements"][1]["digest"]
        )
        self.assertIn("never a logical Track ID", result["identity_warning"])

    def test_result_outputs_are_small_structured_files(self):
        result = hashbench.benchmark(
            sizes_bytes=[4096],
            repeats=2,
            chunk_size=1024,
            device_label="synthetic-output",
            platform_kind="vps",
        )
        with tempfile.TemporaryDirectory() as temporary:
            json_path = Path(temporary) / "result.json"
            markdown_path = Path(temporary) / "result.md"
            hashbench.write_results(result, json_path, markdown_path)
            self.assertIn('"schema_version": 2', json_path.read_text(encoding="utf-8"))
            self.assertIn("Experimental VPS evidence only", markdown_path.read_text(encoding="utf-8"))

    def test_android_report_uses_recorded_environment_without_vps_language(self):
        result = hashbench.benchmark(
            sizes_bytes=[4096],
            repeats=2,
            chunk_size=1024,
            device_label="android-test-device",
            platform_kind="android",
            os_label="Android 16 test-build",
            architecture="aarch64",
            runtime_label="Python 3.14.6",
        )
        report = hashbench.render_markdown(result)
        self.assertIn("Experimental Android evidence only", report)
        self.assertIn("Android 16 test-build", report)
        self.assertIn("aarch64", report)
        self.assertIn("Python 3.14.6", report)
        self.assertNotIn("VPS", report)
        self.assertNotIn("not available on this VPS", report)
        self.assertIn(hashbench.MISSING_OBSERVATION, report)

    def test_report_renders_explicit_optional_environment_fields(self):
        result = hashbench.benchmark(
            sizes_bytes=[4096],
            repeats=2,
            chunk_size=1024,
            device_label="android-test-device",
            platform_kind="android",
        )
        result["environment"].update(
            {
                "device_model": "Synthetic Android Device",
                "os_build": "synthetic-build",
                "kernel": "synthetic-kernel",
                "storage_context": "synthetic storage",
                "filesystem": "synthetic filesystem",
            }
        )
        report = hashbench.render_markdown(result)
        for recorded_value in result["environment"].values():
            self.assertIn(recorded_value, report)

    def test_windows_report_never_infers_platform_from_device_label(self):
        result = hashbench.benchmark(
            sizes_bytes=[4096],
            repeats=2,
            chunk_size=1024,
            device_label="contains-vps-text",
            platform_kind="windows",
            os_label="Windows 11 test-build",
        )
        report = hashbench.render_markdown(result)
        self.assertIn("Experimental Windows evidence only", report)
        self.assertNotIn("Experimental VPS evidence only", report)

    def test_committed_reports_regenerate_from_recorded_platform_inputs(self):
        results_root = Path(__file__).resolve().parents[1] / "results"
        for stem, expected_scope in (
            ("vps-sha256", "VPS"),
            ("android-internal-sha256", "Android"),
            ("android-portable-sd-sha256", "Android"),
        ):
            with self.subTest(stem=stem):
                result = json.loads(
                    (results_root / f"{stem}.json").read_text(encoding="utf-8")
                )
                report = (results_root / f"{stem}.md").read_text(encoding="utf-8")
                self.assertEqual(hashbench.render_markdown(result), report)
                self.assertIn(f"Experimental {expected_scope} evidence only", report)
                if expected_scope != "VPS":
                    self.assertNotIn("not available on this VPS", report)


if __name__ == "__main__":
    unittest.main()
