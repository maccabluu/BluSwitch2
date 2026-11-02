/* BluSwitch 2 advanced settings, backed by the native NX2 configuration API.
 * Option codes and ranges reflect the upstream Eden NX2 Android settings.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
(function () {
'use strict';
function b(key,label,desc){return {key:key,type:'bool',label:label,desc:desc||''};}
function c(key,label,values,desc){return {key:key,type:'choice',label:label,choices:values,desc:desc||''};}
function n(key,label,min,max,desc){return {key:key,type:'number',label:label,min:min,max:max,desc:desc||''};}
function t(key,label,desc){return {key:key,type:'text',label:label,desc:desc||''};}
var positions=[[0,'Top left'],[1,'Top centre'],[2,'Top right'],[3,'Bottom left'],[4,'Bottom centre'],[5,'Bottom right']];
var sections = [
 {id:'system',title:'System',subtitle:'Console mode, memory, CPU and language',rows:[
 b('use_docked_mode','Docked mode','Switch between handheld and docked emulation.'),
 c('memory_layout_mode','Memory layout',[[0,'4 GB (recommended)'],[1,'6 GB'],[2,'8 GB'],[3,'10 GB'],[4,'12 GB']]),
 b('use_custom_cpu_ticks','Custom CPU ticks','Advanced. Changing the default can cause games to freeze.'),
 n('cpu_ticks','CPU ticks',77,65535,'77–21000 is the recommended range when custom ticks are enabled.'),
 c('region_index','Region',[[0,'Japan'],[1,'USA'],[2,'Europe'],[3,'Australia'],[4,'China'],[5,'Korea'],[6,'Taiwan']]),
 c('language_index','Language',[[12,'English (UK)'],[1,'English (US)'],[0,'Japanese'],[2,'French'],[3,'German'],[4,'Italian'],[6,'Chinese'],[7,'Korean'],[8,'Dutch'],[9,'Russian'],[10,'Simplified Chinese'],[11,'Traditional Chinese']]),
 t('eden_username','Web username','Optional name for supported multiplayer lobbies.'),
 {key:'eden_token',type:'secret',label:'Web token',desc:'Optional 48-letter lobby token. Existing token is hidden.'}
 ]},
 {id:'graphics',title:'Graphics',subtitle:'Resolution, GPU accuracy and shader controls',rows:[
 c('resolution_setup','Resolution (Handheld / Docked)',[[0,'0.25x'],[1,'0.5x'],[2,'0.75x'],[3,'1x (720p / 1080p)'],[4,'1.25x'],[5,'1.5x'],[6,'2x'],[7,'3x'],[8,'4x']]),
 c('use_vsync','VSync mode',[[0,'Immediate'],[1,'Mailbox'],[2,'FIFO'],[3,'FIFO Relaxed']]),
 c('scaling_filter','Window adapting filter',[[0,'Nearest neighbour'],[1,'Bilinear'],[2,'Bicubic'],[3,'Gaussian'],[4,'Lanczos'],[5,'ScaleForce'],[6,'FSR'],[7,'Area'],[8,'MMPX'],[9,'Zero tangent'],[10,'B-Spline'],[11,'Mitchell'],[12,'Spline1'],[13,'SGSR'],[14,'SGSR Edge'],[15,'FSR3']]),
 c('anti_aliasing','Anti-aliasing method',[[0,'None'],[1,'FXAA'],[2,'SMAA']]),
 c('gpu_accuracy','GPU mode',[[0,'Fast'],[1,'Accurate']]),
 c('dma_accuracy','DMA accuracy',[[0,'Default'],[1,'Unsafe'],[2,'Safe']]),
 c('max_anisotropy','Anisotropic filtering',[[0,'Automatic'],[1,'Default'],[2,'2x'],[3,'4x'],[4,'8x'],[5,'16x']]),
 c('vram_usage_mode','VRAM usage mode',[[0,'Conservative'],[1,'Aggressive']]),
 c('accelerate_astc','ASTC decoding method',[[0,'CPU'],[1,'GPU'],[2,'CPU Async']]),
 b('sync_memory_operations','Sync memory operations','Can improve accuracy but lower performance.'),
 b('use_disk_shader_cache','Disk shader cache','Store compiled shaders locally to reduce stutter.'),
 b('force_max_clock','Force maximum clocks (Adreno only)','Can increase heat and battery use.'),
 b('use_reactive_flushing','Use reactive flushing','May improve rendering accuracy.'),
 b('enable_buffer_history','Enable buffer history','Uses previous buffer states on supported games.'),
 b('use_optimized_vertex_buffers','Optimised vertex buffers','Requires supported drivers and may crash on older drivers.')
 ]},
 {id:'performance',title:'Performance Overlay',subtitle:'Choose gameplay performance statistics',rows:[
 b('show_performance_overlay','Show performance overlay'),
 b('perf_overlay_background','Overlay background'),
 c('perf_overlay_position','Overlay position',positions),
 b('show_fps','Show FPS'),b('show_frame_time','Show frame time'),
 b('show_app_ram_usage','Show app RAM usage'),b('show_system_ram_usage','Show system RAM usage'),
 b('show_bat_temperature','Show battery temperature'),
 c('bat_temperature_unit','Temperature unit',[[0,'Celsius'],[1,'Fahrenheit']]),
 b('show_power_info','Show power information'),b('show_shaders_building','Show shaders compiling')
 ]},
 {id:'device',title:'Device Overlay',subtitle:'Handheld hardware details while playing',rows:[
 b('show_soc_overlay','Show device overlay'),b('soc_overlay_background','Overlay background'),
 c('soc_overlay_position','Overlay position',positions),
 b('show_build_id','Show build ID'),b('show_driver_version','Show graphics driver'),
 b('show_device_model','Show device model'),b('show_gpu_model','Show GPU model'),
 b('show_soc_model','Show system-on-chip'),b('show_firmware_version','Show firmware version')
 ]},
 {id:'input',title:'Input Overlay',subtitle:'Touch controls and overlay behaviour',rows:[
 b('show_input_overlay','Show on-screen controls'),b('overlay_snap_to_grid','Snap controls to grid'),
 n('overlay_grid_size','Overlay grid size (px)',16,128),
 b('enable_input_overlay_auto_hide','Automatically hide controls'),
 n('input_overlay_auto_hide','Hide controls after (seconds)',1,999),
 b('hide_overlay_on_controller_input','Hide when physical controller is used'),
 b('haptic_feedback','Touch vibration feedback')
 ]}
];
var byKey={};sections.forEach(function(s){s.rows.forEach(function(o){byKey[o.key]=o;});});
var active='', data={}, available=false, message='';
function safe(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function trigger(){if(window.BluSwitch&&typeof window.BluSwitch.refreshSettingsView==='function')window.BluSwitch.refreshSettingsView();}
function row(o){
 var val=data[o.key],control='';
 if(o.type==='bool'){
  control='<input aria-label="'+safe(o.label)+'" type="checkbox" '+(val?'checked':'')+
   ' onchange="BluAdvanced.change(\''+o.key+'\',this.checked)">';
 }else if(o.type==='choice'){
  control='<select aria-label="'+safe(o.label)+'" onchange="BluAdvanced.change(\''+o.key+'\',this.value)">'+
   o.choices.map(function(v){return '<option value="'+v[0]+'" '+(Number(val)===v[0]?'selected':'')+'>'+safe(v[1])+'</option>';}).join('')+'</select>';
 }else if(o.type==='number'){
  control='<input aria-label="'+safe(o.label)+'" inputmode="numeric" type="number" min="'+o.min+'" max="'+o.max+'" value="'+safe(val==null?'':val)+'" onchange="BluAdvanced.change(\''+o.key+'\',this.value)">';
 }else if(o.type==='secret'){
  control='<input aria-label="'+safe(o.label)+'" type="password" placeholder="'+(data.tokenPresent?'Saved. Enter to replace':'Enter your own token')+'" autocomplete="off" onblur="BluAdvanced.change(\''+o.key+'\',this.value);this.value=\'\'">';
 }else{
  control='<input aria-label="'+safe(o.label)+'" type="text" maxlength="20" value="'+safe(val==null?'':val)+'" onblur="BluAdvanced.change(\''+o.key+'\',this.value)">';
 }
 return '<label class="advanced-setting-row"><span class="advanced-setting-info"><strong>'+safe(o.label)+'</strong>'+
  (o.desc?'<small>'+safe(o.desc)+'</small>':'')+'</span><span class="advanced-setting-control">'+control+'</span></label>';
}
window.BluAdvanced={
 render:function(){
  if(!active)return '<section class="advanced-shell"><div class="advanced-heading"><h1>BluSwitch 2 settings</h1>'+
   '<p>Configure the native emulator inside BluSwitch 2. Options take effect in games supported by the core.</p></div>'+
   '<div class="advanced-categories">'+sections.map(function(s){return '<button class="advanced-category" onclick="BluAdvanced.open(\''+s.id+'\')"><strong>'+safe(s.title)+'</strong><small>'+safe(s.subtitle)+'</small><span>›</span></button>';}).join('')+'</div></section>';
  var section=sections.find(function(s){return s.id===active;});
  return '<section class="advanced-shell"><div class="advanced-heading"><button class="advanced-back" onclick="BluAdvanced.back()">‹ Settings</button>'+
   '<h1>'+safe(section.title)+'</h1><p>'+safe(section.subtitle)+'</p></div>'+
   '<div class="advanced-rows">'+(available?section.rows.map(row).join(''):'<p class="notice">Loading the native emulator settings...</p>')+'</div>'+
   (message?'<p class="advanced-feedback">'+safe(message)+'</p>':'')+'</section>';
 },
 open:function(id){if(sections.some(function(s){return s.id===id;})){active=id;message='';trigger();if(window.BluNative)window.BluNative.requestAdvancedSettings();}},
 back:function(){if(active){active='';message='';trigger();return true;}return false;},
 reset:function(){active='';message='';trigger();},
 setValues:function(json){try{data=JSON.parse(json);available=true;message='';trigger();}catch(e){message='Could not read native settings.';trigger();}},
 saved:function(json){try{var r=JSON.parse(json);message=r.message||'';if(r.ok){data[r.key]=r.value;if(r.key==='eden_token'){data.tokenPresent=true;delete data.eden_token;}}trigger();}catch(e){}},
 change:function(key,value){
  var entry=byKey[key];if(!entry||!available)return;
  if(entry.type==='secret'){
   if(!value)return;if(!/^[a-z]{48}$/.test(String(value))){message='Web token must be exactly 48 lowercase letters.';trigger();return;}
  }
  if(entry.type==='number'){var num=Number(value);if(!Number.isInteger(num)||num<entry.min||num>entry.max){message=entry.label+' must be between '+entry.min+' and '+entry.max+'.';trigger();return;}}
  if(entry.type==='choice'&&!entry.choices.some(function(x){return x[0]===Number(value);}))return;
  if(entry.type==='text'&&!/^[\w. -]{0,20}$/.test(String(value))){message='Username must be 20 characters or fewer.';trigger();return;}
  message='Saving…';trigger();
  if(window.BluNative&&window.BluNative.writeAdvancedSetting){
   window.BluNative.writeAdvancedSetting(key,String(value));
  }else message='Native settings are unavailable in preview.';
 }
};
})();