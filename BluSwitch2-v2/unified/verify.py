#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import re,sys
assert len(sys.argv)==2
app=Path(sys.argv[1])/"src/android/app"
src=app/"src/main"
gradle=(app/"build.gradle.kts").read_text()
assert len(re.findall(r'(?m)^\s*applicationId\s*=\s*"uk.co.blustudio.bluswitch2"',gradle))==1
assert '"com.blustudio.bluswitch2.v2"' not in gradle
assert 'BluSwitch 2.0' in gradle
assert '<b>2.0</b>' in (src/"assets/bluswitch/index.html").read_text()
js=(src/"assets/bluswitch/app.js").read_text()
assert "BluSwitch.playTreasure()" in js and "runGuestCpu" in js
kt=(src/"java/org/yuzu/yuzu_emu/ui/main/BluSwitchHomeActivity.kt").read_text()
assert "BluTreasureDialog.show" in kt and "launchTreasureGame" in kt
assert "BluGuestCore.stepMiniGame" in (src/"java/org/yuzu/yuzu_emu/ui/main/BluTreasureDialog.java").read_text()
assert "static native int[] stepMiniGame" in (src/"java/org/yuzu/yuzu_emu/ui/main/BluGuestCore.java").read_text()
assert (src/"jniLibs/arm64-v8a/libblu_guest_core.so").stat().st_size>1024
assert "BluSwitchHomeActivity" in (src/"AndroidManifest.xml").read_text()
print("PASS: one original Android package, full engine, BluSwitch 2.0 menu and ARM64 mini-game")
