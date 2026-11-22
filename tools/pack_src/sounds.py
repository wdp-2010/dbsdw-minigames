"""Synthesised sound effects and sounds.json.

Run: python3 tools/pack_src/sounds.py <resourcepack dir>
Pure Python synthesis (fixed seeds) to 44.1 kHz mono WAV, then ffmpeg to Ogg Vorbis. Mono, so the
client places the sounds in the world. Every sound is normalised to a peak of -1 dBFS.
"""

from __future__ import annotations

import json
import math
import random
import struct
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

RATE = 44100
PEAK = 10 ** (-1 / 20)
Signal = list[float]


# ---------------------------------------------------------------- building blocks

def silence(seconds: float) -> Signal:
    return [0.0] * int(seconds * RATE)


def mix(*parts: tuple[float, Signal]) -> Signal:
    """Sum signals; each part is (start time in seconds, samples)."""
    end = max(int(t * RATE) + len(s) for t, s in parts)
    out = [0.0] * end
    for t, s in parts:
        o = int(t * RATE)
        for i, v in enumerate(s):
            out[o + i] += v
    return out


def gain(s: Signal, g: float) -> Signal:
    return [v * g for v in s]


def env(n: int, attack: float, decay: float, curve: float = 4.0) -> Signal:
    """Attack (seconds) linear, then exponential-ish decay over `decay` seconds."""
    a = max(1, int(attack * RATE))
    out = []
    for i in range(n):
        if i < a:
            out.append(i / a)
        else:
            t = (i - a) / max(1, decay * RATE)
            out.append(math.exp(-curve * t))
    return out


def tone(freq: float | tuple[float, float], seconds: float, attack: float = 0.004, decay: float | None = None,
         harmonics: tuple[float, ...] = (1.0,), curve: float = 4.0, vibrato: float = 0.0) -> Signal:
    """A note with a few harmonics; freq may be (start, end) for an exponential sweep."""
    n = int(seconds * RATE)
    f0, f1 = (freq, freq) if isinstance(freq, (int, float)) else freq
    e = env(n, attack, decay if decay is not None else seconds, curve)
    phase = 0.0
    out = []
    for i in range(n):
        f = f0 * (f1 / f0) ** (i / n)
        if vibrato:
            f *= 1 + vibrato * math.sin(2 * math.pi * 6 * i / RATE)
        phase += 2 * math.pi * f / RATE
        v = sum(h_amp * math.sin(phase * (k + 1)) for k, h_amp in enumerate(harmonics))
        out.append(v * e[i])
    return out


def noise(rng: random.Random, seconds: float, lowpass: float = 1.0, highpass: float = 0.0,
          attack: float = 0.001, decay: float | None = None, curve: float = 5.0) -> Signal:
    """White noise through one-pole filters (coefficients 0..1), with an envelope."""
    n = int(seconds * RATE)
    e = env(n, attack, decay if decay is not None else seconds, curve)
    lp = hp_prev_in = hp = 0.0
    out = []
    for i in range(n):
        x = rng.uniform(-1, 1)
        lp += lowpass * (x - lp)
        y = lp
        if highpass:
            hp = (1 - highpass) * (hp + y - hp_prev_in)
            hp_prev_in = y
            y = hp
        out.append(y * e[i])
    return out


def note(name: str) -> float:
    names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
    semis = names[name[0]] + (1 if "#" in name else -1 if "b" in name[1:] else 0)
    octave = int(name[-1])
    return 440.0 * 2 ** ((semis + (octave - 4) * 12) / 12)


BELL = (1.0, 0.0, 0.35, 0.0, 0.12)
SOFT_SQUARE = (1.0, 0.0, 0.3, 0.0, 0.15, 0.0, 0.08)


# ---------------------------------------------------------------- the sounds

def shot(seed: int) -> Signal:
    rng = random.Random(seed)
    pitch = 1 + (seed % 3) * 0.06
    pop = noise(rng, 0.05, lowpass=0.55, highpass=0.08, decay=0.03, curve=6)
    thump = tone((190 * pitch, 55), 0.09, attack=0.002, decay=0.07, harmonics=(1.0, 0.3))
    hiss = noise(rng, 0.16, lowpass=0.35, highpass=0.2, attack=0.01, decay=0.12, curve=4)
    click = tone(2600 * pitch, 0.012, attack=0.0005, decay=0.01)
    # a low sub-thump under the pop gives the marker some punch
    sub = tone((92 * pitch, 36), 0.12, attack=0.001, decay=0.1, harmonics=(1.0, 0.2))
    return mix((0, gain(pop, 1.0)), (0, gain(thump, 0.95)), (0, gain(sub, 0.7)), (0.004, gain(hiss, 0.22)),
               (0, gain(click, 0.35)))


