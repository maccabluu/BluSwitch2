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

# File pickers use the DocumentsProvider root title, not the settings button text.
# Force the user-visible root name and icon to BluSwitch 2 without altering
# the provider authority, document IDs or existing files.
provider = app / "java/org/yuzu/yuzu_emu/features/DocumentProvider.kt"
source = provider.read_text()
import re
title_pattern = r'(add\(DocumentsContract\.Root\.COLUMN_TITLE,\s*)context!!\.getString\(R\.string\.app_name\)(\))'
source, title_count = re.subn(title_pattern, r'\1"BluSwitch 2"\2', source)
if title_count != 1:
    raise SystemExit("DocumentProvider root title changed upstream; BluSwitch branding needs review")
source = source.replace(
    "add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_yuzu)",
    "add(DocumentsContract.Root.COLUMN_ICON, R.drawable.bluswitch_icon)"
)
provider.write_text(source)

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
