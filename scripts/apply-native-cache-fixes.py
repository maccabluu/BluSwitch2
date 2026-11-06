#!/usr/bin/env python3
"""Apply reviewed native changes only to the exact Eden source used by BluSwitch2."""

import argparse
import hashlib
from pathlib import Path
import subprocess


UPSTREAM_COMMIT = "dd3cb327c79f511830e460b916a45a33b5738759"
SOURCE = Path("src/video_core/renderer_vulkan/vk_pipeline_cache.cpp")
ORIGINAL_SHA256 = "3c7b4d642bffeb140aba0569d8811a3e777c7833f713a52c6cb6c58940f743bc"
PATCHED_SHA256 = "dcf753698da0dc07285b950a1ca2b9b455993e193083cf8910f1de6dfa2484a8"
PATCH = Path(__file__).resolve().parents[1] / "native-patches/0001-vulkan-cache-lifecycle.patch"


def apply(root: Path) -> None:
    root = root.resolve()
    commit = subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
    ).strip()
    if commit != UPSTREAM_COMMIT:
        raise RuntimeError(f"Unreviewed Eden revision: {commit}")
    digest = hashlib.sha256((root / SOURCE).read_bytes()).hexdigest()
    if digest == PATCHED_SHA256:
        print("Native cache fixes already applied")
        return
    if digest != ORIGINAL_SHA256:
        raise RuntimeError("Native pipeline source differs from the reviewed version")
    subprocess.run(["git", "-C", str(root), "apply", "--check", str(PATCH)], check=True)
    subprocess.run(["git", "-C", str(root), "apply", str(PATCH)], check=True)
    if hashlib.sha256((root / SOURCE).read_bytes()).hexdigest() != PATCHED_SHA256:
        raise RuntimeError("Patched native source checksum mismatch")
    print("Applied native cache fixes. No compilation or APK build performed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("eden_source", type=Path)
    args = parser.parse_args()
    apply(args.eden_source)
