#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Integrate the existing BluSwitch2 frontend into the real Banana Native APK.

This does not start a second Android application or replace the game's native
preparation engine. It replaces the *visible launcher surface*, while calling
the existing MainActivity for game import, native Prepare/Play, options, and
save management.
"""
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: python3 scripts/port-banana-ui.py <banana-source-root>")
repo = Path(__file__).resolve().parents[1]
root = Path(sys.argv[1]).resolve()
app = root / "android/app"
java_dir = app / "src/main/java/dev/nxnative/android"
assets = app / "src/main/assets/bluswitch"
assert (java_dir / "MainActivity.java").is_file()
assets.mkdir(parents=True, exist_ok=True)

shutil.copyfile(repo / "bluswitch-native/BluSwitchNativeUi.java",
                java_dir / "BluSwitchNativeUi.java")
for name in ("index.html", "app.css", "app.js"):
    shutil.copyfile(repo / "bluswitch-ui" / name, assets / name)
shutil.copyfile(repo / "bluswitch-native/settings-hub.js",
                assets / "settings-hub.js")

# Match the existing BluSwitch UI to native dumps and genuine Prepare/Play.
js_file = assets / "app.js"
js = js_file.read_text()
def replace_once(before, after):
    global js
    assert js.count(before) == 1, f"Unrecognised BluSwitch frontend: {before[:95]!r}"
    js = js.replace(before, after, 1)

replace_once(
    "function card(g) {",
    "function card(g) {")
replace_once(
    "'<button class=\"game-open\" aria-label=\"Play '+safe(g.title)+'\" onclick=\"BluSwitch.play('+g.index+')\">Open</button>'+",
    "'<button class=\"game-open\" aria-label=\"Play or Prepare '+safe(g.title)+'\" onclick=\"BluSwitch.play('+g.index+')\">'+(g.ready?'Play':'Prepare / Play')+'</button>'+")
replace_once(
    "'<button class=\"game-check\" aria-label=\"Check game boot for '+safe(g.title)+'\" onclick=\"BluSwitch.diagnose('+g.index+')\">Check boot</button>'+",
    "'<button class=\"game-check\" aria-label=\"Manage '+safe(g.title)+'\" onclick=\"BluSwitch.diagnose('+g.index+')\">Manage</button>'+")
js = js.replace("Folder · Launch Switch 2 game", "Add Switch 2 game")
js = js.replace("Like Eden: choose your extracted game version folder containing <strong>exefs</strong> and <strong>romfs</strong>, then tap <strong>Use this folder</strong>. Do not choose the ZIP.",
                "Select your extracted game dump containing <strong>base/exefs</strong> and <strong>base/romfs</strong>, or add a supported <strong>.nup</strong> file. BluSwitch2 prepares a game before playing.")
js = js.replace("For a Switch 2 game, open the extracted game folder, then base or updates / numbered version, until you see exefs and romfs. Tap Use this folder. Do not choose a ZIP archive.",
                "Choose the game dump folder containing base/ and updates/, or a supported .nup file. A ZIP archive is not a game. Preparing a game and its shaders is required before first play.")
js = js.replace("Only files recognised by the emulator appear in Library.",
                "Only games supported by the native engine appear in Library after being added.")
js = js.replace("The emulator stores game saves in its app data. Save browsing and backup controls are not available in this version.",
                "Back up or restore saves for each supported game.")
before = "else if(current==='saves')view.innerHTML=section('Saves','Back up or restore saves for each supported game.',actionButton('Back to library','home'));"
assert before in js, "Saves renderer changed"
js = js.replace(before, "else if(current==='saves')view.innerHTML=renderSaves();", 1)
save_view = """function renderSaves() {
 var h='<section class="screen-panel"><h1>Game saves</h1><p class="notice">Back up or restore your native game saves. Stop the game before changing saves.</p>';
 if(!games.length)return h+'<p class="notice">Add a game to manage its saves.</p></section>';
 games.forEach(function(g){
  h+='<div class="folder-row"><div class="file-summary"><strong>'+safe(g.title)+'</strong><small>'+safe(g.titleId||'')+'</small></div>'+
    '<button class="secondary-btn" onclick="BluSwitch.saveBackup('+g.index+')">Back up</button>'+
    '<button class="secondary-btn" onclick="BluSwitch.saveRestore('+g.index+')">Restore</button></div>';
 });
 return h+'</section>';
}
"""
assert js.count("function render() {") == 1
js = js.replace("function render() {", save_view + "\nfunction render() {", 1)
js = js.replace(" renderDock();\n", " renderDock();\n", 1)
# Replace the controls hub with functions the new engine actually exposes.
start = js.index("function renderController() {")
end = js.index("function renderSaves()", start)
js = js[:start] + """function renderController() {
 return '<section class="screen-panel"><h1>Controller</h1>'+
  '<p class="notice">BluSwitch2 supports Android gamepads and touchscreen input using the native engine. Button remapping is not yet built in.</p>'+
  '<div class="action-row">'+actionButton('On-screen overlay','overlay')+
  actionButton('Input support','controls',true)+'</div></section>';
}

