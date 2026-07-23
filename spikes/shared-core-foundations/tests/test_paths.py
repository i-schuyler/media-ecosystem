from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from shared_core_spike import paths
from support import load_json


class PathVectorTests(unittest.TestCase):
    def test_machine_readable_vectors(self):
        corpus = load_json("path-vectors.json")
        for vector in corpus["vectors"]:
            with self.subTest(vector["name"]):
                if vector["valid"]:
                    result = paths.canonicalize(vector["input"])
                    self.assertEqual(vector["canonical"], result.display)
                    self.assertEqual(vector["comparison_key"], result.comparison_key)
                else:
                    with self.assertRaisesRegex(paths.PathContractError, vector["error"]):
                        paths.canonicalize(vector["input"])

    def test_collision_vectors_stop_for_review(self):
        corpus = load_json("path-vectors.json")
        for vector in corpus["collision_sets"]:
            with self.subTest(vector["name"]):
                self.assertIn(vector["collision_key"], paths.detect_case_collisions(vector["paths"]))

    def test_component_and_path_limits(self):
        paths.canonicalize("a" * paths.MAX_COMPONENT_UTF8_BYTES)
        with self.assertRaisesRegex(paths.PathContractError, "component exceeds"):
            paths.canonicalize("a" * (paths.MAX_COMPONENT_UTF8_BYTES + 1))
        component = "b" * 200
        within = "/".join([component] * 5)
        self.assertLessEqual(len(within.encode()), paths.MAX_PATH_UTF8_BYTES)
        paths.canonicalize(within)
        with self.assertRaisesRegex(paths.PathContractError, "path exceeds"):
            paths.canonicalize("/".join([component] * 6))

    def test_folder_and_file_rename_move_round_trips(self):
        renamed_folder = paths.rename("Synthetic Artist/Old Album", "New Album")
        self.assertEqual("Synthetic Artist/New Album", renamed_folder.display)
        renamed = paths.rename("Synthetic Album/Proof.flac", "Renamed.flac")
        moved = paths.move(renamed.display, "Synthetic Artist/Moved Album")
        self.assertEqual("Synthetic Artist/Moved Album/Renamed.flac", moved.display)
        self.assertEqual(moved, paths.canonicalize(moved.display))

    def test_root_relink_changes_only_device_local_resolution(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            first = base / "removable-root-a"
            second = base / "removable-root-b"
            first.mkdir()
            second.mkdir()
            registry = paths.DeviceLocalRoot(first)
            logical = "Synthetic/Proof.flac"
            first_resolution = registry.resolve(logical)
            registry.relink(second)
            second_resolution = registry.resolve(logical)
            self.assertEqual("Synthetic/Proof.flac", paths.canonicalize(logical).display)
            self.assertNotEqual(first_resolution, second_resolution)
            self.assertEqual(second.resolve(), second_resolution.parents[1])

    def test_missing_root_is_unavailable_not_deletion(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "registered-removable-root"
            root.mkdir()
            registry = paths.DeviceLocalRoot(root)
            root.rmdir()
            self.assertFalse(registry.available)
            with self.assertRaisesRegex(paths.RootUnavailableError, "no deletion is implied"):
                registry.resolve("Synthetic/Proof.flac")


if __name__ == "__main__":
    unittest.main()
