"""Digifarm textures: original atlas detail, jade grass and violet digital sections.

Outputs (POT sizes for mipmaps):
- island_top.png 512x512: tinted original grass tiles under the existing
  broken black/violet wire grid sections.
- island_side.png 256x256: original side-atlas detail tinted by the existing
  mauve gradient, strata and transparent cliff-foot fade.
- voxel_block_dark/lit.png 128x128: two dark violet grid panels for the
  detached cubes around the island.
Usage: py tools/digifarm/build_reference_textures.py --dst DIR
"""
import argparse
import colorsys
from io import BytesIO
import random
import statistics
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

SEED = 7
TOP_SIZE = 512
TOP_PITCH = 12
SIDE = 256
BLOCK_SIZE = 128
PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE_ARCHIVE = (
    PROJECT_ROOT / "DigifarmAssets" / "Mobile - Digimon Links - Maps - Digi-Farm.zip"
)
SOURCE_ATLAS_ENTRY = "Digi-Farm/farm_base_tex01.png"
# These original atlas areas are the green surface and side-material UV regions
# used by the supplied Farm FBX. Only the source detail is sampled; the runtime
# wireframe patches and mauve side treatment remain authored below.
GRASS_ATLAS_REGION = (0, 0, 96, 96)
# A continuous earth band from the original island edge. The broader UV area
# also contains grass, sand and water blocks; stretching that whole area over
# the cliff creates hard rectangular seams around the island.
SIDE_ATLAS_REGION = (384, 400, 512, 448)
CURRENT_GRASS = (112, 169, 137)


def load_original_atlas(source_archive: Path) -> Image.Image:
    """Load the supplied Farm atlas without extracting or modifying the ZIP."""
    with zipfile.ZipFile(source_archive) as archive:
        with Image.open(BytesIO(archive.read(SOURCE_ATLAS_ENTRY))) as image:
            return image.convert("RGB")


def colorize_grass(source: Image.Image) -> Image.Image:
    """Retain original tile shading while matching the current jade tone."""
    target_h, target_l, target_s = colorsys.rgb_to_hls(
        *(channel / 255 for channel in CURRENT_GRASS)
    )
    source_lightness = [
        colorsys.rgb_to_hls(*(channel / 255 for channel in pixel))[1]
        for pixel in source.getdata()
    ]
    median_lightness = statistics.median(source_lightness)
    tinted = []
    for pixel in source.getdata():
        lightness = colorsys.rgb_to_hls(*(channel / 255 for channel in pixel))[1]
        lightness = max(.25, min(.78, target_l + (lightness - median_lightness) * .72))
        tinted.append(tuple(round(channel * 255) for channel in colorsys.hls_to_rgb(
            target_h, lightness, target_s
        )))
    output = Image.new("RGB", source.size)
    output.putdata(tinted)
    return output


def tile_mirrored(source: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Repeat a source tile without a hard seam at each repeated edge."""
    output = Image.new("RGB", size)
    for row, y in enumerate(range(0, size[1], source.height)):
        for column, x in enumerate(range(0, size[0], source.width)):
            tile = source
            if column % 2:
                tile = ImageOps.mirror(tile)
            if row % 2:
                tile = ImageOps.flip(tile)
            output.paste(tile, (x, y))
    return output.crop((0, 0, *size))


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


def island_top(atlas: Image.Image) -> Image.Image:
    rng = random.Random(SEED)
    grass = colorize_grass(atlas.crop(GRASS_ATLAS_REGION))
    img = tile_mirrored(grass, (TOP_SIZE, TOP_SIZE))
    px = img.load()
    # Retain the existing per-tile tone variation over the original texture.
    for ty in range(0, TOP_SIZE, TOP_PITCH):
        for tx in range(0, TOP_SIZE, TOP_PITCH):
            j = rng.randint(-6, 6)
            for y in range(ty, min(ty + TOP_PITCH, TOP_SIZE)):
                for x in range(tx, min(tx + TOP_PITCH, TOP_SIZE)):
                    r, g, b = px[x, y]
                    px[x, y] = tuple(max(0, min(255, c + j)) for c in (r, g, b))
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


def island_side(atlas: Image.Image) -> Image.Image:
    # The geometry ends on a fully transparent band, including texture-filter
    # samples along its last row. This prevents a one-pixel foot outline.
    img = Image.new("RGBA", (SIDE, SIDE))
    px = img.load()
    side_detail = tile_mirrored(atlas.crop(SIDE_ATLAS_REGION), (SIDE, SIDE))
    side_pixels = list(side_detail.getdata())
    side_luma = [.2126 * r + .7152 * g + .0722 * b for r, g, b in side_pixels]
    side_mean = statistics.mean(side_luma)
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
            index = y * SIDE + x
            # The atlas contributes original stone detail while the existing
            # mauve palette and vertical fade remain the dominant colors.
            shade = max(.78, min(1.22, 1.0 + (side_luma[index] - side_mean) * .75 / 255))
            textured = tuple(max(0, min(255, round(channel * shade))) for channel in col)
            px[x, y] = (*textured, alpha)
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
    p.add_argument("--source-archive", default=str(DEFAULT_SOURCE_ARCHIVE))
    a = p.parse_args()
    dst = Path(a.dst)
    dst.mkdir(parents=True, exist_ok=True)
    atlas = load_original_atlas(Path(a.source_archive))
    island_top(atlas).save(dst / "island_top.png")
    island_side(atlas).save(dst / "island_side.png")
    voxel_block(False).save(dst / "voxel_block_dark.png")
    voxel_block(True).save(dst / "voxel_block_lit.png")
    print("wrote original-atlas jade grass, mauve cliff, and unchanged violet blocks")


if __name__ == "__main__":
    main()
