#!/usr/bin/env python3
"""Stage a private MM3D installation for the Android SDL host; never builds an APK."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil

MANIFEST_FORMAT = "oot3d_native_process_manifest_v1"
PROFILE_FORMAT = "oot3d_native_game_launch_profile_v1"
MM3D_TITLE_ID = "mm3d"
MM3D_PROGRAM_ID = "0004000000125500"


def contained(root: Path, path: Path) -> Path:
    resolved = path.resolve()
    if not resolved.is_relative_to(root.resolve()):
        raise ValueError(f"Input escapes the MM3D installation: {path}")
    if path.is_symlink():
        raise ValueError(f"Symbolic-link inputs are not accepted: {path}")
    return resolved


def _resolve_manifest_input(root: Path, manifest_path: Path, value: str) -> Path:
    raw = Path(value.replace("\\", "/"))
    if raw.is_absolute():
        return contained(root, raw)
    manifest_dir = manifest_path.parent
    parts = raw.parts
    # Match ResolvePackagedInputPath in oot3d_native_a32_process_image.cpp.
    if parts and parts[0] == manifest_dir.name:
        resolved = manifest_dir.parent / raw
    else:
        resolved = manifest_dir / raw
    return contained(root, resolved)


def _hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _copy_file(root: Path, source: Path, output: Path) -> str:
    source = contained(root, source)
    if not source.is_file():
        raise FileNotFoundError(f"Required MM3D input is missing: {source}")
    relative = source.relative_to(root)
    destination = output / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    return relative.as_posix()


def _copy_tree(root: Path, source: Path, output: Path) -> str:
    source = contained(root, source)
    if not source.is_dir():
        raise FileNotFoundError(f"Required MM3D resource directory is missing: {source}")
    relative = source.relative_to(root)
    destination = output / relative
    destination.mkdir(parents=True, exist_ok=True)
    for path in sorted(source.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Resource tree contains a symbolic link: {path}")
        if path.is_dir():
            (destination / path.relative_to(source)).mkdir(parents=True, exist_ok=True)
        elif path.is_file():
            target = destination / path.relative_to(source)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
    return relative.as_posix()


def prepare(installation: Path, output: Path, *, manifest_file: str = "process-manifest.json") -> Path:
    root = installation.resolve()
    output = output.resolve()
    repository = Path(__file__).resolve().parents[2]
    if output == repository or output.is_relative_to(repository):
        raise ValueError("Private ROM-derived data must remain outside the source repository")
    if output == root or output.is_relative_to(root) or root.is_relative_to(output):
        raise ValueError("Use a separate staging directory outside the MM3D installation")

    manifest_path = contained(root, root / manifest_file)
    if not manifest_path.is_file():
        raise FileNotFoundError(f"MM3D process manifest not found: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("format") != MANIFEST_FORMAT:
        raise ValueError("Unsupported process manifest format")
    title = manifest.get("title", {})
    if title.get("id") != MM3D_TITLE_ID or title.get("program_id", "").upper() != MM3D_PROGRAM_ID:
        raise ValueError("The process manifest is not for Majora's Mask 3D USA")
    source = manifest.get("source")
    if not isinstance(source, dict):
        raise ValueError("The process manifest has no source object")

    inputs: dict[str, Path] = {}
    for key in ("exheader_path", "code_bin_path", "romfs_image_path"):
        value = source.get(key)
        if not isinstance(value, str) or not value:
            raise ValueError(f"The process manifest is missing source.{key}")
        inputs[key] = _resolve_manifest_input(root, manifest_path, value)
        if not inputs[key].is_file():
            raise FileNotFoundError(f"Manifest input does not exist: {inputs[key]}")
    if source.get("code_bin_sha256") and _hash(inputs["code_bin_path"]) != source["code_bin_sha256"]:
        raise ValueError("MM3D code.bin hash does not match the process manifest")
    if source.get("exheader_sha256") and _hash(inputs["exheader_path"]) != source["exheader_sha256"]:
        raise ValueError("MM3D ExHeader hash does not match the process manifest")
    expected_romfs_size = source.get("romfs_image_file_size")
    if expected_romfs_size is not None and inputs["romfs_image_path"].stat().st_size != expected_romfs_size:
        raise ValueError("MM3D RomFS size does not match the process manifest")

    resource_root = contained(root, root / "resources")
    if not resource_root.is_dir():
        raise FileNotFoundError(f"MM3D resources directory not found: {resource_root}")
    manifest_relative = manifest_path.relative_to(root).as_posix()
    profile_path = output / "TriAevum.android.launch.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=False)
    try:
        for input_path in inputs.values():
            _copy_file(root, input_path, output)
        resources_relative = _copy_tree(root, resource_root, output)

        # Make the relocated manifest refer to the copied ROM-derived inputs.
        staged_manifest = json.loads(json.dumps(manifest))
        for key, input_path in inputs.items():
            staged_manifest["source"][key] = input_path.relative_to(root).as_posix()
        manifest_destination = output / manifest_relative
        manifest_destination.parent.mkdir(parents=True, exist_ok=True)
        manifest_destination.write_text(json.dumps(staged_manifest, indent=1) + "\n", encoding="utf-8")

        for folder in ("data/config", "data/savedata", "data/cache/renderer", "logs"):
            (output / folder).mkdir(parents=True, exist_ok=True)
        config = output / "data/config/TriAevum.android.json"
        config.write_text("{}\n", encoding="utf-8")
        args = [
            "--a32-process-manifest", f"${{profile_dir}}/{manifest_relative}",
            "--resource-root", f"${{profile_dir}}/{resources_relative}",
            "--renderer", "nri",
            "--ui-profile", "oot3d",
            "--config", "${profile_dir}/data/config/TriAevum.android.json",
            "--save-data", "${profile_dir}/data/savedata",
            "--renderer-cache-directory", "${profile_dir}/data/cache/renderer",
            "--gameplay-timing", "native30_no_interpolation",
            "--presentation-rate", "30",
            "--width", "640", "--height", "360",
            "--output", "${profile_dir}/data/runtime-state.json",
        ]
        profile_path.write_text(json.dumps({"format": PROFILE_FORMAT, "arguments": args}, indent=2) + "\n",
                                encoding="utf-8")
    except Exception:
        shutil.rmtree(output, ignore_errors=True)
        raise
    print(f"Private MM3D Android data staged at {output}; no ROM data was placed in the APK.")
    return profile_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installation", type=Path, required=True,
                        help="prepared MM3D installation containing process-manifest.json and resources/")
    parser.add_argument("--output", type=Path, required=True,
                        help="fresh staging directory outside the repo and installation")
    parser.add_argument("--manifest", default="process-manifest.json",
                        help="manifest path relative to --installation")
    args = parser.parse_args()
    prepare(args.installation, args.output, manifest_file=args.manifest)


if __name__ == "__main__":
    main()
