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
shutil.copyfile(repo / "bluswitch-native/BluSwitchControllerSettings.java",
                java_dir / "BluSwitchControllerSettings.java")
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
# Show the real preparation state on the game tile and never mislabel an
# unsupported title as ready. The full card remains clickable: the bridge
# routes unprepared games to native Prepare, not the native runtime.
badge = """ '<span class="game-prep-state '+(g.ready?'ready':g.preparable?'needs-prep':'unsupported')+'">'+safe(g.status||'Needs preparing')+'</span>'+ """
before_badge = """ '<button class="game-check" aria-label="Manage '+safe(g.title)+'" onclick="BluSwitch.diagnose('+g.index+')">Manage</button>'+ """
assert js.count(before_badge) == 1
js = js.replace(before_badge, badge + before_badge, 1)
# Put Prepare above the cover button on the page and leave Manage accessible.
js = js.replace(before_badge,
    """ (g.preparable&&!g.ready?'<button class="game-prep-button" onclick="BluSwitch.prepare('+g.index+')">Prepare game</button>':'')+ """ + before_badge, 1)
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
 var buttons=[
  ['button-mapping','Button mapping','Choose what each physical controller button does'],
  ['player-one','Player 1 controls','View connected gamepads and configure their buttons'],
  ['player-two','Player 2 controls','Set up a second connected gamepad'],
  ['input-overlay','On-screen controls','Show buttons, change their size, opacity and vibration']
 ];
 return '<section class="screen-panel blu-controller-hub"><h1>Controller</h1>'+
  '<p class="notice">Physical gamepads and native touch controls for BluSwitch2. Changes are saved for your next game.</p>'+
  '<div class="blu-controller-grid">'+buttons.map(function(item){
    return '<button class="blu-controller-tile" type="button" onclick="BluSwitch.controlSetting(&quot;'+item[0]+'&quot;)">'+
      '<strong>'+safe(item[1])+'</strong><small>'+safe(item[2])+'</small><span aria-hidden="true">›</span></button>';
  }).join('')+'</div></section>';
}

""" + js[end:]
needle = " controlSetting:function(id){nativeCall('openSetting',id);},"
assert needle in js
js = js.replace(needle, """ controlSetting:function(id){nativeCall('openSetting',id);},
 saveBackup:function(index){nativeCall('saveBackup',index);},
 saveRestore:function(index){nativeCall('saveRestore',index)},""", 1)
# A native preparation may take time, so refresh game status without losing the view.
js = js.replace(" nativeCall('requestStatus');\n});", " nativeCall('requestStatus');\n setInterval(function(){nativeCall('requestGames');nativeCall('requestStatus');},10000);\n});", 1)
# Display the real job progress and let the user pause/resume without
# opening the invisible old native home screen behind the new WebView.
prep_renderer = """function renderPreparation() {
 var p=status.preparation;
 if(!p || (p.state==='done'&&p.packCreated))return '';
 var percent=Math.max(0,Math.min(100,Number(p.percent)||0));
 var busy=p.state==='running';
 var label=busy?'Preparing game':p.state==='failed'?'Preparation failed':p.state==='paused'?'Preparation paused':p.state==='done'?'Checking finished pack':'Game preparation';
 var control=(p.state==='running'||p.state==='paused'||p.state==='failed'||p.state==='pending')?
    '<button class="secondary-btn" onclick="BluSwitch.controlPreparation(\\''+(busy?'pause':'resume')+'\\')">'+(busy?'Pause':'Resume preparation')+'</button>':'';
 return '<section class="blu-preparation" aria-live="polite"><div class="blu-prep-head"><strong>'+safe(label+': '+(p.name||'Game'))+
  '</strong><span>'+percent+'%</span></div><progress max="100" value="'+percent+'"></progress>'+
  '<p>'+safe(p.error||p.step||'Preparing your game files')+'</p>'+
  '<p>Shaders: '+safe(p.shadersDone||0)+' / '+safe(p.shadersTotal||0)+'</p>'+
  '<div class="action-row">'+control+'</div></section>';
}
"""
assert js.count("function library(showFavorites) {") == 1
js = js.replace("function library(showFavorites) {", prep_renderer + "\nfunction library(showFavorites) {", 1)
assert js.count("return '<div class=\"empty-home\">") == 1
js = js.replace("return '<div class=\"empty-home\">", "return renderPreparation()+'<div class=\"empty-home\">", 1)
assert js.count("return '<section class=\"carousel-panel\">") == 1
js = js.replace("return '<section class=\"carousel-panel\">", "return renderPreparation()+'<section class=\"carousel-panel\">", 1)
needle = " play:function(index){nativeCall('launchGame',index);},"
assert needle in js
js = js.replace(needle, needle + """
 prepare:function(index){nativeCall('prepareGame',index);},
 controlPreparation:function(action){nativeCall('controlPreparation',action);},
