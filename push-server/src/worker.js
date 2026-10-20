// ReefLive push sender: a Cloudflare Worker (free plan). The iPhone page registers the times its tank will need
// attention; a cron trigger every 5 minutes sends the ones that are due as Web Push. No tank data is kept: only the
// push address, and a title and sentence per alert. See push-server/README.md.
import { sendPush } from './webpush.js';

const ORIGIN = 'https://tdprinting.github.io';
const MAX_ALERTS = 8;
const cors = (extra = {}) => ({ 'Access-Control-Allow-Origin': ORIGIN, 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS', ...extra });
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: cors({ 'Content-Type': 'application/json' }) });
const idOf = async (endpoint) => [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(endpoint)))].slice(0, 16).map((b) => b.toString(16).padStart(2, '0')).join('');
const validSub = (s) => s && typeof s.endpoint === 'string' && s.endpoint.startsWith('https://') && s.keys && typeof s.keys.p256dh === 'string' && typeof s.keys.auth === 'string';

export default {
  async fetch(req, env) {
    if (req.method === 'OPTIONS') return new Response(null, { headers: cors() });
    const url = new URL(req.url);
    if (url.pathname === '/key') return json({ key: env.VAPID_PUBLIC });
    if (req.method !== 'POST') return json({ ok: true });
    let b;
    try { b = await req.json(); } catch { return json({ error: 'bad json' }, 400); }
    if (!validSub(b.sub)) return json({ error: 'bad subscription' }, 400);
    const id = await idOf(b.sub.endpoint);
    if (url.pathname === '/cancel') { await env.QUEUE.delete(id); return json({ ok: true }); }
    if (url.pathname === '/schedule') {
      const alerts = (Array.isArray(b.alerts) ? b.alerts : []).slice(0, MAX_ALERTS)
        .filter((a) => Number.isFinite(a.atMs) && typeof a.title === 'string' && typeof a.body === 'string')
        .map((a) => ({ atMs: a.atMs, tag: String(a.tag).slice(0, 20), title: a.title.slice(0, 80), body: a.body.slice(0, 200) }));
      if (!alerts.length) await env.QUEUE.delete(id);
      else await env.QUEUE.put(id, JSON.stringify({ sub: b.sub, alerts }), { expirationTtl: 5 * 86400 });
      return json({ ok: true, count: alerts.length });
    }
    return json({ error: 'not found' }, 404);
  },

  async scheduled(_event, env) {
    const now = Date.now();
    let cursor;
    do {
      const page = await env.QUEUE.list({ cursor });
      cursor = page.list_complete ? undefined : page.cursor;
      for (const k of page.keys) {
        const rec = JSON.parse((await env.QUEUE.get(k.name)) || 'null');
        if (!rec) continue;
        const due = rec.alerts.filter((a) => a.atMs <= now);
        if (!due.length) continue;
        // a note that is hours late is stale: skip it rather than ring at the wrong time
        const fresh = due.filter((a) => now - a.atMs < 3 * 3600_000);
        let gone = false;
        for (const a of fresh.slice(0, 1)) {
          const st = await sendPush(rec.sub, { title: a.title, body: a.body, tag: a.tag }, env).catch(() => 0);
          if (st === 404 || st === 410) gone = true;
        }
        if (gone) { await env.QUEUE.delete(k.name); continue; }
        rec.alerts = rec.alerts.filter((a) => a.atMs > now);
        if (rec.alerts.length) await env.QUEUE.put(k.name, JSON.stringify(rec), { expirationTtl: 5 * 86400 });
        else await env.QUEUE.delete(k.name);
      }
    } while (cursor);
  },
};
