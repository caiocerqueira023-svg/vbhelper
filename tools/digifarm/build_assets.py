"""Canonical Digifarm scene. Blender -b --python build_assets.py -- --source-root DIR --output-dir DIR.
Original archives remain unchanged. Runtime is Y-up, island top y=0, width about 2 units.
Island/islets keep the authored patchwork, repaired deterministically (welded
tiles, connected boundary-loop side closure, degenerate purge), then split by
face normal (green tiled top, tan cliffs) with fresh planar/cylindrical UVs,
matching the reference farm. The lower cliff uses image alpha for its fade.
"""
import argparse, bmesh, hashlib, json, math, sys
from pathlib import Path
import bpy
from mathutils import Matrix, Vector


def emission_mat(name, image_path=None, color=None):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.node_tree.nodes.clear()
    output = mat.node_tree.nodes.new('ShaderNodeOutputMaterial')
    emission = mat.node_tree.nodes.new('ShaderNodeEmission')
    mat.node_tree.links.new(emission.outputs[0], output.inputs['Surface'])
    if image_path is not None:
        tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
        tex.image = bpy.data.images.load(str(image_path), check_existing=True)
        mat.node_tree.links.new(tex.outputs['Color'], emission.inputs['Color'])
        if Path(image_path).stem in {'island_side', 'island_keel'}:
            # Let the image alpha remove the lower cliff instead of baking
            # the app's black background into the model.
            mat.surface_render_method = 'DITHERED'
            mat.blend_method = 'BLEND'
            mat.use_transparency_overlap = False
    else:
        emission.inputs['Color'].default_value = (*color, 1)
    return mat


def cap_foot(o):
    """Close the foot opening with a FLAT fan at foot level (no extension):
    angle-sorted low-band verts fanned to their centroid, facing down, tagged
    997 (side rock, dark). Kills see-through slits without changing silhouette."""
    mesh = o.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    mw = o.matrix_world.copy()
    inv = mw.inverted()
    nor = mw.inverted().transposed().to_3x3()
    minz_all = min((mw @ v.co).z for v in bm.verts)
    band = [v for e in bm.edges if len(e.link_faces) == 1 for v in e.verts
            if (mw @ v.co).z < minz_all + 0.10]
    rim = list(dict.fromkeys(band))
    if len(rim) < 3:
        bm.free()
        return 0
    world = {v: mw @ v.co for v in rim}
    cx = sum(p.x for p in world.values()) / len(world)
    cy = sum(p.y for p in world.values()) / len(world)
    cz = sum(p.z for p in world.values()) / len(world)
    ordered = sorted(rim, key=lambda v: math.atan2(world[v].y - cy, world[v].x - cx))
    cent = bm.verts.new(inv @ Vector((cx, cy, cz)))
    bm.verts.ensure_lookup_table()
    n = len(ordered)
    count = 0
    for i in range(n):
        a, b = ordered[i], ordered[(i + 1) % n]
        if math.dist(world[a], world[b]) > 0.5:
            continue
        try:
            f = bm.faces.new([cent, b, a])
        except ValueError:
            continue
        nw = (nor @ f.normal).normalized()
        if nw.z > 0:
            f.normal_flip()
        f.material_index = 997
        count += 1
    bm.to_mesh(mesh)
    mesh.update()
    bm.free()
    print('cap', o.name, 'faces', count)
    return count


def boundary_groups(bm):
    """Connected components of boundary edges (flood fill; safe on branches)."""
    remaining = {e for e in bm.edges if len(e.link_faces) == 1}
    groups = []
    while remaining:
        seed = remaining.pop()
        members = {seed}
        frontier = set(seed.verts)
        grew = True
        while grew:
            grew = False
            for e in list(remaining):
                if e.verts[0] in frontier or e.verts[1] in frontier:
                    remaining.discard(e)
                    members.add(e)
                    frontier.update(e.verts)
                    grew = True
        groups.append({v for e in members for v in e.verts})
    return groups


