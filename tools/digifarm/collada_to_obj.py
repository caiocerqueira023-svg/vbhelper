"""Small offline fallback for facility DAE files without a Blender importer.

The supplied facility FBX files are ASCII. Blender's current importer accepts
the binary map FBX but intentionally rejects ASCII FBX, while the sibling DAE
contains the same static triangles. This converter flattens those triangles to
an OBJ/MTL pair so the normal Blender asset build can continue. It is a build
tool only and is never packaged in the APK.
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

import collada


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dae", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source = collada.Collada(str(args.dae))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    mtl_path = args.output.with_suffix(".mtl")

    material_files: dict[str, str | None] = {}
    with mtl_path.open("w", encoding="utf-8") as mtl:
        for index, material in enumerate(source.materials):
            name = f"material_{index}"
            texture_name: str | None = None
            diffuse = material.effect.diffuse
            sampler = getattr(diffuse, "sampler", None)
            image = getattr(getattr(sampler, "surface", None), "image", None)
            if image is not None and getattr(image, "path", None):
                source_texture = args.dae.parent / image.path
                if source_texture.exists():
                    texture_name = source_texture.name
                    destination = args.output.parent / texture_name
                    if destination.resolve() != source_texture.resolve():
                        shutil.copy2(source_texture, destination)
            material_files[name] = texture_name
            mtl.write(f"newmtl {name}\n")
            mtl.write("Kd 1.0 1.0 1.0\nKa 0.0 0.0 0.0\n")
            if texture_name:
                mtl.write(f"map_Kd {texture_name}\n")
            mtl.write("\n")

    vertex_offset = 1
    with args.output.open("w", encoding="utf-8") as obj:
        obj.write(f"mtllib {mtl_path.name}\n")
        for geometry_index, geometry in enumerate(source.geometries):
            material_name = f"material_{min(geometry_index, len(source.materials) - 1)}"
            for primitive in geometry.primitives:
                obj.write(f"o {geometry.name}\nusemtl {material_name}\n")
                for vertex in primitive.vertex:
                    obj.write(f"v {vertex[0]} {vertex[1]} {vertex[2]}\n")
                for uv in (primitive.texcoordset[0] if primitive.texcoordset else []):
                    obj.write(f"vt {uv[0]} {1.0 - uv[1]}\n")
                if getattr(primitive, "normal", None) is not None:
                    for normal in primitive.normal:
                        obj.write(f"vn {normal[0]} {normal[1]} {normal[2]}\n")
                has_uv = bool(primitive.texcoordset)
                has_normals = getattr(primitive, "normal", None) is not None
                for face in primitive.vertex_index:
                    indices = []
                    for index in face:
                        position = vertex_offset + int(index)
                        uv = f"/{position}" if has_uv else "/"
                        normal = f"/{position}" if has_normals else ""
                        indices.append(f"{position}{uv}{normal}")
                    obj.write(f"f {' '.join(indices)}\n")
                vertex_offset += len(primitive.vertex)


if __name__ == "__main__":
    main()
