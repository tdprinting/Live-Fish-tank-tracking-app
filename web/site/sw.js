// Offline support and instant repeat visits. The build (web/build.gradle.kts SiteOptimizer) fills in FILES: every
// file of this build with its content hash. Each file is cached under `path?v=hash`, so a cached copy is served
// without asking the network, and a file that changed in a new build has a new key. A new build is a new sw.js: it
// installs in the background, keeps the files that did not change, drops the rest, and the next launch runs it.
const BUILD = '__BUILD__';
const FILES = __FILES__;
const CACHE = 'reeflive-files';
// what the page needs to start offline; everything else is cached the first time it is used
const SHELL = ['index.html', 'app.js', 'style.css', 'manifest.webmanifest', 'boot.json', 'icons/icon.svg', 'icons/icon-192.png'];
const scope = () => self.registration.scope;
const keyOf = (p) => new URL(`${p}?v=${FILES[p]}`, scope()).href;

self.addEventListener('install', (e) => {
  // An update fetches the new build's shell now. A first install does not: the page is still downloading those
  // files itself, and hands them over from the browser's cache once the tank is in (app.js, 'cache-used').
  if (self.registration.active) e.waitUntil(caches.open(CACHE).then((c) => Promise.all(SHELL.filter((p) => FILES[p]).map((p) => cacheFile(c, p)))).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    const c = await caches.open(CACHE);
    // earlier builds kept models and species sheets per build (reeflive-data-<build>): keep the ones that still match
    for (const name of await caches.keys()) {
      if (name === CACHE) continue;
      if (name.startsWith('reeflive-data-')) {
        const old = await caches.open(name);
        for (const req of await old.keys()) {
          const p = pathOf(new URL(req.url));
          if (!p || !FILES[p] || await c.match(keyOf(p))) continue;
          const res = await old.match(req);
          const body = res && await res.arrayBuffer();
          if (body && await hashOf(body) === FILES[p]) await c.put(keyOf(p), new Response(body, { headers: { 'Content-Type': res.headers.get('Content-Type') || '' } }));
        }
      }
      await caches.delete(name);
    }
    // files this build no longer has, or has changed
    const live = new Set(Object.keys(FILES).map(keyOf));
    for (const req of await c.keys()) if (!live.has(req.url)) await c.delete(req);
  })().catch(() => {}));
  self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  // radio songs stream with range requests (206s can't be cached): let the browser fetch them itself
  if (req.method !== 'GET' || req.headers.has('range')) return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  const p = pathOf(url);
  if (!p || !FILES[p]) return;
  e.respondWith(serve(p, req));
});

/** The file's path in the site ("" and "/" are index.html), or null outside this app. */
function pathOf(url) {
  const base = new URL(scope()).pathname;
  if (!url.pathname.startsWith(base)) return null;
  return decodeURIComponent(url.pathname.slice(base.length)) || 'index.html';
}

async function serve(p, req) {
  const c = await caches.open(CACHE);
  const hit = await c.match(keyOf(p));
  if (hit) return hit;
  return (await cacheFile(c, p)) || fetch(req);
}

/**
 * Downloads [p], checks it against this build's hash and caches it. The browser's HTTP cache is tried first (the
 * page's first visit downloaded the files before this worker took over), then the network. Null when neither has
 * this build's copy (the site was updated since): the page then gets the network's copy, uncached.
 */
async function cacheFile(c, p) {
  for (const [url, mode] of [[p, 'force-cache'], [keyOf(p), 'no-cache']]) {
    try {
      const res = await fetch(new URL(url, scope()), { cache: mode });
      if (!res.ok) continue;
      const body = await res.arrayBuffer();
      if (await hashOf(body) !== FILES[p]) continue;
      const out = new Response(body, { headers: { 'Content-Type': res.headers.get('Content-Type') || '' } });
      await c.put(keyOf(p), out.clone());
      return out;
    } catch { /* offline: try the next way */ }
  }
  return null;
}

async function hashOf(buf) {
  const d = new Uint8Array(await crypto.subtle.digest('SHA-256', buf));
  return Array.from(d.subarray(0, 8), (b) => b.toString(16).padStart(2, '0')).join('');
}

// The page's first visit ran before this worker: it lists what it downloaded so the next visit finds it all here.
self.addEventListener('message', (e) => {
  const m = e.data || {};
  if (m.type !== 'cache-used') return;
  e.waitUntil(caches.open(CACHE).then(async (c) => {
    for (const p of new Set(m.paths || [])) if (FILES[p] && !(await c.match(keyOf(p)))) await cacheFile(c, p);
  }).catch(() => {}));
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

