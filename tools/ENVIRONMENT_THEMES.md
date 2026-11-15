# Theme-aware 3D environments

The theme system reuses the shipped Digifarm, Colosseum and Radar geometry. Only
allowlisted environment material factors and texture bindings are changed; actor
faces/edges, attack images, contact shadows, transforms and visibility masks are
outside the controller's scope.

## Reproducible assets and provenance

```powershell
py -3 -B tools/build_environment_themes.py
py -3 -B tools/build_environment_themes.py --check
```

`tools/environment_theme_palettes.json` defines environment-specific roles for the
four saved theme keys. The generator reads these existing packaged sources:

- `app/src/main/assets/digifarm/3d/digi_farm_3d.glb`
- `app/src/main/assets/Arena/Colosseum/Colosseum.glb`
- `app/src/main/assets/Arena/Radar/radar_grid.glb`

Their embedded PNGs are the provenance of the generated rasters. The generator
records the original GLB SHA-256, material name, theme, texture channel and original
sampler in `app/src/main/assets/environment_themes/manifest.json`. PNG filenames
are their own SHA-256 hashes, allowing shared texture payloads to be deduplicated.
The initial pack contains **43 PNGs serving 56 channel/theme bindings**.

VB Helper texture payloads are byte-identical copies of the originals. Alternative
palettes preserve image dimensions and every alpha byte. Digifarm recoloring is
restricted to the original `wireframe_tiles()` cell mask, leaving the grass and stone
accents untouched. Cliff/Colosseum structural textures retain their source luminance
detail; cube panels and dome energy receive curated theme colors. No original GLB,
geometry, source archive, camera manifest, or sprite asset is rewritten.

## Island surface and cliff depth

`tools/digifarm/build_reference_textures.py` builds the island's **top** from the
original Links farm ground as a **tile mosaic**: the floor is cut into large 16px pieces (a
2x2 block of the authored cells, so the source's tile seams stay visible inside each piece),
and the pieces are laid out in contiguous regions like the reference floor - broad patches
of one tile, occasional warm path tiles, long runs of the same piece, mirrored/flipped for
variety.
The mosaic is then pulled onto the Tamers Lab accent's low-saturation hue (other palettes
derive their own at build time), keeping most of the tile variation. On the exposed cells (`wireframe_tiles()`) the
texture paints a near-black void carrying **one continuous bright square grid**. The *same*
`wireframe_tiles()` mask drives the texture and the per-palette recolor, which keeps the
farm **monochromatic**: the turf is derived at build time from the theme's `energy` accent
(low saturation, dark value) and the grid uses that same accent at full strength - the
very color the voxel blocks already use for their edges - so grass, patches and cubes are
one hue per theme. The authored cliff texture is unchanged, except that its strata
highlight rows are drawn in the authored accent; the generator lifts those rows to the
theme's `energy` accent for the other palettes (they are the only strongly blue pixels, so
they can be masked without touching the rim ramp). The rebuild reproduces the packaged
`island_side.png` bytes verbatim.

The farm's emissive materials keep the dark-theme baseline in the light palettes too (0.94x
instead of the 0.75x used by white arena surfaces), and the Digifarm viewport passes a
higher light-theme pulse ceiling (1.62) into `EnvironmentThemeController`, so the floor and
cube accents bloom in every theme. The trim on the baseline means only bright lines reach
the bloom range, which keeps the floor's color saturated instead of washing it out.

`build_assets.py` splits the cliff into two materials, because it is a one-sided skirt
that fades to transparent at its foot: the upper 48% (fully opaque in the authored art)
uses `island_side_rim_baked`, the same cliff image declared as an alpha-masked
(`alphaMode: MASK`) material. Rendering in the opaque pass makes those bands **write
depth**, so the near side of the island occludes the inside of the far side that used to
show through it; the fading foot keeps the blended material and its transparency. The
masked bands use `alphaCutoff = 0.25`, not 0.5: the authored strata lines are drawn at
alpha 110/150 and a 0.5 cutoff silently dropped the 110 rows, which made the island's
side lines read as broken aliasing. `validate_asset.py` pins the cutoff for that reason.

Measured on the shipped geometry (offline ray/triangle check at five camera yaws, layers
more than 2cm apart): 100% of the double-layer cliff pixels showed through before, the
masked upper bands hide ~70% of them, and the rest sit in the heavily faded foot where
both layers are already nearly transparent. A previous inset "interior lining" was
removed: it outlived the fading foot and read as a dark shadow hanging under the island.

Reproduce the asset (the generator reproduces the packaged cliff and voxel textures byte for
byte, so "the sides stay unchanged" is verifiable, not a promise):

```powershell
# once: unpack the reference archive next to the source FBX
#   <reference zip> -> build/digifarm_src/Digi-Farm/farm_01.fbx + farm_base_tex01.png
py -3 -B tools/digifarm/build_reference_textures.py --dst build/digifarm_src/Digi-Farm `
    --source-archive "<reference zip>"
