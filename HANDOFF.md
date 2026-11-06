# Anatej Photo Editor — Handoff

**Version:** 0.3.0 (versionCode 3)
**Last updated:** 2026-11-06, after Auto Rescue strength + authentic camera looks
**Location:** `/mnt/1tb/AI stuff/FUJI Camera`
**Platform:** Android 10+ (`minSdk 29`), built against API 36
**Stack:** Kotlin 2.2.21, Jetpack Compose, AGP 8.13.2, Gradle 8.14.4, JDK 17,
TensorFlow Lite 2.17, ML Kit selfie segmentation
**Artifact:** `app/build/outputs/apk/debug/app-debug.apk` (~152 MB debug)
**SHA-256:** `0c2a3ef5f912f685c10de025da4023fbeb61bc0cdaf5280c09bf737a22f1929d`
**GitHub:** https://github.com/areyouanand/anatej-photo-editor
**State:** latest 0.3.0 APK (SHA above) **installed on the CPH2841** and smoke-tested on
device: 0–100 gentle-max Brighten slider, Export quality setting (70–100%), About credits
(@areyouanand + GitHub link), Auto-rescue strength slider (30–200%), and rebuilt looks
(Fuji/Nikon/Hasselblad with grain/vignette/sharpness/bloom). Velvia preview + exported
JPEG verified with per-stock contrast/saturation and bloom. The user verified Settings/About
manually. No app crash in logcat. The emulator is closed. Test media was removed.

---

## 0. Handoff snapshot

This build covers the requested v0.3 scope:

- Settings + About, with the theme selector moved off Home/Editor (gear is Home-only).
- Undo/redo, offline library, real `.cube` LUTs, bundled offline segmentation.
- **Natural portrait relight** using MiDaS depth normals, feathered subject blending and a
  normals-derived shadow.
- **Instant LUT previews for tone/look changes** because the LUT is baked once and the
  live grade runs through the GPU colour matrix.
- **Zoom/pan** with pinch, one-finger pan and −/×/+ controls; **hold to compare**.

Changes since the v0.2 handoff:

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
- Trilinear sampling.
- Import/remove from Looks, strength slider, persisted to
  `files/luts/active.cube` and restored on launch.
- **Latency fix:** the LUT is baked into a 1280 px preview base **once per LUT/strength
  change**. Tone/look changes remain live GPU ColorMatrix operations. Export order is
  LUT → grade → relight → vignette/grain, matching the preview.

### Settings + About
- Appearance: Signature / M3 Expressive, applied immediately and persisted.
- Scan on import (on/off; manual rescan in Auto panel).
- Subject masking (on/off for subsequent imports).
- About: version/build, processing pipeline, privacy, export behavior, credits, restore
  defaults.
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
| Simple brighten | Light panel opens on one **Brighten face** slider (`EditorViewModel.brightenFace` auto-places on the subject and enables the key) + Auto-brighten; presets/direction pad/sliders sit behind **Advanced tools**. Display scale is 0–100, but the internal model maps it to `(intensity/100)·30%` of full relight — 100 behaves like the old 30% mark. |
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
│   ├── EditorModels.kt          adjustments, light, looks, analysis, UI state
│   └── Lut3D.kt                 trilinear sampling
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

Unit tests cover `.cube` parsing, trilinear sampling, 1D rejection and incomplete-LUT
rejection.

Debug APK size is dominated by ML Kit native libraries for all ABIs. For distribution,
next step is an App Bundle or ABI splits (`arm64-v8a` covers the test phone).

---

## 6. Known constraints / recommended next work

1. **Depth is 256×256 relative depth.** MiDaS-small is fast and small; the 384 px normal
   map gets fine detail from the luminance blend. A higher-resolution depth model can be
   swapped behind `DepthEstimator` without touching the UI.
2. **LUT strength still rebakes the 1280 px base** (~90 ms) when the strength slider moves.
   Tone/look sliders are immediate. A GPU AGSL sampler would remove even that.
3. No crop/rotate, RAW decode, EXIF preservation, batch export, or session persistence
   across process death.
4. Library requires a runtime permission; SAF/system picker remain permission-free.
5. Release packaging: shrink the 151 MB debug APK with ABI splits, enable R8 and a signed
   release config. The 33.5 MB depth model dominates.
6. Optional polish: custom crop tool, split-tone/HSL, LUT gallery with multiple saved
   LUTs, and a before/after hold gesture (compare chip exists).

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
- Export must remain in the order: LUT → grade → normals light/shadow → vignette/grain.
- The app is not a git repository yet; `local.properties` is ignored.

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
3. Crop/rotate and EXIF preservation.
4. Optional: higher-resolution depth model or a face-mesh refinement behind
   `DepthEstimator`.
5. Optional: GPU AGSL LUT sampler to remove the LUT-strength re-bake, and multiple saved
   LUT slots.

Avoid using the phone's private gallery for docs screenshots; the current `docs/` set only
uses the public test portrait and UI chrome.

---

## 8. Current device state

- Final 0.3.0 APK installed on the CPH2841 (`3B164J000QB00000`) with SHA above.
  On-device checks passed: launcher label “Anatej Photo Editor”, no `INTERNET` or
  `ACCESS_NETWORK_STATE` in `dumpsys package`, zoom reached 2.3×, one-finger pan moved the
  frame, a 2.5 s hold showed ORIGINAL, and SAVE wrote a 1920×1080 JPEG to
  `Pictures/Anatej` matching the preview (with the new softbox light applied). The new
  Light panel opened with presets/AZ·EL pad, VIEW MASK tinted the subject orange, and
  the PAN MODE toggle switched the gesture from light placement to pan.
  No `FATAL EXCEPTION` for `com.anatej.photo` in logcat.
- The temporary test image and its test export were deleted from the phone after the pass.
- Test media was written to app-private emulator storage only; the repo contains none.
- Library permission on the phone was reset by the clean install preceding v0.2; prompt
  will appear on first library use.
- No internet permission in the manifest.
