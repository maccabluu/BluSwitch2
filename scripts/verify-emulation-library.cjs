// Regression checks for BluSwitch 2 native game launching and saved imports.
// No game or console key files required.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const kt = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt','utf8');
const js = fs.readFileSync('bluswitch-ui/app.js','utf8');
const branding = fs.readFileSync('scripts/brand-ingame.py','utf8');
const workflow = fs.readFileSync('.github/workflows/bluswitch2-native-android.yml','utf8');

assert(kt.includes('startActivity(game.launchIntent)'), 'Game launcher must use the upstream Game.launchIntent, including ExeFS paths');
assert(!kt.includes('intent.putExtra("game", game)'), 'Old incorrect game extra remains');
assert(/NativeConfig\.addGameDir\(GameDir\(uri\.toString\(\), true\)\)\s*NativeConfig\.saveGlobalConfig\(\)/.test(kt),
  'Imported game folders must be persisted before native config reload');
assert(/NativeConfig\.setGameDirs\(dirs\.toTypedArray\(\)\)\s*NativeConfig\.saveGlobalConfig\(\)/.test(kt),
  'Deleted game folders must be saved to disk');
assert(kt.includes('takePersistableUriPermission'), 'Picked game files must retain SAF permission');
assert(/findExeFSGames\(File\(path\)\)[\s\S]{0,1100}NativeConfig\.addGameDir\(GameDir\(uri\.toString\(\), true\)\)[\s\S]{0,90}NativeConfig\.saveGlobalConfig\(\)/.test(kt),
  'Launch extracted game must persist valid folder before starting it');
assert(kt.includes('bluswitch_imported_games'), 'Individual files need durable preferences');
assert(kt.includes('GameHelper.getGame(uri, true)'), 'Saved individual files must be rescanned on resume');
assert(kt.includes('fun pickGameFile()'), 'Single game picker bridge missing');
assert(kt.includes('private fun diagnoseGame(index: Int)'), 'Game boot diagnostic missing');
assert(kt.includes('SwitchContainerInspector.inspect(this, uri)'), 'Boot check must inspect the actual container');
assert(kt.includes('Switch 2 NSP/XCI launching is not supported'), 'Unsupported Switch 2 container warning missing');
assert(kt.includes('settingsActions.open("readiness")'), 'Boot diagnostic must open readiness check');
assert(kt.includes('exeFSFolderPicker.launch(null)'), 'Boot diagnostic must offer extracted game launch');
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
window.BluSwitch.setGames(JSON.stringify([{index:0,title:'Pokopia',favorite:false}]));
window.BluSwitch.open('home');
assert(nodes['main-view'].innerHTML.includes('Check boot'),'Missing visible boot check for game');
window.BluSwitch.diagnose(0);
assert(events.some(([name,index])=>name==='diagnoseGame'&&index===0),'Boot check must call native diagnostic');
window.BluSwitch.open('files');
window.BluSwitch.addGameFile();
window.BluSwitch.removeGameFile(0);
assert(events.some(([name])=>name==='pickGameFile'),'File picker did not call Android');
assert(events.some(([name,index])=>name==='removeGameFile'&&index===0),'File remover did not call Android');
console.log('PASS: native game launch, folder/file persistence, in-game drawer styling and Files actions');