""" + js[end:]
needle = " controlSetting:function(id){nativeCall('openSetting',id);},"
assert needle in js
js = js.replace(needle, """ controlSetting:function(id){nativeCall('openSetting',id);},
 saveBackup:function(index){nativeCall('saveBackup',index);},
 saveRestore:function(index){nativeCall('saveRestore',index)},""", 1)
# A native preparation may take time, so refresh game status without losing the view.
js = js.replace(" nativeCall('requestStatus');\n});", " nativeCall('requestStatus');\n setInterval(function(){nativeCall('requestGames');nativeCall('requestStatus');},10000);\n});", 1)
js_file.write_text(js)

source = java_dir / "MainActivity.java"
text = source.read_text()
def patch(before, after, label):
    global text
    assert text.count(before) == 1, f"{label}: Eden/Banana source changed (expected one exact anchor)"
    text = text.replace(before, after, 1)

patch(
    """        if (settingsPage) buildSettings(content);
        else buildHome(content);
    }""",
    """        if (settingsPage) buildSettings(content);
        else buildHome(content);
        // BluSwitch2 owns the visible interface; preparation and game runtime
        // stay in the original native host within this same APK.
        BluSwitchNativeUi.show(this, frame);
    }""",
    "UI overlay")
patch(
    """    @Override public void onBackPressed() {
        if (settingsPage) page(false);
        else super.onBackPressed();
    }""",
    """    @Override public void onBackPressed() {
        if (BluSwitchNativeUi.back(this)) return;
        if (settingsPage) page(false);
        else super.onBackPressed();
    }""",
    "Back navigation")
patch(
    """        games.addAll(Game.load(this));
        if (gameBox == null) return;""",
    """        games.addAll(Game.load(this));
        BluSwitchNativeUi.sendGames(this);
        BluSwitchNativeUi.sendStatus(this);
        if (gameBox == null) return;""",
    "Game list live refresh")

helper = """    // Package-local BluSwitch2 actions use the original engine's own dialogs,
    // preparation, GPU driver management and verified save backup code.
    void bluManage(Game game) { manage(game); }
    void bluRemove(Game game) { confirmRemove(game); }
    void bluPickNup() { if (!needAccess()) pickNup(); }
    void bluStorageAccess() { openAccessSettings(); }
    void bluOpenSetting(String id) {
        switch (id) {
            case "gpu": GpuDriver.dialog(this, PICK_DRIVER); break;
            case "folders": Folders.dialog(this); break;
            case "readiness": startActivityForResult(new Intent(this, ReadinessActivity.class),
                    ReadinessActivity.REQUEST); break;
            case "selftest": startActivity(new Intent(this, SelftestActivity.class)); break;
            case "logs": shareLog(); break;
            case "resolution": gameRes(); break;
            case "fps": fpsCap(); break;
            case "framegen": frameGen(); break;
            case "overlay": overlayItems(); break;
            case "controls": error("Bluetooth and USB gamepads use Android native input. Button remapping has not been added to this engine yet."); break;
            case "about": error("BluSwitch2 Native Preview. Built using nxnative / Banana Native source. See open-source licence notices and the GitHub source."); break;
            default: error("This setting has not been connected yet.");
        }
    }

"""
patch("    void error(String msg) {", helper + "    void error(String msg) {", "Native settings adapters")
source.write_text(text)

# A fresh launcher identity without touching users' previous Eden saves.
icon_dir = app / "src/main/res/drawable"
shutil.copyfile(repo / "bluswitch-native/ic_bluswitch.xml", icon_dir / "ic_bluswitch.xml")
launcher_fg = icon_dir / "ic_launcher_foreground.xml"
launcher_fg.write_text('''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_bluswitch" />
</layer-list>
''')
ET.parse(launcher_fg)
ET.parse(icon_dir / "ic_bluswitch.xml")
for mode in ("values", "values-night"):
    color_path = app / f"src/main/res/{mode}/colors.xml"
    colors = color_path.read_text()
    replacements = {
        "ic_launcher_background": "#FF061A4C",
        "bn_bg": "#FFF1F7FF" if mode == "values" else "#FF07132A",
        "bn_card": "#FFFFFFFF" if mode == "values" else "#FF0F2140",
        "bn_card_stroke": "#FFBDD7FC" if mode == "values" else "#FF335789",
        "bn_tint": "#FFE1EFFF" if mode == "values" else "#FF18335B",
        "bn_banana": "#FF3C92FF",
        "bn_banana_deep": "#FF1373E6",
        "bn_on_banana": "#FFFFFFFF",
        "bn_text": "#FF142849" if mode == "values" else "#FFF0F7FF",
        "bn_text_2": "#FF536F94" if mode == "values" else "#FFB8D0F0",
        "bn_accent": "#FF1969DE" if mode == "values" else "#FF7DBDFF",
        "bn_leaf": "#FF168BCA" if mode == "values" else "#FF5AC1FF",
        "bn_on_leaf": "#FFFFFFFF",
    }
    import re
    for name, value in replacements.items():
        expr = rf'(<color name="{re.escape(name)}">)#[0-9A-Fa-f]{{8}}(</color>)'
        colors, count = re.subn(expr, lambda m: m.group(1)+value+m.group(2), colors)
        if mode == "values-night" and name == "ic_launcher_background":
            continue
        assert count == 1, f"Missing colour {mode}/{name}"
    color_path.write_text(colors)
    ET.parse(color_path)
print("BluSwitch2 native UI, buttons, settings, saves, icon and blue palette applied")
