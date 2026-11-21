# Imagram Editor

A lightweight, standalone Android photo editor — photo only.

Pick a photo from the gallery, crop it, then draw (brush, arrow, line),
add text, adjust it with real sliders (exposure, contrast, saturation,
sharpness, warmth), undo/redo, and save the result to the gallery.

## What it is

- **Photo only.** No video, no chat, no login, no stickers.
- **Clean build.** Written from scratch in Kotlin. It contains **no Telegram
  code** — the editor is built on three open-source libraries (see
  [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)):
  - [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor) 3.1.1 (MIT)
    — brush, arrow/line shapes, text, undo/redo.
  - [CanHub Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper)
    4.7.0 (Apache 2.0) — crop, via an embedded `CropImageView` in our
    own screen with our own CANCEL / DONE (since v0.3).
  - [GPUImage for Android](https://github.com/cats-oss/android-gpuimage)
    2.1.0 (Apache 2.0) — real adjust sliders (since v0.3).
- **No permissions.** Photos are picked with the system picker and saved
  with a MediaStore insert (scoped storage).

## Build

The project builds on GitHub Actions (`.github/workflows/build-android.yml`):
JDK 17, Android SDK, `gradle assembleDebug`. Every push to `main` uploads
the debug APK as a build artifact (`imagram-editor-v0.3-debug-apk`).

## Status

v0.3 — fixes the v0.2 bug (picking a photo forced the crop screen
first and its confirm/cancel never appeared under the NoActionBar
theme, so no edit options were reachable) and adds real GPUImage
adjust sliders (Exposure / Contrast / Saturation / Sharpness /
Warmth + Enhance AUTO) in place of the v0.2 filter presets. Picking
a photo now loads it directly into the editor with the full toolbar;
crop is a toolbar tool on our own screen with our own CANCEL / DONE.
No caption field, no SD/HD button, no send button, no stickers —
those are messaging parts. See [BUILD-NOTES.md](BUILD-NOTES.md) for
the verified library versions, the project structure, and the known
risks.

## License

Imagram Editor is licensed under the GNU General Public License v2.0
(the `LICENSE` file already in this repository). Third-party library
notices are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
This is not legal advice.
