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

1. ~~`setBrushColor()` is deprecated since PhotoEditor 1.5.1~~ —
   CONFIRMED REMOVED in 3.1.1: there is no `setBrushColor()` method at
   all (see the v0.1 fix section below). The README still documents
   it — do not trust the README for this API.
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

## v0.1 fix — 3 compile errors (2026-11-21, after first CI run)

First GitHub Actions build (run 44932092663, commit f252890) failed in
`:app:compileDebugKotlin` with exactly 3 errors, all in MainActivity.kt.
The real API was checked against the library source itself — tag
`v.3.1.1`, commit `41d27636ab56e8185c2339869f865b8134ae0e82`
(github.com/burhanrashid52/PhotoEditor) — not the README, which was
wrong about `setBrushColor`.

Real API found in 3.1.1:

- `ShapeType` is a `sealed interface`
  (`photoeditor/.../shape/ShapeType.kt`): `Brush`, `Oval`, `Rectangle`
  and `Line` are `object`s, but `Arrow` is a **class** —
  `class Arrow(val pointerLocation: ArrowPointerLocation =
  ArrowPointerLocation.START) : ShapeType`. It must be instantiated:
  `ShapeType.Arrow()`. (`ArrowPointerLocation` is
  `enum class { START, END, BOTH }`.)
- There is **no** `setBrushColor()` method in 3.1.1. Colour is a
  deprecated Kotlin `var` property on the interface —
  `@set:Deprecated("use setShape of a ShapeBuilder") var brushColor: Int`
  (`PhotoEditor.kt`, ~line 149) — implemented in `PhotoEditorImpl.kt`
  as get/set on `drawingView.currentShapeBuilder`
  (`withShapeColor(color)`). From Kotlin the property syntax
  `photoEditor.brushColor = color` would work, but the documented
  non-deprecated path is `ShapeBuilder.withShapeColor(@ColorInt color)`
  (`ShapeBuilder.kt`), and `setShape()` (`PhotoEditorImpl.kt:278`)
  **replaces** `drawingView.currentShapeBuilder` wholesale — so the
  colour must be part of the builder passed to `setShape()`.

Errors and fix (MainActivity.kt only):

1. Line 128:48 — `ShapeType.Arrow` used as a value, but `Arrow` is a
   class. Fixed: `applyShape(ShapeType.Arrow())`.
2. Line 141:21 — `photoEditor.setBrushColor(brushColor)` unresolved.
   Fixed: removed; `applyShape()` now builds the shape with
   `.withShapeColor(brushColor)` before `setShape()`.
3. Line 147:21 — `photoEditor.setBrushColor(color)` unresolved (private
   `setBrushColor()`). Fixed: it now stores the colour in the
   `brushColor` field and, if a tool is active, re-applies that tool
   (`selectTool(currentTool)`) so the new colour takes effect
   immediately; the colour is also baked into every new shape via
   `applyShape()`. Colour changes therefore work whether the user
   picks the colour before or after choosing Brush/Arrow/Line.

Local verification after the fix: braces 52/52, parens 129/129;
`photoEditor.setBrushColor` no longer appears anywhere (the only
`setBrushColor` refs left are our own private function and its four
button calls); all `R.string.*` used are defined; every `binding.*`
id exists in `activity_main.xml`. No green build claimed yet — the
fix must still pass the GitHub Actions run after re-upload.

