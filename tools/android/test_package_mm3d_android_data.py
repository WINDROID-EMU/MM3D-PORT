import tempfile
from pathlib import Path
import unittest
import zipfile

from package_mm3d_android_data import MAX_ENTRY_BYTES, package


class Mm3dDataPackageTests(unittest.TestCase):
    def fixture(self, root: Path) -> Path:
        stage = root / "stage"
        for relative in (
            "TriAevum.android.launch.json", "process-manifest.json",
            "game/exheader.bin", "game/code.bin", "game/romfs.bin",
            "data/config/TriAevum.android.json", "resources/gamecontrollerdb.txt",
        ):
            path = stage / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"synthetic fixture")
        (stage / "TriAevum.android.launch.json").write_text("{}\n")
        return stage

    def test_packages_only_private_data_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stage = self.fixture(root)
            (stage / "data/savedata").mkdir()
            (stage / "data/savedata/user.save").write_bytes(b"must not be included")
            output = root / "transfer/mm3d-data.zip"
            package(stage, output)
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
            self.assertIn("game/romfs.bin", names)
            self.assertIn("resources/gamecontrollerdb.txt", names)
            self.assertNotIn("data/savedata/user.save", names)
            self.assertFalse(any(".so" in name for name in names))

    def test_filters_native_modules_and_rejects_in_repo_outputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stage = self.fixture(root)
            (stage / "private").mkdir()
            (stage / "private/mm3d_title_aot.so").write_bytes(b"private module")
            output = root / "filtered.zip"
            package(stage, output)
            with zipfile.ZipFile(output) as archive:
                names = set(archive.namelist())
            self.assertFalse(any(name.endswith(".so") for name in names))
            repo = Path(__file__).resolve().parents[2]
            with self.assertRaisesRegex(ValueError, "outside the source repository"):
                package(self.fixture(root), repo / "forbidden-mm3d-data.zip")

    def test_refuses_zip_output_inside_stage(self):
        with tempfile.TemporaryDirectory() as temporary:
            stage = self.fixture(Path(temporary))
            with self.assertRaisesRegex(ValueError, "outside the staged data directory"):
                package(stage, stage / "nested.zip")

    def test_rejects_single_staged_file_over_android_import_limit(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stage = self.fixture(root)
            oversized = stage / "game/romfs.bin"
            with oversized.open("wb") as stream:
                stream.truncate(MAX_ENTRY_BYTES + 1)
            with self.assertRaisesRegex(ValueError, "1-GiB safety limit"):
                package(stage, root / "oversized.zip")
            self.assertFalse((root / "oversized.zip").exists())


if __name__ == "__main__":
    unittest.main()
