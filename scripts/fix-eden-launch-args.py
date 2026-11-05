#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fix Eden SX2A SelectedGame launch not reaching EmulationFragment's game nav argument.

BluSwitchHomeActivity calls EmulationActivity.launch(activity, game).
The upstream launcher supplies SelectedGame, used by ROM-swap handling, but the
fresh emulation_navigation graph expects an argument literally named "game".
Consequently the first launch displays "No bootable game present!" despite
the file being recognised and selected.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: fix-eden-launch-args.py <nx2-source-root>")

source = Path(sys.argv[1]).resolve() / (
    "src/android/app/src/main/java/org/yuzu/yuzu_emu/activities/EmulationActivity.kt"
)
text = source.read_text(encoding="utf-8")
before = """        fun launch(activity: AppCompatActivity, game: Game) {
            val launcher = Intent(activity, EmulationActivity::class.java)
            launcher.putExtra(EXTRA_SELECTED_GAME, game)
            activity.startActivity(launcher)
        }"""
after = """        fun launch(activity: AppCompatActivity, game: Game) {
            val launcher = Intent(activity, EmulationActivity::class.java)
            // Keep SelectedGame for ROM-swap handling, and supply the actual
            // 'game' navigation argument used by EmulationFragment on first boot.
            launcher.putExtra(EXTRA_SELECTED_GAME, game)
            launcher.putExtra("game", game)
            activity.startActivity(launcher)
        }"""
if text.count(before) != 1:
    raise SystemExit(
        f"Refusing to patch changed Eden launcher: expected 1 anchor, found {text.count(before)}"
    )
text = text.replace(before, after, 1)
source.write_text(text, encoding="utf-8")

fragment = source.parent.parent / "fragments/EmulationFragment.kt"
fragment_source = fragment.read_text(encoding="utf-8")
assert "val gameToUse = args.game ?: intentGame" in fragment_source, (
    "EmulationFragment's game argument contract has changed"
)
nav = Path(sys.argv[1]).resolve() / (
    "src/android/app/src/main/res/navigation/emulation_navigation.xml"
)
nav_source = nav.read_text(encoding="utf-8")
assert 'android:name="game"' in nav_source, "Native emulation navigation game argument missing"
print("Fixed SelectedGame -> native game navigation argument for first boot")
