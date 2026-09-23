"""Digifarm textures: jade habitat with exposed violet digital sections.

Outputs (POT sizes for mipmaps):
- island_top.png 512x512: jade tiles with broken black/violet wire grid
  sections near the perimeter, leaving the resident area legible.
- island_side.png 256x256: muted mauve rim at image top (= cliff top, v=0),
  strata fading to fully transparent before the lower edge (= cliff foot).
- voxel_block_dark/lit.png 128x128: two dark violet grid panels for the
  detached cubes around the island.
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
BLOCK_SIZE = 128


def wireframe_tiles() -> set[tuple[int, int]]:
    """Irregular exposed cells on the top surface only."""
    columns = (TOP_SIZE + TOP_PITCH - 1) // TOP_PITCH
    patches = (
        (.08, .17, .22, .19), (.88, .10, .15, .13),
        (.03, .52, .11, .16), (.96, .48, .08, .10),
        (.18, .86, .14, .13), (.88, .82, .22, .19),
        (.70, .53, .07, .07),
    )
    exposed = set()
    fracture_rng = random.Random(SEED + 41)
    for ty in range(columns):
        for tx in range(columns):
            u, v = (tx + .5) / columns, (ty + .5) / columns
            strength = max(
                1 - ((u - cx) / rx) ** 2 - ((v - cy) / ry) ** 2
                for cx, cy, rx, ry in patches
            )
            if strength + fracture_rng.uniform(-.25, .25) > .12:
                exposed.add((tx, ty))
    return exposed


def island_top() -> Image.Image:
    rng = random.Random(SEED)
    img = Image.new("RGB", (TOP_SIZE, TOP_SIZE), (112, 169, 137))
    px = img.load()
    # Per-tile brightness jitter (reference has lively but uniform tiles).
    for ty in range(0, TOP_SIZE, TOP_PITCH):
        for tx in range(0, TOP_SIZE, TOP_PITCH):
            j = rng.randint(-6, 6)
            for y in range(ty, min(ty + TOP_PITCH, TOP_SIZE)):
                for x in range(tx, min(tx + TOP_PITCH, TOP_SIZE)):
                    r, g, b = px[x, y]
                    px[x, y] = (r + j, g + j, b + j)
    d = ImageDraw.Draw(img, "RGBA")
    # Quiet tonal variation keeps the turf alive without competing with sprites.
    for _ in range(12):
        cx, cy = rng.randint(0, TOP_SIZE), rng.randint(0, TOP_SIZE)
        rx, ry = rng.randint(40, 110), rng.randint(30, 80)
        d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=(81, 145, 116, 100))
    # Sparse cool stone accents echo the app's violet surfaces.
    for _ in range(46):
        tx = rng.randrange(0, TOP_SIZE // TOP_PITCH) * TOP_PITCH
        ty = rng.randrange(0, TOP_SIZE // TOP_PITCH) * TOP_PITCH
        d.rectangle([tx, ty, tx + TOP_PITCH - 1, ty + TOP_PITCH - 1], fill=(178, 178, 197))
    # Crisp grid lines.
    for i in range(0, TOP_SIZE + 1, TOP_PITCH):
        d.line([(i, 0), (i, TOP_SIZE)], fill=(79, 128, 105), width=1)
        d.line([(0, i), (TOP_SIZE, i)], fill=(79, 128, 105), width=1)
    # The reference has missing, gridded terrain at its edges. Keep the
    # Digimon's central stage green and expose only irregular outer patches.
    exposed = wireframe_tiles()
    for tx, ty in exposed:
        x, y = tx * TOP_PITCH, ty * TOP_PITCH
        tone = ((tx * 11 + ty * 7) % 4) * 2
        d.rectangle((x, y, x + TOP_PITCH - 1, y + TOP_PITCH - 1),
                    fill=(13 + tone, 9 + tone, 25 + tone))
        grid = (105, 72, 170) if (tx + 2 * ty) % 7 else (154, 119, 230)
        d.line((x, y, x + TOP_PITCH - 1, y), fill=grid, width=2)
        d.line((x, y, x, y + TOP_PITCH - 1), fill=grid, width=2)
    # A brighter broken seam marks where digital substrate meets living turf.
    for tx, ty in exposed:
        x, y = tx * TOP_PITCH, ty * TOP_PITCH
        if (tx - 1, ty) not in exposed:
            d.line((x, y, x, y + TOP_PITCH - 1), fill=(139, 92, 246), width=2)
        if (tx + 1, ty) not in exposed:
            d.line((x + TOP_PITCH - 1, y, x + TOP_PITCH - 1, y + TOP_PITCH - 1), fill=(139, 92, 246), width=2)
        if (tx, ty - 1) not in exposed:
            d.line((x, y, x + TOP_PITCH - 1, y), fill=(139, 92, 246), width=2)
        if (tx, ty + 1) not in exposed:
            d.line((x, y + TOP_PITCH - 1, x + TOP_PITCH - 1, y + TOP_PITCH - 1), fill=(139, 92, 246), width=2)
    return img


def voxel_block(lit: bool) -> Image.Image:
    base = (35, 23, 59) if lit else (14, 10, 26)
    grid = (111, 78, 177) if lit else (69, 47, 113)
    edge = (167, 139, 250) if lit else (139, 92, 246)
    img = Image.new('RGB', (BLOCK_SIZE, BLOCK_SIZE), base)
    d = ImageDraw.Draw(img)
    for p in (32, 64, 96):
        d.line((p, 0, p, BLOCK_SIZE), fill=grid, width=2)
        d.line((0, p, BLOCK_SIZE, p), fill=grid, width=2)
    d.rectangle((1, 1, BLOCK_SIZE - 2, BLOCK_SIZE - 2), outline=edge, width=9)
    d.rectangle((13, 13, BLOCK_SIZE - 14, BLOCK_SIZE - 14), outline=grid, width=2)
    return img


def island_side() -> Image.Image:
    # The geometry ends on a fully transparent band, including texture-filter
    # samples along its last row. This prevents a one-pixel foot outline.
    img = Image.new("RGBA", (SIDE, SIDE))
    px = img.load()
    stops = [
        (0.00, (187, 170, 194)),
        (0.10, (157, 139, 174)),
        (0.30, (109, 90, 128)),
        (0.55, (56, 46, 77)),
        (0.75, (27, 22, 43)),
        (1.00, (23, 19, 39)),
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
        fade_t = max(0.0, min(1.0, (t - 0.48) / 0.38))
        smooth_fade = fade_t * fade_t * (3.0 - 2.0 * fade_t)
        alpha = int(round(255 * (1.0 - smooth_fade)))
        for x in range(SIDE):
            px[x, y] = (*col, alpha)
    d = ImageDraw.Draw(img, "RGBA")
    # Strata live in the cliff band only.
    for frac in (0.15, 0.25, 0.35):
        y = int(frac * SIDE)
        d.line([(0, y), (SIDE, y)], fill=(91, 72, 110, 150), width=2)
        d.line([(0, y + 2), (SIDE, y + 2)], fill=(170, 150, 189, 110), width=1)
    # Speckle above the fade zone.
    rng = random.Random(SEED)
    for _ in range(900):
        x, y = rng.randrange(SIDE), rng.randrange(10, int(SIDE * 0.6))
        r, g, b, a = px[x, y]
        j = rng.randint(-8, 8)
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
    voxel_block(False).save(dst / "voxel_block_dark.png")
    voxel_block(True).save(dst / "voxel_block_lit.png")
    print("wrote jade island, mauve cliff, and violet block textures")


if __name__ == "__main__":
    main()
