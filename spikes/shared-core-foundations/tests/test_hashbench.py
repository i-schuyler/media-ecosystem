from __future__ import annotations

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
            sizes_bytes=[32 * 1024], repeats=2, chunk_size=4096, device_label="synthetic-ci"
        )
        self.assertEqual("sha256", result["measurements"][0]["algorithm"])
        self.assertEqual(2, len(result["measurements"]))
        self.assertEqual(
            result["measurements"][0]["digest"], result["measurements"][1]["digest"]
        )
        self.assertIn("never a logical Track ID", result["identity_warning"])

    def test_result_outputs_are_small_structured_files(self):
        result = hashbench.benchmark(
            sizes_bytes=[4096], repeats=2, chunk_size=1024, device_label="synthetic-output"
        )
        with tempfile.TemporaryDirectory() as temporary:
            json_path = Path(temporary) / "result.json"
            markdown_path = Path(temporary) / "result.md"
            hashbench.write_results(result, json_path, markdown_path)
            self.assertIn('"schema_version": 1', json_path.read_text(encoding="utf-8"))
            self.assertIn("Experimental VPS evidence only", markdown_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
