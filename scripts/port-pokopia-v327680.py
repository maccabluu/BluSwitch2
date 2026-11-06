#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Experimental Pokopia v327680 Android profile for BluSwitch2's native engine.

Version and ROMFS marker from the public MIT-licensed profile:
  https://github.com/xFractals/pokopia-native/blob/main/profile/0400A3A01EE4C000/game.tsv
  v327680 main 816c5358e8178d802fcec4a7ce5a741a
This is profile metadata ONLY. No game data or copyrighted game binaries are
included, and a successful APK build is not a real-game compatibility result.

Build a SEPARATE v327680 alpha, not an unsafe pretend pack.tsv for v655360.
Keep the already-tested v655360 source untouched in upstream.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: port-pokopia-v327680.py <banana-source-root>")

profile = Path(sys.argv[1]) / "dm/games/0400A3A01EE4C000/game.tsv"
source = profile.read_text(encoding="utf-8")

def change(old: str, new: str, label: str) -> None:
    global source
    found = source.count(old)
    if found != 1:
        raise SystemExit(f"{label}: unexpected profile, expected 1 match, found {found}")
    source = source.replace(old, new, 1)

change(
    "build\tv655360\tv655360\tmain=76a489f3bd3642fa5e18bd83d32c98ba\tsubsdk0=9646a69cb1024ba919fddaa4265e6bc5\tsdk=cc7da00d672399efe7958790d17fae39",
    "build\tv327680\tv327680\tmain=816c5358e8178d802fcec4a7ce5a741a\tsubsdk0=9646a69cb1024ba919fddaa4265e6bc5\tsdk=cc7da00d672399efe7958790d17fae39",
    "Pokopia build ID",
)
change("package\tv655360", "package\tv327680", "default package")
change("shader_versions\tv655360", "shader_versions\tv327680", "shader version")
change(
    "romfs_marker\tmotor_asset_re/KIDS_PLATFORM_OUNCE/root.rdb",
    "romfs_marker\tmotor_asset_re/KIDS_PLATFORM_OUNCE/0x38f972af.fdata",
    "v327680 RomFS marker",
)
# The v655360 dynamic-resolution addresses are build-specific. Never apply
# guessed offsets to an unrelated version of the game executable.
lines = source.splitlines()
matches = [line for line in lines if line.startswith("res_levels\t76a489f3bd3642fa5e18bd83d32c98ba\t")]
if len(matches) != 1:
    raise SystemExit("Missing exactly one newer-version-only dynamic-resolution row")
source = source.replace(matches[0], "# v327680 render-size offsets not verified; default game resolution used")
source = source.replace(
    "# Pokemon Pokopia (Switch 2, update v655360). Plays on the Mac",
    "# Pokopia v327680 experimental Android variant. Based on MIT-licensed xFractals profile.",
    1,
)
# Keep the established Android-safe memory budget and newer-version shader
# maps as the first experiment; both need device validation for old Pokopia.
profile.write_text(source, encoding="utf-8")
assert "816c5358e8178d80" in source
assert "build\tv655360" not in source
assert "0x38f972af.fdata" in source
print("Pinned experimental v327680 profile installed; real device test required")