Files changed in this fix: `MainActivity.kt` and `BUILD-NOTES.md`
(this section). Ready for browser re-upload to Imagram-Editor
(commit message suggestion: "Fix v0.1 compile errors: ShapeType.Arrow()
and withShapeColor").

## v0.2 — Telegram-style UI redesign (2026-11-22)

The user tested v0.1 on the A54 and called the UI (three rows of big
green pill text buttons on a white screen) the ugliest they had ever
seen. Three real editor UIs were researched on GitHub with screenshots
(see `UI-RESEARCH.md` + `ui-options/`); the user chose **Option 1,
Telegram style**, from their own Telegram screenshots, and explicitly
excluded the caption field and SD/HD — messaging parts.

### What the new UI looks like

- **Dark, photo fullscreen.** Black background; `PhotoEditorView`
  fills the whole screen. No white page, no text buttons.
- **Top bar** (only when a photo is loaded): back arrow (closes an
  open panel, otherwise picks another photo), undo, redo, and
  "Clear All" (only while the draw panel is open).
- **Main toolbar** (bottom): a rounded dark pill with 4 icon buttons
  — crop, brush, text, adjust — plus a round teal **save** circle to
  its right, where Telegram has the blue send button.
- **Draw panel** (opens from the brush icon): pill with a rainbow
  colour-wheel button (opens a "More colours" dialog) + brush / arrow
  / line icons; a row of 8 colour dots below it (selected dot gets a
  white ring); bottom row ✕ / DRAW / TEXT / ✓, like the user's
  screenshot. TEXT opens the add-text dialog. No STICKER tab.
- **Adjust panel** (opens from the adjust icon): a horizontal strip of
  filter-preset chips + CANCEL / current filter / DONE at the bottom,
  like the user's screenshot.
- **Empty state:** dim photo icon + "Pick a photo to start editing"
  + a teal "Pick photo" pill, on black.

### Honest mapping notes (what Telegram has that the library does not)

- **Adjust sliders are NOT faked.** Telegram's Enhance / Exposure /
  Contrast are continuous sliders; PhotoEditor 3.1.1 has no slider
  API at all — `setFilterEffect(PhotoFilter)` applies one fixed
  preset. The full enum was verified from the library source at tag
  `v.3.1.1`
  (`photoeditor/.../PhotoFilter.kt`): NONE, AUTO_FIX, BLACK_WHITE,
  BRIGHTNESS, CONTRAST, CROSS_PROCESS, DOCUMENTARY, DUE_TONE,
  FILL_LIGHT, FISH_EYE, FLIP_VERTICAL, FLIP_HORIZONTAL, GRAIN,
  GRAY_SCALE, LOMISH, NEGATIVE, POSTERIZE, ROTATE, SATURATE, SEPIA,
  SHARPEN, TEMPERATURE, TINT, VIGNETTE. The panel offers 10 of them
  as chips (None, Auto Fix, Brightness, Contrast, Sepia, B&W,
  Saturate, Warm, Vignette, Gray), applied live on tap; CANCEL
  restores the filter that was active before opening the panel.
- **Clear All** uses `photoEditor.clearAllViews()`, which in 3.1.1
  calls `mBoxHelper.clearAllViews(drawingView)` — it clears brush
  strokes and added text views (verified in `PhotoEditorImpl.kt`
  lines 382–386). It also resets the filter to NONE.
- **Icons are hand-written vectors** (Material-style paths) in
  `res/drawable/` — 13 icons + colour wheel (six rainbow arcs +
  red centre dot), dots, pill/chip backgrounds. They were NOT
  rendered or previewed locally; check them on the A54.
- No new dependency was added (Material + AppCompat were already
  there). No Telegram code is used — the look is imitated, the code
  is all PhotoEditor 3.1.1 + CanHub 4.7.0 + our own UI.

### Files changed in v0.2

- `app/build.gradle` — versionCode 2, versionName 0.2.
- `app/src/main/java/com/imagram/editor/MainActivity.kt` — full
  rewrite (panels, dots, chips, more-colours dialog, same
  pick → crop → edit → save flow, same verified 3.1.1 API usage).
- `app/src/main/res/layout/activity_main.xml` — full rewrite.
- `app/src/main/res/values/{strings,colors,themes}.xml` — dark
  palette, new strings, black window background.
- `app/src/main/res/color/tool_icon_tint.xml` — NEW (selected white /
  unselected grey icon tint).
- `app/src/main/res/drawable/` — NEW: `ic_back`, `ic_undo`,
  `ic_redo`, `ic_crop`, `ic_brush`, `ic_text`, `ic_tune`,
  `ic_check`, `ic_close`, `ic_arrow_ne`, `ic_line`, `ic_save`,
  `ic_image`, `ic_color_wheel`, `dot`, `dot_ring`, `pill_bg`,
  `pick_pill`, `chip_bg`, `chip_bg_selected`, `save_circle`.
- `.github/workflows/build-android.yml` — artifact renamed to
  `imagram-editor-v0.2-debug-apk`.
- `README.md`, `UI-RESEARCH.md` (choice note), `BUILD-NOTES.md`
  (this section).

### Local verification (no build yet — same as v0.1)

- All XML files parse. Every `binding.*` id exists in
  `activity_main.xml` (31 ids); every `R.string.*` / `R.drawable.*` /
  `R.color.*` used in code and layout is defined;
  `photoEditor.setBrushColor` appears nowhere (only our own private
  `setBrushColor()` helper, as in v0.1). Kotlin braces 88/88,
  parens 252/252.
- **Not verified:** the build itself (no JDK/SDK here — the GitHub
  Actions run is the first real check), how the hand-written icons
  actually look on the phone, and the visual layout on the A54.
  The old v0.1 text-button UI is fully replaced; nothing of it
  remains except the identical pick → crop → edit → save flow.

## v0.3 — fix the v0.2 "no edit options after picking" bug + real adjust sliders (2026-11-22)

The user tested v0.2 on the A54 and reported a very big bug: after
picking a photo from the gallery, NO edit options are available — it
seems only crop is possible, with no confirm or cancel. They asked
for Telegram's bottom options (crop / brush / text / adjust) to be
available immediately after picking, and for professional sliders in
v0.3.

### Bug root cause (found, not guessed)

Two causes stacked:

1. **Our own flow (certain, from our code).** `pickImageLauncher`
   called `startCrop(uri)` immediately — crop was FORCED as the first
   step. `cropImageLauncher` only called `loadImage()` (which finally
   shows `topBar` + `bottomContainer`) on a successful crop; on
   cancel with no error it did nothing and the user stayed on the
   empty state. So the toolbar only appeared after a successful
   crop, and the user experienced the CanHub crop screen as the
   whole app.
2. **CanHub's CropImageActivity controls (documented cause).** The
   library's own wiki ("Activity Visual Customization") states the
   activity "is derived from `AppCompatActivity` which requires that
   its theme to derive from `Theme.AppCompat`. If your main theme is
   one of `*.NoActionBar` sub-theme's you must override
   `CropImageActivity` theme in the manifest with theme that includes
   action bar." Issue #142 (ArthurHub): "No toolbar at
   CropImageActivity… It's because of your theme, if you are using
   NoActionBar." The Crop confirm control is an ActionBar menu item
   ("Crop" menu item, text overridable via `crop_image_menu_crop`
   string or `setCropMenuCropButtonTitle()`; icon colour via
   `setActivityMenuIconColor()`), and cancel is the toolbar back
   arrow. Our app uses `Theme.Material3.DayNight.NoActionBar`
   (`themes.xml`) and never declared `CropImageActivity` in the
   manifest, so its ActionBar — hosting Crop confirm and
   back/cancel — never appears. The user saw only the crop view with
   no confirm/cancel. This matches the reported symptom exactly.

### The fix

- **Pick loads directly into the editor.** `pickImageLauncher` now
  calls `loadImage(uri)`: the photo is decoded (downscaled to max
  2048 px on the long side, EXIF rotation applied via
  `android.media.ExifInterface`, API 24+ = our minSdk, no new
  dependency) on a background thread, then shown with the full
  toolbar (crop / brush / text / adjust / save) immediately. No
  forced crop first.
- **Crop is a toolbar tool, on our own screen.** `CropImageView` is
  embedded in a full-screen `cropContainer` in `activity_main.xml`
  with our own CANCEL / DONE buttons — always visible, no ActionBar
  needed. This also closes the old risk item 2 below (CanHub marks
  `CropImageContract` deprecated in favour of embedding
  `CropImageView`; the migration planned for v0.2 is done here).
  DONE crops the image **as currently displayed** (adjustments baked
  in); drawings/text were placed on the old photo, so they are
  cleared and adjustments reset to neutral for the new base.
  CANCEL changes nothing. System back also leaves the crop screen
  first, then closes a panel, otherwise finishes the app. The user
  is never left in a screen with no way out.
- `CropImageContract` / `CropImageContractOptions` /
  `CropImageOptions` are gone entirely; `currentImageUri` is gone
  (save / draw / text / adjust now guard on `baseBitmap != null`).

### Real sliders — GPUImage for Android 2.1.0 (Apache 2.0)

- **Version verified:** latest stable on Maven Central is **2.1.0**
  (`jp.co.cyberagent.android:gpuimage:2.1.0`, released 2020-09-21;
  confirmed 22 Nov 2026 via search.maven.org /
  central.sonatype.com; repo now cats-oss/android-gpuimage).
  No newer stable exists.
- **API verified from the actual 2.1.0 AAR** (downloaded from
  repo1.maven.org, `classes.jar` inspected — not just docs):
  `GPUImage(Context)`, `setImage(Bitmap)`, `setFilter(GPUImageFilter)`,
  `getBitmapWithFilterApplied()` and
  `getBitmapWithFilterApplied(Bitmap)`; `GPUImageFilterGroup(List<GPUImageFilter>)`;
  `GPUImageExposureFilter(float)`, `GPUImageContrastFilter(float)`,
  `GPUImageSaturationFilter(float)`, `GPUImageSharpenFilter(float)`,
  `GPUImageWhiteBalanceFilter(float temperature, float tint)`.
  Filter ranges (Android port mirrors the iOS docs): Exposure
  −10..+10 (default 0), Contrast 0..4 (default 1), Saturation
  default 1, Sharpen −4..+4 (default 0), WhiteBalance temperature
  4000–7000 K (default 5000), tint −200..200 (default 0).
- **Sliders** (Telegram adjust panel, without fake controls):
  Exposure −2.0..+2.0, Contrast 0.0..2.0, Saturation 0.0..2.0,
  Sharpness 0.0..2.0, Warmth 4000–7000 K — each a real Android
  SeekBar driving a `GPUImageFilterGroup` of the five filters above,
  live preview while dragging (60 ms debounce, renders serialised
  on one GPU thread via `Dispatchers.Default.limitedParallelism(1)`,
  a newer render cancels an older one via a sequence token).
  CANCEL reverts to the values before the panel opened, DONE
  confirms. The summary line shows the first non-neutral slider
  (e.g. "Exposure +0.40").
- **Enhance AUTO — honest note.** GPUImage has no single Enhance
  filter, so AUTO applies a tuned combination, exactly:
  Exposure **+0.10**, Contrast **1.18**, Saturation **1.20**,
  Sharpness **0.40**, Warmth **5200 K**. The sliders show those
  values after tapping AUTO, so the user can see and adjust them.
- **Data flow (verified in code, not assumed).** Adjustments always
  start from `baseBitmap` (the picked/cropped photo), never
  compounded on a previous render; neutral values short-circuit to
  showing `baseBitmap` itself with no GPU work. Drawings and text
  stay on top via PhotoEditor (`PhotoEditorView.source` is the
  ImageView that shows the adjusted preview); saving still goes
  through `photoEditor.saveAsBitmap()`, which captures the source
  ImageView plus all overlay views. `clearAll()` now resets the
  sliders to neutral as well as clearing drawings/text.
- **CropImageView API verified from the actual 4.7.0 AAR** in the
  same way: `setImageBitmap(Bitmap)`, `getCroppedImage()`,
  `setGuidelines(Guidelines)` / `Guidelines.ON` all exist.
- The v0.2 filter-preset chips are gone (`PhotoFilter`,
  `setFilterEffect`, `filter_*` strings all removed). PhotoEditor
  3.1.1 is still used for brush / arrow / line / text / undo / redo
  only.

### Files changed in v0.3

- `app/build.gradle` — versionCode 3, versionName 0.3; added
  `jp.co.cyberagent.android:gpuimage:2.1.0`.
- `app/src/main/java/com/imagram/editor/MainActivity.kt` — full
  rewrite of the pick/crop/adjust flow (see above); draw panel,
  colour dots, text dialog and save path unchanged in behaviour.
- `app/src/main/res/layout/activity_main.xml` — adjust panel rebuilt
  as Enhance AUTO + 5 sliders (ids: `btnEnhanceAuto`,
  `seekExposure`/`tvExposureValue`, `seekContrast`/`tvContrastValue`,
  `seekSaturation`/`tvSaturationValue`,
  `seekSharpness`/`tvSharpnessValue`, `seekWarmth`/`tvWarmthValue`);
  new full-screen `cropContainer` with `cropImageView` +
  `btnCropCancel` / `btnCropDone`.
- `app/src/main/res/values/strings.xml` — filter strings removed,
  adjust strings added (`enhance`, `enhance_auto`,
  `adjust_exposure/contrast/saturation/sharpness/warmth`,
  `adjust_current`, `load_failed`, `adjust_failed`).
- `.github/workflows/build-android.yml` — artifact renamed to
  `imagram-editor-v0.3-debug-apk`.
- `README.md`, `THIRD-PARTY-NOTICES.md` (GPUImage entry),
  `BUILD-NOTES.md` (this section).

### Local verification (no build yet — same as v0.1/v0.2)

- All XML files parse. Every `binding.*` id exists in
  `activity_main.xml` (44 real ids; `root` and the
  `ActivityMainBinding` class name are the only regex false
  positives). Every `R.string.*` / `R.drawable.*` / `R.color.*`
  used in code and layout is defined — including
  `@color/tool_icon_tint`, which lives in
  `res/color/tool_icon_tint.xml` (a selector), not in
  `values/colors.xml`. `photoEditor.setBrushColor` appears nowhere
  (only our own private `setBrushColor()` helper, as in v0.1/v0.2).
  Kotlin braces 138/138, parens 374/374. Removed APIs confirmed
  gone: `PhotoFilter`, `CropImageContract`, `currentImageUri`,
  `startCrop`, `setFilterEffect`, `filter_*` strings.
- **Not verified:** the build itself (no JDK/SDK here — the GitHub
  Actions run is the first real check), the GPUImage render path at
  runtime (the `setImage` → `setFilter` → `getBitmapWithFilterApplied`
  pattern is the library's documented one and the method signatures
  were verified in the published AAR, but first-run behaviour on a
  real phone — e.g. PixelBuffer sizing after `setImage` on a new
  bitmap — is untested), and how the sliders and the crop screen
  actually look and feel on the A54. If the CI build fails, the most
  likely spots are the GPUImage call chain or the `croppedImage`
  property — both were verified against the published AARs, so a
  failure there would mean a Kotlin-vs-Java signature surprise, and
  the log will say exactly which line.