def dry_fire() -> Signal:
    """Trigger pulled while out: two dry metal clicks, no air."""
    c = lambda f: tone(f, 0.018, attack=0.0005, decay=0.014, harmonics=(1.0, 0.0, 0.35))
    return mix((0, gain(c(1900), 0.5)), (0.045, gain(c(1500), 0.4)))


def splat(seed: int, size: float = 1.0) -> Signal:
    rng = random.Random(seed)
    body = noise(rng, 0.12 * size, lowpass=0.18, attack=0.002, decay=0.07 * size, curve=5)
    squelch = tone((420, 140), 0.09 * size, attack=0.003, decay=0.07, harmonics=(1.0, 0.4, 0.2))
    drops = [(rng.uniform(0.03, 0.14) * size, gain(noise(rng, 0.025, lowpass=0.3, decay=0.015), rng.uniform(0.2, 0.4)))
             for _ in range(4)]
    return mix((0, body), (0, gain(squelch, 0.5)), *drops)


def hit(seed: int) -> Signal:
    rng = random.Random(seed)
    thud = tone((140, 60), 0.12, attack=0.002, decay=0.1, harmonics=(1.0, 0.5))
    ow = tone((520, 300), 0.16, attack=0.01, decay=0.14, harmonics=SOFT_SQUARE)
    return mix((0, splat(seed + 50, 1.3)), (0, gain(thud, 0.8)), (0.02, gain(ow, 0.25)), (0, gain(noise(rng, 0.01), 0.2)))


def hitmarker() -> Signal:
    return mix((0, tone(2400, 0.05, attack=0.0005, decay=0.03, harmonics=(1.0, 0.0, 0.25))),
               (0, gain(tone(3600, 0.035, attack=0.0005, decay=0.02), 0.5)))


def eliminate() -> Signal:
    return mix((0, tone(note("C6"), 0.14, decay=0.12, harmonics=BELL)),
               (0.09, tone(note("G6"), 0.28, decay=0.25, harmonics=BELL)),
               (0.09, gain(tone(note("C7"), 0.2, decay=0.18, harmonics=BELL), 0.3)))


def out_sound() -> Signal:
    wah = tone((620, 150), 0.65, attack=0.01, decay=0.6, harmonics=SOFT_SQUARE, curve=2.5, vibrato=0.03)
    return mix((0, gain(splat(901, 1.8), 0.9)), (0.05, gain(wah, 0.5)))


def arpeggio(names: list[str], step: float, length: float, harmonics=BELL, last: float = 0.4) -> Signal:
    parts = []
    for i, n in enumerate(names):
        dur = last if i == len(names) - 1 else length
        parts.append((i * step, tone(note(n), dur, decay=dur, harmonics=harmonics)))
    return mix(*parts)


def respawn() -> Signal:
    return arpeggio(["C5", "E5", "G5", "C6"], 0.06, 0.12, last=0.3)


def shield_block() -> Signal:
    rng = random.Random(31)
    clang = mix(*[(0, gain(tone(f, 0.4, attack=0.001, decay=0.35, curve=5), a))
                  for f, a in ((820, 1.0), (1290, 0.6), (1960, 0.4), (2730, 0.25))])
    return mix((0, clang), (0, gain(noise(rng, 0.03, lowpass=0.6, decay=0.02), 0.5)))


def shield_break() -> Signal:
    rng = random.Random(32)
    crunch = noise(rng, 0.35, lowpass=0.5, highpass=0.05, decay=0.3, curve=3)
    fall = tone((900, 200), 0.4, decay=0.35, harmonics=(1.0, 0.0, 0.5))
    return mix((0, gain(shield_block(), 0.8)), (0.02, gain(crunch, 0.6)), (0.05, gain(fall, 0.4)))


def flag_taken() -> Signal:
    return arpeggio(["E6", "A6"], 0.09, 0.15, last=0.35)


def flag_dropped() -> Signal:
    return arpeggio(["A5", "E5"], 0.12, 0.18, harmonics=SOFT_SQUARE, last=0.35)


def flag_returned() -> Signal:
    return arpeggio(["G5", "B5", "D6"], 0.07, 0.12, last=0.3)


def flag_captured() -> Signal:
    run = arpeggio(["C5", "E5", "G5"], 0.08, 0.14, harmonics=SOFT_SQUARE, last=0.14)
    chord = mix(*[(0, gain(tone(note(n), 0.8, attack=0.01, decay=0.75, harmonics=SOFT_SQUARE, curve=2.5), 0.45))
                  for n in ("C5", "E5", "G5", "C6")])
    return mix((0, run), (0.24, chord), (0.24, gain(tone(note("C7"), 0.5, decay=0.45, harmonics=BELL), 0.25)))


