// GameTDB integration smoke tests: UI bridge, safe cached artwork, and native wiring.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const js = fs.readFileSync('bluswitch-ui/app.js', 'utf8');
const kotlin = fs.readFileSync('bluswitch-ui/BluSwitchHomeActivity.kt', 'utf8');
const helper = fs.readFileSync('bluswitch-ui/BluGameTdb.kt', 'utf8');
const installer = fs.readFileSync('scripts/apply-ui.py', 'utf8');
const dom = {};
const calls = [];
let ready;
const document = {
  getElementById: id => dom[id] ||= { innerHTML:'', textContent:'' },
  addEventListener: (event, callback) => { if(event==='DOMContentLoaded') ready=callback; }
};
const window = { BluNative: new Proxy({}, {
  get(_object, method){ return value => { calls.push([method,value]); }; }
}) };
vm.runInNewContext(js, {window, document, setInterval(){}, alert(){}, confirm(){return false;}, Date, console});
assert(ready, 'Startup handler missing');
ready();
window.BluSwitch.setGames(JSON.stringify([
 {index:0,title:'Metroid Dread',art:'',favorite:false,gameTdbCover:false},
 {index:1,title:'Animal Crossing',art:'data:image/jpeg;base64,AAAA',favorite:true,gameTdbCover:true}
]));
assert(dom['main-view'].innerHTML.includes('BluSwitch.findGameTdbCover(0)'),'Download button missing');
assert(dom['main-view'].innerHTML.includes('BluSwitch.resetGameTdbCover(1)'),'Reset button missing');
assert(!dom['main-view'].innerHTML.includes('BluSwitch.resetGameTdbCover(0)'),'Reset offered without cached art');
window.BluSwitch.findGameTdbCover(0);
window.BluSwitch.resetGameTdbCover(1);
assert(calls.some(([method,n])=>method==='findGameTdbCover'&&n===0),'Native find bridge missing');
assert(calls.some(([method,n])=>method==='resetGameTdbCover'&&n===1),'Native reset bridge missing');
for (const term of ['BluGameTdb(this)', 'gameTdb.cachedCover(game.path)',
 'openGameTdbCoverMenu(index)', 'resetGameTdbCover(index)']) {
 assert(kotlin.includes(term), 'Native handler missing: '+term);
}
assert(installer.includes('BluGameTdb.kt'), 'Downloader not bundled in APK');
assert(helper.includes('www.gametdb.com/switchtdb.txt?LANG=EN'),'Official title index missing');
assert(helper.includes('art.gametdb.com/switch/'),'Artwork source missing');
assert(helper.includes('matches.singleOrNull()'),'Ambiguous title matches must be rejected');
assert(helper.includes('Regex("[A-Z0-9]{5}")'),'Five-character ID validation missing');
assert(helper.includes('IMAGE_LIMIT'),'Download size limit missing');
assert(helper.includes('cachedCover(gamePath)'), 'On-device cover persistence missing');
assert(!helper.includes('game.programId'), 'Never confuse console IDs with GameTDB IDs');
assert(!kotlin.includes('MainActivity::class.java'),'No Eden navigation allowed');
console.log('PASS GameTDB: per-game lookup, reset, safe title matching and native wiring');
