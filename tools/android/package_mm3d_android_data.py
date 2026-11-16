#!/usr/bin/env python3
"""Create a private transfer ZIP from prepared MM3D Android data; never an APK asset."""
from __future__ import annotations

import argparse
from pathlib import Path
import zipfile

REQUIRED = {
    "TriAevum.android.launch.json",
    "process-manifest.json",
    "game/exheader.bin",
    "game/code.bin",
    "game/romfs.bin",
    "data/config/TriAevum.android.json",
}
MAX_PACKAGE_BYTES = 2 * 1024 * 1024 * 1024
MAX_ENTRY_BYTES = 1024 * 1024 * 1024


def package(stage: Path, output: Path) -> Path:
    stage = stage.resolve()
    output = output.resolve()
    repository = Path(__file__).resolve().parents[2]
    if output == repository or output.is_relative_to(repository):
        raise ValueError("Private MM3D data packages must remain outside the source repository")
    if output == stage or output.is_relative_to(stage) or stage.is_relative_to(output):
        raise ValueError("Write the ZIP outside the staged data directory")
    if not stage.is_dir():
        raise FileNotFoundError(f"MM3D staging directory does not exist: {stage}")
    files: list[Path] = []
    for path in sorted(stage.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Staging tree contains a symbolic link: {path}")
        if path.is_file():
            rel = path.relative_to(stage).as_posix()
            if rel in REQUIRED or rel.startswith("resources/"):
                files.append(path)
    found = {path.relative_to(stage).as_posix() for path in files}
    missing = REQUIRED - found
    if missing:
        raise FileNotFoundError(f"Staged MM3D data is incomplete: {sorted(missing)}")
    if not any(name.startswith("resources/") for name in found):
        raise FileNotFoundError("Staged MM3D resources are empty")
    total_bytes = 0
    for path in files:
        size = path.stat().st_size
        if size > MAX_ENTRY_BYTES:
            raise ValueError(f"Staged MM3D file exceeds the 1-GiB safety limit: {path}")
        total_bytes += size
    if total_bytes > MAX_PACKAGE_BYTES:
        raise ValueError("Staged MM3D package exceeds the 2-GiB safety limit")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
        for path in files:
            archive.write(path, path.relative_to(stage).as_posix())
    print(f"Private MM3D data ZIP created at {output}; it is not an APK or public release asset.")
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", type=Path, required=True,
                        help="output directory created by prepare_mm3d_android_data.py")
    parser.add_argument("--output", type=Path, required=True, help="private .zip outside the repository")
    args = parser.parse_args()
    package(args.stage, args.output)


if __name__ == "__main__":
    main()
