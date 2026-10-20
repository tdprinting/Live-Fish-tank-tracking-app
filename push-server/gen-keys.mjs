// Prints a new VAPID key pair: the public key (goes in web/site/push-config.js and the worker's VAPID_PUBLIC) and the
// private JWK (store ONLY as the worker secret VAPID_PRIVATE_JWK; never commit it).
import { b64u } from './src/webpush.js';
const k = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign']);
console.log('VAPID_PUBLIC=' + b64u(await crypto.subtle.exportKey('raw', k.publicKey)));
console.log('VAPID_PRIVATE_JWK=' + JSON.stringify(await crypto.subtle.exportKey('jwk', k.privateKey)));
