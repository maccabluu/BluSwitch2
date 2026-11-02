#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Install the original BluSwitch 2 interface into an unpacked NX2 Android tree."""
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: python3 apply-ui.py <nx2-source-root>")

repo = Path(__file__).resolve().parents[1]
root = Path(sys.argv[1]).resolve()
app = root / "src/android/app"
manifest = app / "src/main/AndroidManifest.xml"
assert manifest.exists(), "Upstream Android manifest missing"

target = app / "src/main/java/org/yuzu/yuzu_emu/ui/main/BluSwitchHomeActivity.kt"
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(repo / "bluswitch-ui/BluSwitchHomeActivity.kt", target)
shutil.copyfile(repo / "bluswitch-ui/SwitchContainerInspector.kt", target.parent / "SwitchContainerInspector.kt")
assets = app / "src/main/assets/bluswitch"
assets.mkdir(parents=True, exist_ok=True)
for name in ("index.html", "app.css", "app.js"):
    shutil.copyfile(repo / ("bluswitch-ui/" + name), assets / name)

s = manifest.read_text()
assert 'android:name="org.yuzu.yuzu_emu.ui.main.MainActivity"' in s
assert 'android:name="org.yuzu.yuzu_emu.ui.main.BluSwitchHomeActivity"' not in s
s, count = re.subn(r'<action\s+android:name="android.intent.action.MAIN"\s*/>', "", s, count=1)
assert count == 1, "Native MAIN action moved or missing"
s, count = re.subn(r'<category\s+android:name="android.intent.category.LAUNCHER"\s*/>', "", s, count=1)
assert count == 1, "Native LAUNCHER category moved or missing"

activity = """        <activity
            android:name="org.yuzu.yuzu_emu.ui.main.BluSwitchHomeActivity"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:theme="@style/Theme.Yuzu.Main">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
"""
needle = '        <activity\n            android:name="org.yuzu.yuzu_emu.ui.main.MainActivity"'
assert needle in s, "Cannot locate native MainActivity declaration"
s = s.replace(needle, activity + "\n" + needle, 1)
ET.fromstring(s)
manifest.write_text(s)

gradle = app / "build.gradle.kts"
g = gradle.read_text()
g = re.sub(r'(appNameBase"\s+to\s+)"[^"]+"',
           lambda m: m.group(1) + '"BluSwitch 2"', g)
g = re.sub(r'(resValue\("string",\s*"app_name_suffixed",\s*)"[^"]+"',
           lambda m: m.group(1) + '"BluSwitch 2"', g)
g = g.replace('"Eden$suffix"', '"BluSwitch 2$suffix"')
gradle.write_text(g)

strings = app / "src/main/res/values/strings.xml"
st = strings.read_text()
st = st.replace('>Eden SX2A<', '>BluSwitch 2<')
ET.fromstring(st)
strings.write_text(st)

assert (assets / "index.html").exists()
assert "BluSwitchHomeActivity" in manifest.read_text()
print("BluSwitch original UI installed into the native emulator APK")
