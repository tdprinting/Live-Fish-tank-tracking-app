"""Procedural sound effects for the Live Tank (no samples, no downloads; numpy + stdlib only).

Writes app/src/main/res/raw/tank_*.ogg (Vorbis, via ffmpeg's libvorbis) or, when no Vorbis encoder is available,
16-bit mono 22.05 kHz WAV. Every sound is synthesised from noise and sines with a fixed seed, so a re-run gives the
same files.

Ambience loops (played with loop = -1 in SoundPool):
  tank_flow_loop     powerhead wash: soft, dark low-passed noise, steady (only a slight swell)
  tank_hum_loop      pumps and powerheads on 60 Hz mains: 60/120/240 Hz lines over a low motor rumble
  tank_hob_loop      the HOB waterfall: a smooth, steady 0.5-2.5 kHz splash "shhh" with a rare soft drip
  tank_bubbles_loop  a rare quiet bubble pop

Matched to Tee's own tank audio (IMG_0106 night feeding clip and kenya_tree_tee.mov; analysis in
docs/research/video/tee_tank_night_feeding.md "Sound"): a steady bed (50 ms level p10-p90 within ~3 dB), most energy at
63-500 Hz, a smooth waterfall hump at 1-2.5 kHz (octave bands 2 kHz -20 dB, 4 kHz -27 dB under the total), hum lines at
60, 120 and 240 Hz, and only ~0.1 drips a second.
One-shots:
  tank_feed_plop, tank_pellet_drop, tank_shrimp_click, tank_ui_tick, tank_alert_chime, tank_glass_scrape,
  tank_reward_sparkle

Loops are rendered a crossfade-length longer than the loop and the tail is blended into the head with an
equal-power crossfade, so the loop point has no click. The script checks the loop-point jump afterwards.

  python3 tools/audio/generate_sounds.py            # writes the files
  python3 tools/audio/generate_sounds.py --wav      # force WAV output
  python3 tools/audio/generate_sounds.py --only tank_glass_scrape   # rewrite one file (all are still synthesised
                                                                    # in order, so the output matches a full run)
"""
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "raw")
SR_OGG = 32000
SR_WAV = 22050
SR = SR_OGG
XFADE_S = 0.5

rng = np.random.default_rng(20261012)


# ---------------------------------------------------------------- building blocks
def t_axis(n):
    return np.arange(n) / SR


def spectral_noise(n, lo, hi, tilt=-0.5, order=4):
    """Noise of length n with a band-pass (lo..hi Hz, Butterworth-like skirts) and a 1/f^tilt slope, via FFT."""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = 1e-3
    shape = f ** tilt
    if lo:
        shape /= np.sqrt(1 + (lo / f) ** (2 * order))
    if hi:
        shape /= np.sqrt(1 + (f / hi) ** (2 * order))
    out = np.fft.irfft(spec * shape, n)
    return out / (np.std(out) + 1e-12)


def smooth_random(n, rate_hz, depth):
    """Slowly varying random gain around 1 (low-passed noise), for surges and flutter."""
    x = spectral_noise(n, 0, rate_hz, tilt=0.0, order=2)
    return 1 + depth * np.tanh(x * 0.7)


def env_exp(n, tau_s, attack_s=0.002):
    t = t_axis(n)
    a = np.clip(t / max(attack_s, 1e-6), 0, 1)
    return a * np.exp(-t / tau_s)


def bubble(dur_s, f0, rise=0.35, tau=None, amp=1.0):
    """A single bubble: decaying sine whose pitch rises as it closes (Minnaert resonance)."""
    n = int(dur_s * SR)
    t = t_axis(n)
    tau = tau or dur_s / 4
    freq = f0 * (1 + rise * t / dur_s)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return amp * np.sin(phase) * env_exp(n, tau, 0.0015)


def add_at(buf, sig, start):
    """Mixes sig into buf at sample start (clipped to the buffer)."""
    if start >= len(buf):
        return
    end = min(len(buf), start + len(sig))
    buf[start:end] += sig[: end - start]


