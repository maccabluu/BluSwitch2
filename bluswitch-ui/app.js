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
   '<button class="primary-btn" onclick="BluSwitch.launchExeFSFolder()">Launch extracted Switch 2 game</button>'+'<button class="primary-btn" onclick="BluSwitch.addFolder()">Add game folder</button>'+
   '<button class="secondary-btn" onclick="BluSwitch.addGameFile()">Add game file</button>'+
   '<button class="secondary-btn" onclick="BluSwitch.refresh()">Refresh library</button></div></div>';
 }
 return '<section class="carousel-panel"><div class="carousel-meta"><span>'+
 (showFavorites?'Your favourites':'Your game library')+' · '+items.length+' '+(items.length===1?'game':'games')+
 '</span><div><button onclick="BluSwitch.refresh()">Refresh</button><button onclick="BluSwitch.addFolder()">+ Add game folder</button></div></div>'+
 '<div class="game-strip" aria-label="Your games">'+items.map(card).join('')+'</div></section>';
}
var status = {keys:false, firmware:false, folders:[], files:[], ready:false};
var notice = '';
function actionButton(title,command,secondary) {
 return '<button class="'+(secondary?'secondary-btn':'primary-btn')+'" onclick="BluSwitch.openAction(\''+command+'\')">'+safe(title)+'</button>';
}
function section(title,description,actions) {
 return '<section class="screen-panel"><h1>'+safe(title)+'</h1><p class="notice">'+safe(description)+'</p><div class="action-row">'+(actions||'')+'</div></section>';
}
function renderFiles() {
 var folders=(status.folders||[]), files=(status.files||[]);
 var folderList=folders.length ? '<div class="folder-list">'+folders.map(function(folder,i){
   return '<div class="folder-row"><span>'+safe(folder)+'</span><button class="secondary-btn" onclick="BluSwitch.removeFolder('+i+')">Remove</button></div>';
 }).join('')+'</div>' : '<p class="notice">No game folders selected.</p>';
 var fileList=files.length ? '<div class="folder-list">'+files.map(function(file,i){
   return '<div class="folder-row container-file-row"><div class="file-summary"><strong>'+safe(file.name)+'</strong>'+
   (file.available ? '<span class="container-structure">'+safe(file.structure||'Ready to scan')+'</span>'+
     (file.contents?'<small>'+safe(file.contents)+'</small>':'')+
     (file.note?'<small>'+safe(file.note)+'</small>':'')
     :'<span class="container-structure">File access missing. Reconnect the original file.</span>')+
   '</div><button class="secondary-btn" onclick="BluSwitch.removeGameFile('+i+')">Remove</button></div>';
 }).join('')+'</div>' : '<p class="notice">No individual game files selected.</p>';
 var storage=status.ready ? (status.allFiles?'Loose ExeFS folder access enabled.':'Loose ExeFS folders may need Android All files access.') : '';
 return '<section class="screen-panel"><h1>Game files</h1>'+
  '<p class="notice">Your selected folders and game files are kept when you close a game. Only files recognised by the emulator appear in Library.</p>'+
  '<h2>Game folders</h2>'+folderList+
  '<h2>Individual game files</h2>'+fileList+
  '<div class="action-row">'+actionButton('Launch extracted Switch 2 game','launchExeFSFolder')+actionButton('Add game folder','addFolder')+
  actionButton('Add game file','addGameFile')+actionButton('Refresh library','refresh',true)+
  (!status.allFiles?actionButton('Enable ExeFS access','enableStorage',true):'')+
  '</div><p class="notice">'+safe(storage)+'</p></section>';
}
function renderSettings() {
 return window.BluSettings ? window.BluSettings.render(status) : '<p>Settings are loading...</p>';
}

function render() {
 renderDock();
 var view=document.getElementById('main-view');
 if(current==='home')view.innerHTML=library(false);
 else if(current==='collections')view.innerHTML=library(true);
 else if(current==='files')view.innerHTML=renderFiles();
 else if(current==='settings')view.innerHTML=renderSettings();
 else if(current==='store')view.innerHTML=section('Store','BluSwitch 2 does not have a game store. Add your own legally obtained game files using Files.',actionButton('Open Files','files'));
 else if(current==='media')view.innerHTML=section('Media','Media capture management is not available yet. This screen is part of BluSwitch 2 and will not open another emulator.',actionButton('Back to library','home'));
 else if(current==='saves')view.innerHTML=section('Saves','The emulator stores game saves in its app data. Save browsing and backup controls are not available in this version.',actionButton('Back to library','home'));
 else if(current==='controller')view.innerHTML=section('Controller','BluSwitch 2 uses the emulator core for controls. Dedicated AYN Thor controller mapping from this screen is still being developed.',actionButton('Back to library','home'));
 else if(current==='handheld')view.innerHTML=section('Handheld Mode','BluSwitch 2 is running in landscape handheld mode. Native gameplay graphics and input settings are controlled by the emulator engine.',actionButton('Back to library','home'));
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
 addFolder:function(){nativeCall('pickGameFolder');},
 launchExeFSFolder:function(){nativeCall('launchExeFSFolder');},
 addGameFile:function(){nativeCall('pickGameFile');},
 removeGameFile:function(index){
  if(confirm('Remove this file from the BluSwitch 2 library? The game file stays on your device.'))
   nativeCall('removeGameFile',index);
 },
 importKeys:function(){nativeCall('pickProdKeys');},
 enableStorage:function(){nativeCall('requestStorageAccess');},
 checkStatus:function(){nativeCall('requestStatus');},
 removeFolder:function(index){
   if(confirm('Remove this game folder from BluSwitch 2? Your actual game files will not be deleted.')) nativeCall('removeGameFolder',index);
 },
 openAction:function(command){if(typeof this[command]==='function')this[command]();else this.open(command);},
 setStatus:function(json){try{status=JSON.parse(json);render();}catch(e){}},
 onAction:function(message){notice=message;alert(message);nativeCall('requestStatus');nativeCall('requestGames');},
 back:function(){
  if(current !== 'home') {current='home';render();}
  else nativeCall('exit');
 },
 open:function(id){
  if(id==='power'){
   if(confirm('Exit BluSwitch 2?'))nativeCall('exit');return;
  }
  current=id;render();
 }
};
document.addEventListener('DOMContentLoaded',function(){
 updateClock();setInterval(updateClock,30000);render();
 nativeCall('requestGames');
 nativeCall('requestStatus');
});
})();
