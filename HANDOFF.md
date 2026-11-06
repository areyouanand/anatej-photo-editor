# Anatej Photo Editor — Handoff

**Version:** 0.4.0 (versionCode 4)
**Last updated:** 2026-11-06, after interactive crop + straighten, adaptive preview, 31 multi-effect looks, dedicated 3D LUT library, EXIF preservation, share sheet, and tone sliders
**Location:** `/mnt/1tb/AI stuff/FUJI Camera`
**Platform:** Android 10+ (`minSdk 29`), built against API 36
**Stack:** Kotlin 2.2.21, Jetpack Compose, AGP 8.13.2, Gradle 8.14.4, JDK 17,
TensorFlow Lite 2.17, ML Kit selfie segmentation
**Artifact:** `app/build/outputs/apk/debug/app-debug.apk` (~152 MB debug)
**SHA-256:** `19c41a98264a20f572039792aff40f1e04805fa0e3c22c083e507a5fca38dd83`
**GitHub:** https://github.com/areyouanand/anatej-photo-editor
**State:** latest 0.4.0 APK (SHA above) installed on the CPH2841 and verified:
1. **Interactive gallery-style crop** — draggable L-corner + edge handles, inside-box pan, 7 aspect locks (`FREE`/`ORIG`/`1:1`/`4:5`/`3:2`/`16:9`/`9:16`), a ±45° **Straighten** slider with automatic inscribed zoom, Rotate 90°, Flip, and Reset. Leaving the CROP tab shows only the cropped region fitted to the preview (non-destructive).
2. **Adaptive preview frame** — the preview card now sizes itself to the photo's aspect ratio (landscape, portrait, square, or the cropped ratio) instead of a fixed box with dead space; the CROP tab drops the card corner radius so corner handles are never clipped.
3. **Multi-layered looks + dedicated 3D LUT library** — 31 stocks across `Cinema`, `Fujifilm`, `Kodak`, `Basic`, `Nikon & Leica`, `Hasselblad`, each carrying tone, split-tone, grain, bloom, halation, vignette and micro-sharpness; `3D LUT` is now its own series tab with 45 bundled/on-device `.cube` LUTs and multi-select import (`17³`/`33³`/`65³`).
4. **Tone, metadata and sharing** — Highlights / Shadows / Whites / Blacks recovery sliders, EXIF preservation (GPS never copied) with a Settings toggle, a `↗` share button using `FileProvider`, and double-tap-to-reset on every slider.
Emulator closed. Test media cleaned up.

---

## 0. Handoff snapshot

This build covers the v0.4 scope on top of v0.3:

- Settings + About, with the theme selector moved off Home/Editor (gear is Home-only).
- Undo/redo (now including crop + straighten + Square Pic), offline library, real `.cube` LUTs,
  bundled offline segmentation.
- **Natural portrait relight** using MiDaS depth normals, feathered subject blending and a
  normals-derived shadow.
- **Instant LUT previews for tone/look changes** because the LUT is baked once and the
  live grade runs through the GPU colour matrix.
- **Zoom/pan** with pinch, one-finger pan and −/×/+ controls; **hold to compare**.

### v0.4 change log

