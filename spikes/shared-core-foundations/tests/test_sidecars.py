from __future__ import annotations

import copy
import os
from pathlib import Path
import tempfile
import unittest

from shared_core_spike import sidecars
from support import DEVICE_A, DEVICE_B, FOLDER_ID, INSTANCE_A, INSTANCE_B, TRACK_ID, file_sidecar


class SidecarTests(unittest.TestCase):
    def test_file_and_folder_round_trip_is_deterministic(self):
        file_identity = sidecars.validate(file_sidecar())
        serialized = sidecars.dumps(file_identity)
        self.assertEqual(serialized, sidecars.dumps(sidecars.loads(serialized)))
        folder = sidecars.validate(
            {
                "schema_version": 1,
                "sidecar_kind": "folder_identity",
                "folder_id": FOLDER_ID,
                "logical_path": "Synthetic Artist/Synthetic Album",
                "extensions": {"future": "preserved"},
            }
        )
        self.assertEqual(folder.document, sidecars.loads(sidecars.dumps(folder)).document)
        folder.update_logical_path("Renamed Artist/Moved Album")
        self.assertEqual(FOLDER_ID, sidecars.loads(sidecars.dumps(folder)).document["folder_id"])

    def test_move_and_rename_preserve_ids_and_unknown_fields(self):
        identity = sidecars.validate(file_sidecar())
        before = {key: identity.document[key] for key in ("track_id", "file_instance_id", "folder_id")}
        identity.update_logical_path("Moved Album/Renamed.flac")
        after = sidecars.loads(sidecars.dumps(identity))
        self.assertEqual(before, {key: after.document[key] for key in before})
        self.assertEqual({"must_survive": True}, after.document["future_top_level_field"])

    def test_intended_device_copies_share_track_but_not_instance(self):
        first = sidecars.validate(file_sidecar(instance_id=INSTANCE_A, device_id=DEVICE_A))
        second = sidecars.validate(file_sidecar(instance_id=INSTANCE_B, device_id=DEVICE_B))
        self.assertEqual(first.document["track_id"], second.document["track_id"])
        self.assertNotEqual(first.document["file_instance_id"], second.document["file_instance_id"])
        self.assertEqual({}, sidecars.duplicate_track_ids_on_device([first, second], DEVICE_A))
        sidecars.validate_inventory([first, second])

        same_device_copy = sidecars.validate(file_sidecar(instance_id=INSTANCE_B, device_id=DEVICE_A, logical_path="Copy/Proof.flac"))
        duplicates = sidecars.duplicate_track_ids_on_device([first, same_device_copy], DEVICE_A)
        self.assertEqual([INSTANCE_A, INSTANCE_B], duplicates[TRACK_ID])

        repeated_instance = sidecars.validate(file_sidecar(instance_id=INSTANCE_A, device_id=DEVICE_A, logical_path="Copy/Proof.flac"))
        with self.assertRaisesRegex(sidecars.SidecarError, "duplicate file_instance_id"):
            sidecars.validate_inventory([first, repeated_instance])

    def test_invalid_duplicate_and_missing_identity_fail_closed(self):
        invalid = file_sidecar()
        invalid["track_id"] = "derived-from-file-name"
        with self.assertRaisesRegex(sidecars.SidecarError, "valid UUID"):
            sidecars.validate(invalid)
        duplicate_identifier = file_sidecar()
        duplicate_identifier["folder_id"] = duplicate_identifier["track_id"]
        with self.assertRaisesRegex(sidecars.SidecarError, "must be distinct"):
            sidecars.validate(duplicate_identifier)
        duplicate_json_key = '{"schema_version":1,"schema_version":1}'
        with self.assertRaisesRegex(sidecars.SidecarError, "duplicate JSON key"):
            sidecars.loads(duplicate_json_key)
        with tempfile.TemporaryDirectory() as temporary:
            unidentified = sidecars.read(Path(temporary) / "missing.identity.json")
            self.assertIsInstance(unidentified, sidecars.Unidentified)
            self.assertIn("no identity was guessed", unidentified.reason)

    def test_atomic_write_retains_old_sidecar_when_interrupted(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "identities").mkdir()
            original = sidecars.validate(file_sidecar())
            target = sidecars.atomic_write(root, "identities/proof.identity.json", original)
            changed = sidecars.validate(file_sidecar(logical_path="Moved/Proof.flac"))

            def fault(point: str):
                if point == "after_temp_sync":
                    raise RuntimeError("synthetic interruption")

            with self.assertRaisesRegex(RuntimeError, "synthetic interruption"):
                sidecars.atomic_write(root, "identities/proof.identity.json", changed, fault)
            self.assertEqual(original.document, sidecars.read(target).document)
            self.assertEqual([], list(target.parent.glob("*.tmp")))

    def test_target_cannot_escape_registered_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            identity = sidecars.validate(file_sidecar())
            with self.assertRaises(sidecars.PathContractError if hasattr(sidecars, "PathContractError") else ValueError):
                sidecars.atomic_write(root, "../outside.identity.json", identity)
            with self.assertRaises(ValueError):
                sidecars.atomic_write(root, "/outside.identity.json", identity)

    @unittest.skipIf(os.name == "nt", "Windows symlink creation requires environment-specific privileges")
    def test_symlink_parent_cannot_escape_registered_root(self):
        with tempfile.TemporaryDirectory() as temporary, tempfile.TemporaryDirectory() as outside:
            root = Path(temporary)
            (root / "escape").symlink_to(Path(outside), target_is_directory=True)
            with self.assertRaisesRegex(sidecars.SidecarError, "escaped"):
                sidecars.atomic_write(root, "escape/proof.identity.json", sidecars.validate(file_sidecar()))

    def test_track_id_retention_requires_explicit_replacement(self):
        original = sidecars.validate(file_sidecar())
        with self.assertRaisesRegex(sidecars.SidecarError, "explicit authorization"):
            sidecars.model_explicit_replacement(
                original, new_file_instance_id=INSTANCE_B, new_content=b"replacement", explicit=False
            )
        replacement = sidecars.model_explicit_replacement(
            original, new_file_instance_id=INSTANCE_B, new_content=b"replacement", explicit=True
        )
        self.assertEqual(TRACK_ID, replacement.document["track_id"])
        self.assertEqual(INSTANCE_B, replacement.document["file_instance_id"])
        self.assertNotEqual(original.document["expected_content_hash"], replacement.document["expected_content_hash"])

    def test_identity_is_not_derived_from_mutable_properties(self):
        first = sidecars.validate(file_sidecar(logical_path="A/One.flac"))
        changed_document = copy.deepcopy(first.document)
        changed_document["logical_path"] = "B/Two.flac"
        changed_document["expected_content_hash"]["digest"] = "cd" * 32
        changed = sidecars.validate(changed_document)
        self.assertEqual(first.document["track_id"], changed.document["track_id"])


if __name__ == "__main__":
    unittest.main()
