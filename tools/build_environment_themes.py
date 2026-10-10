"""Bake color-only texture variants from the shipped environment GLBs.

The original GLBs, geometry, manifests, sprite/effect assets and VB Helper pixels
are never rewritten. Material allowlists and the farm's authored patch mask keep
grass and unrelated artwork out of palette conversion. Filament swaps these PNGs
on existing material instances, not entire models. Run with --check to validate
the packaged sources, channels, alpha and texture payloads without writing.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import struct
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps
from digifarm.build_reference_textures import TOP_PITCH, TOP_SIZE, wireframe_tiles

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
OUTPUT = ASSETS / "environment_themes"
PALETTES = json.loads((Path(__file__).with_name("environment_theme_palettes.json")).read_text(encoding="utf-8"))
SCENES = {
    "farm": ("digifarm/3d/digi_farm_3d.glb", {
        "island_top_baked": "grass_patches", "island_side_baked": "structure",
        "voxel_block_dark_baked": "panel", "voxel_block_lit_baked": "panel",
    }),
    "colosseum": ("Arena/Colosseum/Colosseum.glb", {"Ground": "structure", "Sphere001": "dome"}),
    "radar": ("Arena/Radar/radar_grid.glb", {
        "radar_floor": "floor", "world_grid": "grid", "battle_boundary": "boundary",
        "voxel_block_dark_baked": "panel", "voxel_block_lit_baked": "panel", "RadarSkyDome": "dome",
    }),
}


def read_glb(path):
    raw = path.read_bytes()
    magic, version, size = struct.unpack_from("<III", raw)
    if magic != 0x46546C67 or version != 2 or size != len(raw):
        raise ValueError(f"Invalid GLB: {path}")
    length, kind = struct.unpack_from("<II", raw, 12)
    if kind != 0x4E4F534A:
        raise ValueError("GLB JSON missing")
    doc = json.loads(raw[20:20 + length])
    binary_length, binary_kind = struct.unpack_from("<II", raw, 20 + length)
    if binary_kind != 0x004E4942:
        raise ValueError("GLB binary missing")
    return doc, raw[28 + length:28 + length + binary_length], hashlib.sha256(raw).hexdigest()


def texture_source(doc, binary, index):
    texture = doc["textures"][index]
    image = doc["images"][texture["source"]]
    view = doc["bufferViews"][image["bufferView"]]
    offset = view.get("byteOffset", 0)
    payload = binary[offset:offset + view["byteLength"]]
    sampler = doc.get("samplers", [])[texture["sampler"]] if "sampler" in texture else {}
    return payload, {"wrapS": sampler.get("wrapS", 10497), "wrapT": sampler.get("wrapT", 10497),
                     "minFilter": sampler.get("minFilter", 9987), "magFilter": sampler.get("magFilter", 9729)}


def rgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))


def linear(value):
    return [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in (v / 255 for v in rgb(value))]


def recolor(source, role, palette, emission=False):
    image = source.convert("RGBA")
    alpha = image.getchannel("A")
    if role == "grass_patches":
        if image.size != (TOP_SIZE, TOP_SIZE):
            raise ValueError("Farm top changed size; its authored patch mask must be updated")
        # Work only on authored digital cells; all natural pixels remain exact.
        pixels = image.load()
        exposed = wireframe_tiles()
        panel, grid, energy = (rgb(palette[k]) for k in ("panel", "grid", "energy"))
        for tx, ty in exposed:
            x, y = tx * TOP_PITCH, ty * TOP_PITCH
            for py in range(y, min(y + TOP_PITCH, TOP_SIZE)):
                for px in range(x, min(x + TOP_PITCH, TOP_SIZE)):
                    original = pixels[px, py]
                    # Geometry defines patch membership; luminance differentiates
                    # the authored panel/grid/seam inside that membership only.
                    brightness = max(original[:3])
                    target = energy if brightness >= 200 else grid if brightness >= 65 else panel
                    pixels[px, py] = (*target, original[3])
    elif role == "panel":
        gray = ImageOps.grayscale(image)
        image = ImageOps.colorize(gray, black=palette["panel"], mid=palette["grid"], white=palette["energy"],
                                 midpoint=72, whitepoint=180).convert("RGBA")
    elif role == "structure":
        gray = ImageOps.autocontrast(ImageOps.grayscale(image), cutoff=1)
        image = ImageOps.colorize(gray, black=palette["structureDark"], white=palette["structureLight"]).convert("RGBA")
    elif role == "dome":
        gray = ImageOps.autocontrast(ImageOps.grayscale(image), cutoff=1)
        if emission:
            # The interior dome receives little direct light. A restrained pale
            # emission base prevents its opaque dark patches from showing as
            # black cutouts in light arenas; the runtime still bounds HDR gain.
            gray = gray.point(lambda v: round(255 * (v / 255) ** 1.35))
            background = palette["panel"] if palette["light"] else "#000000"
            image = ImageOps.colorize(gray, black=background, white=palette["energy"]).convert("RGBA")
        else:
            image = ImageOps.colorize(gray, black=palette["panel"], mid=palette["grid"],
                                     white=palette["energy"], midpoint=90).convert("RGBA")
        # Keep a few high-energy peaks as the theme's secondary highlight.
        highlight = gray.point(lambda v: max(0, min(80, (v - 230) * 3)))
        image = Image.composite(Image.new("RGBA", image.size, (*rgb(palette["secondaryEnergy"]), 255)), image, highlight)
    else:
        raise ValueError(f"Unexpected textured environment role: {role}")
    image.putalpha(alpha)
    return image


def encoded(image):
    buffer = io.BytesIO()
    image.save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def generate(output=OUTPUT):
    output = Path(output)
    old_files = set()
    if (output / "manifest.json").is_file():
        previous = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
        old_files = {Path(spec["asset"]).name for scene in previous["scenes"].values()
                     for material in scene["materials"] for variant in material["variants"].values()
                     for spec in variant["textures"].values()}
    output.mkdir(parents=True, exist_ok=True)
    textures = output / "textures"
    textures.mkdir(exist_ok=True)
    manifest = {"schema": 1, "palettes": PALETTES, "scenes": {}}
    total = 0
    for scene, (asset_path, roles) in SCENES.items():
        doc, binary, source_hash = read_glb(ASSETS / asset_path)
        materials = {m["name"]: m for m in doc["materials"]}
        missing = roles.keys() - materials.keys()
        if missing:
            raise ValueError(f"{scene}: source material contract changed: {sorted(missing)}")
        scene_doc = {"asset": asset_path, "sourceSha256": source_hash, "materials": []}
        for name, role in roles.items():
            material = materials[name]
            pbr = material.get("pbrMetallicRoughness", {})
            base = pbr.get("baseColorFactor", [1, 1, 1, 1])
            emission_factor = material.get("emissiveFactor", [0, 0, 0])
            channels = {}
            for parameter, owner, key in (("baseColorMap", pbr, "baseColorTexture"),
                                          ("emissiveMap", material, "emissiveTexture")):
                if key in owner:
                    channels[parameter] = texture_source(doc, binary, owner[key]["index"])
            entry = {"name": name, "role": role, "variants": {}}
            for theme, palette in PALETTES.items():
                factors = list(base)
                emit = list(emission_factor)
                if theme != "vb_helper":
                    if not channels:
                        color_role = {"floor": "floor", "grid": "grid", "boundary": "energy"}[role]
                        factors = [*linear(palette[color_role]), base[3]]
                    if any(emission_factor):
                        gain = .75 if palette["light"] else 1.0
                        emit = [gain] * 3
                variant = {"baseColorFactor": factors, "emissiveFactor": emit, "textures": {}}
                for parameter, (original, sampler) in channels.items():
                    with Image.open(io.BytesIO(original)) as image:
                        payload = original if theme == "vb_helper" else encoded(recolor(
                            image, role, palette, emission=parameter == "emissiveMap"))
                    digest = hashlib.sha256(payload).hexdigest()
                    path = textures / f"{digest}.png"
                    if not path.exists():
                        path.write_bytes(payload)
                    variant["textures"][parameter] = {"asset": f"environment_themes/textures/{digest}.png", **sampler}
                    total += 1
                entry["variants"][theme] = variant
            scene_doc["materials"].append(entry)
        manifest["scenes"][scene] = scene_doc
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    referenced = {Path(spec["asset"]).name for scene in manifest["scenes"].values()
                  for material in scene["materials"] for variant in material["variants"].values()
                  for spec in variant["textures"].values()}
    # Retire only verified, previously generated payloads, never arbitrary files.
    for name in old_files - referenced:
        path = textures / name
        if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == path.stem:
            path.unlink()
    print(f"Built {len(SCENES)} environment contracts, {total} texture bindings, {len(list(textures.glob('*.png')))} unique PNGs")
    return manifest


def validate(output=OUTPUT):
    output = Path(output)
    manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["schema"] == 1 and manifest["palettes"] == PALETTES
    for scene, (asset_path, roles) in SCENES.items():
        doc, binary, digest = read_glb(ASSETS / asset_path)
        recorded = manifest["scenes"][scene]
        assert recorded["sourceSha256"] == digest, f"Stale {scene} textures; regenerate after GLB changes"
        source_materials = {m["name"]: m for m in doc["materials"]}
        assert {m["name"] for m in recorded["materials"]} == set(roles)
        for material in recorded["materials"]:
            original = source_materials[material["name"]]
            for theme, variant in material["variants"].items():
                assert set(material["variants"]) == set(PALETTES)
                for parameter, spec in variant["textures"].items():
                    path = output / "textures" / Path(spec["asset"]).name
                    payload = path.read_bytes()
                    assert hashlib.sha256(payload).hexdigest() == path.stem
                    owner, key = (original.get("pbrMetallicRoughness", {}), "baseColorTexture") if parameter == "baseColorMap" else (original, "emissiveTexture")
                    source, sampler = texture_source(doc, binary, owner[key]["index"])
                    assert all(spec[k] == v for k, v in sampler.items())
                    if theme == "vb_helper":
                        assert payload == source, "Default-theme texture changed"
                    with Image.open(io.BytesIO(source)) as a, Image.open(io.BytesIO(payload)) as b:
                        assert a.size == b.size
                        assert a.convert("RGBA").getchannel("A").tobytes() == b.convert("RGBA").getchannel("A").tobytes(), "Alpha changed"
                        if material["role"] == "grass_patches":
                            outside = [(x, y) for x, y in ((256, 256), (300, 300), (220, 240)) if (x // TOP_PITCH, y // TOP_PITCH) not in wireframe_tiles()]
                            assert outside and all(a.convert("RGBA").getpixel(p) == b.convert("RGBA").getpixel(p) for p in outside), "Natural grass changed"
    print("Environment themes valid: original geometry/sources, default pixels, grass, alpha, samplers and four-theme bindings preserved")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.check:
        generate(args.output)
    validate(args.output)