def powerup() -> Signal:
    sweep = tone((400, 1600), 0.22, attack=0.005, decay=0.22, harmonics=(1.0, 0.3), curve=1.5)
    sparkle = arpeggio(["E6", "B6", "E7"], 0.04, 0.08, last=0.2)
    return mix((0, gain(sweep, 0.6)), (0.15, gain(sparkle, 0.5)))


def countdown() -> Signal:
    rng = random.Random(41)
    return mix((0, tone(1050, 0.07, attack=0.001, decay=0.05, harmonics=(1.0, 0.0, 0.3))),
               (0, gain(noise(rng, 0.02, lowpass=0.7, highpass=0.3, decay=0.015), 0.4)))


def match_start() -> Signal:
    return mix(*[(0, gain(tone(note(n), 0.55, attack=0.02, decay=0.5, harmonics=SOFT_SQUARE, curve=2), 0.5))
                 for n in ("C5", "G5", "C6")])


def match_win() -> Signal:
    run = arpeggio(["C5", "E5", "G5", "C6"], 0.1, 0.16, harmonics=SOFT_SQUARE, last=0.16)
    chord = mix(*[(0, gain(tone(note(n), 1.1, attack=0.01, decay=1.0, harmonics=SOFT_SQUARE, curve=2.2), 0.4))
                  for n in ("C5", "E5", "G5", "C6", "E6")])
    return mix((0, run), (0.4, chord))


def match_lose() -> Signal:
    return mix(*[(i * 0.22, tone(note(n), 0.5 if i < 2 else 0.8, attack=0.01, decay=0.5 if i < 2 else 0.75,
                                  harmonics=SOFT_SQUARE, curve=2.5, vibrato=0.006))
                 for i, n in enumerate(["G4", "Eb4", "C4"])])


def match_draw() -> Signal:
    return arpeggio(["G4", "G4"], 0.25, 0.22, harmonics=SOFT_SQUARE, last=0.4)


# event -> list of (file name, generator)
EVENTS: dict[str, list[tuple[str, callable]]] = {
    "paintball.shot": [("paintball/shot1", lambda: shot(1)), ("paintball/shot2", lambda: shot(2))],
    "paintball.splat": [("paintball/splat1", lambda: splat(11)), ("paintball/splat2", lambda: splat(12))],
    "paintball.hit": [("paintball/hit1", lambda: hit(21)), ("paintball/hit2", lambda: hit(22))],
    "paintball.hitmarker": [("paintball/hitmarker", hitmarker)],
    "paintball.dry": [("paintball/dry", dry_fire)],
    "paintball.eliminate": [("paintball/eliminate", eliminate)],
    "paintball.out": [("paintball/out", out_sound)],
    "paintball.respawn": [("paintball/respawn", respawn)],
    "paintball.shield_block": [("paintball/shield_block", shield_block)],
    "paintball.shield_break": [("paintball/shield_break", shield_break)],
    "paintball.flag.taken": [("paintball/flag_taken", flag_taken)],
    "paintball.flag.dropped": [("paintball/flag_dropped", flag_dropped)],
    "paintball.flag.returned": [("paintball/flag_returned", flag_returned)],
    "paintball.flag.captured": [("paintball/flag_captured", flag_captured)],
    "paintball.powerup": [("paintball/powerup", powerup)],
    "match.countdown": [("match/countdown", countdown)],
    "match.start": [("match/start", match_start)],
    "match.win": [("match/win", match_win)],
    "match.lose": [("match/lose", match_lose)],
    "match.draw": [("match/draw", match_draw)],
}


def write_wav(path: Path, s: Signal) -> None:
    peak = max(1e-9, max(abs(v) for v in s))
    fade = int(0.004 * RATE)  # tiny fade-out so no sound ends in a click
    data = bytearray()
    for i, v in enumerate(s):
        if i >= len(s) - fade:
            v *= (len(s) - i) / fade
        data += struct.pack("<h", int(max(-1.0, min(1.0, v / peak * PEAK)) * 32767))
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(bytes(data))


def main(out: Path) -> None:
    base = out / "assets" / "dbsdw"
    sounds_json = {}
    with tempfile.TemporaryDirectory() as tmp:
        for event, files in EVENTS.items():
            for name, gen in files:
                wav = Path(tmp) / (name.replace("/", "_") + ".wav")
                write_wav(wav, gen())
                ogg = base / "sounds" / f"{name}.ogg"
                ogg.parent.mkdir(parents=True, exist_ok=True)
                subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4",
                                "-ac", "1", "-fflags", "+bitexact", "-flags:a", "+bitexact", "-map_metadata", "-1",
                                str(ogg)], check=True)
            sounds_json[event] = {"sounds": [f"dbsdw:{name}" for name, _ in files]}
    (base / "sounds.json").write_text(json.dumps(sounds_json, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
