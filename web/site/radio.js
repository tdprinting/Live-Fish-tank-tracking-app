// The little radio on the table beside the tank (Android TankRadio): quiet background songs from RadioStation (shared
// Kotlin), one after another. Songs stream through an <audio> element routed into the page's AudioContext, so the
// level works on iPhone too (iOS ignores audio.volume) and nothing big is decoded into memory.
// The record shelf (Android RecordShelf) picks a song, buys locked records with sand dollars in My tank and sets the
// music volume.
let R = null;          // { songs:[{file,title,blurb,price,art}], gain, tuneGain, tune }
let opts = null;       // { SB, store, ctx(), say, records() → bought files in My tank, buy(file) → bool, coins() → number|null }
let el = null, gain = null, tuneBuf = null, playing = -1;
const st = { on: true, track: 0, volume: 0.6 };

export function initRadio(o) {
  opts = o;
  R = JSON.parse(o.SB.radio());
  st.on = o.store.get('reeflive.radio', true);
  st.volume = o.store.get('reeflive.radioVolume', 0.6);
  st.track = playableOr(o.store.get('reeflive.radioTrack', 0));
  document.addEventListener('visibilitychange', () => { if (document.hidden) el?.pause(); else sync(); });
}

export const radioOn = () => st.on;
export const radioSong = () => R.songs[st.track];
export const radioVol = () => st.volume;

/** Call from a tap: iOS only lets sound start inside one. */
export function radioKick() { if (R && st.on && (!el || el.paused)) sync(); }

export function radioToggle() {
  st.on = !st.on; opts.store.set('reeflive.radio', st.on);
  const s = radioSong();
  opts.say(st.on ? `Radio on: ${s.title}. ${s.blurb}, playing softly.` : 'Radio off. Just the tank sounds now.');
  sync();
}

export function radioStep(d) {
  setTrack(step(st.track, d));
  const s = radioSong();
  opts.say(`Now playing: ${s.title}. ${s.blurb}.`);
  tuneStatic();
}

/** The music volume slider (0..1). */
export function radioVolume(v) { st.volume = v; opts.store.set('reeflive.radioVolume', v); if (R && el) sync(); }

/** A record on the shelf was tapped: play it if it is ours, else try to buy it. */
export function radioPick(i) {
  const s = R.songs[i];
  if (playable(s)) {
    if (!st.on) { st.on = true; opts.store.set('reeflive.radio', true); }
    setTrack(i); tuneStatic();
    opts.say(`Now playing: ${s.title}. ${s.blurb}.`);
    return;
  }
  if (opts.coins() == null) { opts.say(`${s.title} is in the record shop. Buy it in My tank for ${s.price} sand dollars.`); return; }
  if (!opts.buy(s.file)) { opts.say(`Not enough sand dollars: ${s.title} costs ${s.price}. Do today's care jobs to earn more!`); return; }
  remember([s.file]);
  st.on = true; opts.store.set('reeflive.radio', true);
  setTrack(i); tuneStatic();
  opts.say(`A new record: ${s.title}! ${s.blurb}. Let's give it a spin.`);
}

/** The record shelf's HTML (inside Settings & help). */
export function radioShelfHtml(esc) {
  const coins = opts.coins();
  return `<label class="slider"><span>Music volume</span><output>${Math.round(st.volume * 100)}%</output><input type="range" data-radiovol min="0" max="1" step="0.05" value="${st.volume}"></label>
    ${coins != null ? `<p class="muted">You have ${coins} sand dollars. Locked records show their price.</p>` : '<p class="muted">Locked records are sold in My tank, for sand dollars.</p>'}
    <div class="records">${R.songs.map((s, i) => {
      const mine = playable(s), now = st.on && i === st.track;
      return `<button class="record ${mine ? '' : 'locked'} ${now ? 'now' : ''}" data-record="${i}"><span class="disc"><img src="vinyl/${s.art}.webp" alt="" loading="lazy">${mine ? '' : `<em>${s.price}</em>`}</span>
        <strong>${esc(s.title)}</strong><small>${now ? 'Playing' : mine ? esc(s.blurb) : coins != null ? 'Buy' : 'Buy in My tank'}</small></button>`;
    }).join('')}</div>`;
}

// ---------------------------------------------------------------- internals
const wrap = (i) => ((i % R.songs.length) + R.songs.length) % R.songs.length;
const level = () => R.gain * st.volume;
// Safari can't play every Ogg: the build also ships .m4a (web/build.gradle.kts webSounds)
const ext = () => (el.canPlayType('audio/mp4; codecs="mp4a.40.2"') ? 'm4a' : 'ogg');

/** Bought records: remembered in this browser too, so Watch mode plays them (Android keeps them in settings). */
function owned() {
  const mine = opts.records();
  const kept = opts.store.get('reeflive.radioOwned', []);
  if (mine.some((f) => !kept.includes(f))) remember(mine);
  return new Set([...kept, ...mine]);
}
function remember(files) { opts.store.set('reeflive.radioOwned', [...new Set([...opts.store.get('reeflive.radioOwned', []), ...files])]); }
const playable = (s, own = owned()) => s.price === 0 || own.has(s.file);
const playableOr = (i) => (playable(R.songs[wrap(i)]) ? wrap(i) : 0);
/** RadioStation.step: the next playable song, skipping records not bought yet. */
function step(i, d) {
  const own = owned();
  let j = wrap(i);
  for (let k = 0; k < R.songs.length; k++) { j = wrap(j + (d < 0 ? -1 : 1)); if (playable(R.songs[j], own)) return j; }
  return wrap(i);
}

function setTrack(i) { st.track = wrap(i); opts.store.set('reeflive.radioTrack', st.track); sync(); }

function sync() {
  if (!st.on || document.hidden || st.volume <= 0) { el?.pause(); return; }
  const ctx = opts.ctx();
  if (!el) {
    el = new Audio(); el.preload = 'auto';
    el.addEventListener('ended', () => setTrack(step(st.track, 1)));
    // no .m4a next to the page (a local build without ffmpeg): fall back to the .ogg once
    el.addEventListener('error', () => { if (el.src.endsWith('.m4a')) { el.src = el.src.replace(/\.m4a$/, '.ogg'); el.play().catch(() => {}); } });
    gain = ctx.createGain(); gain.gain.value = level();
    ctx.createMediaElementSource(el).connect(gain).connect(ctx.destination);
  }
  if (playing !== st.track) { el.src = `sounds/${R.songs[st.track].file}.${ext()}`; playing = st.track; }
  gain.gain.setTargetAtTime(level(), ctx.currentTime, 0.1);
  if (ctx.state === 'suspended') ctx.resume();
  el.play().catch(() => { /* waits for the next tap (radioKick) */ });
}

/** The dial turning: a short sweep of static. */
async function tuneStatic() {
  if (!st.on) return;
  const ctx = opts.ctx();
  if (!tuneBuf) {
    for (const e of ['m4a', 'ogg']) {
      try { const r = await fetch(`sounds/${R.tune}.${e}`); if (r.ok) { tuneBuf = await ctx.decodeAudioData(await r.arrayBuffer()); break; } } catch { /* next format */ }
    }
    if (!tuneBuf) return;
  }
  const src = ctx.createBufferSource(); src.buffer = tuneBuf;
  const g = ctx.createGain(); g.gain.value = R.tuneGain * st.volume;
  src.connect(g).connect(ctx.destination); src.start();
}
