// NSP/XCI loader integration checks (no game dumps or keys required).
const fs = require('node:fs');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const kt = fs.readFileSync('bluswitch-ui/SwitchContainerInspector.kt','utf8');
const home = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt','utf8');
const bridge = fs.readFileSync('scripts/apply-ui.py','utf8');
const js = fs.readFileSync('bluswitch-ui/app.js','utf8');

assert(kt.includes('PFS0'), 'NSP header must be validated');
assert(kt.includes('"HEAD"'), 'XCI header must be validated');
assert(kt.includes('MAX_ENTRIES') && kt.includes('MAX_NAMES_BYTES'), 'Bound container table reads');
assert(kt.includes('channel.read(buffer, current)'), 'Read bounded headers without loading whole game');
assert(!kt.includes('decrypt('), 'Read-only inspector should not attempt to decrypt games');
assert(kt.includes('Switch 2 NCA4 game loading is not implemented'),
       'Avoid pretending Switch 2 packages are playable');
assert(bridge.includes('SwitchContainerInspector.kt'), 'Build must copy archive inspector');
assert(home.includes('SwitchContainerInspector.inspect(this, fileUri)'),
       'Archive inspection must be called for saved game files');
assert(home.includes('val launchIntent = Intent(this, EmulationActivity::class.java).apply') && home.includes('startActivity(launchIntent)'),
       'Existing native engine must receive supported original Switch or loose ExeFS paths');

const nodes = {};
const calls = [];
let ready;
const window = {BluNative:new Proxy({}, {get(_obj,name){return v=>calls.push([name,v]);}})};
const document = {
 getElementById(id){return nodes[id] ||= {innerHTML:'',textContent:''};},
 addEventListener(name,fn){if(name==='DOMContentLoaded')ready=fn;}
};
vm.runInNewContext(js,{window,document,alert(){},confirm(){return true;},setInterval(){},Date,console});
ready();
window.BluSwitch.setStatus(JSON.stringify({
 ready:true, folders:[], allFiles:false, files:[
 {name:'Test.nsp', available:true,format:'NSP',structure:'Recognised PFS0 package',
  contents:'3 entries, 2 NCA files, 1 ticket',note:'Switch 2 NCA4 game loading is not implemented.'},
 {name:'Other.xci',available:true,format:'XCI',structure:'Unrecognised XCI layout',
  note:'This game card does not use the expected legacy header.'}
 ]}));
window.BluSwitch.open('files');
const html=nodes['main-view'].innerHTML;
assert(html.includes('Test.nsp') && html.includes('Other.xci'), 'Both container files must show');
assert(html.includes('Recognised PFS0 package'), 'Valid package structure must show');
assert(html.includes('Unrecognised XCI layout'), 'Unsupported card structure must show');
assert(html.includes('NCA4 game loading is not implemented'), 'Unsupported Switch 2 format should be clear');
assert(!js.includes('openAdvanced'), 'Eden home navigation must not return');
console.log('PASS: NSP/XCI inspection wiring, bounded header probe, honest compatibility status and library UI');
