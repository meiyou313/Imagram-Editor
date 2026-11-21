# Imagram Editor — UI research for v0.2 (2026-11-22)

## Why this exists

v0.1 shipped with three rows of big green pill **text** buttons over a mostly
empty white screen. The user tested it on their Samsung A54 and said it is
the ugliest UI they have ever seen. This file records the GitHub research
done before the redesign, so the user can pick a direction from real,
well-liked editors instead of from a guess.

Screenshots in `ui-options/` are the projects' own images (README / official
blog / guide screenshots of the real apps), not mockups drawn for this
project.

## The dominant pattern (found in every well-liked editor)

Photo fills the screen. Tools are small **icons** in one row at the bottom
(never text buttons). A slim top bar holds undo / redo / save. Colour is
small dots, shown only while a drawing tool is active. This exact pattern
appears in Telegram, in the PhotoEditor sample app, and in Snapseed-style
editors — it is the consensus, not one app's quirk.

---

## Option 1 — Telegram style (dark, photo-fullscreen)

- **Source:** Telegram's own photo editor (in-house since 2015, Photo
  Editor 2.0 in Sept 2016). No GitHub repo — it is Telegram's own code —
  but it is the editor the user asked for originally, and the most-used
  photo editor on Android.
- **Evidence of praise:** Telegram's editor is the reference every guide
  compares other editors to ("the most powerful photo editing tool ever to
  be implemented in a messaging app", Telegram blog, 2015); Guiding Tech's
  2023 guide walks through its drawing / annotation / blur / text tools
  step by step.
- **Layout (see images):** photo fills the whole screen on a black
  background. Bottom row is five small icons only: back, crop, brush,
  adjust, send. Inside the drawing tool: brush-size sliders + a row of
  colour dots + colour wheel at the bottom, a Draw / Sticker / Text
  switch, and a checkmark to confirm. Undo arrow and "Clear All" sit at
  the top.
- **Images:**
  - `01-telegram-editor-entry.jpg` — the editor entry screen: photo
    fullscreen, bottom icon toolbar (crop / brush / adjust / send).
  - `01-telegram-editor-text.jpg` — inside the editor: colour dots,
    brush-size sliders, Draw / Sticker / Text switch, checkmark confirm.
- **Fit for Imagram:** closest to what the user originally wanted; dark
  theme, very compact. Note: dark, not the light minimal Minab style the
  user also likes — it would need a light re-skin.

## Option 2 — PhotoEditor sample app (dark, story-style toolbar) ⭐ recommended

- **Repo:** https://github.com/burhanrashid52/PhotoEditor
  (~4,500 stars, MIT, Kotlin, latest release v.3.1.1 — the same library
  Imagram already uses).
- **Evidence of praise:** 4.5k stars; F-Droid ships the sample as "Photo
  Editor" (v3.1.1, 11.8 MB); its README is the most-forked Android photo
  editor reference; the library is used by Pixxo, Couple Blog and
  ScreenshotTile (cvzi/ScreenshotTile bundles the same engine).
- **Layout (verified from the real layout file
  `app/src/main/res/layout/activity_edit_image.xml`):** `ConstraintLayout`,
  black background. `PhotoEditorView` fills all space above the bottom
  tools bar. Undo / redo / gallery / camera / share are small floating
  icons overlaid on the bottom of the photo. Tools live in one horizontal
  icon bar at the bottom (`rvConstraintTools`) with icon + tiny label
  (Brush, Text, Eraser, Filter, Emoji, Sticker). Brush colour/size and
  shape options open in a small bottom sheet; filters slide in as a
  second horizontal strip.
- **Image:** `02-photoeditor-sample.png` — the sample app on a phone:
  photo fullscreen, floating undo/redo icons, bottom icon toolbar with
  labels (Brush / Text / Eraser / Filter / Emoji / Sticker).
- **Fit for Imagram:** the cheapest and safest to build — it is the
  reference implementation of the exact engine Imagram already uses, so
  every control maps 1:1 to our code. My recommendation is this layout
  re-skinned LIGHT (white background, teal accent) in the Minab minimal
  style, instead of its original black theme.

## Option 3 — ImageToolbox, Material You light (light, modern M3)

- **Repo:** https://github.com/T8RIN/ImageToolbox — ~15.6k stars (the
  most-starred Android photo-editor app found), Kotlin + Jetpack Compose,
  Material You / Material 3, FOSS, actively maintained.
- **Evidence of praise:** 15.6k stars (and +192 in a day in Nov 2026);
  featured in design-tool roundups as a "powerful tool for advanced image
  manipulation… 15.5 thousand stars… no ads, no subscription"; Google Play
  / F-Droid distribution; praised for deep UI customisation (dynamic
  colours, light/dark/AMOLED modes, switch and shape styles).
- **Layout (see image):** light, rounded Material You cards; tools as a
  clean list of icon + title + one-line description (Single resize, Batch
  resize, Crop, Filter, Image preview…); sliders with value readouts
  (Hue 202.44, White balance); preset chips (100 / 90 / 80 / 70 / 60).
  Soft green palette, big rounded corners, generous spacing — the most
  "designed" of the three.
- **Image:** `03-imagetoolbox-light.png` — README promo frame: light
  tool list with icons, sliders and preset chips.
- **Fit for Imagram:** the best-looking LIGHT option and closest to the
  Minab minimal taste, but the heaviest to copy: it is a full Compose
  app with 100+ tools, and its look comes from Compose + Material You,
  not from the View-based stack Imagram uses. Copying its *style*
  (light cards, rounded controls, sliders) is doable; copying its
  architecture is not the goal.

## Considered and dropped

- **eventtus PhotoEditorSDK** (the 2017 Instagram-story editor that
  inspired PhotoEditor): the surviving fork has 1 star and its README
  screenshot failed to download ("Cannot proxy the given URL"), so there
  is no usable image or live feedback to show.
- **DS Photo Editor** (Play rating 4.35/5, ~480 ratings, 130k
  downloads): a closed SDK demo, last updated 2023, no GitHub repo and
  no usable screenshots — not a GitHub candidate.
- **Snapseed:** widely praised (4.8★, ~1.8M Play reviews) and the
  pattern reference for gesture-based editing, but closed-source with no
  repo screenshots; its pattern is already covered by Options 1–2.

## Recommendation (for the user to confirm or overrule)

**Option 2's layout, re-skinned light** — the PhotoEditor sample's
canvas-first structure (photo fullscreen, floating undo/redo, one bottom
icon toolbar with tiny labels, options in a small panel above it) in the
light minimal Minab style (white background, teal accent, rounded small
controls, colour dots only while drawing). It is the only option that is
both proven-liked and a direct map onto Imagram's existing engine, so
v0.2 can be built from it without changing libraries or behaviour.

## User's choice (2026-11-22)

The user picked **Option 1 (Telegram style)** from their own Telegram
screenshots, and explicitly excluded the caption field and SD/HD
(messaging parts). v0.2 was built in that style: dark, photo
fullscreen, bottom pill toolbar, draw panel with colour wheel + dots +
DRAW/TEXT switch, adjust panel with CANCEL/DONE — see BUILD-NOTES.md
(v0.2 section) for the mapping and its honest limits.

## Files

- `ui-options/01-telegram-editor-entry.jpg` (474×1024)
- `ui-options/01-telegram-editor-text.jpg` (474×1024)
- `ui-options/02-photoeditor-sample.png` (1920×1080)
- `ui-options/03-imagetoolbox-light.png` (6627×3757)

No project code was changed for this research. Version is still 0.1.
