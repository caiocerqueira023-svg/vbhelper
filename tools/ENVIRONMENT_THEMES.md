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
Light domes combine energy detail with a restrained pale emission base to prevent
their interior geometry from appearing as black patches. No original
GLB, geometry, source archive, camera manifest, or sprite asset is rewritten.

Run the generator again after changing an environment GLB or the palette config.
`--check` rejects stale source hashes, changed default pixels, alpha/sampler drift,
missing material roles and incomplete four-theme mappings.

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
sampling equality, light Radar floors, distinct boundary colors, authored Helper
dome factors and smoothly bounded light emission. The generator verifies the
actual source/PNG bytes.

`EnvironmentThemeRuntimeTest` and the integrity-only
`EnvironmentThemeValidationActivity` exercise the real Compose/native hosts for
farm, Colosseum, Radar battle and Radar first-person. They check all four palettes,
retained viewport/load identity, paused/resumed updates and Colosseum retry/rebind,
and save native `TextureView` captures under the integrity app's external-files
`environment-theme-captures` directory. The original build-only delivery missed
the native mipmap-usage precondition. The follow-up reproduced it on-device, fixed
the usage flags and readiness gate, and passed all **five** environment/battle
theme cases plus `OfflineBattleRematchTest` on a Samsung SM-A346M running Android
15. Sixteen native environment captures were collected and representative dark,
light, farm, Colosseum, Radar and first-person views inspected. The actual battle
case verifies cold VB Lab startup and live recoloring without fighter reloads.
Tablet-specific visuals and performance profiling remain outside this evidence.
