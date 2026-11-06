# Third-party notices

Anatej Photo Editor bundles the following runtime components and model assets.

## MiDaS-small depth model

- Asset: `app/src/main/assets/midas_small_256_fp16.tflite`
- Source: [intel-isl/MiDaS](https://github.com/isl-org/MiDaS) — MiDaS v2.1 small
- Converted for LiteRT by [litert-community/MiDaS-small](https://huggingface.co/litert-community/MiDaS-small)
- License: MIT (see `app/src/main/assets/MIDAS_LICENSE.txt`)
- Copyright (c) 2019 Intel ISL (Intel Intelligent Systems Lab)

## ML Kit Selfie Segmentation

- Artifact: `com.google.mlkit:segmentation-selfie`
- Bundled model: `selfiesegmentation_mlkit-256x256-2021_01_19-v1215.f16.tflite`
- Provided by Google under the [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).

## TensorFlow Lite

- Artifact: `org.tensorflow:tensorflow-lite`
- License: Apache License 2.0

## AndroidX / Jetpack Compose / Kotlin

- Licenses: Apache License 2.0
- Projects: AndroidX, Jetpack Compose, Kotlin, Kotlin Coroutines.

## Bundled / downloaded 3D LUTs

The 45 `.cube` LUT files placed in `Download/LUTs/` on the device (and mirrored into the
app's private `files/luts/` library) come from open-source collections. They are user data,
not APK assets.

| Collection | Source | License |
| --- | --- | --- |
| Spectral film-stock LUTs (Portra, Gold, Ektar, Kodachrome, Vision3, Pro 400H, Superia, Natura, Provia, Velvia, Tri-X, HP5, …) | [jeremieLouvaert/ComfyUI-Darkroom](https://github.com/jeremieLouvaert/ComfyUI-Darkroom) → `data/spectral_luts/`, built on [JanLohse/spectral_film_lut](https://github.com/JanLohse/spectral_film_lut) | MIT |
| `slay-01 … slay-05` cinematic LUTs | [slaycamera/luts](https://github.com/slaycamera/luts) | CC BY 4.0 — credit **slay, slaycamera.com** |
| Fujifilm Provia conversion LUTs (Classic Neg, Nostalgic Neg, Bleach Bypass) | [abpy/FujifilmCameraProfiles](https://github.com/abpy/FujifilmCameraProfiles) | Free to use with attribution |
| Leica / Fuji standard LUTs | [shenmintao/V-Log-Alchemy](https://github.com/shenmintao/V-Log-Alchemy) | Free to use with attribution |

If you redistribute the LUT files with a build, keep this attribution and the upstream
licenses with them.
