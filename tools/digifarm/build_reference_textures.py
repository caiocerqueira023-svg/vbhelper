"""Digifarm textures: the original Links ground with simple square wireframe patches.

Outputs (POT sizes for mipmaps):
- island_top.png 512x512: the original farm atlas grass, tiled as authored, with the
  exposed cells drawn as a dark void carrying one continuous bright square grid.
- island_side.png 256x256: original side-atlas detail tinted by the existing
  mauve gradient, strata and transparent cliff-foot fade.
- voxel_block_dark/lit.png 128x128: two dark violet grid panels for the
  detached cubes around the island.
Usage: py tools/digifarm/build_reference_textures.py --dst DIR
"""
import argparse
import math
import colorsys
from io import BytesIO
import random
import statistics
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

SEED = 7
TOP_SIZE = 512
TOP_PITCH = 8
SIDE = 256
BLOCK_SIZE = 128
PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE_ARCHIVE = (
    PROJECT_ROOT / "DigifarmAssets" / "Mobile - Digimon Links - Maps - Digi-Farm.zip"
)
SOURCE_ATLAS_ENTRY = "Digi-Farm/farm_base_tex01.png"
# The authored farm grass tile and the earth band used for the cliff.
GRASS_ATLAS_REGION = (0, 0, 96, 96)
# Floor mosaic: the atlas' authored 8px ground tiles are arranged the way the reference farm's
# floor is - broad tone regions, soft clumps and a few warm accent tiles - instead of a repeated
# grid. The warm tiles come from the atlas path band.
# The reference floor uses large pieces cut straight from the atlas (16px = a 2x2 block of the
# authored cells), laid out in contiguous regions like the hand-made reference floor.
GRASS_TILE = 16
ACCENT_ATLAS_REGION = (96, 0, 224, 96)
LAWN_REGIONS = 70
LAWN_REGION_MIN = 6
LAWN_REGION_MAX = 64
ACCENT_PATCHES = 12
SIDE_ATLAS_REGION = (384, 400, 512, 448)
# Exposed cells: a plain dark square per cell with a bright square wireframe on top.
PANEL_VOID = (13, 9, 24)
WIRE_SQUARE = (186, 160, 255)


def load_original_atlas(source_archive: Path) -> Image.Image:
    """Load the supplied Farm atlas without extracting or modifying the ZIP."""
    with zipfile.ZipFile(source_archive) as archive:
        with Image.open(BytesIO(archive.read(SOURCE_ATLAS_ENTRY))) as image:
            return image.convert("RGB")


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


# Authored farm tint: a desaturated Tamers Lab violet, i.e. the theme's cube/patch accent
# at low saturation. The default theme has no runtime recolor, so baking it here keeps the
# whole farm monochromatic there too; every other theme derives its grass from its own
# accent at build time.
GRASS_AUTHORED_HUE = 0.717
GRASS_AUTHORED_SAT = 0.34
GRASS_AUTHORED_LIGHTNESS = 0.30


def _atlas_tiles(atlas: Image.Image, region: tuple[int, int, int, int], warm_only: bool = False):
    """Every authored ground tile of one atlas region, sorted by mean luminance."""
    area = atlas.crop(region)
    tiles = []
    for ty in range(0, area.height - GRASS_TILE + 1, GRASS_TILE):
        for tx in range(0, area.width - GRASS_TILE + 1, GRASS_TILE):
            cell = area.crop((tx, ty, tx + GRASS_TILE, ty + GRASS_TILE))
            pixels = list(cell.getdata())
            count = len(pixels)
            red = sum(pixel[0] for pixel in pixels) / count
            green = sum(pixel[1] for pixel in pixels) / count
            blue = sum(pixel[2] for pixel in pixels) / count
            if warm_only and not (red > blue + 12 and red > green - 6):
                continue
            luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            tiles.append((luminance, cell))
    tiles.sort(key=lambda entry: entry[0])
    return tiles


