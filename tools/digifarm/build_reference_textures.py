"""Reference-style island textures (match image 3: bright green tiled top, tan cliffs).

Outputs (POT sizes for mipmaps):
- island_top.png  512x512: light-green tiles, subtle darker grid, soft tonal
  patches, sparse sand accents.
- island_side.png 256x256: pale rim at image top (= cliff top, v=0), tan
  gradient with strata lines down to image bottom (= cliff foot, v=1).
- island_keel.png 64x256: plain vertical rock fade for the keel spike
  (image top = cliff-foot tone, image bottom = near-black melt).
Usage: py tools/digifarm/build_reference_textures.py --dst DIR
"""
import argparse
import random
from pathlib import Path
from PIL import Image, ImageDraw

SEED = 7
TOP_SIZE = 512
TOP_PITCH = 12
SIDE = 256
KEEL_W = 64
KEEL_H = 256


def island_top() -> Image.Image:
    rng = random.Random(SEED)
    img = Image.new("RGB", (TOP_SIZE, TOP_SIZE), (122, 214, 102))
    px = img.load()
    # Per-tile brightness jitter (reference has lively but uniform tiles).
    for ty in range(0, TOP_SIZE, TOP_PITCH):
        for tx in range(0, TOP_SIZE, TOP_PITCH):
            j = rng.randint(-7, 7)
            for y in range(ty, min(ty + TOP_PITCH, TOP_SIZE)):
                for x in range(tx, min(tx + TOP_PITCH, TOP_SIZE)):
                    r, g, b = px[x, y]
                    px[x, y] = (r + j, g + j, b + j)
    d = ImageDraw.Draw(img, "RGBA")
    # Soft tonal patches (mid greens like the reference middle).
    for _ in range(12):
        cx, cy = rng.randint(0, TOP_SIZE), rng.randint(0, TOP_SIZE)
        rx, ry = rng.randint(40, 110), rng.randint(30, 80)
        d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=(105, 196, 88, 110))
    # Sparse sand accent tiles.
    for _ in range(46):
        tx = rng.randrange(0, TOP_SIZE // TOP_PITCH) * TOP_PITCH
        ty = rng.randrange(0, TOP_SIZE // TOP_PITCH) * TOP_PITCH
        d.rectangle([tx, ty, tx + TOP_PITCH - 1, ty + TOP_PITCH - 1], fill=(233, 221, 181))
    # Crisp grid lines.
    for i in range(0, TOP_SIZE + 1, TOP_PITCH):
        d.line([(i, 0), (i, TOP_SIZE)], fill=(88, 172, 80), width=1)
        d.line([(0, i), (TOP_SIZE, i)], fill=(88, 172, 80), width=1)
    return img


def island_side() -> Image.Image:
    # Full-height fade for original-height cliffs: pale rim -> tan cliff ->
    # dark rock -> near-black melt at the foot (v=1). No extension below.
    # Alpha is the fade. Baking black into RGB creates a visible dark slab in
    # the app background instead of letting the model disappear naturally.
    img = Image.new("RGBA", (SIDE, SIDE))
    px = img.load()
    stops = [
        (0.00, (236, 226, 196)),
        (0.10, (206, 172, 122)),
        (0.30, (150, 116, 80)),
        (0.55, (60, 48, 36)),
        (0.75, (22, 17, 26)),
        (1.00, (6, 4, 12)),
    ]
    for y in range(SIDE):
        t = y / (SIDE - 1)
        for i in range(len(stops) - 1):
            t0, c0 = stops[i]
            t1, c1 = stops[i + 1]
            if t0 <= t <= t1:
                f = (t - t0) / max(t1 - t0, 1e-9)
                col = tuple(int(c0[k] + (c1[k] - c0[k]) * f) for k in range(3))
                break
        for x in range(SIDE):
            px[x, y] = (*col, int(round(255 * (1.0 - t) ** 1.35)))
    d = ImageDraw.Draw(img, "RGBA")
    # Strata live in the cliff band only.
    for frac in (0.15, 0.25, 0.35):
        y = int(frac * SIDE)
        d.line([(0, y), (SIDE, y)], fill=(120, 92, 62, 160), width=2)
        d.line([(0, y + 2), (SIDE, y + 2)], fill=(226, 200, 150, 120), width=1)
    # Speckle above the fade zone.
    rng = random.Random(SEED)
    for _ in range(900):
        x, y = rng.randrange(SIDE), rng.randrange(10, int(SIDE * 0.6))
        r, g, b, a = px[x, y]
        j = rng.randint(-12, 12)
        px[x, y] = (max(0, min(255, r + j)), max(0, min(255, g + j)), max(0, min(255, b + j)), a)
    return img


def island_keel() -> Image.Image:
    img = Image.new("RGBA", (KEEL_W, KEEL_H))
    px = img.load()
    stops = [
        (0.00, (170, 135, 96)),
        (0.35, (80, 62, 44)),
        (0.70, (24, 18, 26)),
        (1.00, (6, 4, 12)),
    ]
    for y in range(KEEL_H):
        t = y / (KEEL_H - 1)
        for i in range(len(stops) - 1):
            t0, c0 = stops[i]
            t1, c1 = stops[i + 1]
            if t0 <= t <= t1:
                f = (t - t0) / max(t1 - t0, 1e-9)
                col = tuple(int(c0[k] + (c1[k] - c0[k]) * f) for k in range(3))
                break
        for x in range(KEEL_W):
            px[x, y] = (*col, int(round(255 * (1.0 - t) ** 1.35)))
    rng = random.Random(SEED + 1)
    for _ in range(260):
        x, y = rng.randrange(KEEL_W), rng.randrange(int(KEEL_H * 0.5))
        r, g, b, a = px[x, y]
        j = rng.randint(-10, 10)
        px[x, y] = (max(0, min(255, r + j)), max(0, min(255, g + j)), max(0, min(255, b + j)), a)
    return img


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--dst", required=True)
    a = p.parse_args()
    dst = Path(a.dst)
    dst.mkdir(parents=True, exist_ok=True)
    island_top().save(dst / "island_top.png")
    island_side().save(dst / "island_side.png")
    island_keel().save(dst / "island_keel.png")
    print("wrote island_top.png island_side.png island_keel.png")


if __name__ == "__main__":
    main()
