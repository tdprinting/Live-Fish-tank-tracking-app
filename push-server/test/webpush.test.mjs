// node push-server/test/webpush.test.mjs : encrypt like the worker, decrypt like a browser (RFC 8291), check VAPID signature.
import assert from 'node:assert/strict';
import { encryptPayload, vapidHeader, b64u, unb64u } from '../src/webpush.js';
const ec = { name: 'ECDH', namedCurve: 'P-256' };
const ua = await crypto.subtle.generateKey(ec, true, ['deriveBits']);
const uaPub = new Uint8Array(await crypto.subtle.exportKey('raw', ua.publicKey));
const auth = crypto.getRandomValues(new Uint8Array(16));
const msg = JSON.stringify({ title: 'Feeding time! 🐟', body: 'Nemo is hungry.' });
const body = await encryptPayload(msg, b64u(uaPub), b64u(auth));
const salt = body.slice(0, 16), idlen = body[20], asPub = body.slice(21, 21 + idlen), ct = body.slice(21 + idlen);
const te = new TextEncoder();
const hk = async (s, ikm, info, n) => new Uint8Array(await crypto.subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: s, info }, await crypto.subtle.importKey('raw', ikm, 'HKDF', false, ['deriveBits']), n * 8));
const asKey = await crypto.subtle.importKey('raw', asPub, ec, false, []);
const ecdh = new Uint8Array(await crypto.subtle.deriveBits({ name: 'ECDH', public: asKey }, ua.privateKey, 256));
const cat = (...a) => Uint8Array.from(a.flatMap((x) => [...x]));
const prk = await hk(auth, ecdh, cat(te.encode('WebPush: info\0'), uaPub, asPub), 32);
const cek = await hk(salt, prk, te.encode('Content-Encoding: aes128gcm\0'), 16);
const nonce = await hk(salt, prk, te.encode('Content-Encoding: nonce\0'), 12);
const plain = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce }, await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['decrypt']), ct));
assert.equal(plain.at(-1), 2);
assert.equal(new TextDecoder().decode(plain.slice(0, -1)), msg);

const k = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
const pub = b64u(await crypto.subtle.exportKey('raw', k.publicKey));
const h = await vapidHeader('https://web.push.apple.com/abc', pub, await crypto.subtle.exportKey('jwk', k.privateKey), 'mailto:a@b.c');
const [, t] = /t=([^,]+),/.exec(h); const [hd, cl, sg] = t.split('.');
assert.equal(JSON.parse(atob(cl.replace(/-/g, '+').replace(/_/g, '/'))).aud, 'https://web.push.apple.com');
assert.ok(await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, k.publicKey, unb64u(sg), te.encode(`${hd}.${cl}`)));
console.log('webpush ok');
