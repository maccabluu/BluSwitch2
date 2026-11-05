/* BluSwitch2 on the Banana Native engine: visible controls map to real host actions. */
(function () {
'use strict';
var items = [
 ['readiness','Switch 2 readiness','Check your phone before preparing games','check-circle'],
 ['gpu','GPU driver','Choose the system driver or an installed Turnip driver','monitor'],
 ['resolution','Game resolution','Adjust the native output resolution','monitor'],
 ['fps','Frame rate cap','Choose a performance or battery-friendly limit','gauge'],
 ['framegen','Frame generation','Configure frame generation if supported','layers'],
 ['overlay','On-screen overlay','Choose which game statistics to show','eye'],
 ['folders','Game folders','Manage dump, pack and save storage paths','folder'],
 ['selftest','Graphics self-test','Run the native Vulkan graphics checks','zap'],
 ['logs','Share logs','Export the latest native engine log','file-text'],
 ['about','About BluSwitch2','Source credits and engine information','info']
];
function esc(v) { return String(v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];}); }
window.BluSettings = {
 render:function(status) {
   var storage = status && status.allFiles ?
     'Game storage access is enabled.' :
     'Your game folders require Android All files access.';
   return '<section class="screen-panel blu-settings-home"><h1>BluSwitch2 Settings</h1>'+
       '<p class="notice">Native game settings and diagnostics. '+esc(storage)+'</p>'+
       '<div class="blu-settings-tiles">'+items.map(function(i){
        return '<button class="blu-settings-tile" onclick="BluSwitch.controlSetting(\''+i[0]+'\')">'+
          '<span class="blu-setting-text"><strong>'+esc(i[1])+'</strong><small>'+esc(i[2])+'</small></span>'+
          '<span aria-hidden="true">›</span></button>';
       }).join('')+'</div></section>';
 }
};
})();
