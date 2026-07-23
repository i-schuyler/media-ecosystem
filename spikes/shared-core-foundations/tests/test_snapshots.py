from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from shared_core_spike import snapshots
from support import load_json


class SnapshotTests(unittest.TestCase):
    def test_serialization_is_deterministic_and_checksummed(self):
        first = {"z": [2, 1], "a": {"name": "Synthetic"}}
        second = {"a": {"name": "Synthetic"}, "z": [2, 1]}
        self.assertEqual(snapshots.serialize(first), snapshots.serialize(second))
        self.assertEqual(first, snapshots.deserialize(snapshots.serialize(first)))

    def test_prior_known_good_is_retained(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            snapshots.write(root, {"generation": 1})
            snapshots.write(root, {"generation": 2})
            latest, source = snapshots.recover(root)
            self.assertEqual({"generation": 2}, latest)
            self.assertEqual(snapshots.LATEST, source)
            self.assertEqual(
                {"generation": 1}, snapshots.deserialize((root / snapshots.PREVIOUS).read_bytes())
            )

    def test_repeatable_faults_before_promotion_leave_latest_valid(self):
        for point in ("after_temp_sync", "after_prior_retained", "before_atomic_promotion"):
            with self.subTest(point), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                snapshots.write(root, {"generation": 1})

                def fault(observed: str):
                    if observed == point:
                        raise RuntimeError(point)

                with self.assertRaisesRegex(RuntimeError, point):
                    snapshots.write(root, {"generation": 2}, fault)
                recovered, source = snapshots.recover(root)
                self.assertEqual({"generation": 1}, recovered)
                self.assertEqual(snapshots.LATEST, source)

    def test_fault_after_atomic_promotion_leaves_complete_new_snapshot(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            snapshots.write(root, {"generation": 1})

            def fault(point: str):
                if point == "after_atomic_promotion":
                    raise RuntimeError(point)

            with self.assertRaisesRegex(RuntimeError, "after_atomic_promotion"):
                snapshots.write(root, {"generation": 2}, fault)
            recovered, _ = snapshots.recover(root)
            self.assertEqual({"generation": 2}, recovered)

    def test_corrupt_latest_falls_back_to_prior(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            snapshots.write(root, {"generation": 1})
            snapshots.write(root, {"generation": 2})
            (root / snapshots.LATEST).write_bytes(b'{"truncated":')
            recovered, source = snapshots.recover(root)
            self.assertEqual({"generation": 1}, recovered)
            self.assertEqual(snapshots.PREVIOUS, source)

    def test_explicit_failure_when_no_valid_snapshot_exists(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / snapshots.LATEST).write_bytes(b"not-json")
            with self.assertRaisesRegex(snapshots.SnapshotError, "no valid snapshot exists"):
                snapshots.recover(root)

    def test_rebuild_from_valid_events_matches_direct_event_state(self):
        from shared_core_spike import events

        corpus = load_json("event-corpus.json")["events"]
        expected_model = events.merge(corpus)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            rebuilt = snapshots.rebuild_from_events(root, corpus)
            recovered, _ = snapshots.recover(root)
        self.assertEqual(events.public_state(expected_model), rebuilt["event_state"])
        self.assertEqual(rebuilt, recovered)


if __name__ == "__main__":
    unittest.main()
