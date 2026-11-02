// Check that BluSwitch 2 menu navigation never opens the upstream Eden UI.
// This uses the shipped JavaScript and native activity code, without mocks of emulator behavior.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const js = fs.readFileSync('bluswitch-ui/app.js', 'utf8');
const kotlin = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt', 'utf8');
const overlay = fs.readFileSync('scripts/apply-ui.py', 'utf8');
assert(!js.includes('openAdvanced'), 'The old advanced menu redirect was reintroduced');
assert(!kotlin.includes('MainActivity::class.java'), 'BluSwitch should not launch Eden MainActivity');
assert(kotlin.includes('onBackPressedDispatcher.addCallback'), 'Android Back must stay inside BluSwitch');
assert(overlay.includes('BluSwitchHomeActivity') && overlay.includes('android.intent.category.LAUNCHER'), 'BluSwitch must be the Android launcher');

const nodes = {};
const calls = [];
let onLoad;
const dom = {
  getElementById(id) { return nodes[id] ||= { textContent: '', innerHTML: '' }; },
  addEventListener(type, fn) { if(type === 'DOMContentLoaded') onLoad = fn; }
};
const bridge = new Proxy({}, { get(_target,name) {
  return arg => {calls.push([name,arg]);};
} });
const window = { BluNative: bridge };
const sandbox = {
  window, document:dom, setInterval(){}, confirm(){return false;},
  alert(){}, Date, console
};
vm.runInNewContext(js, sandbox, { filename:'bluswitch-ui/app.js' });
onLoad();
window.BluSwitch.setGames(JSON.stringify([
  {title:'Test game',index:0,favorite:false}
]));
calls.length = 0;
const views = ['home','collections','files','store','media','saves','controller','handheld','settings'];
for(const page of views){
  window.BluSwitch.open(page);
  assert(nodes['main-view'].innerHTML, 'Page '+page+' renders empty');
  assert(!calls.some(x=>x[0]==='openAdvanced'), 'Page '+page+' opened Eden');
}
window.BluSwitch.open('settings');
window.BluSwitch.back();
assert(nodes['main-view'].innerHTML.includes('Your game library'), 'Back did not return to BluSwitch Library');
assert(!calls.some(x=>x[0]==='openAdvanced'), 'Back opened Eden');
console.log('PASS: all nine BluSwitch menu pages and Back remain in BluSwitch');
