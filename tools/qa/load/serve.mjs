// HTTP/2 static server that behaves like GitHub Pages for load measurements: gzip for text types only (not
// .glb/.bin/images), Cache-Control max-age=600, ETag + 304. The link is shaped here (shared bandwidth + per-request
// latency) so service-worker fetches are throttled the same as page fetches.
// usage: node serve.mjs <root> <port> <mbps> <rttMs>   (mbps / rttMs 0 = unshaped)
import http2 from 'http2';
import fs from 'fs';
import path from 'path';
import zlib from 'zlib';
import { execFileSync } from 'child_process';
// self-signed localhost certificate (created with openssl on first use) in $QA_OUT (default ./qa-out)
const D = path.resolve(process.env.QA_OUT || 'qa-out');
fs.mkdirSync(D, { recursive: true });
if (!fs.existsSync(`${D}/key.pem`) || !fs.existsSync(`${D}/cert.pem`)) {
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', `${D}/key.pem`, '-out', `${D}/cert.pem`, '-days', '30', '-subj', '/CN=localhost'], { stdio: 'ignore' });
}
const root = path.resolve(process.argv[2]);
const port = +process.argv[3];
const mbps = +(process.argv[4] || 0);
const rtt = +(process.argv[5] || 0);
const TYPES = { '.html': 'text/html', '.js': 'application/javascript', '.mjs': 'application/javascript', '.css': 'text/css', '.json': 'application/json',
  '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.webp': 'image/webp', '.jpg': 'image/jpeg',
  '.glb': 'model/gltf-binary', '.bin': 'application/octet-stream', '.gz': 'application/gzip', '.ogg': 'audio/ogg', '.m4a': 'audio/mp4' };
const TEXT = new Set(['.html', '.js', '.mjs', '.css', '.json', '.svg', '.webmanifest', '.glsl', '.vert', '.frag', '.txt']);
const gzCache = new Map();
// global token bucket: bytes per ms, FIFO waiters
const rate = mbps > 0 ? (mbps * 1e6 / 8) / 1000 : Infinity;
let tokens = 0, last = Date.now(); const waiters = [];
function pumpTokens() {
  const now = Date.now(); tokens = Math.min(tokens + (now - last) * rate, 64 * 1024); last = now;
  while (waiters.length && tokens >= waiters[0].n) { tokens -= waiters[0].n; waiters.shift().ok(); }
  if (waiters.length) setTimeout(pumpTokens, 2);
}
const take = (n) => rate === Infinity ? Promise.resolve() : new Promise((ok) => { waiters.push({ n, ok }); if (waiters.length === 1) setTimeout(pumpTokens, 0); });
let bytesOut = 0, reqs = 0;
const log = [];
const server = http2.createSecureServer({ key: fs.readFileSync(`${D}/key.pem`), cert: fs.readFileSync(`${D}/cert.pem`), allowHTTP1: true }, async (req, res) => {
  const u = new URL(req.url, 'https://x');
  if (u.pathname === '/__stats') { res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify({ bytesOut, reqs, log })); return; }
  if (u.pathname === '/__reset') { bytesOut = 0; reqs = 0; log.length = 0; res.end('ok'); return; }
  let p = decodeURIComponent(u.pathname);
  if (p.endsWith('/')) p += 'index.html';
  const f = path.join(root, p);
  await new Promise((r) => setTimeout(r, rtt));
  reqs++;
  if (!f.startsWith(root) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); res.end('404'); log.push([Date.now(), p, 404, 0]); return; }
  const st = fs.statSync(f);
  const etag = `"${st.size.toString(16)}-${Math.floor(st.mtimeMs).toString(16)}"`;
  const ext = path.extname(f);
  const h = { 'Content-Type': TYPES[ext] || 'text/plain', 'Cache-Control': 'max-age=600', ETag: etag, 'Last-Modified': st.mtime.toUTCString(), Vary: 'Accept-Encoding' };
  if (req.headers['if-none-match'] === etag) { res.writeHead(304, h); res.end(); log.push([Date.now(), p, 304, 0]); return; }
  let body = fs.readFileSync(f);
  if (TEXT.has(ext) && /gzip/.test(req.headers['accept-encoding'] || '')) {
    const k = f + etag;
    if (!gzCache.has(k)) gzCache.set(k, zlib.gzipSync(body, { level: 6 }));
    body = gzCache.get(k); h['Content-Encoding'] = 'gzip';
  }
  h['Content-Length'] = body.length;
  res.writeHead(200, h);
  const entry = [Date.now(), p, 200, body.length, 0]; log.push(entry);
  for (let i = 0; i < body.length; i += 16384) {
    const c = body.subarray(i, i + 16384);
    await take(c.length);
    bytesOut += c.length;
    if (res.destroyed || res.closed) return;
    if (!res.write(c)) await new Promise((r) => res.once('drain', r));
  }
  res.end(); entry[4] = Date.now();
});
server.listen(port, '127.0.0.1', () => console.log('serving', root, 'on', port, mbps, 'Mbps', rtt, 'ms'));
