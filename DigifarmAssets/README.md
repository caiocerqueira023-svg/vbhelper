# Digifarm 2.5D asset source

This directory contains the source ZIP archives supplied for the Digifarm 3D
environment. They are authoring inputs only. The Android app does not import
DAE, FBX or SMD at runtime.

The deterministic conversion entry point is
`tools/digifarm/build_assets.py`, executed by Blender 5.0 or a compatible
version. It removes authoring helpers and collision-only geometry, packs the
map texture into `digi_farm_3d.glb`, and writes `manifest.json` with the source
hash, coordinate convention, playable bounds and safe spawn points.

The current runtime scene is derived from `Mobile - Digimon Links - Maps -
Digi-Farm.zip`, specifically `farm_01.fbx`, plus the ten facility archives.
The default `Cube` proxy is not a visible facility and is intentionally
excluded. `FarmColosseum_C` is retained as collision provenance in the
manifest but is not exported as visible geometry. Facility DAE files are
flattened by `tools/digifarm/collada_to_obj.py` and composed with the map by
`tools/digifarm/compose_scene.py`; this is the offline fallback for the
supplied ASCII FBX files that Blender rejects.

Do not redistribute these source archives outside the project. Any derived
runtime asset must keep this provenance note and its SHA-256 in the manifest.