def make_loop(x, loop_n):
    """x has loop_n + crossfade samples; blend the tail into the head (equal power) so the loop is seamless."""
    f = len(x) - loop_n
    assert f > 0
    out = x[:loop_n].copy()
    k = np.linspace(0, np.pi / 2, f)
    fade_in, fade_out = np.sin(k), np.cos(k)
    out[:f] = x[:f] * fade_in + x[loop_n:loop_n + f] * fade_out
    return out


def fade_edges(x, fade_in_s=0.003, fade_out_s=0.02):
    """Gentle edges for one-shots so they start and end at zero."""
    x = x.copy()
    a = int(fade_in_s * SR)
    b = int(fade_out_s * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


def normalize(x, peak):
    return x * (peak / (np.max(np.abs(x)) + 1e-12))


# ---------------------------------------------------------------- loops
def loop_len(sec):
    return int(sec * SR), int(XFADE_S * SR)


def flow_loop():
    n, f = loop_len(8.0)
    m = n + f
    # soft, dark water movement heard through the glass: almost no energy above ~900 Hz (the old 1.5-5 kHz hiss
    # read as a scratchy, loud static on phone and laptop speakers)
    wash = spectral_noise(m, 60, 700, tilt=-1.0)
    surge = smooth_random(m, 0.25, 0.10)          # slight swells: Tee's tank sounds steady (no wavemaker)
    ripple = smooth_random(m, 2.0, 0.04)          # gentle turbulence
    x = wash * surge * ripple
    return normalize(make_loop(x, n), 0.5)


def hum_loop():
    n, f = loop_len(4.0)
    m = n + f
    t = t_axis(m)
    x = np.zeros(m)
    # pumps on US 60 Hz mains: Tee's clips show steady lines at 60, 120 and 240 Hz (+11-17 dB over the noise around
    # them), 120 Hz (the magnet's double-mains buzz) strongest, over a low impeller rumble. Each line is a whole number
    # of cycles in the 4 s loop, so the loop point stays clean
    for f0, a in ((60, 0.22), (120, 0.30), (180, 0.06), (240, 0.12), (300, 0.05)):
        x += a * np.sin(2 * np.pi * f0 * t + rng.uniform(0, 2 * np.pi))
    x += 0.12 * spectral_noise(m, 50, 400, tilt=-1.2)
    x *= 1 + 0.03 * np.sin(2 * np.pi * 0.5 * t)  # slow beat
    return normalize(make_loop(x, n), 0.45)


def hob_loop():
    n, f = loop_len(6.0)
    m = n + f
    # the HOB's waterfall as it sounds in Tee's room: a smooth, steady "shhh" peaking at 1-2.5 kHz with a rounded
    # low body. Smooth Gaussian noise with almost no flutter: the old flickering grains were what read as scratchy
    splash = spectral_noise(m, 600, 1900, tilt=-0.3, order=3)
    body = spectral_noise(m, 120, 900, tilt=-0.6)
    x = (0.7 * splash + body) * smooth_random(m, 0.6, 0.06)
    drops = np.zeros(m)
    for _ in range(1):  # ~0.1 drips/s in the clips: one soft drip per 6 s loop
        d = bubble(rng.uniform(0.04, 0.07), rng.uniform(700, 1200), rise=rng.uniform(0.3, 0.6), amp=0.5)
        add_at(drops, d, int(rng.uniform(0.5, 5.0) * SR))
    x = x + drops
    return normalize(make_loop(x, n), 0.45)


def bubbles_loop():
    n, f = loop_len(8.0)
    m = n + f
    x = np.zeros(m)
    # clusters of a few pops, well apart (the loop is mostly quiet)
    t = 0.3
    while t < 8.0 + XFADE_S - 0.3:
        for k in range(int(rng.integers(1, 3))):
            f0 = rng.uniform(450, 1400)
            b = bubble(rng.uniform(0.04, 0.09), f0, rise=rng.uniform(0.4, 1.2), amp=rng.uniform(0.5, 1.0))
            add_at(x, b, int((t + k * rng.uniform(0.05, 0.14)) * SR))
        t += rng.uniform(2.5, 5.0)  # rare: Tee's clips have ~0.1 pops a second
    x += 0.01 * spectral_noise(m, 300, 3000)  # keep the bed from being digital silence
    return normalize(make_loop(x, n), 0.6)


# ---------------------------------------------------------------- one-shots
def feed_plop():
    n = int(0.75 * SR)
    t = t_axis(n)
    x = np.zeros(n)
    # the plop: a pitch-dropping low thump
    k = int(0.18 * SR)
    freq = 220 * np.exp(-t[:k] / 0.06) + 85
    x[:k] += np.sin(2 * np.pi * np.cumsum(freq) / SR) * env_exp(k, 0.045, 0.003)
    # the splash: bright noise burst
    s = spectral_noise(int(0.4 * SR), 900, 6000, tilt=-0.3) * env_exp(int(0.4 * SR), 0.07, 0.004) * 0.45
    add_at(x, s, int(0.005 * SR))
    # a couple of droplets falling back
    for _ in range(4):
        d = bubble(rng.uniform(0.03, 0.06), rng.uniform(1000, 2200), rise=0.8, amp=rng.uniform(0.15, 0.3))
        add_at(x, d, int(rng.uniform(0.12, 0.5) * SR))
    return normalize(fade_edges(x), 0.85)


def pellet_drop():
    n = int(0.16 * SR)
    x = np.zeros(n)
    add_at(x, bubble(0.09, 1300, rise=0.9, tau=0.022, amp=1.0), int(0.004 * SR))
    click = spectral_noise(int(0.006 * SR), 2500, 9000, tilt=0) * env_exp(int(0.006 * SR), 0.0015, 0.0003) * 0.4
    add_at(x, click, 0)
    return normalize(fade_edges(x, 0.001, 0.02), 0.8)


def shrimp_click():
    n = int(0.12 * SR)
    x = np.zeros(n)
    for i, at in enumerate([0.002, 0.034]):
        m = int(0.012 * SR)
        c = spectral_noise(m, 2200, 7000, tilt=0) * env_exp(m, 0.0018, 0.0002)
        c += 0.5 * np.sin(2 * np.pi * 3100 * t_axis(m)) * env_exp(m, 0.0025, 0.0002)
        add_at(x, c * (1.0 if i == 0 else 0.6), int(at * SR))
    return normalize(fade_edges(x, 0.0005, 0.02), 0.7)


def ui_tick():
    n = int(0.06 * SR)
    t = t_axis(n)
    x = np.sin(2 * np.pi * 1750 * t) * env_exp(n, 0.009, 0.0015)
    x += 0.3 * np.sin(2 * np.pi * 3500 * t) * env_exp(n, 0.004, 0.001)
    return normalize(fade_edges(x, 0.001, 0.01), 0.6)


def alert_chime():
    """Two soft bell tones, descending (E5 then C#5): noticeable without being harsh."""
    n = int(1.9 * SR)
    x = np.zeros(n)

    def bell(f0, dur):
        m = int(dur * SR)
        t = t_axis(m)
        y = np.zeros(m)
        for ratio, amp, tau in [(1.0, 1.0, 0.7), (2.0, 0.35, 0.4), (2.76, 0.18, 0.25), (4.07, 0.07, 0.12)]:
            y += amp * np.sin(2 * np.pi * f0 * ratio * t) * env_exp(m, tau, 0.006)
        return y

    add_at(x, bell(659.25, 1.3), 0)
    add_at(x, bell(554.37, 1.5), int(0.32 * SR))
    return normalize(fade_edges(x, 0.002, 0.25), 0.8)


def reward_sparkle():
    """A reef moment or birthday: four soft glassy bells rising (C6 E6 G6 C7) with a shimmer, bright but gentle."""
    n = int(1.6 * SR)
    x = np.zeros(n)

    def bell(f0, dur, amp):
        m = int(dur * SR)
        t = t_axis(m)
        y = np.zeros(m)
        for ratio, a, tau in [(1.0, 1.0, 0.45), (2.0, 0.25, 0.25), (3.01, 0.12, 0.15), (5.4, 0.05, 0.06)]:
            y += a * np.sin(2 * np.pi * f0 * ratio * t) * env_exp(m, tau, 0.004)
        return amp * y

    for i, f in enumerate([1046.5, 1318.5, 1568.0, 2093.0]):
        add_at(x, bell(f, 1.0, 0.8 + 0.1 * i), int(i * 0.09 * SR))
    return normalize(fade_edges(x, 0.002, 0.3), 0.6)


def glass_scrape():
    """The Flipper's felt pad sliding over algae-filmed glass: a soft, slightly squeaky swish with stick-slip judder."""
    n = int(0.32 * SR)
    t = t_axis(n)
    swish = spectral_noise(n, 700, 4200, tilt=-0.3)
    judder = 1 + 0.45 * np.sin(2 * np.pi * 38 * t + 1.3 * np.sin(2 * np.pi * 5 * t))
    squeak = 0.12 * np.sin(2 * np.pi * (1450 + 120 * t / t[-1]) * t)
    env = np.clip(t / 0.035, 0, 1) * np.clip((t[-1] - t) / 0.12, 0, 1)
    return normalize(fade_edges((swish * judder + squeak) * env, 0.003, 0.03), 0.55)


SOUNDS = {
    "tank_flow_loop": (flow_loop, True),
    "tank_hum_loop": (hum_loop, True),
    "tank_hob_loop": (hob_loop, True),
    "tank_bubbles_loop": (bubbles_loop, True),
    "tank_feed_plop": (feed_plop, False),
    "tank_pellet_drop": (pellet_drop, False),
    "tank_shrimp_click": (shrimp_click, False),
    "tank_ui_tick": (ui_tick, False),
    "tank_alert_chime": (alert_chime, False),
    "tank_glass_scrape": (glass_scrape, False),
    "tank_reward_sparkle": (reward_sparkle, False),
}


# ---------------------------------------------------------------- output
def loop_seam_ratio(x):
    """Jump across the loop point relative to the 99th-percentile sample-to-sample step (≈1 means no click)."""
    d = np.abs(np.diff(x))
    return abs(x[0] - x[-1]) / (np.percentile(d, 99) + 1e-12)


def write_wav(path, x, sr):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def vorbis_encoder():
    ff = shutil.which("ffmpeg")
    if ff:
        enc = subprocess.run([ff, "-hide_banner", "-encoders"], capture_output=True, text=True).stdout
        if "libvorbis" in enc:
            return ("ffmpeg", ff)
    oe = shutil.which("oggenc")
    if oe:
        return ("oggenc", oe)
    return None


def main():
    global SR
    force_wav = "--wav" in sys.argv
    only = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else set(SOUNDS)
    enc = None if force_wav else vorbis_encoder()
    os.makedirs(OUT, exist_ok=True)
    for ext in (".ogg", ".wav"):
        for name in only:
            p = os.path.join(OUT, name + ext)
            if os.path.exists(p):
                os.remove(p)
    SR = SR_OGG if enc else SR_WAV
    total = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, (fn, is_loop) in SOUNDS.items():
            x = fn()
            if name not in only:
                total += sum(os.path.getsize(os.path.join(OUT, name + e)) for e in (".ogg", ".wav")
                             if os.path.exists(os.path.join(OUT, name + e)))
                continue
            if is_loop:
                r = loop_seam_ratio(x)
                assert r < 1.5, f"{name}: loop seam jump {r:.2f}x the typical step"
            if enc:
                wav = os.path.join(tmp, name + ".wav")
                write_wav(wav, x, SR)
                out = os.path.join(OUT, name + ".ogg")
                if enc[0] == "ffmpeg":
                    cmd = [enc[1], "-hide_banner", "-loglevel", "error", "-y", "-i", wav, "-map_metadata", "-1",
                           "-fflags", "+bitexact", "-c:a", "libvorbis", "-q:a", "3", out]
                else:
                    cmd = [enc[1], "-Q", "-q", "3", "-o", out, wav]
                subprocess.run(cmd, check=True)
            else:
                out = os.path.join(OUT, name + ".wav")
                write_wav(out, x, SR)
            size = os.path.getsize(out)
            total += size
            seam = f" seam {loop_seam_ratio(x):.2f}" if is_loop else ""
            print(f"{os.path.basename(out):28s} {len(x) / SR:5.2f} s {size / 1024:7.1f} KiB{seam}")
    print(f"total {total / 1024:.1f} KiB ({'Vorbis' if enc else 'WAV'}, {SR} Hz mono)")
    assert total < 1.5 * 1024 * 1024, "audio assets over budget"


if __name__ == "__main__":
    main()
