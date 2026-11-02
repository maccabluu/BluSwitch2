(function () {
'use strict';
var games = [], current = 'home', error = '', busy = true;
var libraryView = 'covers', libraryFilter = 'all';
var deviceStatus = { battery: null, charging: false, batteryTemp: null, thermal: 'Unknown' };
var controllerTest = { device: 'No controller input yet', button: 'Press a button', pressed: false, lx: 0, ly: 0, rx: 0, ry: 0, lt: 0, rt: 0 };

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
 '<button class="favorite-star '+(g.favorite?'active':'')+'" onclick="BluSwitch.favorite('+g.index+')" aria-label="Toggle favourite for '+safe(g.title)+'">'+(g.favorite?'★':'☆')+'</button>'+
 '<button class="cover-edit" onclick="BluSwitch.chooseCover('+g.index+')" aria-label="Choose cover for '+safe(g.title)+'">Cover</button>'+
 (g.customCover?'<button class="cover-reset" onclick="BluSwitch.clearCover('+g.index+')" aria-label="Reset cover for '+safe(g.title)+'">Reset</button>':'')+
 '</article>';
}
function library(showFavorites) {
 var heading = showFavorites ? 'Your favourites' : libraryFilter === 'recent' ? 'Recently played' : 'Your game library';
 var items = showFavorites ? games.filter(function(g) {return !!g.favorite;}) : games.slice();
 if(!showFavorites && libraryFilter === 'recent') {
   items=items.filter(function(g){return Number(g.lastPlayed||0)>0;})
     .sort(function(a,b){return Number(b.lastPlayed||0)-Number(a.lastPlayed||0);});
 }
 if(!items.length) {
   var msg = error ? safe(error) : busy ? 'Scanning your game folders...' : showFavorites ?
     'Choose the star on a game cover to add it to Favourites.' :
     libraryFilter === 'recent' && games.length ? 'Games appear here after you play them.' :
     'No games found yet. Add your own game folder to start.';
   return '<div class="empty-home"><h2>'+heading+'</h2><p>'+msg+'</p><div class="action-row" style="justify-content:center">'+
     (!showFavorites && libraryFilter==='recent' ? actionButton('All games','allGames',true) : '')+
     actionButton('Add game folder','addFolder')+actionButton('Refresh library','refresh',true)+'</div></div>';
 }
 var count = items.length+' '+(items.length===1?'game':'games');
 var tabs = showFavorites ? '' :
   '<button class="'+(libraryFilter==='all'?'selected':'')+'" onclick="BluSwitch.setFilter(\'all\')">All games</button>'+
   '<button class="'+(libraryFilter==='recent'?'selected':'')+'" onclick="BluSwitch.setFilter(\'recent\')">Recent</button>';
 var views = '<button class="'+(libraryView==='covers'?'selected':'')+'" onclick="BluSwitch.setView(\'covers\')">Covers</button>'+
   '<button class="'+(libraryView==='grid'?'selected':'')+'" onclick="BluSwitch.setView(\'grid\')">Grid</button>';
 return '<section class="carousel-panel"><div class="carousel-meta"><span>'+heading+' · '+count+'</span>'+
   '<div class="library-tools">'+tabs+views+
   '<button onclick="BluSwitch.refresh()">Refresh</button>'+
   '<button onclick="BluSwitch.addFolder()">+ Add game folder</button></div></div>'+
   '<div class="game-strip '+(libraryView==='grid'?'game-grid':'')+'" aria-label="Your games">'+items.map(card).join('')+'</div></section>';
}
var status = {keys:false, firmware:false, folders:[], ready:false};
var notice = '';
function actionButton(title,command,secondary) {
 return '<button class="'+(secondary?'secondary-btn':'primary-btn')+'" onclick="BluSwitch.openAction(\''+command+'\')">'+safe(title)+'</button>';
}
function section(title,description,actions) {
 return '<section class="screen-panel"><h1>'+safe(title)+'</h1><p class="notice">'+safe(description)+'</p><div class="action-row">'+(actions||'')+'</div></section>';
}
function renderFiles() {
 var folders = (status.folders||[]);
 var list = folders.length ? '<div class="folder-list">'+folders.map(function(folder,i){
   return '<div class="folder-row"><span>'+safe(folder)+'</span><button class="secondary-btn" onclick="BluSwitch.removeFolder('+i+')">Remove</button></div>';
 }).join('')+'</div>' : '<p class="notice">No game folders added yet.</p>';
 var storage = status.ready ? (status.allFiles ? 'Switch 2 folder access: enabled.' : 'Switch 2 loose ExeFS folders might require Android All files access.') : '';
 return '<section class="screen-panel"><h1>Game files</h1><p class="notice">Add a folder containing your own game dumps. BluSwitch 2 will scan for supported files.</p>'+list+
 '<div class="action-row">'+actionButton('Add game folder','addFolder')+actionButton('Refresh library','refresh',true)+
 (!status.allFiles ? actionButton('Enable Switch 2 folder access','enableStorage',true) : '')+'</div><p class="notice">'+safe(storage)+'</p></section>';
}
function renderSettings() {
 var keyText = status.ready ? (status.keys ? 'Installed' : 'Not installed') : 'Checking';
 var firmText = status.ready ? (status.firmware ? 'Installed' : 'Not installed') : 'Checking';
 return '<section class="screen-panel"><h1>BluSwitch 2 settings</h1>'+
 '<div class="settings-grid"><div class="setting-card"><strong>Decryption keys</strong><span>'+keyText+'</span><small>Loose Switch 2 ExeFS games do not require keys. Some encrypted game formats do.</small></div>'+
 '<div class="setting-card"><strong>Firmware</strong><span>'+firmText+'</span><small>Some titles need firmware. BluSwitch 2 does not include system files.</small></div>'+
 '<div class="setting-card"><strong>Game folders</strong><span>'+(status.folders||[]).length+'</span><small>Folders added to your game library.</small></div></div>'+
 '<div class="action-row">'+actionButton('Import my prod.keys','importKeys')+actionButton('Add game folder','addFolder',true)+actionButton('Check again','checkStatus',true)+'</div>'+
 '<p class="notice">Use files dumped from hardware you own. Your keys stay on your device and are never uploaded.</p></section>';
}
function renderDeviceInfo() {
 var charge = deviceStatus.battery === null ? 'Not available' : safe(deviceStatus.battery) + '%';
 var temp = deviceStatus.batteryTemp === null ? 'Not available' : safe(Number(deviceStatus.batteryTemp).toFixed(1)) + ' °C';
 return '<section class="screen-panel device-panel"><h1>Handheld performance hub</h1><p>Live Android readings from your handheld. Battery temperature is not CPU or GPU temperature.</p>'+
  '<div class="device-stats">'+
  '<div class="setting-card"><strong>Battery</strong><span>'+charge+'</span><small>'+(deviceStatus.charging?'Charging':'Not charging')+'</small></div>'+
  '<div class="setting-card"><strong>Battery temperature</strong><span>'+temp+'</span><small>Android battery sensor</small></div>'+
  '<div class="setting-card"><strong>Thermal status</strong><span>'+safe(deviceStatus.thermal)+'</span><small>Reported by Android</small></div>'+
  '</div><div class="action-row">'+actionButton('Refresh readings','refreshDevice')+'</div></section>';
}
function renderControllerTest() {
 function axis(v) { return Number(v||0).toFixed(2); }
 return '<section class="screen-panel device-panel"><h1>Controller tester</h1>'+
  '<p>Press the buttons and move the sticks on your AYN Thor. BluSwitch 2 reads Android gamepad input without changing your controller mapping.</p>'+
  '<div class="device-stats">'+
  '<div class="setting-card"><strong>Connected controller</strong><span>'+safe(controllerTest.device)+'</span></div>'+
  '<div class="setting-card"><strong>Last button</strong><span>'+safe(controllerTest.button)+'</span><small>'+(controllerTest.pressed?'Pressed':'Released')+'</small></div>'+
  '<div class="setting-card"><strong>Left stick</strong><span>X '+axis(controllerTest.lx)+' · Y '+axis(controllerTest.ly)+'</span></div>'+
  '<div class="setting-card"><strong>Right stick</strong><span>X '+axis(controllerTest.rx)+' · Y '+axis(controllerTest.ry)+'</span></div>'+
  '<div class="setting-card"><strong>Triggers</strong><span>L '+axis(controllerTest.lt)+' · R '+axis(controllerTest.rt)+'</span></div>'+
  '</div><div class="action-row">'+actionButton('Back to library','home')+'</div></section>';
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
 else if(current==='controller')view.innerHTML=renderControllerTest();
 else if(current==='handheld')view.innerHTML=renderDeviceInfo();
 else view.innerHTML=library(false);
}
window.BluSwitch = {
 setFilter:function(mode){if(mode==='all'||mode==='recent'){libraryFilter=mode;render();}},
 allGames:function(){libraryFilter='all';render();},
 setView:function(mode){if(mode==='covers'||mode==='grid'){libraryView=mode;render();}},
 setDevice:function(json){
  try{deviceStatus=JSON.parse(json);var batteryLabel=document.getElementById('battery-percent');
   if(batteryLabel)batteryLabel.textContent=deviceStatus.battery===null?'':String(deviceStatus.battery)+'%'+(deviceStatus.charging?' +':'');
   if(current==='handheld')render();
  }catch(e){}
 },
 controllerInput:function(json){
  try{var event=JSON.parse(json);if(event.device)controllerTest.device=event.device;
   if(event.type==='key'){controllerTest.button=event.button;controllerTest.pressed=!!event.pressed;}
   if(event.type==='axis'){['lx','ly','rx','ry','lt','rt'].forEach(function(k){if(typeof event[k]==='number')controllerTest[k]=event[k];});}
   if(current==='controller')render();
  }catch(e){}
 },
 refreshDevice:function(){nativeCall('requestDeviceStatus');},
 setGames:function(json) {
  try { games=JSON.parse(json); busy=false; error=''; render(); }
  catch(e) {this.showError('Could not load your game list.');}
 },
 showError:function(message) { error=message;busy=false;render(); },
 refresh:function(){ busy=true;render();nativeCall('requestGames'); },
 play:function(index){nativeCall('launchGame',index);},
 favorite:function(index){nativeCall('favorite',index);},
 chooseCover:function(index){nativeCall('pickCover',index);},
 clearCover:function(index){nativeCall('resetCover',index);},
 addFolder:function(){nativeCall('pickGameFolder');},
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
  if(current!=='home'){if(current==='controller')nativeCall('setControllerTestActive',false);current='home';render();}
  else nativeCall('exit');
 },
 open:function(id){
  if(id==='power'){
   if(confirm('Exit BluSwitch 2?'))nativeCall('exit');return;
  }
  current=id;render();
  nativeCall('setControllerTestActive',id==='controller');
  if(id==='handheld')nativeCall('requestDeviceStatus');
 }
};
document.addEventListener('DOMContentLoaded',function(){
 updateClock();setInterval(updateClock,30000);render();
 nativeCall('requestGames');
 nativeCall('requestStatus');
 nativeCall('requestDeviceStatus');
});
})();
