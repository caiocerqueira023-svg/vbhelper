"""Tron/wireframe variants of the Digi-Farm textures.

Black base with vital-cyan (#2DE1FC) edge lines following the original pixel
grid; alpha preserved. Run once per source pack; outputs overwrite the copies
under a staging dir consumed by build_assets.py (same filenames).
Usage: py tools/digifarm/build_tron_textures.py --src DIR --dst DIR
"""
import argparse
from pathlib import Path
from PIL import Image, ImageFilter

CYAN = (45, 225, 252, 255)
BASE = (6, 4, 14, 255)  # near-black with a purple tint (space-black family)
DARKEN = 0.12  # how much of the original luminance survives under the black
EDGE_THRESHOLD = 28


def tronify(src: Image.Image) -> Image.Image:
    src = src.convert("RGBA")
    edges = src.convert("L").filter(ImageFilter.FIND_EDGES)
    mask = edges.point(lambda v: 255 if v > EDGE_THRESHOLD else 0)
    dark = Image.new("RGBA", src.size, BASE)
    base = Image.blend(dark, src, DARKEN)
    cyan = Image.new("RGBA", src.size, CYAN)
    out = Image.composite(cyan, base, mask.convert("L"))
    out.putalpha(src.getchannel("A"))
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--src", required=True, help="dir with farm_base_tex01/stadium/stadium_close PNGs")
    p.add_argument("--dst", required=True)
    a = p.parse_args()
    src, dst = Path(a.src), Path(a.dst)
    dst.mkdir(parents=True, exist_ok=True)
    for name in ("farm_base_tex01.png", "stadium.png", "stadium_close.png",
                 "island_top.png", "island_side.png", "island_keel.png"):
        if not (src / name).exists():
            print("skip missing", name)
            continue
        out = tronify(Image.open(src / name))
        out.save(dst / name)
        print("tron", name, out.size)


if __name__ == "__main__":
    main()
