#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Apply BluSwitch's native blue theme to existing functional settings screens.

The underlying controls, safety checks, and emulator handlers remain unchanged.
Do not rewrite licence attribution or change underlying data paths.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: python3 brand-settings.py <nx2-source-root>")
app = Path(sys.argv[1]) / "src/android/app/src/main"
activities = [
    app / "java/org/yuzu/yuzu_emu/features/settings/ui/SettingsActivity.kt",
    app / "java/org/yuzu/yuzu_emu/features/settings/ui/SettingsSubscreenActivity.kt",
]
for path in activities:
    text = path.read_text()
    pattern = "        ThemeHelper.setTheme(this)"
    if text.count(pattern) != 1:
        raise SystemExit(f"Upstream settings theme changed: {path}")
    text = text.replace(
        pattern,
        pattern + "\n        // BluSwitch-specific blue colours for real native emulator settings.\n"
                  + "        setTheme(R.style.Theme_Yuzu_Main_Blue)",
        1
    )
    path.write_text(text)

strings = app / "res/values/strings.xml"
text = strings.read_text()
names = {
    "Manage Eden data": "Manage BluSwitch 2 data",
    "Open Eden folder": "Open BluSwitch 2 folder",
    "Manage Eden\\'s internal files": "Manage BluSwitch 2 internal files",
    "Share Eden\\'s GPU log file to debug graphics issues": "Share BluSwitch 2 GPU logs for graphics checks",
    "Could not open Eden directory": "Could not open BluSwitch 2 directory",
}
for old, new in names.items():
    text = text.replace(old, new)
ET.fromstring(text)
strings.write_text(text)
print("BluSwitch 2 blue native settings theme applied. Source licences and internal file paths unchanged.")