def tri_span(tri, world_of):
    pts = [world_of(v) for v in tri]
    return max(
        math.dist(pts[0], pts[1]),
        math.dist(pts[1], pts[2]),
        math.dist(pts[2], pts[0]),
    )


def fill_top_holes(o, thresh=0.12, max_span=0.25):
    """Fan-fill high boundary loops with flat upward faces (repaired as grass
    by the later split). Long spanning tris (C-shaped loops) are skipped:
    they would become vertical green sheets."""
    mesh = o.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    mw = o.matrix_world.copy()
    inv = mw.inverted()
    nor = mw.inverted().transposed().to_3x3()
    maxz = max((mw @ v.co).z for v in bm.verts)
    allw = [(mw @ v.co) for v in bm.verts]
    ocx = sum(p.x for p in allw) / len(allw)
    ocy = sum(p.y for p in allw) / len(allw)
    obj_radius = max(math.hypot(p.x - ocx, p.y - ocy) for p in allw)
    count = 0
    for group in boundary_groups(bm):
        world = {v: mw @ v.co for v in group}
        avg_z = sum(p.z for p in world.values()) / len(world)
        if avg_z < maxz - thresh:
            continue
        gx = sum(p.x for p in world.values()) / len(world)
        gy = sum(p.y for p in world.values()) / len(world)
        cz = avg_z
        group_radius = max(math.hypot(p.x - gx, p.y - gy) for p in world.values())
        if group_radius > 0.6 * obj_radius:
            continue  # outer perimeter, not a hole: filling it would make a green eave
        ordered = sorted(group, key=lambda v: math.atan2(world[v].y - gy, world[v].x - gx))
        cent = bm.verts.new(inv @ Vector((gx, gy, cz)))
        bm.verts.ensure_lookup_table()
        world_of = dict(world)
        world_of[cent] = Vector((gx, gy, cz))
        n = len(ordered)
        for i in range(n):
            tri = (cent, ordered[i], ordered[(i + 1) % n])
            if tri_span(tri, lambda v: world_of[v]) > max_span:
                continue
            try:
                f = bm.faces.new(tri)
            except ValueError:
                continue
            nw = (nor @ f.normal).normalized()
            if nw.z < 0:
                f.normal_flip()
            count += 1
    bm.to_mesh(mesh)
    mesh.update()
    bm.free()
    print('filled', o.name, 'faces', count)
    return count


def add_backer(o, backer_mat, drop=0.025):
    """Deprecated compatibility hook.

    The old exporter copied every grass face below the original surface. That
    created two nearly coplanar green layers and visible z-fighting on mobile
    GPUs. Holes are now closed by the continuous side/skirt geometry, so the
    renderer must contain exactly one grass surface.
    """
    return False


def clean_mesh(o):
    """Weld exact-duplicate seam verts and delete zero-area faces + loose
    verts. Degenerate tris can rasterize as stray bright slivers on some
    GPUs even with ~zero area, so the file must ship none."""
    mesh = o.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bm.verts.ensure_lookup_table()
    bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=1e-4)
    bm.faces.ensure_lookup_table()
    dead = [f for f in bm.faces if f.calc_area() < 1e-6]
    if dead:
        bmesh.ops.delete(bm, geom=dead, context='FACES')
    loose = [v for v in bm.verts if not v.link_faces]
    if loose:
        bmesh.ops.delete(bm, geom=loose, context='VERTS')
    bm.to_mesh(mesh)
    mesh.update()
    bm.free()


