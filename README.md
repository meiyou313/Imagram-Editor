# Imagram Editor

A lightweight, standalone Android photo editor — photo only.

Pick a photo from the gallery, crop it, then draw (brush, arrow, line),
add text, apply a filter, undo/redo, and save the result to the gallery.

## What it is

- **Photo only.** No video, no chat, no login, no stickers.
- **Clean build.** Written from scratch in Kotlin. It contains **no Telegram
  code** — the editor is built on two open-source libraries (see
  [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)):
  - [PhotoEditor](https://github.com/burhanrashid52/PhotoEditor) 3.1.1 (MIT)
    — brush, arrow/line shapes, text, filters, undo/redo.
  - [CanHub Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper)
    4.7.0 (Apache 2.0) — crop, rotate, aspect ratio.
- **No permissions.** Photos are picked with the system picker and saved
  with a MediaStore insert (scoped storage).

## Build

The project builds on GitHub Actions (`.github/workflows/build-android.yml`):
JDK 17, Android SDK, `gradle assembleDebug`. Every push to `main` uploads
the debug APK as a build artifact (`imagram-editor-v0.2-debug-apk`).

## Status

v0.2 — Telegram-style UI redesign (dark, photo fullscreen, icon pill
toolbars), built from the user's own Telegram screenshots and chosen by
the user from three researched options (see [UI-RESEARCH.md](UI-RESEARCH.md)).
No caption field, no SD/HD button, no send button, no stickers — those
are messaging parts. See [BUILD-NOTES.md](BUILD-NOTES.md) for the verified
library versions, the project structure, and the known risks.

## License

Imagram Editor is licensed under the GNU General Public License v2.0
(the `LICENSE` file already in this repository). Third-party library
notices are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
This is not legal advice.
