from __future__ import annotations

from pathlib import Path
from unittest import mock
import tempfile
import unittest

from shared_core_spike import storageprobe


class StorageProbeTests(unittest.TestCase):
    def test_probe_uses_unique_child_and_leaves_nonempty_parent_untouched(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "selected-target"
            target.mkdir()
            sentinel = target / "unrelated-sentinel.txt"
            sentinel.write_text("must remain untouched\n", encoding="utf-8")

            first = storageprobe.run_probe(
                target_root=target,
                storage_context="portable-sd-raw-path",
                target_label="synthetic-portable-root",
                size_bytes=64 * 1024,
            )
            second = storageprobe.run_probe(
                target_root=target,
                storage_context="portable-sd-raw-path",
                target_label="synthetic-portable-root",
                size_bytes=64 * 1024,
            )

            self.assertNotEqual(first["disposable_child"], second["disposable_child"])
            self.assertFalse((target / first["disposable_child"]).exists())
            self.assertFalse((target / second["disposable_child"]).exists())
            self.assertEqual("must remain untouched\n", sentinel.read_text(encoding="utf-8"))
            self.assertTrue(first["observations"]["write_read_hash"]["passed"])
            self.assertTrue(first["observations"]["replacement"]["passed"])
            self.assertTrue(first["observations"]["streaming_read"]["passed"])
            self.assertTrue(first["observations"]["cleanup"]["disposable_root_removed"])

    def test_unsupported_symlink_is_an_observation_not_a_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "selected-target"
            target.mkdir()
            with mock.patch(
                "shared_core_spike.storageprobe.os.symlink",
                side_effect=PermissionError(13, "synthetic denial"),
            ):
                result = storageprobe.run_probe(
                    target_root=target,
                    storage_context="android-shared-internal",
                    target_label="synthetic-shared-root",
                    size_bytes=4096,
                )
            self.assertFalse(result["observations"]["symlink"]["supported"])
            self.assertEqual("EACCES", result["observations"]["symlink"]["errno"])
            self.assertTrue(result["observations"]["cleanup"]["passed"])

    def test_refuses_filesystem_home_and_repository_roots(self):
        refused = [Path(Path.cwd().anchor), Path.home(), storageprobe.REPOSITORY_ROOT]
        for target in refused:
            with self.subTest(target=target):
                with self.assertRaises(storageprobe.StorageProbeError):
                    storageprobe.run_probe(
                        target_root=target,
                        storage_context="termux-private-internal",
                        target_label="refusal-test",
                        size_bytes=4096,
                    )

    def test_requires_explicit_known_context_and_sanitized_label(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "selected-target"
            target.mkdir()
            with self.assertRaises(storageprobe.StorageProbeError):
                storageprobe.run_probe(
                    target_root=target,
                    storage_context="guessed-storage",
                    target_label="safe-label",
                    size_bytes=4096,
                )
            with self.assertRaises(storageprobe.StorageProbeError):
                storageprobe.run_probe(
                    target_root=target,
                    storage_context="app-saf",
                    target_label="private/path",
                    size_bytes=4096,
                )

    def test_windows_internal_is_an_explicit_storage_context(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "selected-target"
            target.mkdir()
            result = storageprobe.run_probe(
                target_root=target,
                storage_context="windows-internal",
                target_label="synthetic-windows-root",
                size_bytes=4096,
            )
            self.assertEqual("windows-internal", result["storage_context"])
            self.assertEqual(
                "Windows internal storage", result["storage_context_description"]
            )

    def test_failure_preserves_named_child_for_manual_cleanup(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "selected-target"
            target.mkdir()
            with mock.patch(
                "shared_core_spike.storageprobe._stream_digest",
                side_effect=OSError("synthetic interrupted read"),
            ):
                with self.assertRaises(storageprobe.StorageProbeRunError) as raised:
                    storageprobe.run_probe(
                        target_root=target,
                        storage_context="termux-private-internal",
                        target_label="synthetic-private-root",
                        size_bytes=4096,
                    )
            child = raised.exception.disposable_child
            self.assertRegex(child, rf"^{storageprobe.PROBE_PREFIX}")
            self.assertTrue((target / child).is_dir())


if __name__ == "__main__":
    unittest.main()
