// SPDX-License-Identifier: GPL-3.0-or-later
// Verify BluSwitch advanced settings use native keys without showing the Eden UI.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const home = fs.readFileSync('bluswitch-ui/app.js','utf8');
const advanced = fs.readFileSync('bluswitch-ui/advanced-settings.js','utf8');
const native = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt','utf8');
const html = fs.readFileSync('bluswitch-ui/index.html','utf8');
assert(html.includes('advanced-settings.js'), 'Missing advanced settings script');
assert(native.includes('NativeConfig.saveGlobalConfig()'), 'Changes must persist to native configuration');
assert(native.includes('NativeLibrary.applySettings()'), 'Settings must be applied to native emulator');
assert(native.includes('requestAdvancedSettings()'), 'Missing native settings read');
assert(native.includes('writeAdvancedSetting('), 'Missing native settings write');
assert(!native.includes('MainActivity::class.java'), 'Old Eden home route must not return');

const nodes = {};
let ready;
const calls=[];
const document={
 getElementById(id){return nodes[id] ||= {textContent:'',innerHTML:''};},
 addEventListener(name,fn){if(name==='DOMContentLoaded') ready=fn;}
};
const window={BluNative:{
 requestGames(){},requestStatus(){},requestAdvancedSettings(){calls.push(['load']);},
 writeAdvancedSetting(key,val){calls.push(['save',key,val]);},
 exit(){calls.push(['exit']);}
}};
const ctx={window,document,setInterval(){},Date,console,alert(){},confirm(){return false;}};
vm.runInNewContext(advanced,ctx,{filename:'advanced-settings.js'});
vm.runInNewContext(home,ctx,{filename:'app.js'});
ready();
window.BluSwitch.open('settings');
assert(nodes['main-view'].innerHTML.includes('Graphics'),'Graphics category missing');
assert(nodes['main-view'].innerHTML.includes('Performance Overlay'),'Performance overlay category missing');
assert(nodes['main-view'].innerHTML.includes('Device Overlay'),'Device overlay category missing');
assert(nodes['main-view'].innerHTML.includes('Input Overlay'),'Input overlay category missing');
window.BluSwitch.setAdvancedSettings(JSON.stringify({
 'resolution_setup':3,'use_vsync':1,'scaling_filter':1,
 'use_disk_shader_cache':true,'show_performance_overlay':false,
 'memory_layout_mode':0,'use_custom_cpu_ticks':false,
 'eden_username':'DemoUser','tokenPresent':true
}));
window.BluAdvanced.open('graphics');
assert(nodes['main-view'].innerHTML.includes('GPU mode'),'GPU mode missing');
assert(nodes['main-view'].innerHTML.includes('ASTC decoding'),'ASTC decoder missing');
assert(nodes['main-view'].innerHTML.includes('Disk shader cache'),'Shader cache missing');
window.BluAdvanced.change('resolution_setup','5');
assert(calls.some(x=>x[0]==='save'&&x[1]==='resolution_setup'&&x[2]==='5'),
       'Graphics change did not call native settings');
window.BluAdvanced.open('system');
assert(nodes['main-view'].innerHTML.includes('Web token'),'Network token field missing');
assert(!nodes['main-view'].innerHTML.includes('abcdefghijklmnopqrstuvwxyzabcdef'),
       'Network token leaked');
window.BluAdvanced.change('cpu_ticks','1');
assert(!calls.some(x=>x[1]==='cpu_ticks'), 'Invalid CPU tick setting accepted');
window.BluSwitch.back();
assert(nodes['main-view'].innerHTML.includes('Graphics'),'Back should return to categories');
window.BluSwitch.back();
assert(nodes['main-view'].innerHTML.includes('Your game library'),'Second Back should return to BluSwitch library');
console.log('PASS: BluSwitch settings categories, native write, secret hiding and Back');
