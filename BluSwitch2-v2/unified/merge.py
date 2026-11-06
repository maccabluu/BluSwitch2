#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Create one BluSwitch 2.0 UI and guest game within the full NX2 Android app."""
from pathlib import Path
import sys,re,shutil,xml.etree.ElementTree as ET
if len(sys.argv)!=2: raise SystemExit("usage: merge.py <NX2 tree>")
repo=Path(__file__).resolve().parents[2]
app=Path(sys.argv[1]).resolve()/"src/android/app"
main=app/"src/main"
assets=main/"assets/bluswitch"
native=main/"java/org/yuzu/yuzu_emu/ui/main"
assert native.is_dir() and assets.is_dir()
gradle=app/"build.gradle.kts"
s=gradle.read_text()
s,n=re.subn(r'(?m)^(\s*applicationId\s*=\s*)"[^"]+"',
             lambda m:m.group(1)+'"uk.co.blustudio.bluswitch2"',s,count=1)
assert n==1,"Original package ID missing"
gradle.write_text(s)
def label(s):
    s=re.sub(r'BluSwitch 2(?!\.0)','BluSwitch 2.0',s)
    return re.sub(r'BLUSWITCH 2(?!\.0)','BLUSWITCH 2.0',s)
paths=[gradle,*main.glob("res/values*/strings.xml"),
main/"java/org/yuzu/yuzu_emu/features/DocumentProvider.kt",
main/"java/org/yuzu/yuzu_emu/utils/BugReport.kt",
main/"java/org/yuzu/yuzu_emu/utils/DirectoryInitialization.kt",
main/"java/org/yuzu/yuzu_emu/features/settings/model/StringSetting.kt",
native/"BluSwitchHomeActivity.kt",
*(assets/name for name in ("index.html","app.js","settings-hub.js","store.js"))]
for path in paths:
    assert path.is_file(),str(path)
    s=label(path.read_text())
    if path.suffix==".xml": ET.fromstring(s)
    path.write_text(s)
index=assets/"index.html"
s=index.read_text()
assert "BluSwitch <b>2</b>" in s
index.write_text(s.replace("BluSwitch <b>2</b>","BluSwitch <b>2.0</b>"))
js=assets/"app.js"
s=js.read_text()
old="""'<button class="secondary-btn" onclick="BluSwitch.refresh()">Refresh library</button></div></div>';"""
new="""'<button class="secondary-btn" onclick="BluSwitch.refresh()">Refresh library</button>'+
   '<button class="secondary-btn" onclick="BluSwitch.playTreasure()">Blu Treasure Hunt</button></div></div>';"""
assert s.count(old)==1
s=s.replace(old,new,1)
old="""'<div class="bluswitch-quicklaunch"><button class="primary-btn" onclick="BluSwitch.launchExeFSFolder()">Folder · Launch Switch 2 game</button>'+"""
new=old+"""
 '<button class="secondary-btn" onclick="BluSwitch.playTreasure()">Blu Treasure Hunt</button>'+"""
assert s.count(old)==1
s=s.replace(old,new,1)
old=" play:function(index){nativeCall('launchGame',index);},"
new=old+"""
 playTreasure:function(){nativeCall('launchTreasureGame');},
 runGuestCpu:function(){nativeCall('testGuestCpu');},
 runGuestElf:function(){nativeCall('testGuestElf');},"""
assert s.count(old)==1
s=s.replace(old,new,1)
old="  actionButton('Add game file','addGameFile')+actionButton('Refresh library','refresh',true)+"
new=old+"""
  actionButton('CPU test','runGuestCpu',true)+actionButton('ELF loader test','runGuestElf',true)+"""
assert s.count(old)==1
js.write_text(s.replace(old,new,1))

original=repo/"BluSwitch2-v2/app/src/main/java/com/blustudio/bluswitch2/v2/MiniGameDialog.java"
s=original.read_text()
assert "final class MiniGameDialog" in s and "NativeCore.stepMiniGame" in s
s=s.replace("package com.blustudio.bluswitch2.v2;",
"package org.yuzu.yuzu_emu.ui.main;")
s=s.replace("MiniGameDialog","BluTreasureDialog")
s=s.replace("NativeCore.stepMiniGame","BluGuestCore.stepMiniGame")
(native/"BluTreasureDialog.java").write_text(s)
shutil.copyfile(repo/"BluSwitch2-v2/unified/BluGuestCore.java",native/"BluGuestCore.java")

kotlin=native/"BluSwitchHomeActivity.kt"
s=kotlin.read_text()
old="    inner class Bridge {"
new="""    inner class Bridge {
        @JavascriptInterface fun launchTreasureGame() {
            handler.post { if (!closing && !isFinishing)
                BluTreasureDialog.show(this@BluSwitchHomeActivity) }
        }
        @JavascriptInterface fun testGuestCpu() {
            handler.post {
                val result = try { BluGuestCore.runCpuTest() }
                    catch (ex: LinkageError) { "Native CPU test unavailable" }
                if (!closing && !isFinishing) AlertDialog.Builder(this@BluSwitchHomeActivity)
                    .setTitle("BluSwitch 2.0 ARM64 CPU")
                    .setMessage(result).setPositiveButton("OK", null).show()
            }
        }
        @JavascriptInterface fun testGuestElf() {
            handler.post {
                val result = try { BluGuestCore.runElfTest() }
                    catch (ex: LinkageError) { "Native ELF loader test unavailable" }
                if (!closing && !isFinishing) AlertDialog.Builder(this@BluSwitchHomeActivity)
                    .setTitle("BluSwitch 2.0 ELF loader")
                    .setMessage(result).setPositiveButton("OK", null).show()
            }
        }"""
assert s.count(old)==1
kotlin.write_text(s.replace(old,new,1))
assert 'applicationId = "uk.co.blustudio.bluswitch2"' in gradle.read_text()
assert "BluSwitchHomeActivity" in (main/"AndroidManifest.xml").read_text()
print("PASS: BluSwitch 2.0 one original-package app, native engine and guest demo")
