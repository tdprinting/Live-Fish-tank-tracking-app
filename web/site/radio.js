// The little radio on the table beside the tank (Android TankRadio): quiet background songs from RadioStation (shared
// Kotlin), one after another. Songs stream through an <audio> element routed into the page's AudioContext, so the
// level works on iPhone too (iOS ignores audio.volume) and nothing big is decoded into memory.
let R = null;          // { songs, gain, tuneGain, tune }
let opts = null;       // { SB, store, ctx(), volume(), say }
let el = null, gain = null, tuneBuf = null, playing = -1;
const st = { on: true, track: 0 };

export function initRadio(o) {
  opts = o;
  R = JSON.parse(o.SB.radio());
  st.on = o.store.get('reeflive.radio', true);
  st.track = wrap(o.store.get('reeflive.radioTrack', 0));
  document.addEventListener('visibilitychange', () => { if (document.hidden) el?.pause(); else sync(); });
}

export const radioOn = () => st.on;
export const radioSong = () => R.songs[st.track];

/** Call from a tap: iOS only lets sound start inside one. */
export function radioKick() { if (R && st.on && (!el || el.paused)) sync(); }

export function radioToggle() {
  st.on = !st.on; opts.store.set('reeflive.radio', st.on);
  const s = radioSong();
  opts.say(st.on ? `Radio on: ${s.title}. ${s.blurb}, playing softly.` : 'Radio off. Just the tank sounds now.');
  sync();
}

export function radioStep(d) {
  setTrack(st.track + d);
  const s = radioSong();
  opts.say(`Now playing: ${s.title}. ${s.blurb}.`);
  tuneStatic();
}

/** The shared volume slider moved. */
export function radioVolume() { if (R && el) sync(); }

// ---------------------------------------------------------------- internals
const wrap = (i) => ((i % R.songs.length) + R.songs.length) % R.songs.length;
const level = () => R.gain * opts.volume();
// Safari can't play every Ogg: the build also ships .m4a (web/build.gradle.kts webSounds)
const ext = () => (el.canPlayType('audio/mp4; codecs="mp4a.40.2"') ? 'm4a' : 'ogg');

function setTrack(i) { st.track = wrap(i); opts.store.set('reeflive.radioTrack', st.track); sync(); }

function sync() {
  if (!st.on || document.hidden || opts.volume() <= 0) { el?.pause(); return; }
  const ctx = opts.ctx();
  if (!el) {
    el = new Audio(); el.preload = 'auto';
    el.addEventListener('ended', () => setTrack(st.track + 1));
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
  const g = ctx.createGain(); g.gain.value = R.tuneGain * opts.volume();
  src.connect(g).connect(ctx.destination); src.start();
}
