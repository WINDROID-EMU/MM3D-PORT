import os
from pathlib import Path
import tempfile
import unittest

from stage_native_app import stage


class StageNativeAppTests(unittest.TestCase):
    def fixture(self, root: Path):
        runtime = root / "runtime"
        paths = [
            runtime / "_deps/sdl2-build/libSDL2.so",
            runtime / "product/libTriAevum.so",
            runtime / "product/libtriaevum_title_bootstrap.so",
        ]
        for path in paths:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"fake ARM64 ELF")
        title = root / "private/mm3d_title_aot.so"
        title.parent.mkdir(parents=True)
        title.write_bytes(b"fake MM3D ARM64 ELF")
        ndk = root / "ndk"
        bin_dir = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
        lib_dir = ndk / "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android"
        bin_dir.mkdir(parents=True)
        lib_dir.mkdir(parents=True)
        readelf = bin_dir / "llvm-readelf"
        readelf.write_text("#!/bin/sh\nif [ \"$1\" = \"-h\" ]; then echo 'Machine: AArch64'; else echo 'Dynamic section: no dependencies'; fi\n")
        readelf.chmod(0o755)
        strip = bin_dir / "llvm-strip"
        strip.write_text("#!/bin/sh\nexit 0\n")
        strip.chmod(0o755)
        (lib_dir / "libc++_shared.so").write_bytes(b"fake ARM64 ELF")
        return runtime, title, ndk

    def test_mm3d_module_is_staged_with_distinct_name(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            runtime, title, ndk = self.fixture(root)
            output = root / "staged-libs"
            stage(runtime, title, ndk, output, "libmm3d_title_aot.so")
            abi = output / "arm64-v8a"
            self.assertTrue((abi / "libmm3d_title_aot.so").is_file())
            self.assertFalse((abi / "libtriaevum_title_aot.so").exists())
            self.assertTrue((abi / "libTriAevum.so").is_file())

    def test_rejects_unknown_library_name(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            runtime, title, ndk = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "Unsupported title-library name"):
                stage(runtime, title, ndk, root / "stage", "liboot3d_title_aot.so")


if __name__ == "__main__":
    unittest.main()