def close_top_side_gaps(o, top_band=0.018):
    """Close the authored side opening with a connected boundary strip.

    The source FBX has one open boundary loop: one arc is the grass rim and
    the other is the lower cliff rim.  The old repair projected every top
    edge to one global ``foot`` height, which made an independent skirt that
    could float below the island.  This repair follows the actual boundary
    loop, zips the two arcs together with triangles/quads, and reuses every
    original boundary vertex.  The new surface therefore shares edges with
    the source shell at both rims and cannot create a detached bottom piece.
    """
    mesh = o.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bm.verts.ensure_lookup_table()
    bm.edges.ensure_lookup_table()
    mw = o.matrix_world.copy()
    world_pos = {v: mw @ v.co for v in bm.verts}
    if not world_pos:
        bm.free()
        return 0

    boundary = [e for e in bm.edges if len(e.link_faces) == 1]
    if len(boundary) < 3:
        bm.free()
        return 0
    adjacency = {}
    for edge in boundary:
        for vertex in edge.verts:
            adjacency.setdefault(vertex, []).append(edge)

    # The island boundary is a single degree-two loop.  Keep this generic so
    # a future farm asset with a few more vertices still follows the same path.
    seed = boundary[0]
    loop = [seed.verts[0]]
    previous = seed
    current = seed.verts[1]
    guard = len(boundary) + 2
    while current is not loop[0] and len(loop) < guard:
        loop.append(current)
        choices = [edge for edge in adjacency.get(current, []) if edge is not previous]
        if not choices:
            break
        edge = choices[0]
        previous, current = edge, edge.verts[0] if edge.verts[1] is current else edge.verts[1]
    if current is not loop[0] or len(loop) != len(boundary):
        # A branched/multi-loop mesh is safer left untouched than patched with
        # guessed geometry.  The normal FBX path is the single loop above.
        bm.free()
        return 0

    top = max(p.z for p in world_pos.values())
    foot = min(p.z for p in world_pos.values())
    top_cut = top - top_band
    bottom_cut = foot + max(0.025, (top - foot) * 0.06)
    transition = {
        i for i, vertex in enumerate(loop)
        if bottom_cut < world_pos[vertex].z < top_cut
    }
    # A stepped cliff has several transition vertices at each endpoint.  Turn
    # them into two contiguous runs, retaining the whole authored staircase.
    runs = []
    remaining = set(transition)
    while remaining:
        start = min(remaining)
        run = [start]
        remaining.remove(start)
        while (run[-1] + 1) % len(loop) in remaining:
            nxt = (run[-1] + 1) % len(loop)
            run.append(nxt)
            remaining.remove(nxt)
        runs.append(run)
    if len(runs) != 2:
        bm.free()
        return 0

    def run_endpoints(run):
        before = (run[0] - 1) % len(loop)
        after = (run[-1] + 1) % len(loop)
        candidates = (before, after)
        top_index = max(candidates, key=lambda index: world_pos[loop[index]].z)
        bottom_index = min(candidates, key=lambda index: world_pos[loop[index]].z)
        if world_pos[loop[top_index]].z < top_cut or world_pos[loop[bottom_index]].z > bottom_cut:
            return None
        return top_index, bottom_index

    endpoints = [run_endpoints(run) for run in runs]
    if any(pair is None for pair in endpoints):
        bm.free()
        return 0
    (top_a, bottom_a), (top_b, bottom_b) = endpoints
    # Reserve one valid source material slot so the generated-face marker
    # survives the BMesh -> Mesh round-trip.  split_island replaces the slots
    # with the final top/side/keel materials and consumes this marker.
    connector_marker = len(mesh.materials)
    mesh.materials.append(None)
    o['_digifarm_connector_slot'] = connector_marker

    def segment(start, end):
        if start <= end:
            return loop[start:end + 1]
        return loop[start:] + loop[:end + 1]

    def choose_path(start, end, prefer_high):
        direct = segment(start, end)
        opposite = list(reversed(segment(end, start)))
        direct_mean = interior_mean(direct)
        opposite_mean = interior_mean(opposite)
        if prefer_high:
            return direct if direct_mean >= opposite_mean else opposite
        return direct if direct_mean <= opposite_mean else opposite

    def interior_mean(path):
        values = [world_pos[v].z for v in path[1:-1]]
        return sum(values) / len(values) if values else world_pos[path[0]].z

    # Choose the perimeter arc at the grass height and the complementary arc
    # at the foot height.  Both paths run from the first transition to the
    # second, so every original boundary edge is shared by the new surface.
    upper = choose_path(top_a, top_b, prefer_high=True)
    lower = choose_path(bottom_a, bottom_b, prefer_high=False)
    if len(lower) < 3 or len(upper) < 3:
        bm.free()
        return 0

    def normalized_lengths(path):
        lengths = [0.0]
        for a, b in zip(path, path[1:]):
            lengths.append(lengths[-1] + (world_pos[b] - world_pos[a]).length)
        total = max(lengths[-1], 1e-8)
        return [value / total for value in lengths]

    lower_s = normalized_lengths(lower)
    upper_s = normalized_lengths(upper)
    cx = sum(p.x for p in world_pos.values()) / len(world_pos)
    cy = sum(p.y for p in world_pos.values()) / len(world_pos)
    normal_matrix = mw.inverted().transposed().to_3x3()
    added = 0

    def add_face(vertices):
        nonlocal added
        if len(set(vertices)) < 3:
            return
        try:
            face = bm.faces.new(vertices)
        except ValueError:
            return
        bm.normal_update()
        points = [world_pos.get(vertex, mw @ vertex.co) for vertex in face.verts]
        midpoint = sum(points, Vector()) / len(points)
        outward = Vector((midpoint.x - cx, midpoint.y - cy, 0.0))
        normal_world = (normal_matrix @ face.normal).normalized()
        if outward.length > 1e-6 and normal_world.dot(outward) < 0:
            face.normal_flip()
        # Keep the generated connector on the cliff material even when a
        # highly sloped zipper triangle would otherwise classify as a top
        # face from its normal alone.
        face.material_index = connector_marker
        added += 1

    # Fill each stepped transition itself with a small fan.  Its closing edge
    # is the endpoint bridge that the zipper below also uses, making the cap
    # one manifold piece instead of a floating triangle.
    inv = mw.inverted()
    for run, (top_index, bottom_index) in zip(runs, endpoints):
        if (top_index + 1) % len(loop) == run[0]:
            ordered = [top_index] + run + [bottom_index]
        else:
            ordered = [top_index] + list(reversed(run)) + [bottom_index]
        points = [world_pos[loop[index]] for index in ordered]
        center_world = sum(points, Vector()) / len(points)
        center = bm.verts.new(inv @ center_world)
        world_pos[center] = center_world
        for a, b in zip(ordered, ordered[1:]):
            add_face((center, loop[a], loop[b]))
        add_face((center, loop[ordered[-1]], loop[ordered[0]]))

    i = j = 0
    while i < len(upper) - 1 or j < len(lower) - 1:
        next_upper = upper_s[i + 1] if i < len(upper) - 1 else float('inf')
        next_lower = lower_s[j + 1] if j < len(lower) - 1 else float('inf')
        if abs(next_upper - next_lower) < 1e-5:
            vertices = (upper[i], upper[i + 1], lower[j + 1], lower[j])
            i += 1
            j += 1
        elif next_upper < next_lower:
            vertices = (upper[i], upper[i + 1], lower[j])
            i += 1
        else:
            vertices = (upper[i], lower[j + 1], lower[j])
            j += 1
        add_face(vertices)

    remaining_boundary = sum(1 for edge in bm.edges if len(edge.link_faces) == 1)
    bm.to_mesh(mesh)
    mesh.update()
    bm.free()
    print('closed connected side gap', o.name, 'faces', added,
          'upper', len(upper), 'lower', len(lower),
          'remaining boundary', remaining_boundary)
    return added


