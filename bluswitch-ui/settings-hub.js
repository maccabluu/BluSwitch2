/* BluSwitch 2 settings homepage. Native handlers provide real actions.
 * The emulator engine, user files and settings are not duplicated. */
(function(){
'use strict';
var options=[
 ['readiness','Switch 2 readiness check','Check your AYN Thor, graphics driver, memory and game access','✓'],
 ['advanced','Advanced settings','System, graphics, audio and gameplay options','⚙'],
 ['app','App Settings','Appearance, language and application options','▣'],
 ['profiles','Profile Manager','Add, choose, edit and remove console profiles','◉'],
 ['drivers','GPU driver manager','Install or select a compatible graphics driver','◈'],
 ['freedreno','Freedreno Settings','Adjust the installed Adreno graphics driver','◇'],
 ['multiplayer','Multiplayer','Host or join an emulator-supported room','◎'],
 ['applets','Applet launcher','Open system applets when firmware is installed','□'],
 ['data','Manage BluSwitch data','Import and export your own firmware, keys and user data','⇄'],
 ['folders','Manage game folders','Choose, view and remove your game folders','+'],
 ['verify','Verify installed content','Check installed emulator content for corruption','✓'],
 ['logs','Share debug logs','Create and share a diagnostic report','≡'],
 ['gpu-logs','Share GPU logs','Share graphics diagnostic logs','▤'],
 ['data-folder','Open BluSwitch folder','Browse the emulator user data directory','▱'],
 ['system-info','System Information','View hardware, graphics and Android information','ⓘ'],
 ['about','About BluSwitch 2','Build version, project information and licences','i']
];
function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){
 return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
window.BluSettings={
 options:options,
 render:function(status){
  status=status||{};
  var ready=status.ready?'':' (checking storage)';
  var badges='<span>Keys: '+(status.keys?'installed':'not installed')+'</span>'+
    '<span>Firmware: '+(status.firmware?'installed':'not installed')+'</span>'+
    '<span>Game folders: '+(status.folders||[]).length+'</span>';
  var html=options.map(function(opt){
   return '<button class="blu-settings-tile" type="button" onclick="BluSettings.select(\''+opt[0]+'\')">'+
     '<span class="blu-setting-icon" aria-hidden="true">'+esc(opt[3])+'</span>'+
     '<span class="blu-setting-text"><strong>'+esc(opt[1])+'</strong><small>'+esc(opt[2])+'</small></span>'+
     '<span class="blu-setting-chevron" aria-hidden="true">›</span></button>';
  }).join('');
  return '<section class="blu-settings-wrap"><div class="blu-settings-head"><h1>BluSwitch 2 Settings</h1>'+
   '<p>Configure your emulator, manage files and check your AYN Thor.</p>'+
   '<div class="blu-settings-status">'+badges+'</div></div>'+
   '<div class="blu-settings-tiles" aria-label="BluSwitch 2 settings">'+html+'</div>'+
   '<div class="blu-settings-bottom"><button onclick="BluSwitch.importKeys()">Import my prod.keys</button>'+
   '<button onclick="BluSwitch.checkStatus()">Refresh setup status'+ready+'</button></div></section>';
 },
 select:function(id){
  if(id==='folders'){window.BluSwitch.open('files');return;}
  var valid=options.some(function(o){return o[0]===id;});
  if(valid && window.BluNative && window.BluNative.openSetting)window.BluNative.openSetting(id);
 },
 result:function(message){
  if(message)alert(String(message));
 }
};
})();