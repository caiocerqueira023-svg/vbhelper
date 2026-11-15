"""Bake color-only texture variants from the shipped environment GLBs.

The original GLBs, geometry, manifests, sprite/effect assets and VB Helper pixels
are never rewritten. Material allowlists and the farm's authored patch mask keep
unrelated artwork out of palette conversion; the farm's grass follows the palette's
`grass` stop and its digital cells use the `patchGrid` accent, while the authored
cliff, block and arena surfaces keep their own roles. Filament swaps these PNGs on
existing material instances, not entire models. Run with --check to validate the
packaged sources, channels, alpha and texture payloads without writing.
"""
from __future__ import annotations

import argparse
import colorsys
import hashlib
import io
import json
import statistics
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
        "island_top_baked": "grass_patches", "island_side_baked": "cliff",
        "island_side_rim_baked": "cliff",
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
        pixels = image.load()
        exposed = wireframe_tiles()
        panel, grid, energy = (rgb(palette[k]) for k in ("panel", "grid", "energy"))
        # The patch wireframe uses the same palette accent as the voxel cubes' edges, so
        # the exposed terrain and the floating blocks always share one color per theme.
        patch_grid = energy
        # Monochromatic farm: the turf is the same accent as the patches/cubes, only far
        # less saturated and much darker. The authored grain survives as a faint lightness
        # field; digital cells keep the authored mask and take the full accent.
        energy_h, _energy_l, energy_s = colorsys.rgb_to_hls(*(channel / 255 for channel in energy))
        grass_h = energy_h
        grass_s = max(0.22, min(0.40, energy_s * 0.45))
        grass_l = 0.30
        exposure = [[False] * TOP_SIZE for _ in range(TOP_SIZE)]
        for tx, ty in exposed:
            for py in range(ty * TOP_PITCH, min((ty + 1) * TOP_PITCH, TOP_SIZE)):
                for px in range(tx * TOP_PITCH, min((tx + 1) * TOP_PITCH, TOP_SIZE)):
                    exposure[py][px] = True
        turf_lightness = [
            colorsys.rgb_to_hls(*(channel / 255 for channel in pixels[x, y][:3]))[1]
            for y in range(TOP_SIZE) for x in range(TOP_SIZE)
            if not exposure[y][x]
        ]
        turf_median = statistics.median(turf_lightness) if turf_lightness else 0.5
        for y in range(TOP_SIZE):
            for x in range(TOP_SIZE):
                original = pixels[x, y]
                if exposure[y][x]:
                    brightness = max(original[:3])
                    target = patch_grid if brightness >= 200 else grid if brightness >= 65 else panel
                    pixels[x, y] = (*target, original[3])
                else:
                    lightness = colorsys.rgb_to_hls(*(channel / 255 for channel in original[:3]))[1]
                    themed = colorsys.hls_to_rgb(
                        grass_h,
                        max(0.16, min(0.46, grass_l + (lightness - turf_median))),
                        grass_s,
                    )
                    pixels[x, y] = (*(round(channel * 255) for channel in themed), original[3])
    elif role == "panel":
        gray = ImageOps.grayscale(image)
        image = ImageOps.colorize(gray, black=palette["panel"], mid=palette["grid"], white=palette["energy"],
                                 midpoint=72, whitepoint=180).convert("RGBA")
    elif role in ("structure", "cliff"):
        authored = image.copy()
        gray = ImageOps.autocontrast(ImageOps.grayscale(image), cutoff=1)
        dark, light = palette["structureDark"], palette["structureLight"]
        if role == "cliff" and palette["light"]:
            # The island's side is authored light-at-the-rim, dark-at-the-base. On light
            # themes that ramp puts its darkest pixels against the bright backdrop, so the
            # floating island loses its silhouette; flip the ramp to keep the base dark.
            dark, light = light, dark
        image = ImageOps.colorize(gray, black=dark, white=light).convert("RGBA")
        if role == "cliff":
            # Only the authored strata highlight rows take the theme accent (so the island's
            # light side lines read like the floating cubes). Those rows are the only strongly
            # blue pixels in the authored cliff, which separates them from the pale rim ramp.
            mask = Image.new("L", image.size)
            authored_pixels = authored.convert("RGBA").load()
            mask_pixels = mask.load()
            for py in range(image.size[1]):
                for px in range(image.size[0]):
                    red, green, blue, _alpha = authored_pixels[px, py]
                    mask_pixels[px, py] = 255 if (
                        blue - red > 55 and blue - green > 55 and max(red, green, blue) > 120
                    ) else 0
            image = Image.composite(
                Image.new("RGBA", image.size, (*rgb(palette["energy"]), 255)), image, mask,
            )
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
                        color_role = {"floor": "floor", "grid": "grid", "boundary": "boundary"}[role]
                        factors = [*linear(palette.get(color_role) or palette["energy"]), base[3]]
                    if any(emission_factor):
                        # The farm's floor and cubes are theme-tinted emissive surfaces, not white
                        # ones, so they keep most of the dark-theme baseline in light palettes too and
                        # bloom the same way in every theme. The small trim keeps the body of the
                        # floor saturated; only its bright lines climb into the bloom range.
                        gain = .94 if (scene == "farm" and palette["light"]) else (
                            1.0 if not palette["light"] else .75)
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
                        if material["role"] == "grass_patches" and theme != "vb_helper":
                            # The turf is themed now: the authored grass hue is replaced by the
                            # palette's grass stop, so a natural pixel has to differ from the source.
                            outside = [(x, y) for x, y in ((256, 256), (300, 300), (220, 240)) if (x // TOP_PITCH, y // TOP_PITCH) not in wireframe_tiles()]
                            assert outside and all(a.convert("RGBA").getpixel(p) != b.convert("RGBA").getpixel(p) for p in outside), "Themed grass did not change"
                        if material["role"] == "grass_patches":
                            # Digital cells keep the authored grid geometry: the mask must still
                            # hold bright wire pixels that the recolor sends to the patch grid color.
                            exposed = next(iter(wireframe_tiles()))
                            corner = (exposed[0] * TOP_PITCH, exposed[1] * TOP_PITCH)
                            assert a.convert("RGBA").getpixel(corner) == b.convert("RGBA").getpixel(corner) or theme != "vb_helper"
    print("Environment themes valid: original geometry/sources, default pixels, themed grass, patch grid, alpha, samplers and four-theme bindings preserved")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.check:
        generate(args.output)
    validate(args.output)
