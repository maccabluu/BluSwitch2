// Check that BluSwitch 2 uses working native controller screens and preserves branding.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('bluswitch-ui/app.js', 'utf8');
const settings = fs.readFileSync('bluswitch-ui/settings-hub.js', 'utf8');
const actions = fs.readFileSync('bluswitch-ui/BluSettingsActions.kt', 'utf8');
const branding = fs.readFileSync('scripts/rebrand-visible.py', 'utf8');
const workflow = fs.readFileSync('.github/workflows/bluswitch2-native-android.yml', 'utf8');

for (const [id,menu] of [
 ['controls','SECTION_INPUT'],
 ['player-one','SECTION_INPUT_PLAYER_ONE'],
 ['player-two','SECTION_INPUT_PLAYER_TWO'],
 ['input-overlay','SECTION_INPUT_OVERLAY']]) {
 assert(actions.includes('"'+id+'" -> settings(Settings.MenuTag.'+menu+')'),
       'No working native route for '+id);
}
assert(!settings.includes("['controls','Controls'"), 'Controls must be moved from Settings to Controller');
assert(source.includes('renderController()'), 'Controller needs its own real screen');
assert(!source.includes('Dedicated AYN Thor controller mapping from this screen is still being developed'),
       'Old nonfunctional Controller placeholder returned');
assert(!actions.includes('Engine: GPL-licensed Eden NX2'),
       'Eden brand must not be in the main About popup');
assert(actions.includes('See Licences for upstream authors and source credits'),
       'Upstream licence attribution must remain accessible');
for (const expected of ['ProfileManagerFragment.kt', 'DirectoryInitialization.kt',
  'StringSetting.kt', 'BugReport.kt', 'bluswitch2-report-', 'ProfileAdapter.kt']) {
 assert(branding.includes(expected), 'Branding migration missing '+expected);
}
assert(workflow.includes('rebrand-visible.py'), 'Build must run the actual rebrand script');

const elements={};const calls=[];let ready;
const document = {
 getElementById(id){return elements[id] ||= {innerHTML:'',textContent:''};},
 addEventListener(name,fn){if(name==='DOMContentLoaded') ready=fn;}
};
const window = {BluNative:new Proxy({},{
 get(_target,name){return value=>calls.push({name,value});}
})};
vm.runInNewContext(settings+'\n'+source,
  {window,document,setInterval(){},Date,confirm(){return true;},alert(){},console},
  {filename:'bluswitch-home'});
assert(ready);
ready();
window.BluSwitch.open('controller');
const html=elements['main-view'].innerHTML;
for(const name of ['Button mapping','Player 1 controls','Player 2 controls','On-screen controls']) {
 assert(html.includes(name), 'Controller screen missing '+name);
}
window.BluSwitch.controlSetting('controls');
window.BluSwitch.controlSetting('player-one');
window.BluSwitch.controlSetting('input-overlay');
for(const name of ['controls','player-one','input-overlay']) {
 assert(calls.some(x=>x.name==='openSetting'&&x.value===name), 'Bridge did not open '+name);
}
window.BluSwitch.open('settings');
assert(elements['main-view'].innerHTML.includes('Profile Manager'),
       'BluSwitch Settings hub disappeared');
console.log('PASS: native Controller tab, branding routes, About credits and Settings');
