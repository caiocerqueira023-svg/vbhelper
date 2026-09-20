"""Compose the map and converted facility OBJ files into one runtime GLB."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector


def args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--map-fbx", required=True)
    parser.add_argument("--facility-dir", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--manifest", required=True)
    argv = sys.argv[sys.argv.index("--") + 1 :] if "--" in sys.argv else sys.argv[1:]
    return parser.parse_args(argv)


def remove_map_helpers() -> None:
    for obj in list(bpy.context.scene.objects):
        if obj.type == "MESH":
            bpy.context.view_layer.objects.active = obj
            obj.select_set(True)
            bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
            obj.select_set(False)
    helper_tokens = ("drawarea", "farmfieldeditor", "farmbuilder", "lighting", "locator", "camera", "digimon", "grid", "scenery")
    for obj in list(bpy.context.scene.objects):
        name = obj.name.lower()
        if (
            obj.type in {"CAMERA", "LIGHT", "EMPTY"}
            or name == "cube"
            or "collision" in name
            or name.endswith("_c")
            or "_c." in name
            or any(token in name for token in helper_tokens)
        ):
            bpy.data.objects.remove(obj, do_unlink=True)


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def tune_backdrop() -> None:
    sea = next((obj for obj in bpy.context.scene.objects if obj.type == "MESH" and obj.name.lower() == "sea"), None)
    if sea is None:
        return
    sea.location = (0.0, 0.0, 0.0)
    sea.scale = (0.35, 0.35, 0.35)
    bpy.context.view_layer.objects.active = sea
    sea.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=False, scale=True)
    sea.select_set(False)


def main() -> None:
    config = args()
    map_path = Path(config.map_fbx).resolve()
    facility_dir = Path(config.facility_dir).resolve()
    output = Path(config.output).resolve()
    manifest_path = Path(config.manifest).resolve()

    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=str(map_path), use_image_search=True)
    remove_map_helpers()
    tune_backdrop()

    # The DAE/OBJ facilities use y-up ground coordinates; the map FBX uses
    # Blender's z-up convention. Rotate once, then use a documented low-poly
    # scale and deterministic positions on the central island.
    placements = [
        (-0.65, -0.45), (-0.22, -0.50), (0.25, -0.45), (0.62, -0.25),
        (-0.65, 0.08), (0.62, 0.12), (-0.50, 0.45), (-0.10, 0.48),
        (0.30, 0.45), (0.62, 0.38),
    ]
    facility_names: list[str] = []
    for index, obj_path in enumerate(sorted(facility_dir.glob("*.obj"))):
        before = set(bpy.context.scene.objects)
        bpy.ops.wm.obj_import(filepath=str(obj_path))
        imported = [obj for obj in bpy.context.scene.objects if obj not in before]
        x, y = placements[index % len(placements)]
        for obj in imported:
            if obj.type != "MESH":
                continue
            obj.name = f"facility_{obj_path.stem}_{obj.name}"
            obj.rotation_euler[0] = math.radians(90)
            # DAE facilities are authored in a larger unit than the map FBX;
            # this keeps a building around one third of the playable island.
            obj.scale = (0.035, 0.035, 0.035)
            obj.location = (x, y, 0.50)
            facility_names.append(obj.name)

    output.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=str(output),
        export_format="GLB",
        export_materials="EXPORT",
        export_image_format="AUTO",
        export_texcoords=True,
        export_normals=True,
        export_animations=False,
        export_cameras=False,
        export_lights=False,
        use_selection=False,
    )
    manifest = {
        "schema": 1,
        "mapId": "digi_farm_3d",
        "mapVersion": 1,
        "asset": "digifarm/3d/digi_farm_3d.glb",
        "source": map_path.name,
        "sourceSha256": hash_file(map_path),
        "coordinateSystem": "blender-x-y-ground-to-runtime-x-z-ground-y-up",
        "playableBounds": {"min": [-0.80, -0.58], "max": [0.70, 0.55]},
        "safeSpawns": [
            [-0.38, -0.28], [0.0, -0.28], [0.38, -0.28],
            [-0.42, -0.08], [0.0, -0.08], [0.42, -0.08],
            [-0.38, 0.12], [0.0, 0.12], [0.38, 0.12],
            [-0.30, 0.30], [0.0, 0.30], [0.30, 0.30],
        ],
        "facilityNodes": facility_names,
        "collisionSource": ["farm_01_FarmColosseum_C.smd", "FarmColosseum_O.smd"],
        "facilityScale": 0.035,
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Composed {output} ({output.stat().st_size} bytes) with {len(facility_names)} facility meshes")


if __name__ == "__main__":
    main()
