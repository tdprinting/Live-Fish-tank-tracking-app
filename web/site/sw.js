// Offline support. App code is network-first (updates show on the next launch); the big, versioned-by-path
// models, textures and species data are cache-first. BUILD is replaced at build time.
const BUILD = '__BUILD__';
const CODE = `reeflive-code-${BUILD}`;
const DATA = `reeflive-data-${BUILD}`;

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CODE).then((c) => c.addAll(['./', 'index.html', 'app.js', 'style.css', 'manifest.webmanifest', 'kotlin/ReefLive-web.mjs'])).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CODE && k !== DATA).map((k) => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  const isData = /\/(assets|res)\//.test(url.pathname) && !url.pathname.endsWith('index.json');
  e.respondWith(isData ? cacheFirst(e.request) : networkFirst(e.request));
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
