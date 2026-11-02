(function(){
'use strict';
var catalogue=[],loaded=false,loading=false,category='all',search='',selected='',busy='',message='';
function esc(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function group(c){
 var name=String(c||'').toLowerCase();
 if(name==='game'||name==='games')return 'Games';
 if(name==='emulator'||name==='emulators')return 'Emulators';
 if(name==='tool'||name==='tools')return 'Tools';
 if(name==='advanced')return 'Advanced';
 return 'Other';
}
function nativeCall(name,arg){
 if(window.BluNative && typeof window.BluNative[name]==='function')
  return typeof arg==='undefined'?window.BluNative[name]():window.BluNative[name](arg);
}
function refresh(){
 if(window.BluSwitch && typeof window.BluSwitch.refreshStore==='function')window.BluSwitch.refreshStore();
}
function item(id){return catalogue.find(function(g){return g.id===id;});}
function actionButton(g){
 if(busy===g.id)return '<button disabled class="hbst-btn">Downloading...</button>';
 return g.downloaded?'<button class="hbst-btn hbst-downloaded" disabled>Downloaded</button>':
   '<button class="hbst-btn" onclick="BluStore.download(\''+esc(g.id)+'\')">Download ZIP</button>';
}
function cover(g){
 var title=esc(g.title);
 var first=title.slice(0,1).toUpperCase()||'H';
 var src=/^https:\/\/switch\.cdn\.fortheusers\.org\/packages\/[a-zA-Z0-9_.-]+\/icon\.png$/.test(g.icon)?g.icon:'';
 return '<div class="hbst-art"><span>'+first+'</span>'+ (src?'<img loading="lazy" alt="" src="'+esc(src)+'" onerror="this.style.display=\'none\'"/>':'') +'</div>';
}
function card(g){
 return '<article class="hbst-card"><button class="hbst-hit" onclick="BluStore.detail(\''+esc(g.id)+'\')">'+
 cover(g)+'<span class="hbst-name">'+esc(g.title)+'</span><span class="hbst-author">'+esc(g.author||'Homebrew developer')+'</span>'+
 '<span class="hbst-type">'+esc(group(g.category))+'</span></button>'+
 '<div class="hbst-actions">'+actionButton(g)+'</div></article>';
}
function detail(g){
 return '<section class="hbst-detail">'+
 '<button class="hbst-back" onclick="BluStore.back()">← Back to Store</button>'+
 '<div class="hbst-details-top">'+cover(g)+'<div><p class="hbst-eyebrow">HOME BREW · '+esc(group(g.category))+'</p>'+
 '<h1>'+esc(g.title)+'</h1><p>By '+esc(g.author||'Unknown author')+'</p>'+
 '<p>Version '+esc(g.version||'Not listed')+'</p>'+
 '<div class="hbst-detail-buttons">'+actionButton(g)+
 (g.downloaded?'<button class="hbst-btn-outline" onclick="BluStore.remove(\''+esc(g.id)+'\')">Remove download</button>':'')+'</div></div></div>'+
 '<p class="hbst-description">'+esc(g.description||'No description provided by the publisher.')+'</p>'+
 '<p class="hbst-caution">Packages are saved in BluSwitch 2. Running NRO homebrew depends on native core compatibility and is not confirmed.</p>'+
 '<p class="hbst-credit">Catalogue supplied by ForTheUsers</p></section>';
}
function main(){
 var groups=['All','Games','Emulators','Tools','Advanced','Other'];
 var filtered=catalogue.filter(function(g){
  var matchCategory=category==='all'||group(g.category).toLowerCase()===category;
  var q=search.trim().toLowerCase();
  return matchCategory&&(!q||(g.title+' '+g.author+' '+g.description).toLowerCase().includes(q));
 });
 return '<section class="hbst-screen"><div class="hbst-title-row"><div>'+
 '<p class="hbst-eyebrow">BLUSWITCH 2 · HOMEBREW</p>'+
 '<h1>Homebrew Store</h1>'+
 '<p class="hbst-subheading">Browse independent games and apps. Downloads stay inside BluSwitch 2.</p></div>'+
 '<span class="hbst-counter">'+(loaded?catalogue.length+' apps':'Live catalogue')+'</span></div>'+
 '<div class="hbst-searchline"><label class="hbst-search-label">Search apps<input type="search" placeholder="Search games, tools, developers" value="'+esc(search)+'" onchange="BluStore.search(this.value)" aria-label="Search homebrew"/></label>'+
 '<button class="hbst-refresh" onclick="BluStore.reload()"'+(loading?' disabled':'')+'>Refresh</button></div>'+
 '<div class="hbst-tabs">'+groups.map(function(label){var id=label.toLowerCase();return '<button class="'+(category===id?'active':'')+'" onclick="BluStore.category(\''+id+'\')">'+label+'</button>';}).join('')+'</div>'+
 (message?'<p class="hbst-message">'+esc(message)+'</p>':'')+
 (loading&& !loaded?'<div class="hbst-empty">Loading the ForTheUsers catalogue...</div>':
  !loaded?'<div class="hbst-empty">Choose Refresh to load the live catalogue. An internet connection is needed.</div>':
  filtered.length?'<div class="hbst-grid">'+filtered.slice(0,200).map(card).join('')+'</div>':
  '<div class="hbst-empty">No homebrew found for this search.</div>')+
 '<p class="hbst-credit">Homebrew catalogue from ForTheUsers. BluSwitch 2 is an independent client. Switch 2 launch compatibility is not guaranteed.</p></section>';
}
window.BluStore={
 render:function(){var g=item(selected);return g?detail(g):main();},
 open:function(){if(!loaded&&!loading)this.reload();},
 reload:function(){loading=true;message='';selected='';refresh();nativeCall('requestHomebrewCatalogue');},
 setCatalogue:function(json){try{var o=JSON.parse(json);if(!Array.isArray(o.packages))throw Error('invalid');
  catalogue=o.packages;loaded=true;loading=false;message='';refresh();}catch(e){this.error('Could not load the homebrew catalogue.');}},
 error:function(msg){loading=false;busy='';message=String(msg);refresh();},
 detail:function(id){if(item(id)){selected=id;message='';refresh();}},
 back:function(){selected='';message='';refresh();return true;},
 isDetail:function(){return !!selected;},
 category:function(c){category=c;selected='';refresh();},
 search:function(q){search=String(q).slice(0,100);refresh();},
 download:function(id){var g=item(id);if(!g||busy)return;busy=id;message='';refresh();nativeCall('downloadHomebrew',id);},
 remove:function(id){var g=item(id);if(!g||!g.downloaded)return;
  if(confirm('Remove the downloaded homebrew package? Your other files will stay.')){busy=id;nativeCall('removeHomebrew',id);}},
 result:function(json){try{var o=JSON.parse(json);busy='';var g=item(o.id);
  if(g)g.downloaded=!!o.downloaded;message=o.message||'';refresh();
 }catch(e){this.error('Could not update the downloaded package.');}}
};
})();