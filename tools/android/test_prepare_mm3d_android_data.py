import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from prepare_mm3d_android_data import prepare


class Mm3dAndroidDataTests(unittest.TestCase):
    def fixture(self, root: Path) -> Path:
        install = root / "mm3d-install"
        game = install / "game"
        resources = install / "resources"
        game.mkdir(parents=True)
        resources.mkdir()
        (resources / "gamecontrollerdb.txt").write_text("fixture controller map\n")
        exheader = b"synthetic ExHeader"
        code = b"synthetic MM3D code"
        romfs = b"synthetic MM3D RomFS"
        (game / "exheader.bin").write_bytes(exheader)
        (game / "code.bin").write_bytes(code)
        (game / "romfs.bin").write_bytes(romfs)
        (install / "private").mkdir()
        (install / "private/mm3d_title_aot.so").write_bytes(b"not app data")
        (install / "data/savedata").mkdir(parents=True)
        (install / "data/savedata/old.save").write_bytes(b"user save")
        manifest = {
            "format": "oot3d_native_process_manifest_v1",
            "title": {"id": "mm3d", "program_id": "0004000000125500", "region": 1, "language": 1},
            "source": {
                "exheader_path": "game/exheader.bin",
                "exheader_sha256": hashlib.sha256(exheader).hexdigest(),
                "code_bin_path": "game/code.bin",
                "code_bin_sha256": hashlib.sha256(code).hexdigest(),
                "code_bin_size": len(code),
                "romfs_image_path": "game/romfs.bin",
                "romfs_image_file_size": len(romfs),
                "romfs_image_sha256": hashlib.sha256(romfs).hexdigest(),
            },
        }
        (install / "process-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return install

    def test_stages_only_runtime_inputs_and_writes_relocatable_mm3d_profile(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            install = self.fixture(root)
            output = root / "android-data"
            profile_path = prepare(install, output)
            profile = json.loads(profile_path.read_text(encoding="utf-8"))
            args = profile["arguments"]
            options = dict(zip(args[::2], args[1::2]))
            self.assertEqual(profile["format"], "oot3d_native_game_launch_profile_v1")
            self.assertEqual(options["--a32-process-manifest"], "${profile_dir}/process-manifest.json")
            self.assertEqual(options["--resource-root"], "${profile_dir}/resources")
            self.assertEqual(options["--gameplay-timing"], "native30_no_interpolation")
            self.assertEqual(options["--presentation-rate"], "30")
            manifest = json.loads((output / "process-manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["source"]["code_bin_path"], "game/code.bin")
            self.assertTrue((output / "game/romfs.bin").is_file())
            self.assertTrue((output / "resources/gamecontrollerdb.txt").is_file())
            self.assertTrue((output / "data/savedata").is_dir())
            self.assertFalse((output / "private/mm3d_title_aot.so").exists())
            self.assertFalse((output / "data/savedata/old.save").exists())
            with self.assertRaises(FileExistsError):
                prepare(install, output)

    def test_rejects_wrong_title_and_hash_mismatch_without_partial_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            install = self.fixture(root)
            manifest_path = install / "process-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["title"]["program_id"] = "0004000000033600"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            output = root / "bad-title-stage"
            with self.assertRaisesRegex(ValueError, "not for Majora"):
                prepare(install, output)
            self.assertFalse(output.exists())

            manifest["title"]["program_id"] = "0004000000125500"
            manifest["source"]["code_bin_sha256"] = "0" * 64
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "code.bin hash"):
                prepare(install, output)
            self.assertFalse(output.exists())

    def test_rejects_manifest_escape_and_output_inside_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            install = self.fixture(root)
            outside = root / "outside.bin"
            outside.write_bytes(b"outside")
            manifest_path = install / "process-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["source"]["romfs_image_path"] = "../outside.bin"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            output = root / "escape-stage"
            with self.assertRaises(ValueError):
                prepare(install, output)
            self.assertFalse(output.exists())
            with self.assertRaisesRegex(ValueError, "separate staging directory"):
                prepare(install, install / "stage")

    def test_rejects_nested_android_manifest_and_romfs_hash_mismatch(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            install = self.fixture(root)
            output = root / "nested-manifest-stage"
            with self.assertRaisesRegex(ValueError, "at the installation root"):
                prepare(install, output, manifest_file="nested/process-manifest.json")
            self.assertFalse(output.exists())

            manifest_path = install / "process-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["source"]["romfs_image_sha256"] = "0" * 64
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "RomFS hash"):
                prepare(install, output)
            self.assertFalse(output.exists())

    def test_refuses_private_rom_data_inside_repository(self):
        with tempfile.TemporaryDirectory() as temporary:
            install = self.fixture(Path(temporary))
            repository = Path(__file__).resolve().parents[2]
            output = repository / ".mm3d-data-test-must-not-be-created"
            with self.assertRaisesRegex(ValueError, "outside the source repository"):
                prepare(install, output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
