/* Network first for the app's own files, so a new version shows up as soon as the phone is online; the cache is only for offline. */
const C='sarrifli-v18';const F=['./','index.html','manifest.webmanifest','icon-192.png','icon-512.png'];
self.addEventListener('install',e=>{self.skipWaiting();e.waitUntil(caches.open(C).then(c=>c.addAll(F)))});
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C).map(x=>caches.delete(x)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;const u=new URL(e.request.url);
 if(u.origin===location.origin&&u.pathname.endsWith('version.json'))return;
 if(u.origin===location.origin){e.respondWith(fetch(e.request,{cache:'no-cache'}).then(res=>{if(res.ok){const c=res.clone();caches.open(C).then(x=>x.put(e.request,c))}return res}).catch(()=>caches.match(e.request,{ignoreSearch:true}).then(r=>r||caches.match('./'))));return}
 if(u.hostname.includes('fonts.')){e.respondWith(caches.match(e.request).then(r=>r||fetch(e.request).then(res=>{const c=res.clone();caches.open(C).then(x=>x.put(e.request,c));return res})))}});
/* Phone push: show every message (Safari on iPhone requires it), and open the app on tap. */
self.addEventListener('push',e=>{let d={};try{d=e.data?e.data.json():{}}catch(x){}const m=d.data||d.notification||d;
 e.waitUntil(self.registration.showNotification(m.title||'صرفلي',{body:m.body||'',icon:'icon-192.png',badge:'icon-192.png',dir:'rtl',lang:'ar',tag:'n'+Date.now()}))});
self.addEventListener('notificationclick',e=>{e.notification.close();e.waitUntil(clients.matchAll({type:'window',includeUncontrolled:true}).then(L=>{for(const c of L)if('focus' in c)return c.focus();return clients.openWindow('./')}))});
