// ReefLive web: "Tank alerts". When My tank is left, the Kotlin side (core custom/CareAlerts) works out when the tank
// will next need you; this file hands those times to the push sender (push-server/), which sends them to the phone as
// Web Push. Opening the app again cancels them. iPhone needs the app added to the Home Screen (iOS 16.4+).
import { PUSH_URL } from './push-config.js';

const KEY = 'reeflive.notify';
const SUB = 'reeflive.pushsub';
const get = (k) => { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } };
const set = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* private mode */ } };

const standalone = () => navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;
const isIos = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const canPush = () => !!PUSH_URL && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
const post = (path, body, keepalive) => fetch(PUSH_URL + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), keepalive }).catch(() => {});

export const alertsOn = () => get(KEY) === true && Notification?.permission === 'granted' && !!get(SUB);

/** Why the switch cannot be turned on right now, or '' when it can. */
export function alertsBlocker() {
  if (!PUSH_URL) return 'Tank alerts are not switched on for this site yet.';
  if (isIos() && !standalone()) return 'On iPhone, tap Share, then Add to Home Screen, and open ReefLive from there. Then turn this on.';
  if (!canPush()) return 'This browser cannot send tank alerts.';
  if (typeof Notification !== 'undefined' && Notification.permission === 'denied') return 'Notifications are blocked. Allow them for ReefLive in Settings.';
  return '';
}

const toKey = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(s.length / 4) * 4, '=')), (c) => c.charCodeAt(0));

/** Turns alerts on (call from a tap: the permission prompt needs one). Returns '' on success or a sentence why not. */
export async function enableAlerts() {
  const why = alertsBlocker();
  if (why) return why;
  try {
    if ((await Notification.requestPermission()) !== 'granted') return 'No problem. You can turn alerts on later.';
    const reg = await navigator.serviceWorker.ready;
    const { key } = await (await fetch(PUSH_URL + '/key')).json();
    const sub = (await reg.pushManager.getSubscription()) || await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toKey(key) });
    set(SUB, sub.toJSON());
    set(KEY, true);
    return '';
  } catch { return 'Could not set up alerts. Check the internet and try again.'; }
}

export async function disableAlerts() {
  const sub = get(SUB);
  set(KEY, false);
  if (sub) await post('/cancel', { sub });
}

/** The page is going away: ask the tank when it will need you and register those times. */
export function scheduleAlerts(my) {
  if (!alertsOn()) return;
  let alerts = [];
  try { alerts = JSON.parse(my.alertPlan(new Date().getTimezoneOffset() * -1)); } catch { /* no tank */ }
  post('/schedule', { sub: get(SUB), alerts }, true);
}

/** The page is back in view: nothing should ring while the keeper is looking at the tank. */
export function cancelAlerts() {
  if (!alertsOn()) return;
  post('/cancel', { sub: get(SUB) }, true);
}