blender -b --python tools/digifarm/build_assets.py -- `
    --source-root build/digifarm_src --output-dir app/src/main/assets/digifarm/3d
py -3 -B tools/digifarm/validate_asset.py app/src/main/assets/digifarm/3d/digi_farm_3d.glb
py -3 -B tools/build_environment_themes.py
```

Run the generator again after changing an environment GLB or the palette config.
`--check` rejects stale source hashes, changed default pixels, alpha/sampler drift,
missing material roles and incomplete four-theme mappings.

## Light-theme contract

The two light palettes (`vb_arena`, `digimon_net`) are calibrated for **value**, not
just hue. A light UI sits next to a bright backdrop, so tinting every mid-tone with the
theme hue is what flattened these maps: against a solid `#F1F4F2` skybox a near-white
floor and near-white structural ramps leave no silhouette and no local contrast, and
the whole arena reads as a green or blue fog.

- Structure ramps keep a neutral ink range and reach true black
  (`structureDark` `#121514` / `#13151A`, `structureLight` `#C6CCCA` / `#C7CBD4`), so
  stone reads as stone. Chroma is reserved for the accent stops.
- Digital surfaces (`panel`) are near black; only their `grid` and `energy` stops carry
  the theme color, so blocks read as black props with a neon edge, matching the dark
  themes instead of washing out.
- The radar keeps a light floor as a *surface* — clearly darker than the backdrop, with
  much darker grid lines — so the chart keeps its structure. `battle_boundary` has its
  own palette stop and must stay readable over that floor.
- The farm's island side (`cliff`) flip: the authored ramp is bright at the rim and dark
  at the base, which on a light theme put its darkest pixels against the backdrop.
  Light palettes flip the ramp so the island's edge stays dark and its base lit.
- The arena's enclosing sphere stays black with the theme's `energy` peaks ("black and
  green" / "black and blue"). The radar's sphere is not rendered at all: seen from
  inside it only left a hard mosaic band across the horizon and hid the themed
  backdrop, so `RadarSkyDome` geometry is masked out in both radar scenes and the
  skybox carries the whole backdrop.

## Runtime ownership

`EnvironmentThemeController` is owned by one native scene/Engine. It decodes the
selected PNGs on a worker, rejects superseded generations, and binds textures only
on the renderer's main/owning thread after gltfio finishes resource loading. Each
new selection replaces the current managed texture set; old textures are retired
after rebinding, and scene clear/release drops native references and cancels work.
The initial Helper load retains gltfio's original texture objects.

Each `AndroidView.update` sends `LocalAppTheme` to its existing native view.
Palettes do not enter actor model keys or trigger engine/model/camera/session
recreation. Animated emission uses the applied variant's RGB/gain with the existing
pulse envelope. Light-theme HDR peaks are compressed smoothly; Helper/Lab envelopes
remain unchanged. Radar's previously black battle backdrop follows light themes
while keeping its original black Helper backdrop.

Managed textures explicitly use `Texture.Usage.DEFAULT | GEN_MIPMAPPABLE`, required
by Filament 1.76.1 before generating their mip chain. Cosmetic theme preparation
does not gate combat readiness: a failed palette update must never leave a battle
behind its loading overlay.

The farm also deduplicates in-flight requests for the same asset. Battle retries
clear both theme bindings and animation material references before model teardown.

## Verification scope

`EnvironmentThemeCatalogTest` checks complete environment-only mappings, alpha and
sampling equality, light Radar floors that stay lighter than their grid, boundary
contrast, neutral light ramps with true-black anchors and accent-only chroma, authored
Helper dome factors and smoothly bounded light emission. The generator verifies the
actual source/PNG bytes.

`EnvironmentThemeRuntimeTest` and the integrity-only
`EnvironmentThemeValidationActivity` exercise the real Compose/native hosts for
farm, Colosseum, Radar battle and Radar first-person. They check all four palettes,
retained viewport/load identity, paused/resumed updates and Colosseum retry/rebind.
The original build-only delivery missed the native mipmap-usage precondition. The
follow-up reproduced it on-device, fixed the usage flags and readiness gate, and
passed all **five** environment/battle theme cases plus `OfflineBattleRematchTest` on
a Samsung SM-A346M running Android 15. The actual battle case verifies cold VB Lab
startup and live recoloring without fighter reloads.

For light-theme art direction the activity is also launched directly
(`am start -n <pkg>/com.tamerslab.EnvironmentThemeValidationActivity --es environment
radar --es theme vb_arena`) and captured with `adb screencap`, because
`TextureView.getBitmap` returns black for these surfaces on the test device. Every
light-theme surface (Colosseum, Radar battle, Radar first-person, Digifarm) was
inspected this way; the runtime captures under the activity's
`environment-theme-captures` directory remain the automated check of theme delivery.
Tablet-specific visuals and performance profiling remain outside this evidence.