""", 1)
# No fake pack.tsv: only the native DumpCheck/PackService pipeline creates it.
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
    void bluPrepareGame(Game game) {
        if (needAccess()) return;
        String unsupported = Profile.missing(game.titleId, game.name);
        if (unsupported != null) { error(unsupported); return; }
        File dump = new File(game.gameDir);
        if (!Ondevice.isDump(dump)) {
            error("Cannot create pack.tsv from this game file. Choose the full extracted game folder containing base/ and updates/, or a supported .nup. The native engine also needs a matching game version.");
            return;
        }
        Ondevice.prepare(this, dump, false);
    }
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
            case "button-mapping": BluSwitchControllerSettings.buttonMapping(this); break;
            case "player-one": BluSwitchControllerSettings.player(this, 1); break;
            case "player-two": BluSwitchControllerSettings.player(this, 2); break;
            case "input-overlay": BluSwitchControllerSettings.touchControls(this); break;
            case "controls": BluSwitchControllerSettings.buttonMapping(this); break;
            case "about": error("BluSwitch2 Native Preview. Built using nxnative / Banana Native source. See open-source licence notices and the GitHub source."); break;
            default: error("This setting has not been connected yet.");
        }
    }

"""
patch("    void error(String msg) {", helper + "    void error(String msg) {", "Native settings adapters")
source.write_text(text)

# Let the blue Prepare panel control the real on-device preparation service.
ondevice = app / "src/ondevice/java/dev/nxnative/android/Ondevice.java"
od = ondevice.read_text()
old_control = """    static void prepare(MainActivity a, File dump, boolean again) { if (panel != null && panel.a == a) panel.picked(dump, false, null, again); }"""
new_control = old_control + """
    static void control(MainActivity a, boolean pause) {
        if (panel == null || panel.a != a || panel.job == null) {
            a.error("No game preparation is active. Add a supported game first.");
            return;
        }
        if (pause) panel.pauseJob("bluswitch");
        else if (!a.needAccess()) panel.startService(panel.job);
    }"""
assert od.count(old_control) == 1, "Banana Native preparation API has changed"
ondevice.write_text(od.replace(old_control, new_control, 1))

# Map physical A/B/X/Y, shoulders and Start/Select before they reach the host.
# The native host still chooses player slots and handles analogue axes itself.
game_file = java_dir / "GameActivity.java"
game_code = game_file.read_text()
before_key = 'Native.key(e.getDeviceId(), e.getKeyCode(), e.getAction() == KeyEvent.ACTION_DOWN)'
after_key = 'Native.key(e.getDeviceId(), BluSwitchControllerSettings.mapKey(this, e), e.getAction() == KeyEvent.ACTION_DOWN)'
assert game_code.count(before_key) == 1, "GameActivity native input dispatch changed"
game_file.write_text(game_code.replace(before_key, after_key, 1))

# A fresh launcher identity without touching users' previous Eden saves.
icon_dir = app / "src/main/res/drawable"
shutil.copyfile(repo / "bluswitch-native/ic_bluswitch.xml", icon_dir / "ic_bluswitch.xml")
shutil.copyfile(repo / "bluswitch-native/ic_bluswitch_mono.xml", icon_dir / "ic_bluswitch_mono.xml")
(icon_dir / "ic_launcher_monochrome.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_bluswitch_mono" />
</layer-list>
''')
ET.parse(icon_dir / "ic_launcher_monochrome.xml")
launcher_fg = icon_dir / "ic_launcher_foreground.xml"
launcher_fg.write_text('''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_bluswitch" />
</layer-list>
''')
ET.parse(launcher_fg)
ET.parse(icon_dir / "ic_bluswitch.xml")
ET.parse(icon_dir / "ic_bluswitch_mono.xml")
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