| Area | What changed |
| --- | --- |
| Crop model | `CropRect(left, top, right, bottom, aspectLock, straighten)` replaces `cropAspect`/`cropZoom`/`cropCenterX`/`cropCenterY`. `isCropped` is true for any inset **or** any `|straighten| > 0.05°`. |
| Crop interaction | Corner/edge hit-testing with a 34 dp radius; aspect-locked drags anchor on the opposite corner/edge; dragging inside the box moves it; outside the CROP tab the same gesture pans the fitted cropped view. |
| Straighten | `-45°..+45°`, `straightenZoom()` computes the inscribed scale (transposition-symmetric, capped at 2.6×). Applied with `withTransform(rotate, scale)` in the preview and a real rotation matrix in `cropBitmap` for export. |
| Adaptive preview | `PreviewArea` uses `BoxWithConstraints` to size the frame to the photo/crop/square aspect, clamped to a 140/160 dp minimum, so no black letterboxing remains. |
| Looks | `Adjustments`/`Look` gained `sharpness`, `bloom`, `halation`, `shadowTint`, `highlightTint`; 6 stock series + 31 looks; preview renders split-tone via one extra matrix and halation through a warm matrix on the blurred overlay; export bakes halation in `applyEnhancements`. |
| 3D LUT | `LookSeries.Lut` is a dedicated section. `Lut3D` accepts up to `65³`. `importLuts(List<Uri>)` imports multiple files, `refreshSavedLuts()` scans `files/luts/` **and** `Download/LUTs/`, and each LUT appears as a one-tap card. |
| Favourites | `LookSeries.Favourites` is the first series chip. Every look card and every 3D LUT card carries a `☆` badge (bottom-right) that pins it; look ids and LUT paths are stored as string sets in `SettingsStore` and survive relaunches. The Favourites tab groups *Favourite looks* and *Favourite LUTs* with counts and an empty-state hint. |
| Look toggle | Tapping the already-applied look disables it and restores the look that was active before it (`lastLookId`), so one tap previews and the second tap reverts. The panel prints **TAP AGAIN TO DISABLE** while a look is applied. |
| LUT previews | `scheduleLutThumbs()` renders each saved `.cube` over a 110 px base of the current frame and publishes thumbnails progressively, so LUT cards look like stock cards. `CubeLutParser` was rewritten to parse straight into a preallocated `FloatArray` (no boxing / per-line token lists), which cut per-file parse time enough for the 45-file library to fill in a few seconds. |
| Tone | New `highlights`, `shadows`, `whites`, `blacks` sliders (luminance-weighted recovery in export, slope/offset approximation in the preview matrix). |
| EXIF | On import, 14 safe tags are captured (dates, make/model, aperture, shutter, ISO, focal length, white balance, flash, lens). Written to the exported JPEG and the share cache; `TAG_ORIENTATION` is normalised. GPS is never read. |
| Share | `FileProvider` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml` → `cache/shared/`), `share()` renders → caches → launches the system chooser. |
| Sliders | Double-tap the label or track resets to the declared default (`ValueSlider(defaultValue = ...)`), with haptics. |

Verification highlights: 18 unit tests green (`.cube` parsing incl. 65³, trilinear sampling,
1D/incomplete rejection, crop/straighten geometry, tone-slider matrix behaviour, LUT series
separation, Square Pic labels); `assembleDebug` and `lintDebug` green. On-device: a 960×540
landscape photo renders in a 16:9 preview card, the CROP tab shows unclipped corner handles
and the Straighten slider at +0.0°, the `3D LUT · CUBE` tab reports **45 LUTs available**, and
a near-square crop shows `CROP · 1013 × 1013` with the cropped viewport on return to LOOKS.

---

### Earlier fixes still in force (v0.2 → v0.3)

| Previous limitation / issue | Resolution |
| --- | --- |
| Relight looked like a cut-out mask | Depth normals are gradient-clamped, double-blurred and blended through a 16 px-feathered matte with 20% spill. Specular and luminance-detail terms were removed because they caused hair halos. |
| Light looked like a hazy veil above ~10% | Replaced the smoothstep wash with a shared directional-softbox model (`RelightMap.sampleLight`): azimuth/elevation key, `1/(1+(d/r)^k)` falloff sharpened by softness, skin wrap diffuse, opposing fill, skin-only specular, and shadow from `(1−wrap)`. Depth field is now cover-fit (not stretched), single-blurred, with luminance blend at 18% for micro-relief. Filmic intensity `pow(i,1.6)·1.2` with a soft clip, so 10% is near-imperceptible and 100% rolls off instead of clipping. Spill is a user slider (default 12%) instead of a fixed 20%. |
| Portrait-light dials were awkward | Replaced the three round dials with compact horizontal sliders for intensity, radius and warmth. |
| Sliders changed while scrolling | The shared slider gesture now waits for horizontal touch slop; vertical drags are left unconsumed so the panel scrolls. Verified in Signature and M3. |
| No preview zoom/pan | Pinch-zoom 1–6×, one-finger pan, and a floating − / × / + control; tapping the multiplier resets. |
| No quick before/after | Press-and-hold the preview to show the original; release restores the grade. The LIVE GRADE chip still toggles a persistent compare. |
| App was labelled “Anatej” | Launcher label is now **Anatej Photo Editor**. |
| ML Kit merged `INTERNET` / `ACCESS_NETWORK_STATE` into the APK | Both are removed with `tools:node="remove"`; the merged manifest now contains only local-media permissions. |

Verification highlights: depth inference logged at **171 ms** on the emulator; normals are
built from `MiDaS` after segmentation finishes. Zoom controls reached 2.3×, one-finger pan
shifted the frame, the zoom multiplier reset to 1.0×, and a 2.5 s hold showed the ORIGINAL
chip before snapping back. The natural-relight pass removed the hair-edge arcs and matte
cut-out visible in the first depth build. A vertical swipe starting on a slider scrolled
the Adjust panel while Exposure stayed at +0.00 EV; a horizontal drag then moved it to
+1.44 EV. The LUT exposure test still moves from +1.79 EV to −0.06 EV instantly. No crashes
were logged during the v0.3 pass.


---

## 1. What this is

An **offline** portrait and film editor for Android. The manifest has **no INTERNET
permission**. Import uses SAF, the Android photo picker, or an optional in-app MediaStore
library. Export goes to `Pictures/Anatej`.

Two design languages live side by side:

- **Signature** — darkroom paper, copper accent, film perforations, serif display type,
  monospace instrument labels.
- **Material 3 Expressive** — large shapes, tonal surfaces, Material controls.

The theme switch lives in **Settings**, which is reachable **only from the home screen**.
The editor header is reserved for back, undo/redo, and save.

---

## 2. Implemented in v0.2

### Core editing
- Photo import via SAF, system photo picker, and in-app library.
- `ACTION_VIEW` / `image/*` so Anatej appears in "Open with" from file managers/gallery.
- Histogram scan (96 px proxy): median, shadows, dynamic range, p05/p95, skin-weighted
  subject lock.
- Subject-aware Auto-rescue (dark frames lifted; high-key frames protected).
- Tone controls: exposure, contrast, saturation, warmth, fade, vignette, grain.
- 21 looks: Basic + Fujifilm / Nikon / Hasselblad-inspired stocks, live thumbnails,
  strength.
- Full undo/redo history (40 steps, slider drags coalesced).
- JPEG export at 95% up to 3000 px, matched to the preview.

### Portrait light (v0.2 normals)
- Draggable key light; intensity / radius / warmth knobs; auto-place from scan.
- **Learned-depth relight:** `DepthEstimator` runs bundled MiDaS-small (256×256 inverse
  depth, ImageNet normalization, XNNPACK CPU). `RelightMap.buildFromDepth()` stretches the
  depth to the frame aspect, converts it to normals, blends 22% luminance detail, and
  stores the subject mask in alpha (384 px on the long edge). Fallback is the luminance
  normal map if the model cannot load.
- Depth gradients are clamped and the depth map is double-blurred; the key light uses
  directional diffuse shading plus a normals-derived multiply shadow. Specular was removed
  because it produced hair-edge halos. The subject matte is box-feathered (16 px) and the
  light has 20% spill outside it so the effect blends into the frame.
- Preview draws Screen light and Multiply shadow layers. Export applies the same equations
  per pixel.
- Subject masking is toggleable in Settings and in the Light panel; ML Kit selfie
  segmentation model is bundled in the APK and runs offline.
- Light controls are horizontal sliders, not rotary dials. All slider gestures are
  horizontal-only so panels remain scrollable from a slider.
- Preview gestures: pinch always zooms; one-finger drag moves the light in the Light tab
  (when enabled) or pans otherwise. A long press without movement shows the original.

### 3D LUT
- Strict 3D `.cube` parser (`LUT_3D_SIZE`, RGB triples; rejects 1D/incomplete files).
  Accepts sizes `2..65`, so `17³`, `33³` and `65³` files all load.
- Trilinear sampling.
- **Dedicated `3D LUT · CUBE` series tab** in LOOKS (separate from the stock series). The
  import block no longer appears under Cinema/Fujifilm/Kodak/Basic/Nikon/Hasselblad.
- `IMPORT .CUBE` uses `OpenMultipleDocuments`; `importLuts()` writes every selected file to
  `files/luts/<name>.cube` and makes the last one active.
- `refreshSavedLuts()` merges `files/luts/` with `Download/LUTs/`, de-duplicates by display
  name, and exposes each file as a one-tap pill.
- **45 open-source 33³ LUTs ship on the device** in `/sdcard/Download/LUTs/` and are seeded
  into `files/luts/` (spectral film stocks – Portra 160/400/800, Gold 200, Ektar 100,
  Kodachrome 64, Vision3 50D/200T/250D/500T 2383/2393, Fuji Pro 400H, Superia X-Tra,
  Natura 1600, Provia/Velvia, Tri-X/HP5; slay cinematic LUTs; Fujifilm Provia conversion
  LUTs; Leica/Fuji standard LUTs).
- Strength slider, persisted to `files/luts/active.cube` and restored on launch.
- **Latency fix:** the LUT is baked into a 1280 px preview base **once per LUT/strength
  change**. Tone/look changes remain live GPU ColorMatrix operations. Export order is
  LUT → grade → relight → vignette/grain, matching the preview.

### Crop, geometry and preview
- `CropRect` is the single crop truth (normalised 0..1 plus `aspectLock` and `straighten`).
- Interactive handles: 4 L-corners (accent), 4 edge bars (white), rule-of-thirds grid,
  dimmed outside strips, inside-box drag to move, 34 dp hit radius.
- Aspect locks: `FREE`, `ORIG`, `1:1`, `4:5`, `3:2`, `16:9`, `9:16`. Locked drags keep the
  target ratio in normalised space (converted through the photo aspect).
- Straighten `-45°..+45°` with `straightenZoom()` inscribed scaling in preview and export.
- Rotate 90°, Flip Horizontal, Reset; all crop/straighten/square changes participate in
  undo/redo via `EditSnapshot`.
- Outside the CROP tab the preview shows only the cropped region, fitted to the frame; the
  CROP tab reveals the full frame with the overlay.
- `PreviewArea` is `BoxWithConstraints`-driven, so the preview card matches the photo,
  crop or square aspect instead of a fixed rectangle.

### Settings + About
- Appearance: Signature / M3 Expressive, applied immediately and persisted.
- Scan on import (on/off; manual rescan in Auto panel).
- Subject masking (on/off for subsequent imports).
- Export quality `70–100%` (default 95%).
- **Preserve camera EXIF** (default on) — 14 safe tags copied onto export/share; GPS is
  never read, orientation is normalised, software tag is set.
- About: version/build, processing pipeline, privacy, export behavior, credits,
  `@areyouanand`, GitHub link, restore defaults.
- Settings gear appears only on Home.

### In-app library
- Local MediaStore grid, 3 columns, thumbnails, dimensions, refresh.
- Runtime permission flow for `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`;
  Android 14+ limited/selected-photo access and a "manage in system settings" link.
- Verified with full access on the test phone: **1,200 frames** rendered.

---

## 3. Verified test log (OnePlus CPH2841, Android 16 / API 37)

| Area | Result |
| --- | --- |
| Build | `assembleDebug` green; unit tests green; lint **0 errors** / 25 warnings |
| App label | `aapt2` reports `application-label:'Anatej Photo Editor'` |
| Offline manifest | Merged manifest has **no** `INTERNET` or `ACCESS_NETWORK_STATE`; only `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`, legacy storage |
| Depth model | MiDaS-small loaded from assets; 256×256 inference in 171 ms; normals built from depth |
| Natural relight | Feathered matte, clamped depth gradients, no hair-edge halos at 1× |
| Zoom / pan | −/×/+ controls reached 2.3×, pan shifted the frame, multiplier reset to 1.0× |
| Hold compare | 2.5 s press showed ORIGINAL; release restored the grade |
| Light controls | Compact horizontal sliders; horizontal drag works |
| Scroll safety | Vertical swipe on a slider scrolls the panel and leaves the value unchanged |
| Theme switch | Signature ↔ M3, persisted in `SharedPreferences`; gear only on home |
| Settings/About | Screens, switches, version 0.3.0, restore defaults |
| Import | SAF (real filename), system picker, `ACTION_VIEW` (filename fallback) |
| Library | Full permission → 1,200-frame grid with thumbnails (v0.2 phone pass) |
| Scan | Dark portrait: median 7%, shadows 78%, subject lock 82%, EV +1.79 |
| Auto-rescue | Dark frame lifted; high-key frame held near 0 EV |
| Undo/redo | Auto and LUT changes reverted/reapplied from header controls |
| Segmentation | `SUBJECT MASK · SURFACE` ready offline; mask toggle changes coverage |
| Normals light | Directional face shading and shadow visible; preview matches export |
| LUT | Imported `teal-orange.cube` (17³), preview, strength, persistence, undo, export |
| LUT latency | Tone slider changes update immediately with LUT active |
| Export | `Pictures/Anatej/*.jpg`, 1200×1800 in tests, matches preview |
| Crashes | None in logcat after the test pass |
| Device coverage | v0.3 UI pass on API 36 emulator and final 0.3.0 install + zoom/pan/hold/export smoke test on CPH2841/Android 16 |
| Softbox relight | Presets (Natural/Rembrandt/Butterfly/Loop/Side/Top) and AZ/EL direction pad change the shading; 91% intensity stays readable under soft clip; export matches preview pixel-for-pixel in structure |
| Light UX | VIEW MASK orange tint, interactive DRAG LIGHT / LIGHT OFF / VIEW MASK / LIGHT⇄PAN chips, labeled SUBJECT MASKING + Background spill sliders, scrollable Light panel (360 dp), slider haptic detent ticks |
| Export quality + About | Settings stores `exportQuality` (70–100%, default 95%); `saveToGallery` compresses with it. About shows `DEVELOPER: @areyouanand` and a tappable `github.com/areyouanand/anatej-photo-editor` link. **User-verified manually on device.** |
| Auto-rescue strength + looks | `autoRescueStrength` (30–200%) scales the whole `autoEnhance` recipe (exposure/contrast/saturation + relight). Looks rebuilt: Fujifilm (Provia/Velvia/Chrome/NEG/Astia/Acros/Reala), Nikon (+Flat), Hasselblad (+X2D) each with distinct grain/vignette/sharpness/bloom recipes; preview shows bloom/sharpen via a blurred overlay (`RelightMap.blurredCopy`), export bakes unsharp-mask + highlight bloom in `PhotoRenderer.applyEnhancements`. On-device: slider visible at 100% default, Fuji series renders, Velvia preview and 1920×1080 export both show the stock curve. |
| Lighting style switch | `PortraitLight.style` (Softbox / Flashlight) branches falloff, wrap, fill and soft-clip in the shared `sampleLight`; toggle lives in the Advanced Light section, default Flashlight. |
| Square Pic crop | New CROP tab with Off / 1:1 / 4:5 / 9:16 / 16:9 chips, drag-to-reposition, 1–3× crop zoom, and `cropBitmap` in export (largest fitting rect, then zoom). Crop grid now uses the same fit math as export. |
| Square Pic (blurred bars) | Separate SQUARE tab: `padToSquare` fits the full frame into a square over a blurred stretched copy; preview shows the blurred bars. Verified as a 1920×1920 export. |
| App-to-app sharing | Manifest SEND/SEND_MULTIPLE image filters; `MainActivity.sharedImageUri()` routes EXTRA_STREAM into the editor; `loadPhoto` decodes content URIs. |
| Simple brighten | Light panel opens on one **Brighten face** slider (`EditorViewModel.brightenFace` auto-places on the subject and enables the key) + Auto-brighten; presets/direction pad/sliders sit behind **Advanced tools**. Display scale is 0–100, but the internal model maps it to `(intensity/100)·30%` of full relight — 100 behaves like the old 30% mark. |
| Interactive crop | Draggable L-corner + edge handles with a 34 dp hit radius, inside-box pan, dimmed outer strips, rule-of-thirds grid. Verified on-device with a 960×540 frame; a 1:1 drag reported `CROP · 1013 × 1013`. |
| Cropped viewport | Switching from CROP to LOOKS shows only the crop, fitted to the frame; returning to CROP restores the full frame plus overlay. |
| Crop aspects | `FREE`/`ORIG`/`1:1`/`4:5`/`3:2`/`16:9`/`9:16` chips; locked drags preserve the ratio. |
| Straighten | ±45° slider (`+0.0°` default, double-tap resets); inscribed zoom keeps corners filled in both preview and export. |
| Rotate / Flip / Reset | `↻ 90°`, `⇄ Flip`, `Reset` row in the CROP panel, all undoable. |
| Adaptive preview | 960×540 source renders in a 16:9 card with no dead space; portrait, square and cropped ratios each size the card accordingly. CROP tab removes the card corner radius so handles stay visible. |
| Multi-effect looks | 31 looks across 6 series. `KODAK 2383`, `800T NIGHT`, `BLOCKBUSTER`, `PRO-MIST 1/4`, `BLEACH BYPASS`, `INDIE 35MM`, `SIN CITY NOIR`, `PORTRA 400`, `GOLD 200`, `EKTAR 100`, `TRI-X 400`, `ILFORD HP5`, `NOSTALGIC NEG`, `ETERNA CINE`, `LEICA CHROME`, `MONOCHROM`, `X2D STUDIO` etc. Each carries grain/bloom/halation/split-tone/vignette/matte; the panel prints the active recipe tags. |
| 3D LUT section | `3D LUT · CUBE` is its own series chip; on-device it reported **45 LUTs available** from `Download/LUTs` + `files/luts`. Multi-select import, thumbnail cards, strength slider default 100%. |
| Favourites | Starred `HNCS PORTRAIT` and confirmed it appears under **FAVOURITE LOOKS** with a count; the star toggles from the card badge without applying the look. LUT favourites use the same badge. |
| Look toggle | Tapped `HNCS PORTRAIT` (applied, title + recipe tags + `TAP AGAIN TO DISABLE` shown), tapped again → reverted to `Original` without leaving the section. |
| LUT thumbnails | The LUT grid renders distinct graded thumbnails per `.cube` (aerosol/aerocolor, Agfa Vista, Ektachrome, …) instead of placeholder `CUBE` tiles. |
| Tone sliders | Highlights / Shadows / Whites / Blacks added to ADJUST alongside Film halation and the two split-tone sliders. |
| EXIF | Safe tags round-trip to export; GPS never read; toggle in Settings (default on). |
| Share | `↗` button next to SAVE renders to `cache/shared/Anatej_Share.jpg`, exposes it through `${applicationId}.fileprovider`, and opens the system chooser with `FLAG_GRANT_READ_URI_PERMISSION`. |
| Slider reset | Double-tap any slider label or track to return to its declared default, with haptics. |
| Unit tests | 19 tests green: `.cube` parsing (incl. `65³`), trilinear endpoints, 1D/incomplete rejection, crop/straighten state, `straightenZoom` symmetry + ceiling, tone-slider matrix offsets, LUT series + Favourites separation, favourite look ids, Square Pic labels. |
| Emulator | Stopped after the pass at the user's request; no emulator process/window remains |

Test photos/LUT were pushed temporarily and deleted. The app was clean-installed after
testing, so library permission is back to the system prompt and preferences are default.

---

## 4. Project map

```
app/src/main/java/com/anatej/photo/
├── MainActivity.kt              edge-to-edge + ACTION_VIEW image intent
├── AnatejApp.kt                 navigation, source chooser, persisted settings
├── data/
│   ├── SettingsStore.kt         SharedPreferences
│   └── PhotoLibrary.kt          MediaStore query + permission helpers
├── model/
│   ├── EditorModels.kt          adjustments, crop, light, looks, LUT entries, UI state
│   └── Lut3D.kt                 trilinear sampling (2..65)
├── engine/
│   ├── PhotoAnalyzer.kt         histogram + skin/saliency scan
│   ├── PhotoRenderer.kt         CPU export; LUT/grade/normals/vignette/grain
│   ├── CubeLutParser.kt         .cube parser
│   ├── RelightMap.kt            normal-map estimation + relight layers
│   └── SubjectSegmenter.kt      bundled ML Kit selfie segmentation
└── ui/
    ├── theme/Design.kt          Signature ↔ M3 tokens/type/shapes
    ├── components/              controls, aperture, backdrop, source sheet
    ├── home/HomeScreen.kt
    ├── editor/                  EditorScreen, EditorCanvas, EditorViewModel
    ├── library/LibraryScreen.kt
    └── settings/SettingsScreen.kt
```

`README.md` contains the public-facing feature list, screenshots, permission table and
build instructions. `docs/screenshots/` has current captures. `THIRD_PARTY_NOTICES.md` and
`app/src/main/assets/MIDAS_LICENSE.txt` cover the bundled MiDaS model (MIT).

---

## 5. Build / test

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests cover `.cube` parsing (including `65³` tables), trilinear sampling, 1D rejection,
incomplete-LUT rejection, crop/straighten geometry and `straightenZoom`, the tone-slider
preview matrix, LUT/Favourites series separation and Square Pic labels — **19 tests**.

Debug APK size is dominated by ML Kit native libraries for all ABIs. For distribution,
next step is an App Bundle or ABI splits (`arm64-v8a` covers the test phone).

---

## 6. Known constraints / recommended next work

1. **Depth is 256×256 relative depth.** MiDaS-small is fast and small; the 384 px normal
   map gets fine detail from the luminance blend. A higher-resolution depth model can be
   swapped behind `DepthEstimator` without touching the UI.
2. **LUT strength still rebakes the 1280 px base** (~90 ms) when the strength slider moves.
   Tone/look sliders are immediate. A GPU AGSL sampler would remove even that.
3. **Straighten is preview-cheap but export-costly** — the export path rotates a full
   3000 px bitmap. Fine for single exports; a tile-based pass would help batch export.
4. RAW decode, batch export, and session persistence across process death are still open.
5. Library requires a runtime permission; SAF/system picker remain permission-free.
6. Release packaging: shrink the 151 MB debug APK with ABI splits, enable R8 and a signed
   release config. The 33.5 MB depth model dominates.
7. Optional polish: custom user presets ("My Looks"), film border/date stamp, and an HSL
   panel next to the split-tone controls.

---

## 7. Notes for the next session

- Theme and scan/mask preferences live in `SettingsStore`; the app reads them on cold
  composition (`AnatejApp`), while navigation uses `rememberSaveable`.
- All edit history is in `EditorViewModel`; snapshots intentionally omit scan/mask data.
- LUT preview base is `EditorUiState.lutPreview`; when non-null, `EditorCanvas` draws it
  and still applies the live ColorMatrix. Keep this invariant if changing the pipeline.
- Relight layers are `relightNormals`, `relightOverlay`, `relightShadow`. The alpha of the
  normal map is the subject mask. `depthMap` is kept in the view-model and reused when the
  mask arrives; depth inference and segmentation run in parallel.
- The depth model asset must remain uncompressed (`noCompress += "tflite"`) so it can be
  memory-mapped through `AssetFileDescriptor`.
- Export must remain in the order: LUT → grade → relight → vignette/grain.
- Crop truth is `EditorUiState.crop` (`CropRect`). Any new geometry feature must update both
  `EditorCanvas` (`fitRect` / `currentViewRect`) and `EditorViewModel.cropBitmap`.
- `PreviewArea` sizes the frame from the photo/crop/square aspect. If you add a new frame
  mode, extend the `targetAspect` `when` block, not the canvas.
- Saved LUTs are merged from `files/luts/` and `Download/LUTs/` by
  `EditorViewModel.refreshSavedLuts()`; the active LUT is also mirrored to
  `files/luts/active.cube` so it survives a cold start. Thumbnails come from
  `scheduleLutThumbs()` (110 px, progressive) and are invalidated when the source changes.
- Favourites are two string sets in `SettingsStore` (`favourite_looks`, `favourite_luts`).
  Look ids must exist in `Looks.byId`; LUT ids are absolute file paths, so a moved file simply
  drops out of the Favourites tab.
- `setLook` is a toggle: applying a different look records the previous id in `lastLookId`,
  and tapping the applied look restores it. Keep that behaviour when touching the panel.
- EXIF is captured once at import (`sourceExif`) and written at export/share time. Never add
  GPS tags to `SAFE_EXIF_TAGS`.
- The share flow depends on the `${applicationId}.fileprovider` authority and
  `res/xml/file_paths.xml` exposing `cache/shared/`; keep the cache file name stable or
  update `saveToShareCache`.
- The repository is now a git repo with `origin` at
  `https://github.com/areyouanand/anatej-photo-editor`.

### Resume checklist

```bash
cd "/mnt/1tb/AI stuff/FUJI Camera"
export JAVA_HOME=/home/anand/.local/opt/jdk-17
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Suggested order for the next milestone:

1. Release packaging: App Bundle + `arm64-v8a` split, R8, signed config.
2. Session persistence (store the working bitmap in app cache + restore URI).
3. HSL panel and user-saved custom presets.
4. Optional: higher-resolution depth model or a face-mesh refinement behind
   `DepthEstimator`.
5. Optional: GPU AGSL LUT sampler to remove the LUT-strength re-bake.

Avoid using the phone's private gallery for docs screenshots; the current `docs/` set only
uses the public test portrait and UI chrome.

---

## 8. Current device state

- Final 0.4.0 APK installed on the CPH2841 (`3B164J000QB00000`) with SHA above.
  On-device checks passed: landscape 960×540 source in a 16:9 adaptive preview card,
  unclipped crop corner handles with the Straighten slider at `+0.0°`, the dedicated
  `3D LUT · CUBE` tab reporting **45 LUTs available** with graded thumbnails, a look
  applying and disabling on a second tap, and a starred look appearing under Favourites.
  No `FATAL EXCEPTION` for `com.anatej.photo` in logcat.
- **45 `.cube` LUTs are stored at `/sdcard/Download/LUTs/`** on the phone and mirrored into
  the app's private `files/luts/` library. They are open-source; see
  `THIRD_PARTY_NOTICES.md` for attribution (spectral film LUTs MIT, slay CC BY 4.0,
  FujifilmCameraProfiles and V-Log-Alchemy free-use).
- The temporary test image and its test export were deleted from the phone after the pass.
- Test media was written to app-private emulator/phone storage only; the repo contains none.
- Library permission on the phone was reset by the clean install preceding v0.2; prompt
  will appear on first library use.
- No internet permission in the manifest.
