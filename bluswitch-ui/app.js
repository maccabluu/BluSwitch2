(function () {
'use strict';
var games = [], current = 'home', error = '', busy = true;
var nav = [
  ['home','Library','gamepad','#9838ee'],
  ['collections','Collections','layers','#ff8b00'],
  ['files','Files','folder','#efa900'],
  ['store','Store','bag','#f13b67'],
  ['media','Media','media','#1686f8'],
  ['saves','Saves','bookmark','#00b9ac'],
  ['controller','Controller','gamepad','#335ff8'],
  ['handheld','Handheld Mode','handheld','#8235e8'],
  ['settings','Settings','settings','#57647d'],
  ['power','Power','power','#526582']
];
var paths = {
 gamepad:'<path d="M6 8h12c2 0 3 1 4 5l1 4c.4 3-2 4-4 2l-3-2H8l-3 2c-2 2-4.4 1-4-2l1-4c1-4 2-5 4-5Z"/><path d="M8 11v6m-3-3h6"/><circle cx="17" cy="12" r=".9" fill="currentColor" stroke="none"/><circle cx="19" cy="15" r=".9" fill="currentColor" stroke="none"/>',
 layers:'<path d="m12 2 10 5-10 5L2 7l10-5Zm-10 10 10 5 10-5M2 17l10 5 10-5"/>',
 folder:'<path d="M2 6a2 2 0 0 1 2-2h5l3 3H20a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2Z"/>',
 bag:'<path d="M4 8h16l1 13H3L4 8Zm5 1V6a3 3 0 0 1 6 0v3"/>',
 media:'<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m10 8 6 4-6 4Z"/>',
 bookmark:'<path d="M5 3h14v19l-7-4-7 4Z"/>',
 handheld:'<rect x="2" y="5" width="20" height="14" rx="3"/><rect x="8" y="7" width="8" height="10" rx="1"/><path d="M5 10v4M3 12h4"/><circle cx="19" cy="11" r=".8" fill="currentColor"/>',
 settings:'<path d="M10 2h4l.6 2.5 1.9.8 2.2-1.3 2.8 2.8-1.3 2.2.8 1.9L23 11v4l-2.5.6-.8 1.9 1.3 2.2-2.8 2.8-2.2-1.3-1.9.8L14 24h-4l-.6-2.5-1.9-.8-2.2 1.3-2.8-2.8 1.3-2.2-.8-1.9L1 15v-4l2.5-.6.8-1.9L3 6.3 5.8 3.5 8 4.8l1.9-.8Z" transform="translate(0 -1) scale(.96)"/><circle cx="12" cy="12" r="3"/>',
 power:'<path d="M12 2v10M6.4 5.3A9 9 0 1 0 17.6 5.3"/>'
};
function svg(name) {
 return '<svg viewBox="0 0 24 24" aria-hidden="true">'+(paths[name] || paths.gamepad)+'</svg>';
}
function safe(v) {
 return String(v == null ? '' : v).replace(/[&<>"']/g,function(c){
  return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
 });
}
function nativeCall(name,arg) {
 if(window.BluNative && typeof window.BluNative[name] === 'function') {
  if(typeof arg === 'undefined') return window.BluNative[name]();
  return window.BluNative[name](arg);
 }
}
function updateClock() {
 document.getElementById('clock').textContent = new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});
}
function renderDock() {
 document.getElementById('dock').innerHTML = nav.map(function(n) {
  return '<button class="dock-item '+(current===n[0]?'active':'')+'" style="--icon-color:'+n[3]+'" onclick="BluSwitch.open(\''+n[0]+'\')" aria-label="'+n[1]+'">'+svg(n[2])+'<span>'+n[1]+'</span></button>';
 }).join('');
}
function card(g) {
 var art = g.art ? '<img class="cover" src="'+g.art+'" alt=""/>' : '<div class="blank-art">B</div>';
 return '<article class="game-card">'+art+
 '<div class="game-label">'+safe(g.title)+'</div>'+
 '<button class="game-open" aria-label="Play '+safe(g.title)+'" onclick="BluSwitch.play('+g.index+')">Open</button>'+
 '<button class="favorite-star '+(g.favorite?'active':'')+'" onclick="BluSwitch.favorite('+g.index+')" aria-label="Toggle favourite for '+safe(g.title)+'">'+(g.favorite?'★':'☆')+'</button></article>';
}
function library(showFavorites) {
 var items = showFavorites ? games.filter(function(g){return !!g.favorite;}) : games;
 if(!items.length) {
  var msg = error ? safe(error) : busy ? 'Scanning your game folders...' : showFavorites ?
    'Choose the star on a game cover to add it to Favourites.' :
    'No games found yet. Add your own game folder to start.';
  return '<div class="empty-home"><h2>'+(showFavorites?'Your favourites':'Your game library')+'</h2><p>'+msg+'</p><div class="action-row" style="justify-content:center">'+
   '<button class="primary-btn" onclick="BluSwitch.advanced()">Add game folder</button>'+
   '<button class="secondary-btn" onclick="BluSwitch.refresh()">Refresh library</button></div></div>';
 }
 return '<section class="carousel-panel"><div class="carousel-meta"><span>'+
 (showFavorites?'Your favourites':'Your game library')+' · '+items.length+' '+(items.length===1?'game':'games')+
 '</span><div><button onclick="BluSwitch.refresh()">Refresh</button><button onclick="BluSwitch.advanced()">+ Add game folder</button></div></div>'+
 '<div class="game-strip" aria-label="Your games">'+items.map(card).join('')+'</div></section>';
}
function panel(title,text,button) {
 return '<section class="screen-panel"><h1>'+safe(title)+'</h1><p class="notice">'+safe(text)+'</p>'+
 '<div class="action-row"><button class="primary-btn" onclick="BluSwitch.advanced()">'+safe(button||'Open advanced options')+'</button>'+
 '<button class="secondary-btn" onclick="BluSwitch.open(\'home\')">Back to library</button></div></section>';
}
function render() {
 renderDock();
 var view=document.getElementById('main-view');
 if(current==='home')view.innerHTML=library(false);
 else if(current==='collections')view.innerHTML=library(true);
 else if(current==='store')view.innerHTML=panel('Store','There is no built-in game store or download service. BluSwitch 2 uses game files you add yourself.','Manage game folders');
 else if(current==='media')view.innerHTML=panel('Media','Media capture management is not yet available on this home screen. Native emulator functions are still included.','Open native options');
 else if(current==='saves')view.innerHTML=panel('Saves','Game saves are handled by the emulator core. Open the native library to manage game-specific saves.','Open native library');
 else if(current==='controller')view.innerHTML=panel('Controller','Set up your AYN Thor controller using the emulator input settings.','Open controller settings');
 else if(current==='handheld')view.innerHTML=panel('Handheld Mode','BluSwitch 2 is using a landscape layout for your Android handheld. Emulator graphics and handheld settings remain in advanced options.','Open handheld settings');
 else view.innerHTML=library(false);
}
window.BluSwitch = {
 setGames:function(json) {
  try { games=JSON.parse(json); busy=false; error=''; render(); }
  catch(e) {this.showError('Could not load your game list.');}
 },
 showError:function(message) { error=message;busy=false;render(); },
 refresh:function(){ busy=true;render();nativeCall('requestGames'); },
 play:function(index){nativeCall('launchGame',index);},
 favorite:function(index){nativeCall('favorite',index);},
 advanced:function(){nativeCall('openAdvanced');},
 open:function(id){
  if(id==='files'||id==='settings'){nativeCall('openAdvanced');return;}
  if(id==='power'){
   if(confirm('Exit BluSwitch 2?'))nativeCall('exit');return;
  }
  current=id;render();
 }
};
document.addEventListener('DOMContentLoaded',function(){
 updateClock();setInterval(updateClock,30000);render();
 nativeCall('requestGames');
});
})();
