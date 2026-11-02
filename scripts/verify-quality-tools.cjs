// Smoke test for BluSwitch 2 tools imported from BluPS2 and BluBox 360.
// Uses app JavaScript with a minimal DOM, and checks native bridge wiring.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const js = fs.readFileSync('bluswitch-ui/app.js','utf8');
const kotlin = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt','utf8');
const nodes = {};
const calls = [];
let loaded = null;
const document = {
 getElementById(id){return nodes[id] ||= {textContent:'',innerHTML:''};},
 addEventListener(type,callback){if(type==='DOMContentLoaded')loaded=callback;}
};
const window = { BluNative: new Proxy({}, {get(_target,name){return value=>calls.push([name,value]);}}) };
vm.runInNewContext(js,{
 window,document,setInterval(){},confirm(){return false;},alert(){},Date,console
},{filename:'bluswitch-ui/app.js'});
assert(loaded, 'UI startup handler missing');
loaded();
const gameList = [
 {index:0,title:'Game One',favorite:true,lastPlayed:0,art:''},
 {index:1,title:'Game Two',favorite:false,lastPlayed:999,customCover:true,art:''}
];
window.BluSwitch.setGames(JSON.stringify(gameList));
assert(nodes['main-view'].innerHTML.includes('Game One') && nodes['main-view'].innerHTML.includes('Game Two'));
window.BluSwitch.setFilter('recent');
assert(nodes['main-view'].innerHTML.includes('Game Two') && !nodes['main-view'].innerHTML.includes('Game One'),'Recently played filter failed');
window.BluSwitch.setView('grid');
assert(nodes['main-view'].innerHTML.includes('game-grid'),'Grid view failed');
window.BluSwitch.setFilter('all');
assert(nodes['main-view'].innerHTML.includes('BluSwitch.chooseCover(0)'),'Cover-picker button missing');
assert(nodes['main-view'].innerHTML.includes('BluSwitch.clearCover(1)'),'Custom cover reset missing');
window.BluSwitch.chooseCover(0);
window.BluSwitch.clearCover(1);
assert(calls.some(([name,value])=>name==='pickCover'&&value===0),'Native cover picker not called');
assert(calls.some(([name,value])=>name==='resetCover'&&value===1),'Native cover reset not called');
window.BluSwitch.open('controller');
assert(calls.some(([name,active])=>name==='setControllerTestActive'&&active===true),'Controller test not enabled');
window.BluSwitch.controllerInput(JSON.stringify({type:'key',button:'BUTTON_A',pressed:true,device:'AYN Thor'}));
assert(nodes['main-view'].innerHTML.includes('BUTTON_A') && nodes['main-view'].innerHTML.includes('AYN Thor'),'Controller data failed');
window.BluSwitch.back();
assert(calls.some(([name,active])=>name==='setControllerTestActive'&&active===false),'Controller test not disabled');
window.BluSwitch.open('handheld');
window.BluSwitch.setDevice(JSON.stringify({battery:75,charging:true,batteryTemp:35.2,thermal:'Normal'}));
assert(nodes['main-view'].innerHTML.includes('75%')&&nodes['main-view'].innerHTML.includes('35.2')&&nodes['main-view'].innerHTML.includes('Normal'),'Live telemetry rendering failed');
assert(nodes['battery-percent'].textContent.includes('75'),'Header battery status not updated');
for(const method of ['pickCover','resetCover','requestDeviceStatus','setControllerTestActive','controllerInput','setDevice']) {
 assert(kotlin.includes(method)||js.includes(method),method+' missing from native bridge');
}
assert(kotlin.includes('Intent.ACTION_BATTERY_CHANGED'),'Android battery readings are not connected');
assert(kotlin.includes('dispatchKeyEvent'),'Physical controller key reader missing');
assert(kotlin.includes('dispatchGenericMotionEvent'),'Physical analog input reader missing');
assert(!kotlin.includes('MainActivity::class.java'),'An Eden MainActivity redirect was reintroduced');
assert(kotlin.includes('onBackPressedDispatcher.addCallback'),'Android Back interception missing');
console.log('PASS BluSwitch library grid/recent, local covers, battery/heat, controller tester and navigation');
