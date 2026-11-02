#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Rebrand the actual native emulation drawer without replacing the emulation core.

Keeps the existing menu item IDs, handlers, controls, and native game surface.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: brand-ingame.py <nx2-source-root>")

root = Path(sys.argv[1]).resolve() / "src/android/app/src/main"
layout = root / "res/layout/fragment_emulation.xml"
header = root / "res/layout/header_in_game.xml"
quick = root / "res/layout/layout_quick_settings.xml"
assert layout.is_file() and header.is_file() and quick.is_file(), "Native in-game layout files missing"
res = root / "res"
colors = res / "values/bluswitch_ingame_colors.xml"
colors.write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="bluswitch_menu_navy">#092454</color>
    <color name="bluswitch_menu_background_light">#EDF5FF</color>
    <color name="bluswitch_menu_white">#F5FAFF</color>
    <color name="bluswitch_menu_muted">#B8D9FF</color>
    <color name="bluswitch_menu_accent">#25D2FF</color>
</resources>
''')
drawable = res / "drawable"
drawable.mkdir(parents=True, exist_ok=True)
(drawable / "bluswitch_ingame_bg.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient android:angle="0" android:startColor="#071A3D"
        android:centerColor="#092B64" android:endColor="#104DA4"/>
</shape>
''')

xml = layout.read_text()
# Keep the same NavigationView IDs: native EmulationFragment keeps every menu action.
pattern = r'(<com\.google\.android\.material\.navigation\.NavigationView\s+[^>]*android:id="@\+id/in_game_menu"[^>]*)(/>)'
match = re.search(pattern, xml)
assert match, "Could not locate native in_game_menu for BluSwitch styling"
native_drawer = match.group(1)
assert 'android:background="?attr/colorSurface"' in native_drawer, "Unexpected game drawer background"
native_drawer = native_drawer.replace('android:background="?attr/colorSurface"',
    'android:background="@drawable/bluswitch_ingame_bg"')
native_drawer += '''    app:itemTextColor="@color/bluswitch_menu_white"
    app:itemIconTint="@color/bluswitch_menu_accent"
    app:itemRippleColor="@color/bluswitch_menu_muted"
    '''
xml = xml[:match.start()] + native_drawer + match.group(2) + xml[match.end():]
ET.fromstring(xml)
layout.write_text(xml)

ht = header.read_text()
assert 'android:id="@+id/text_game_title"' in ht
ht = ht.replace('android:textColor="?attr/colorOnSurfaceVariant"',
    'android:textColor="@color/bluswitch_menu_muted"')
ht = ht.replace('android:textColor="?attr/colorOnSurface"',
    'android:textColor="@color/bluswitch_menu_white"')
logo = '''    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="B  BluSwitch 2"
        android:textSize="22sp"
        android:textStyle="bold"
        android:textColor="@color/bluswitch_menu_accent"
        android:layout_marginBottom="12dp" />

'''
assert '<com.google.android.material.textview.MaterialTextView' in ht
ht = ht.replace('    <com.google.android.material.textview.MaterialTextView', logo +
    '    <com.google.android.material.textview.MaterialTextView', 1)
ET.fromstring(ht)
header.write_text(ht)

q = quick.read_text()
assert '<com.google.android.material.appbar.MaterialToolbar' in q
q = q.replace('android:fillViewport="true"', '''android:fillViewport="true"
    android:background="@color/bluswitch_menu_background_light"''', 1)
q = q.replace('app:title="@string/quick_settings"',
              'app:title="BluSwitch 2 quick settings"\n            app:titleTextColor="@color/bluswitch_menu_white"')
q = q.replace('android:elevation="0dp"', 'android:elevation="0dp"\n            android:background="@color/bluswitch_menu_navy"')
ET.fromstring(q)
quick.write_text(q)

# Match the game's top-screen performance text to the BluSwitch blue palette.
fragment = root / "java/org/yuzu/yuzu_emu/fragments/EmulationFragment.kt"
if fragment.is_file():
    kt = fragment.read_text()
    old = '''MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorPrimary
                )'''
    if kt.count(old) == 2:
        kt = kt.replace(old, 'requireContext().getColor(R.color.bluswitch_menu_accent)')
        fragment.write_text(kt)
    else:
        print("Performance overlay colours unchanged: upstream rendering method differs")

print("Branded BluSwitch 2 in-game drawer and quick settings, native menu actions preserved")
