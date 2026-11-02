#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve saved library folders on temporary document-provider scan failures."""
from pathlib import Path
import sys
if len(sys.argv) != 2:
    raise SystemExit("Usage: fix-gamehelper.py <nx2-source-root>")
file = Path(sys.argv[1]).resolve() / "src/android/app/src/main/java/org/yuzu/yuzu_emu/utils/GameHelper.kt"
src = file.read_text()
old = """        // Remove all game dirs with insufficient permissions from config
        if (badDirs.isNotEmpty()) {
            var offset = 0
            badDirs.forEach {
                gameDirs.removeAt(it - offset)
                offset++
            }
        }
        NativeConfig.setGameDirs(gameDirs.toTypedArray())"""
new = """        // Do not silently delete game folders when a document provider is unavailable.
        // Keep the selection in native config so the user can reconnect storage later.
        // The BluSwitch Files screen provides an explicit Remove button.
        if (oldGamesDir.isNotEmpty()) {
            // One-time migration from the older Android game_path preference.
            NativeConfig.setGameDirs(gameDirs.toTypedArray())
            NativeConfig.saveGlobalConfig()
        }"""
if old not in src:
    raise SystemExit("Refusing to patch: upstream GameHelper scan cleanup changed")
file.write_text(src.replace(old, new, 1))
print("Game folder selections are retained if SAF temporarily fails")
