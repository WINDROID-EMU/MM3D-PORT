import hashlib
import struct
import tempfile
import unittest
from pathlib import Path

from build_process_manifest import build_manifest, sha256_file


class ProcessManifestHashTests(unittest.TestCase):
    def test_manifest_records_full_romfs_hash(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            exheader_path = root / "exheader.bin"
            code_path = root / "code.bin"
            romfs_path = root / "romfs.bin"

            exheader = bytearray(0x400)
            exheader[:8] = b"MM3DTEST"
            for offset, address in ((0x10, 0x00100000), (0x20, 0x00200000),
                                    (0x30, 0x00300000)):
                struct.pack_into("<III", exheader, offset, address, 1, 0x1000)
            struct.pack_into("<I", exheader, 0x1C, 0x1000)
            struct.pack_into("<I", exheader, 0x3C, 0x1000)
            exheader[0x20D] = 0
            exheader[0x20F] = 0x30
            exheader_path.write_bytes(exheader)
            code_path.write_bytes(b"C" * 0x3000)
            romfs = struct.pack("<I", 0x28) + b"synthetic-romfs"
            romfs_path.write_bytes(romfs)

            manifest = build_manifest(exheader_path, code_path, romfs_path)
            source = manifest["source"]
            expected_hash = hashlib.sha256(romfs).hexdigest()
            self.assertEqual(source["romfs_image_sha256"], expected_hash)
            self.assertEqual(sha256_file(romfs_path), expected_hash)
            self.assertEqual(source["romfs_image_file_size"], len(romfs))


if __name__ == "__main__":
    unittest.main()