def split_island(o, tex_dir, mat_cache):
    """Top faces -> island_top.png (planar UV), cliff -> island_side.png
    (cylindrical UV), down-facing strays -> keel fade (safety net)."""
    if 'island_top' not in mat_cache:
        mat_cache['island_top'] = emission_mat('island_top_baked', tex_dir / 'island_top.png')
        mat_cache['island_side'] = emission_mat('island_side_baked', tex_dir / 'island_side.png')
        mat_cache['keel'] = emission_mat('keel_baked', tex_dir / 'island_keel.png')
    mesh = o.data
    connector_marker = o.get('_digifarm_connector_slot')
    connector_polygons = {
        polygon.index for polygon in mesh.polygons
        if connector_marker is not None and polygon.material_index == connector_marker
    }
    mesh.materials.clear()
    mesh.materials.append(mat_cache['island_top'])
    mesh.materials.append(mat_cache['island_side'])
    mesh.materials.append(mat_cache['keel'])
    # The FBX ships its own UV layers; writes below must land on the single
    # layer the exporter reads, or new faces silently keep dead (0,0) UVs.
    while len(mesh.uv_layers) > 0:
        mesh.uv_layers.remove(mesh.uv_layers[0])
    mesh.uv_layers.new(name='UVMap')
    mw = o.matrix_world.copy()
    nor = mw.inverted().transposed().to_3x3()
    # Tight world bounds from real verts: bound_box corners are stale after
    # rotation/scale and would compress every UV range (keel tip must hit v=1).
    wpos = [mw @ v.co for v in o.data.vertices]
    minx, maxx = min(p.x for p in wpos), max(p.x for p in wpos)
    miny, maxy = min(p.y for p in wpos), max(p.y for p in wpos)
    minz, maxz = min(p.z for p in wpos), max(p.z for p in wpos)
    cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bm.normal_update()
    bm.faces.ensure_lookup_table()
    uv = bm.loops.layers.uv.active or bm.loops.layers.uv.new('UVMap')
    for f in bm.faces:
        n = (nor @ f.normal).normalized()
        world = [mw @ v.co for v in f.verts]
        # NOTE: the glTF exporter flips V (round-trips FBX UVs losslessly, so
        # Blender-authored values arrive inverted). Author 1-v so the FILE
        # holds: top full-range, side rim->0/foot->1, keel tip->1.
        is_connector = f.index in connector_polygons
        if is_connector:
            # Generated side connector: use the same cylindrical mapping as
            # authored cliffs so the alpha fade is height-driven and the
            # connector remains visible where the source wall was missing.
            f.material_index = 1
            for loop, w in zip(f.loops, world):
                ang = math.atan2(w.y - cy, w.x - cx)
                u = (ang + math.pi) / (2 * math.pi)
                v = 1 - (maxz - w.z) / max(maxz - minz, 1e-6)
                loop[uv].uv = (u, v)
        elif f.material_index == 997:
            # Flat foot cap: constant near-black (file v=1).
            f.material_index = 1
            for loop in f.loops:
                loop[uv].uv = (0.5, 0.0)
        elif n.z < -0.4:
            # Rare down-facing strays: rock fade, v by depth (tip -> black).
            f.material_index = 2
            for loop, w in zip(f.loops, world):
                ang = math.atan2(w.y - cy, w.x - cx)
                u = (ang + math.pi) / (2 * math.pi)
                v = (w.z - minz) / max(maxz - minz, 1e-6)
                loop[uv].uv = (u, v)
        elif n.z > 0.5:
            f.material_index = 0
            for loop, w in zip(f.loops, world):
                u = (w.x - minx) / max(maxx - minx, 1e-6)
                v = 1 - (w.y - miny) / max(maxy - miny, 1e-6)
                loop[uv].uv = (u, v)
        else:
            f.material_index = 1
            for loop, w in zip(f.loops, world):
                ang = math.atan2(w.y - cy, w.x - cx)
                u = (ang + math.pi) / (2 * math.pi)
                v = 1 - (maxz - w.z) / max(maxz - minz, 1e-6)
                loop[uv].uv = (u, v)
    bm.to_mesh(mesh)
    mesh.update()
    bm.free()
    if connector_marker is not None:
        try:
            del o['_digifarm_connector_slot']
        except KeyError:
            pass


