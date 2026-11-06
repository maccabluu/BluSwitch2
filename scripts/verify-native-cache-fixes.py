#!/usr/bin/env python3
"""Source-only patch checks. Does not compile or run the emulator."""

import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "native_fix", Path(__file__).with_name("apply-native-cache-fixes.py")
)
fix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fix)
UPSTREAM = Path(sys.argv.pop(1)).resolve()


class NativeCachePatchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name) / "eden"
        subprocess.run(
            ["git", "-C", str(UPSTREAM), "worktree", "add", "--detach", str(cls.root),
             fix.UPSTREAM_COMMIT], check=True, capture_output=True
        )

    @classmethod
    def tearDownClass(cls):
        subprocess.run(
            ["git", "-C", str(UPSTREAM), "worktree", "remove", "--force", str(cls.root)],
            check=True, capture_output=True
        )
        cls.temp.cleanup()

    def setUp(self):
        subprocess.run(
            ["git", "-C", str(self.root), "restore", str(fix.SOURCE)], check=True
        )

    def test_exact_source_patch_and_scope(self):
        fix.apply(self.root)
        changed = subprocess.check_output(
            ["git", "-C", str(self.root), "diff", "--name-only"], text=True
        ).splitlines()
        self.assertEqual(changed, [str(fix.SOURCE)])
        subprocess.run(["git", "-C", str(self.root), "diff", "--check"], check=True)

    def test_reapplication_is_idempotent(self):
        fix.apply(self.root)
        before = (self.root / fix.SOURCE).read_bytes()
        fix.apply(self.root)
        self.assertEqual((self.root / fix.SOURCE).read_bytes(), before)

    def test_modified_source_rejected_without_writing(self):
        source = self.root / fix.SOURCE
        source.write_bytes(source.read_bytes() + b"\n// unreviewed edit\n")
        before = source.read_bytes()
        with self.assertRaisesRegex(RuntimeError, "differs"):
            fix.apply(self.root)
        self.assertEqual(source.read_bytes(), before)

    def test_wrong_revision_rejected_without_writing(self):
        before = (self.root / fix.SOURCE).read_bytes()
        with patch.object(fix.subprocess, "check_output", return_value="wrong-revision\n"):
            with self.assertRaisesRegex(RuntimeError, "Unreviewed Eden revision"):
                fix.apply(self.root)
        self.assertEqual((self.root / fix.SOURCE).read_bytes(), before)

    def test_native_lifecycle_source_order(self):
        fix.apply(self.root)
        source = (self.root / fix.SOURCE).read_text()
        for start, end in [
            ("PipelineCache::~PipelineCache()", "GraphicsPipeline* PipelineCache::CurrentGraphicsPipeline"),
            ("void PipelineCache::LoadDiskResources", "const auto shader_dir"),
        ]:
            section = source[source.index(start):source.index(end, source.index(start))]
            operations = [
                "translation_workers.WaitForRequests();", "AdoptTranslatedGraphicsPipelines();",
                "    workers.WaitForRequests();", "serialization_thread.WaitForRequests();",
                "SerializeVulkanPipelineCache(",
            ]
            positions = [section.index(operation) for operation in operations]
            self.assertEqual(positions, sorted(positions))

    def test_native_snapshot_and_dedup_guards(self):
        fix.apply(self.root)
        source = (self.root / fix.SOURCE).read_text()
        save = source.split("void PipelineCache::SerializeVulkanPipelineCache(", 1)[1]
        save = save.split("vk::PipelineCache PipelineCache::LoadVulkanPipelineCache", 1)[0]
        self.assertNotIn("RemoveFile(filename)", save)
        self.assertIn("std::filesystem::remove(temporary, error)", save)
        self.assertLess(save.index("size_result != VK_SUCCESS"), save.index("file.open("))
        self.assertLess(save.index("data_result != VK_SUCCESS"), save.index("file.open("))
        self.assertLess(save.index("file.close();"), save.index("std::filesystem::rename("))
        self.assertIn("std::ofstream::failbit | std::ofstream::badbit", save)
        for name in ("compute", "graphics"):
            loader = source.split(f"const auto load_{name}", 1)[1]
            self.assertLess(loader.index(f"!queued_{name}.insert(key).second"),
                            loader.index("workers.QueueWork("))


if __name__ == "__main__":
    unittest.main(verbosity=2)
