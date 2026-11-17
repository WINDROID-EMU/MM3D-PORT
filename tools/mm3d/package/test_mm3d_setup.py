import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mm3d_setup  # noqa: E402


class NlohmannIncludeTests(unittest.TestCase):
    def test_falls_back_to_android_cmake_dependency(self):
        with tempfile.TemporaryDirectory() as temporary:
            build_dir = Path(temporary)
            include = build_dir / "_deps" / "nlohmann_json-src" / "include"
            header = include / "nlohmann" / "json.hpp"
            header.parent.mkdir(parents=True)
            header.write_text("// synthetic test header\n", encoding="utf-8")
            with patch.dict(os.environ, {"TRIAEVUM_BUILD_DIR": str(build_dir)}):
                self.assertEqual(mm3d_setup.resolve_nlohmann_include(), include.resolve())

    def test_uses_explicit_include_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            include = Path(temporary)
            header = include / "nlohmann" / "json.hpp"
            header.parent.mkdir()
            header.write_text("// synthetic test header\n", encoding="utf-8")
            self.assertEqual(mm3d_setup.resolve_nlohmann_include(include), include.resolve())

    def test_rejects_include_root_without_json_header(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(FileNotFoundError):
                mm3d_setup.resolve_nlohmann_include(Path(temporary))


if __name__ == "__main__":
    unittest.main()