def _lawn_field(atlas: Image.Image) -> Image.Image:
    """Lay the authored ground tiles out like the reference floor's hand-made regions.

    The reference farm is not a per-tile mix: it is large contiguous blocks of one ground tile -
    broad patches of a lighter or darker green, a few warm path tiles, and long runs of the same
    tile - at a piece size much larger than the source cells. This seeds organic regions, grows
    each into a contiguous block of one variant, and pastes the pieces 1:1.
    """
    rng = random.Random(SEED)
    variants = _atlas_tiles(atlas, GRASS_ATLAS_REGION)
    accents = _atlas_tiles(atlas, ACCENT_ATLAS_REGION, warm_only=True)
    if len(variants) < 12:
        raise ValueError("Original farm atlas ground tiles not found")
    cells = TOP_SIZE // GRASS_TILE
    base = variants[len(variants) // 2][1]
    layout = [[base] * cells for _ in range(cells)]

    def grow(origin_x: int, origin_y: int, tile: Image.Image, size: int) -> None:
        seen = {(origin_x, origin_y)}
        layout[origin_y][origin_x] = tile
        frontier = [(origin_x, origin_y)]
        while frontier and len(seen) < size:
            x, y = frontier[rng.randrange(len(frontier))]
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < cells and 0 <= ny < cells) or (nx, ny) in seen:
                    continue
                if rng.random() > 0.72:
                    continue
                seen.add((nx, ny))
                layout[ny][nx] = tile
                frontier.append((nx, ny))
                if len(seen) >= size:
                    break

    for _ in range(LAWN_REGIONS):
        grow(
            rng.randrange(cells), rng.randrange(cells),
            variants[rng.randrange(len(variants))][1],
            rng.randint(LAWN_REGION_MIN, LAWN_REGION_MAX),
        )
    for _ in range(ACCENT_PATCHES if accents else 0):
        grow(
            rng.randrange(cells), rng.randrange(cells),
            accents[rng.randrange(len(accents))][1],
            rng.randint(2, 9),
        )

    field = Image.new("RGB", (TOP_SIZE, TOP_SIZE))
    for ty in range(cells):
        for tx in range(cells):
            tile = layout[ty][tx]
            roll = rng.random()
            if roll < 0.10:
                tile = ImageOps.mirror(tile)
            elif roll < 0.20:
                tile = ImageOps.flip(tile)
            field.paste(tile, (tx * GRASS_TILE, ty * GRASS_TILE))
    return field


def _monochrome_grass(image: Image.Image) -> Image.Image:
    """Flatten the Links grass into one desaturated accent tone.

    The atlas grass is a patchwork of mint, olive and dark tiles; at the island's size those
    steps read as a loud checkerboard. Hue and saturation become the authored accent's
    low-saturation version, and only a sixth of each pixel's lightness deviation survives,
    so the tile bevel and grain remain as faint texture inside a single tone.
    """
    pixels = [colorsys.rgb_to_hls(*(channel / 255 for channel in pixel)) for pixel in image.getdata()]
    median_lightness = statistics.median(entry[1] for entry in pixels)
    output = Image.new("RGB", image.size)
    flattened = []
    for _hue, lightness, _saturation in pixels:
        flattened.append(tuple(round(channel * 255) for channel in colorsys.hls_to_rgb(
            GRASS_AUTHORED_HUE,
            max(0.16, min(0.46, GRASS_AUTHORED_LIGHTNESS
                           + (lightness - median_lightness) * 0.62)),
            GRASS_AUTHORED_SAT,
        )))
    output.putdata(flattened)
    return output


def island_top(atlas: Image.Image | None = None) -> Image.Image:
    """The original Links farm ground, with simple square wireframe patches on top."""
    if atlas is None:
        raise ValueError("island_top needs the original farm atlas")
    img = _monochrome_grass(_lawn_field(atlas))
    d = ImageDraw.Draw(img, "RGBA")
    exposed = wireframe_tiles()
    pitch = TOP_PITCH
    for tx, ty in exposed:
        x, y = tx * pitch, ty * pitch
        d.rectangle((x, y, x + pitch - 1, y + pitch - 1), fill=PANEL_VOID)
    # One continuous square grid across the whole exposed area. The wire cells are
    # coarser than the patch mask cells so the grid reads as a digital mesh at the
    # island's scale instead of a fine fabric.
    grid = pitch * 2
    for tx, ty in exposed:
        x, y = tx * pitch, ty * pitch
        right = x + pitch - 1
        bottom = y + pitch - 1
        if x % grid == 0:
            d.line((x, y, x, bottom), fill=WIRE_SQUARE)
        if (x + pitch) % grid == 0:
            d.line((right, y, right, bottom), fill=WIRE_SQUARE)
        if y % grid == 0:
            d.line((x, y, right, y), fill=WIRE_SQUARE)
        if (y + pitch) % grid == 0:
            d.line((x, bottom, right, bottom), fill=WIRE_SQUARE)
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
        d.line([(0, y + 2), (SIDE, y + 2)], fill=(176, 146, 252, 110), width=1)
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
    print("wrote original-grass island top, mauve cliff and unchanged violet blocks")


if __name__ == "__main__":
    main()
