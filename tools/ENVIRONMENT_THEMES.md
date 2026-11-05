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
The initial pack contains **43 PNGs serving 48 channel/theme bindings**.

VB Helper texture payloads are byte-identical copies of the originals. Alternative
palettes preserve image dimensions and every alpha byte. Digifarm recoloring is
restricted to the original `wireframe_tiles()` cell mask, leaving surrounding jade
turf and stone accents untouched. Cliff/Colosseum structural textures retain their
source luminance detail; cube panels and dome energy receive curated theme colors.
No original GLB, geometry, source archive, camera manifest, or sprite asset is
rewritten.

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
