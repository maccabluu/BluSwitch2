(function(){
'use strict';
var packages=[],loaded=false,loading=false,error='',query='',category='all',selected=null;
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c];});}
function filtered(){var q=query.toLowerCase();return packages.filter(function(p){return (category==='all'||p.category===category)&&(!q||[p.title,p.name,p.description,p.author].join(' ').toLowerCase().indexOf(q)!==-1);});}
function draw(){
 var target=document.getElementById('main-view');
 if(!target||!window.BluSwitch||!window.BluSwitch.isStoreOpen())return;
 var html='<section class="screen-panel blu-store"><h1>Homebrew Store</h1><p class="notice">Free community-made Switch homebrew from ForTheUsers. Downloads are for compatible Switch environments. The BluSwitch 2 emulator does not yet run Switch NRO apps.</p>';
 if(selected){
  var p=packages.filter(function(x){return x.name===selected;})[0];
  if(!p){selected=null;return draw();}
  html+='<button class="secondary-btn" onclick="BluStore.back()">Back to store</button><h2>'+esc(p.title||p.name)+'</h2><p>'+esc(p.description)+'</p><p class="notice">Author: '+esc(p.author||'Unknown')+' · Version: '+esc(p.version||'Unknown')+' · Category: '+esc(p.category||'Other')+'</p>';
  html+='<div class="action-row"><button class="primary-btn" onclick="BluStore.download()">Download homebrew ZIP</button></div><p class="notice">Downloads are saved through Android Downloads. Installing or running NRO packages inside BluSwitch 2 is not supported yet.</p></section>';
 } else {
 html+='<div class="action-row"><input id="blu-store-search" type="search" placeholder="Search homebrew apps" aria-label="Search homebrew" value="'+esc(query)+'" oninput="BluStore.search(this.value)"/>';
 html+='<select aria-label="Category" onchange="BluStore.category(this.value)"><option value="all">All categories</option>';
 var cats=[...new Set(packages.map(function(p){return p.category||'other';}))].sort();
 cats.forEach(function(c){html+='<option value="'+esc(c)+'"'+(category===c?' selected':'')+'>'+esc(c)+'</option>';});
 html+='</select><button class="secondary-btn" onclick="BluStore.refresh()">Refresh</button></div>';
 if(error)html+='<p class="notice">'+esc(error)+'</p>';
 else if(loading)html+='<p class="notice">Loading the live homebrew catalogue...</p>';
 else if(!loaded)html+='<p class="notice">Connect to the internet to browse homebrew.</p>';
 else {var list=filtered();html+='<p class="notice">'+list.length+' homebrew apps</p><div class="blu-store-grid">';
 list.slice(0,120).forEach(function(p){html+='<button class="blu-store-tile" onclick="BluStore.details(\''+esc(p.name)+'\')"><strong>'+esc(p.title||p.name)+'</strong><small>'+esc(p.author||'Community developer')+'</small><span>'+esc(p.description||'View details')+'</span></button>';});
 html+='</div>';if(list.length>120)html+='<p class="notice">Showing first 120 matches. Search to narrow results.</p>'; }
 html+='</section>';
 }
 target.innerHTML=html;
}
window.BluStore={
 render:function(){if(!loaded&&!loading&&!error)this.refresh();setTimeout(draw,0);return '<section class="screen-panel"><h1>Homebrew Store</h1><p>Loading catalogue...</p></section>';},
 refresh:function(){error='';loading=true;selected=null;draw();if(window.BluNative&&window.BluNative.requestStoreCatalog)window.BluNative.requestStoreCatalog();else {loading=false;error='The native catalogue service is not available in this build.';draw();}},
 receive:function(json){loading=false;try{var data=JSON.parse(json);if(data.error)throw Error(data.error);if(!Array.isArray(data.packages))throw Error('Invalid repository data');packages=data.packages.filter(function(p){return p&&typeof p.name==='string'&&/^[A-Za-z0-9_.-]+$/.test(p.name);});loaded=true;error='';}catch(e){error='Could not load the catalogue: '+e.message;}draw();},
 search:function(s){query=s;var el=document.getElementById('blu-store-search');var start=el&&el.selectionStart;draw();el=document.getElementById('blu-store-search');if(el){el.focus();try{el.setSelectionRange(start,start);}catch(e){}}},
 category:function(s){category=s;draw();},
 details:function(name){selected=name;draw();},
 back:function(){selected=null;draw();},
 download:function(){if(selected&&window.BluNative&&window.BluNative.downloadHomebrew)window.BluNative.downloadHomebrew(selected);else alert('Downloads are unavailable in this build.');}
};
})();