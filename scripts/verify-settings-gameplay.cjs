// BluSwitch 2 native settings and gameplay launch smoke checks.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const js = fs.readFileSync('bluswitch-ui/app.js', 'utf8');
const settings = fs.readFileSync('bluswitch-ui/settings-hub.js', 'utf8');
const home = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt', 'utf8');
const actions = fs.readFileSync('bluswitch-ui/BluSettingsActions.kt', 'utf8');
const apply = fs.readFileSync('scripts/apply-ui.py', 'utf8');
const build = fs.readFileSync('.github/workflows/bluswitch2-native-android.yml','utf8');
const names=['readiness','advanced','app','controls','profiles','drivers','freedreno','multiplayer','applets','data','folders','verify','logs','gpu-logs','data-folder','system-info','about'];
for(const name of names) {
  assert(settings.includes("'"+name+"'"), 'Missing settings tile: '+name);
  if(name!=='folders')assert(actions.includes('"'+name+'" ->'), 'Missing native handler: '+name);
}
assert(apply.includes('BluSettingsActions.kt'),'Native actions missing from APK copy');
assert(apply.includes('settings-hub.js'),'Settings UI missing from APK assets');
assert(home.includes('fun openSetting(id: String)'),'Settings Javascript bridge missing');
assert(home.includes('fun launchExeFSFolder()'),'Native loose game picker missing');
assert(home.includes('GameHelper.findExeFSGames(File(path))'),'Native loose game search not connected');
assert(home.includes('startActivity(game.launchIntent)'),'Must launch with native Game.launchIntent');
assert(!home.includes('MainActivity::class.java'),'Old upstream Eden home redirect found');
assert(build.includes('brand-settings.py'),'Native settings branding not present in APK workflow');

const nodes={},invocations=[];
let load;
const document={getElementById(id){return nodes[id] ||= {textContent:'',innerHTML:''};},addEventListener(name,fn){if(name==='DOMContentLoaded')load=fn;}};
const window={BluNative:new Proxy({}, {get(_x,k){return v=>invocations.push([k,v]);}})};
vm.runInNewContext(settings+'\n'+js, {window,document,confirm(){return true;},alert(){},setInterval(){},Date,console}, {filename:'BluSwitch-ui'});
load();
window.BluSwitch.setStatus(JSON.stringify({ready:true,keys:false,firmware:false,folders:[],files:[],allFiles:false}));
window.BluSwitch.open('settings');
const page=nodes['main-view'].innerHTML;
assert(page.includes('Switch 2 readiness check'), 'Readiness check not visible');
assert(page.includes('GPU driver manager'), 'Driver manager missing');
assert(page.includes('Manage BluSwitch data'), 'BluSwitch data panel missing');
assert(page.includes('Share GPU logs'), 'GPU log panel missing');
window.BluSettings.select('drivers');
assert(invocations.some(([k,v])=>k==='openSetting'&&v==='drivers'), 'Native GPU driver manager not invoked');
window.BluSettings.select('folders');
assert(nodes['main-view'].innerHTML.includes('Game files'), 'Game folders did not open Files');
assert(nodes['main-view'].innerHTML.includes('Launch extracted Switch 2 game'), 'Loose game launch missing');
window.BluSwitch.launchExeFSFolder();
assert(invocations.some(([k])=>k==='launchExeFSFolder'), 'ExeFS launcher not connected to native picker');
console.log('PASS settings native actions, UI routes, real ExeFS launcher and no Eden home redirects');
