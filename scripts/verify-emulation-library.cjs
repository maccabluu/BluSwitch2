// Regression checks for BluSwitch 2 native game launching and saved imports.
// No game or console key files required.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const kt = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt','utf8');
const js = fs.readFileSync('bluswitch-ui/app.js','utf8');
const branding = fs.readFileSync('scripts/brand-ingame.py','utf8');
const workflow = fs.readFileSync('.github/workflows/bluswitch2-native-android.yml','utf8');

assert(kt.includes('EmulationActivity.launch(this, game)'), 'Game launcher must use native SelectedGame extra');
assert(!kt.includes('intent.putExtra("game", game)'), 'Old incorrect game extra remains');
assert(/NativeConfig\.addGameDir\(GameDir\(uri\.toString\(\), true\)\)\s*NativeConfig\.saveGlobalConfig\(\)/.test(kt),
  'Imported game folders must be persisted before native config reload');
assert(/NativeConfig\.setGameDirs\(dirs\.toTypedArray\(\)\)\s*NativeConfig\.saveGlobalConfig\(\)/.test(kt),
  'Deleted game folders must be saved to disk');
assert(kt.includes('takePersistableUriPermission'), 'Picked game files must retain SAF permission');
assert(kt.includes('bluswitch_imported_games'), 'Individual files need durable preferences');
assert(kt.includes('GameHelper.getGame(uri, true)'), 'Saved individual files must be rescanned on resume');
assert(kt.includes('https://www.gametdb.com/Switch/Search'), 'GameTDB cover search is not wired to the official Switch page');
assert(kt.includes('fun openGameTdb(index: Int)'), 'GameTDB action bridge missing');
assert(kt.includes('fun pickCover(index: Int)'), 'Custom cover picker bridge missing');
assert(kt.includes('fun resetCover(index: Int)'), 'Reset cover bridge missing');
assert(kt.includes('bluswitch_game_covers'), 'Custom covers must survive game shutdown');
assert(kt.includes('fun pickGameFile()'), 'Single game picker bridge missing');
assert(kt.includes('fun removeGameFile(index: Int)'), 'Single game remove bridge missing');
assert(!kt.includes('MainActivity::class.java'), 'Legacy Eden home must not be started');
assert(branding.includes('fragment_emulation.xml'), 'In-game native navigation view not branded');
assert(branding.includes('header_in_game.xml'), 'In-game BluSwitch title not installed');
assert(branding.includes('bluswitch_ingame_bg.xml'), 'In-game BluSwitch drawer not styled');
assert(workflow.includes('python3 scripts/brand-ingame.py nx2-source'), 'Native skin is not included in APK build');

const nodes = {}, events = [];
let onReady;
const window = {
 BluNative: new Proxy({}, {get(_obj, method){return arg => events.push([method, arg]);}})
};
const document = {
 getElementById(id){return nodes[id] ||= {innerHTML:'',textContent:''};},
 addEventListener(type,fn){if(type === 'DOMContentLoaded')onReady=fn;}
};
vm.runInNewContext(js,{window,document,setInterval(){},confirm(){return true;},alert(){},Date,console});
assert(onReady,'BluSwitch home did not register a load handler');
onReady();
window.BluSwitch.setStatus(JSON.stringify({
 ready:true,folders:['content://example/games'],files:[
 {name:'Example.nsp',available:true},{name:'Missing.xci',available:false}],
 keys:false,firmware:false,allFiles:false
}));
window.BluSwitch.open('files');
const html = nodes['main-view'].innerHTML;
assert(html.includes('Example.nsp'),'Selected game file is missing from Files view');
assert(html.includes('Missing.xci'),'Unavailable game file should remain visible');
assert(html.includes('Add game file'),'There must be a separate game file picker');
window.BluSwitch.setGames(JSON.stringify([{index:0,title:'Cover Test',favorite:false,customCover:true}]));
window.BluSwitch.open('home');
const coverHtml=nodes['main-view'].innerHTML;
assert(coverHtml.includes('BluSwitch.findCover(0)'), 'GameTDB button not shown');
assert(coverHtml.includes('BluSwitch.chooseCover(0)'), 'Choose cover button not shown');
assert(coverHtml.includes('BluSwitch.resetCover(0)'), 'Reset cover button not shown');
window.BluSwitch.findCover(0);
window.BluSwitch.chooseCover(0);
window.BluSwitch.resetCover(0);
assert(events.some(([name])=>name==='openGameTdb'), 'GameTDB browser not opened');
assert(events.some(([name])=>name==='pickCover'), 'Android cover picker not opened');
assert(events.some(([name])=>name==='resetCover'), 'Cover reset not connected');
window.BluSwitch.addGameFile();
window.BluSwitch.removeGameFile(0);
assert(events.some(([name])=>name==='pickGameFile'),'File picker did not call Android');
assert(events.some(([name,index])=>name==='removeGameFile'&&index===0),'File remover did not call Android');
console.log('PASS: native game launch, folder/file persistence, in-game drawer styling and Files actions');