def strip_doublesided(path, names):
    """Finalize glTF culling and texture alpha flags.

    Blender's emission-only node exports the image as ``emissiveTexture``;
    glTF alpha is read from ``baseColorTexture`` instead. Reuse the same PNG
    there and mark the cliff/keel materials as BLEND so Filament can fade the
    actual model to transparent instead of drawing a black slab.
    """
    import struct as _struct
    raw = bytearray(Path(path).read_bytes())
    json_len = _struct.unpack('<I', raw[12:16])[0]
    doc = json.loads(bytes(raw[20:20 + json_len]))
    for m in doc.get('materials', []):
        name = m.get('name')
        if name in names and 'doubleSided' in m:
            del m['doubleSided']
        if name in {'island_side_baked', 'keel_baked'}:
            tex = m.get('emissiveTexture')
            if tex is not None:
                pbr = m.setdefault('pbrMetallicRoughness', {})
                pbr['baseColorTexture'] = {'index': tex['index']}
                pbr['baseColorFactor'] = [1, 1, 1, 1]
            m['alphaMode'] = 'BLEND'
            m['doubleSided'] = True
    new_json = json.dumps(doc, separators=(',', ':')).encode()
    new_json += b' ' * (-len(new_json) % 4)
    off = 20 + json_len
    bin_len = _struct.unpack('<I', raw[off:off + 4])[0]
    bin_type = raw[off + 4:off + 8]
    bindata = bytes(raw[off + 8:off + 8 + bin_len])
    out = bytearray()
    out += raw[0:12]
    out += _struct.pack('<I', len(new_json)) + b'JSON' + new_json
    out += _struct.pack('<I', len(bindata)) + bin_type + bindata
    total = 12 + 8 + len(new_json) + 8 + len(bindata)
    # GLB header: magic(0) + version(4) + length(8). Patch LENGTH only.
    _struct.pack_into('<I', out, 8, total)
    Path(path).write_bytes(bytes(out))
    print('stripped doublesided:', sorted(names))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--source-root', required=True)
    p.add_argument('--output-dir', required=True)
    p.add_argument('--preview')
    p.add_argument('--backer-color', nargs=3, type=float, default=[.10, .28, .12],
        help='Backer-plate grout RGB (dark green; near-black for tron)')
    a = p.parse_args(sys.argv[sys.argv.index('--') + 1:])
    source = next(Path(a.source_root).rglob('farm_01.fbx'))
    out = Path(a.output_dir); out.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=str(source), use_image_search=True)
    # Snapshot BEFORE deleting parents; baking local transforms is insufficient.
    meshes = [(o, o.matrix_world.copy()) for o in bpy.context.scene.objects if o.type == 'MESH']
    # No Sea: open floating islands over the dark background instead of water.
    keep = {'Island', 'IlandColosseum.001', 'FarmColosseum_O.001', 'FarmColosseum_C'}
    canonical = Matrix.Scale(1.2, 4) @ Matrix.Rotation(-math.pi / 2, 4, 'Z')
    for o, world in meshes:
        o.parent = None
        o.matrix_world = canonical @ world
    for o in list(bpy.context.scene.objects):
        if o.type != 'MESH' or o.name not in keep:
            bpy.data.objects.remove(o, do_unlink=True)
    bpy.context.view_layer.update()
    island = bpy.data.objects['Island']
    top = max((island.matrix_world @ Vector(c)).z for c in island.bound_box)
    for o in bpy.context.scene.objects:
        o.location.z -= top
    # Open and closed colosseums are overlapping variants, not collision shells.
    # Place the closed variant to the left on a second copy of the original islet.
    closed = bpy.data.objects['FarmColosseum_C']
    closed.location.x -= .58
    islet = bpy.data.objects['IlandColosseum.001']
    extra = islet.copy(); extra.data = islet.data.copy(); bpy.context.collection.objects.link(extra)
    extra.location.x -= .58
    # Lift stadiums off the islet tops: bases were coplanar (~0.0001) and
    # flickered. +0.035 is invisible but kills the z-fight.
    for name in ('FarmColosseum_O.001', 'FarmColosseum_C'):
        o = bpy.data.objects.get(name)
        if o is not None:
            o.location.z += .035
            print('lifted', name)
    bpy.context.view_layer.update()
    # Island + islets: weld duplicate vertices, close the one authored side
    # opening by reusing its boundary loop, then remove degenerate faces.  No
    # global foot projection or duplicate grass backer is exported.
    mat_cache = {}
    done = set()
    for o in bpy.context.scene.objects:
        if o.type == 'MESH' and (o.name == 'Island' or o.name.startswith('IlandColosseum')):
            clean_mesh(o)
            close_top_side_gaps(o)
    for o in bpy.context.scene.objects:
        if o.type == 'MESH' and (o.name == 'Island' or o.name.startswith('IlandColosseum')):
            split_island(o, source.parent, mat_cache)
            clean_mesh(o)
            done.add(o.name)
    for o in bpy.context.scene.objects:
        if o.name in done:
            continue
        image_name = ('stadium_close.png' if o == closed else 'stadium.png') if 'Colosseum_' in o.name else 'farm_base_tex01.png'
        mat = emission_mat(o.name + '_baked_color', source.parent / image_name)
        o.data.materials.clear(); o.data.materials.append(mat)
    bpy.context.view_layer.update()
    bpy.ops.export_scene.gltf(filepath=str(out/'digi_farm_3d.glb'), export_format='GLB', export_animations=False, export_cameras=False, export_lights=False)
    strip_doublesided(out/'digi_farm_3d.glb', {'island_top_baked'})
    manifest = dict(schema=1, mapId='digi_farm_3d', mapVersion=1,
        asset='digifarm/3d/digi_farm_3d.glb', source=source.name,
        sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        coordinateSystem='canonical-y-up-ground-zero', groundHeight=0,
        playableBounds=dict(min=[-.68, -.43], max=[.68, .43]),
        safeSpawns=[[x,z] for z in [-.28,0,.28] for x in [-.48,-.16,.16,.48]],
        staticNodes=[o.name for o in bpy.context.scene.objects], facilityNodes=[],
        notes='Reference-style map: green tiled top + tan cliffs with image-alpha fade (split unlit materials), welded patchwork with a connected boundary-loop side closure, no detached skirt/cap or duplicate grass backer, open floating islands with no sea, lifted stadia (no z-fight), stadium variants separated; no auto-placed facilities.')
    (out/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    if a.preview:
        bpy.ops.object.camera_add(location=(0, -3.4, 2.8))
        cam = bpy.context.object
        cam.rotation_euler = (Vector((0,.22,-.05))-cam.location).to_track_quat('-Z','Y').to_euler()
        cam.data.type='ORTHO'; cam.data.ortho_scale=2.65
        scene=bpy.context.scene; scene.camera=cam
        scene.render.engine='CYCLES'; scene.cycles.samples=8
        scene.render.resolution_x=800; scene.render.resolution_y=700; scene.render.resolution_percentage=100
        scene.view_settings.view_transform='Standard'
        # Black void like the app background (no sea anymore).
        if scene.world is None:
            scene.world = bpy.data.worlds.new('PreviewVoid')
        scene.world.use_nodes = True
        scene.world.node_tree.nodes['Background'].inputs[0].default_value = (0, 0, 0, 1)
        scene.render.filepath=a.preview
        bpy.ops.render.render(write_still=True)

if __name__ == '__main__': main()
