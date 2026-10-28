// Offline support. App code is network-first (updates show on the next launch); the big, versioned-by-path
// models, textures and species data are cache-first. BUILD is replaced at build time.
const BUILD = '__BUILD__';
const CODE = `reeflive-code-${BUILD}`;
const DATA = `reeflive-data-${BUILD}`;

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CODE).then((c) => c.addAll(['./', 'index.html', 'app.js', 'simhost.js', 'live.js', 'bags.js', 'corals.js', 'flush.js', 'care.js', 'stores.js', 'radio.js', 'room.js', 'tracker.js', 'glyphs.js', 'style.css', 'manifest.webmanifest', 'kotlin/ReefLive-web.mjs'])).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CODE && k !== DATA).map((k) => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // radio songs stream with range requests (206s can't be cached): let the browser fetch them itself
  if (url.pathname.includes('/sounds/radio_')) return;
  const isData = /\/(assets|res)\//.test(url.pathname) && !url.pathname.endsWith('index.json');
  e.respondWith(isData ? cacheFirst(e.request) : networkFirst(e.request));
});

// Acclimation timer (tracker.js): a phone pauses the page's timers once it is in the background, so the page hands
// the end of the running step here. A service worker is only kept alive a few minutes, so this covers short waits;
// on return the page catches up anyway.
let alarm = null;
function dropAlarm() { if (alarm) { clearTimeout(alarm.id); alarm.done(); alarm = null; } }
self.addEventListener('message', (e) => {
  const m = e.data || {};
  if (m.type === 'accl-cancel') { dropAlarm(); return; }
  if (m.type !== 'accl-alarm') return;
  dropAlarm();
  const wait = m.at - Date.now();
  if (wait <= 0 || wait > 5 * 60 * 1000) return;
  e.waitUntil(new Promise((done) => {
    const id = setTimeout(async () => {
      alarm = null;
      const open = await self.clients.matchAll({ type: 'window' });
      // a visible page plays its own chime
      if (!open.some((c) => c.visibilityState === 'visible')) {
        await self.registration.showNotification(m.title, { body: m.body, tag: 'reeflive-accl', renotify: true, icon: 'icons/icon-192.png', vibrate: [200, 100, 200] }).catch(() => {});
      }
      done();
    }, wait);
    alarm = { id, done };
  }));
});

self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => (list[0] ? list[0].focus() : self.clients.openWindow('./'))));
});

async function cacheFirst(req) {
  const hit = await caches.match(req);
  if (hit) return hit;
  const res = await fetch(req);
  if (res.ok) (await caches.open(DATA)).put(req, res.clone());
  return res;
}

async function networkFirst(req) {
  try {
    const res = await fetch(req);
    if (res.ok) (await caches.open(CODE)).put(req, res.clone());
    return res;
  } catch {
    return (await caches.match(req)) || (await caches.match('./'));
  }
}
