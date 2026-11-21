# Imagram Editor v0.1 — Build Notes

Date: 2026-11-21. Clean build, **no Telegram code** — the project was
written from scratch in `~/workspace/imagram-editor/`, a new directory
separate from `~/workspace/telegram-photo-editor/Telegram/` (which was
not opened, copied, or modified for this build). The only pre-existing
Imagram-related files were the two reports in
`~/workspace/telegram-photo-editor/` (`PHASE1-REPORT.md`,
`PHASE2-REPORT.md`) and the empty GitHub repo
https://github.com/meiyou313/Imagram-Editor (README + .gitignore +
GPL-2.0 LICENSE only, no code).

## Verified library versions (checked 2026-11-21)

| Library | Version used | License | Source |
|---|---|---|---|
| PhotoEditor (`com.burhanrashid52:photoeditor`) | **3.1.1** | MIT | Maven Central + project README (badge `3.1.1`, API 21+). Latest release; arrow shape added in 3.0.0, `addText(..., position)` in 3.1.1. ~4,500 stars, Kotlin, Android-only. |
| CanHub Android-Image-Cropper (`com.vanniktech:android-image-cropper`) | **4.7.0** | Apache 2.0 | Maven Central (4.7.0, 28 Nov 2025) + CanHub README (`implementation("com.vanniktech:android-image-cropper:4.7.0")`). Active fork of the abandoned ArthurHub/uCrop line. No newer stable version found. |

No newer stable version of either library was found; 3.1.1 / 4.7.0 are
the pinned versions. API usage was checked against the PhotoEditor
README (Builder, `setBrushDrawingMode`, `ShapeBuilder` /
`ShapeType.Arrow|Brush|Line`, `setShape`, `addText(text=,
colorCodeTextView=)`, `setFilterEffect(PhotoFilter.*)`, `undo()` /
`redo()`, suspend `saveAsBitmap()`) and the CanHub README
(`CropImageContract`, `CropImageContractOptions(uri=,
cropImageOptions= CropImageOptions(guidelines=
CropImageView.Guidelines.ON))`, `result.isSuccessful` /
`result.uriContent` / `result.error`).

## Project structure

```
Imagram-Editor/                      (repo root = Gradle project root)
  settings.gradle                    include ':app'
  build.gradle                       AGP 8.5.2, Kotlin 1.9.24 (apply false)
  gradle.properties                  AndroidX, JVM args
  gradle/wrapper/gradle-wrapper.properties   Gradle 8.7
  .gitignore                         Android template
  README.md
  THIRD-PARTY-NOTICES.md             MIT (PhotoEditor) + Apache 2.0 (CanHub)
  BUILD-NOTES.md                     this file
  .github/workflows/build-android.yml
  app/
    build.gradle                     namespace com.imagram.editor,
                                     compileSdk/targetSdk 34, minSdk 24,
                                     versionCode 1 / versionName 0.1,
                                     viewBinding on
    proguard-rules.pro
    src/main/AndroidManifest.xml     MainActivity only, launcher, no permissions
    src/main/java/com/imagram/editor/MainActivity.kt
    src/main/res/layout/activity_main.xml
    src/main/res/values/{strings,themes,colors}.xml
    src/main/res/drawable/ic_launcher_{foreground,background}.xml
    src/main/res/mipmap-anydpi-v26/ic_launcher{,_round}.xml
```

No `LICENSE` file is included in this tree: the GitHub repo already
has the GPL-2.0 `LICENSE` from its creation, so the upload must not
overwrite it. App icon is a simple placeholder (crop corners + pencil
on teal) — not the Telegram logo.

## User flow (MainActivity, single activity)

1. **Pick** — system picker (`GetContent`, `image/*`). No storage permission.
2. **Crop** — picked Uri goes straight into CanHub `CropImageContract`
   (guidelines on). The Crop button re-crops the current photo.
3. **Edit** — `PhotoEditorView`: Brush / Arrow / Line (ShapeBuilder,
   size 24, opacity 100, colour red/white/yellow/black), Add Text
   (dialog → `addText`, draggable/scalable on canvas), Filter cycles
   NONE → BRIGHTNESS → SEPIA, Undo / Redo.
4. **Save** — `saveAsBitmap()` (suspend) → JPEG 90% → MediaStore insert
   into `Pictures/Imagram Editor` (`Imagram_<timestamp>.jpg`).
   No permissions beyond scoped storage.

## Build (GitHub Actions)

`.github/workflows/build-android.yml` — Minab-style, but no signing and
no release step: checkout → JDK 17 (Temurin) → Android SDK →
accept licences → Gradle 8.7 (`gradle/actions/setup-gradle@v3`) →
`gradle assembleDebug` → upload artifact
`imagram-editor-v0.1-debug-apk` (14-day retention). Triggers: push to
`main`, pull requests, manual dispatch. There is no `gradlew` script in
the tree yet; CI installs Gradle 8.7 via setup-gradle, and the wrapper
properties pin the same version for local use (add the wrapper script
from Android Studio later if wanted).

## What is ready for upload

The full tree above, ready for browser upload to
https://github.com/meiyou313/Imagram-Editor (do **not** `git push`
from this VM; do **not** create a release yet).

## What is NOT yet verified — do not claim it

- **No green build yet.** Nothing was compiled locally (no JDK/SDK
  installed in this VM by design). The first real check is the
  GitHub Actions run after upload.
- **No APK yet, no size measured, no test on the A54 yet.**

## Known risks for the first build (check the CI log first)

1. `setBrushColor()` is deprecated since PhotoEditor 1.5.1 (README
   still documents it; the changelog never records its removal).
   If CI reports it missing, replace it with `ShapeBuilder.withShapeColor()`.
2. CanHub marks `CropImageContract` deprecated in favour of embedding
   `CropImageView` in your own activity ("will be removed in future
   versions") — it is still the documented path in the 4.7.0 README
   and should compile; migrate to `CropImageView` in v0.2.
3. Long-press text editing (`OnPhotoEditorListener`) is deliberately
   NOT wired in v0.1: the listener's `onEditTextChangeListener`
   signature differs between the README (3 params) and newer samples
   (4 params, adds `typeFace`). Text can be added, dragged, scaled
   and deleted via its close icon; wire the listener in v0.2 once the
   exact 3.1.1 signature is confirmed from the published sources.
4. `saveAsBitmap()` is used instead of `saveAsFile()` so the JPEG
   encoding is done by the app itself; if its return type differs,
   the null-check in `saveImage()` is the first place to look.

## Next steps

1. Upload this tree to Imagram-Editor via the logged-in browser.
2. Watch the GitHub Actions run; fix the first compile error from
   the log (most likely one of the risks above).
3. Download the debug APK artifact, install on the Samsung A54
   (Android 13), test pick → crop → arrow/text → save, measure the
   real APK size.
4. Then iterate like Minab: user reports fixes one by one, new
   version per fix round.
