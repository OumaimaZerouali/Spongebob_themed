# Technical documentation

How the Spongebob Theme plugin is built and how its parts fit together.
For installing and using the plugin, see the [README](../README.md). For working on it, see [CONTRIBUTING](../CONTRIBUTING.md).

## Overview

An IntelliJ Platform plugin with three layers:

1. **A theme**: UI colours (`themes/spongebob.theme.json`) and an editor colour scheme (`themes/spongebob.xml`). These are pure resources, no code.
2. **An animated progress bar**: replaces every progress bar in the IDE with an underwater one. A character runs along it, takes short breaks and has decorations drifting behind it.
3. **Extras on top of the editor**: a character that now and then visits the code and sits on a class declaration, and an optional flower wallpaper behind the code.

Everything except the theme is optional and configured in *Settings → Appearance & Behavior → Spongebob Theme*.
The plugin talks to nothing outside the IDE: no network, no telemetry, no external services.

| | |
|---|---|
| Languages | Kotlin (almost everything), one Java class (`SpongeProgressBarLaf`) |
| Build | Gradle 9 (wrapper included), Kotlin DSL, IntelliJ Platform Gradle Plugin 2.x |
| JDK | 21 (Gradle toolchain) |
| Target | IntelliJ-based IDEs 2024.3 (build 243) and newer, no upper bound |
| Built against | IntelliJ IDEA Community 2024.3.7 (`platformVersion` in `gradle.properties`) |

## Repository layout

```
src/main/
  java/…/progress/SpongeProgressBarLaf.java   Swing entry point for the progress bar UI (see design decisions)
  kotlin/…/spongebobtheme/
    Palette.kt                                 Shared colours for the progress bar and the default runner
    buddy/                                     Editor visits
    progress/                                  Progress bar, characters, sprite sheets, decoration, wallpaper
    settings/                                  Settings page and persisted state
  resources/
    META-INF/plugin.xml                        Extension points, listeners, action
    META-INF/pluginIcon.svg                    Marketplace icon
    themes/                                    UI theme + editor colour scheme
templates/                                     Pixel-art specs, blank canvases, example sheets
docs/                                          This file + screenshots used by the README
.github/workflows/build.yml                    CI: build, GitHub Release, Marketplace publish
```

## How it plugs into IntelliJ

Registered in `plugin.xml`:

| Extension | Class | Purpose |
|---|---|---|
| `themeProvider` | `themes/spongebob.theme.json` | The UI theme; it points to the editor scheme `themes/spongebob.xml` |
| `applicationConfigurable` (under *Appearance*) | `SpongeConfigurable` | Settings page |
| `postStartupActivity` | `SpongeStartupActivity` | Installs the progress bar, starts editor visits, applies the wallpaper |
| application listener `LafManagerListener` | `SpongeLafListener` | Re-installs the progress bar after a theme switch (switching resets Swing's UI defaults) |
| application listener `AppLifecycleListener` | `SpongeAppListener` | Installs the progress bar when the first IDE frame opens |
| project listener `FileEditorManagerListener` | `WallpaperEditorListener` | Keeps the wallpaper out of image viewers and previews |
| action in *Tools* | `VisitEditorAction` | "Spongebob Theme: Visit This Editor": starts an editor visit right away |
| light service | `SpongeSettings`, `EditorVisits` | Settings state; scheduler for editor visits |

## Components

### `progress` package

| Class | Responsibility |
|---|---|
| `SpongeProgressBarInstaller` | Puts our UI class in Swing's `UIManager` under `ProgressBarUI`, so every `JProgressBar` gets it. Restores the original when the feature is switched off. Also holds the three startup/listener hooks. |
| `SpongeProgressBarUI` | Paints the bar: track, water fill with light rays and bubbles, optional pixel-art tiles, decoration, bubble trail and the runner. Every bar has its own repaint timer (40 ms, only while visible) and its own `RunnerBrain`. |
| `RunnerBrain` | Small state machine: run, and every 4–9 s a break (idle, sit, jump, wave, emote) when the character has those animations. Decides which animation frame to show. |
| `RunnerSprite` | Turns the chosen image into `CharacterSprites` (cached), picks the character (fixed or random), scales and draws frames. Draws the built-in jellyfish when no image is set. |
| `SpriteSlicer` | Reads "found" sprite sheets: removes the background, finds sprites, groups them into animations. See [Sprite sheet analysis](#sprite-sheet-analysis). |
| `Anim`, `CharacterSprites`, `Action` | Data: an animation strip, a character's animations, and the action names. |
| `PixelImages` | Loads images (cached per path + modification time), picks nearest-neighbour or smooth scaling, tiles images. |
| `FlowerField` | The decoration drifting through the empty part of the bar: background removal, theme-colour variants, drift and occasional spins. |
| `FlowerWallpaper` | Builds a seamless 640 × 640 tile from the decoration and sets it as IntelliJ's background image. Opts image viewers and previews out of it. |
| `WallpaperEditorListener` | Re-runs that opt-out when files are opened or tabs change. |

### `buddy` package

| Class | Responsibility |
|---|---|
| `EditorVisits` | Application service. Every 5 s it may start a visit (average gap depends on the *Editor visits* setting). Skips while you're typing and for 8 s after. Holds the Tools action. |
| `EditorVisit` | One visit: finds a class declaration in the visible code, then runs in from the right → sits on the class name → waves, emotes or idles → runs off. Draws through a line highlighter with a `CustomHighlighterRenderer`, so the editor's components aren't touched. Typing in that editor makes the character hurry off. |

### `settings` package

| Class | Responsibility |
|---|---|
| `SpongeSettings` / `SpongeSettingsState` | Persisted in `spongebobTheme.xml` in the IDE config folder. `stateOrDefault()` also works without a running IDE (handy for tests). |
| `SpongeConfigurable` | The settings page, including a live preview of a determinate and an indeterminate bar. *Apply* clears caches, reinstalls the progress bar and re-applies the wallpaper. |

## Key flows

### Progress bar installation

1. At startup (`SpongeAppListener`, `SpongeStartupActivity`) and after every theme switch (`SpongeLafListener`), `SpongeProgressBarInstaller.install()` runs.
2. It stores the current `ProgressBarUI` class and puts `SpongeProgressBarLaf` in its place.
3. Swing calls the static `SpongeProgressBarLaf.createUI()` for every progress bar created after that.
4. When the feature is switched off in the settings, `refreshAll()` restores the original class and updates the component tree of every window.

### Painting one frame of the bar

1. The bar's timer calls `repaint()` every 40 ms while the bar is visible.
2. `RunnerBrain.update()` decides whether the character keeps running or starts or ends a break.
3. Determinate bars: the runner's right edge sits on the progress. Indeterminate bars: the runner moves back and forth (one sweep ≈ 1.3 s) and stands still during breaks.
4. Drawing order: track or track tile → water or fill tile → decoration → bubble trail → border → runner. The runner is drawn last and without clipping, so the rounded ends never cut it off.

### Sprite sheet analysis

`SpriteSlicer.analyse()` turns a sheet into animation groups, in reading order:

1. **Mask**: every pixel that differs from the top-left pixel (or isn't transparent) counts.
2. **Blobs**: connected components, where pixels up to 4 px apart belong together.
3. **Sprites vs text**: tall blobs are sprites. Short blobs are either labels (wide lines of text) or marks (♥, ?). Marks right above a sprite are glued onto it.
4. **Rows and groups**: sprites are grouped into rows. A label above a row starts a new group; without labels, a big horizontal gap does.
5. **Characters**: on a labelled sheet every six groups form one character (idle, run, sit, jump, wave, emote). Otherwise every group is a run-only character.

Small square strips (height ≤ 64 px, width a multiple of the height) skip all this and are read as a plain grid, because pixel-art frames often touch.

### Editor visit

1. `EditorVisits` picks the selected editor of the focused project window.
2. `EditorVisit.start()` looks for `class|interface|enum|record|object|struct|trait Name` in the visible lines (comments skipped), with enough room above it.
3. The character runs in from the right edge to just after the class name, sits for about 4 s, does one action for 2 s, then runs off to the right.
4. The visit removes its highlighter when it ends, when the editor closes, or when it's interrupted.

### Wallpaper

1. `FlowerWallpaper.apply()` renders the tile into `<config>/spongebob-theme/flower-wallpaper.png`.
2. It sets IntelliJ's background-image properties `idea.background.editor` and, optionally, `idea.background.frame`. Those are the same settings as *View → Appearance → Set Background Image*.
3. It only writes or removes those properties when they're empty or point to our own file, so a background you chose yourself is never touched.
4. IntelliJ paints the background into every component that doesn't opt out. `protectEditorArea()` therefore sets IntelliJ's opt-out on every component of non-text editors, and on the editor area behind them. That covers image viewers and the preview half of SVG and Markdown files. A container listener catches components added later.

## Design decisions

- **Java shim for `createUI`.** Swing looks up a static `createUI(JComponent)` on the registered UI class. Kotlin can't declare one that hides `ComponentUI.createUI`, so `SpongeProgressBarLaf` is a tiny Java subclass of the Kotlin UI.
- **Public API only.** The Marketplace verifier rejects internal API. The background opt-out key is looked up by name (`Key.findKeyByName("SUPPRESS_BACKGROUND")`), and the editor area is recognised by class name instead of importing internal classes.
- **No upper IDE bound.** `untilBuild` is unset, so the plugin keeps installing on new IDE versions. The verifier run (`./gradlew verifyPlugin`) is the safety net.
- **Pixel art stays crisp.** Upscaling uses nearest-neighbour; downscaling uses smooth scaling so pixels don't vanish.
- **Nothing touches your files.** Settings live in the IDE config folder. Images are only read, and the wallpaper is generated in the config folder.
- **Fail soft.** Image loading and sheet analysis are wrapped in `runCatching`. A broken image falls back to the jellyfish or to "no decoration" instead of breaking the IDE. There is no logging yet.

## Known limitations

- **No automated tests.** `SpriteSlicer`, `RunnerBrain` and `FlowerWallpaper.render` are pure enough to unit-test; that's the obvious first step.
- **Sheet analysis on the EDT.** It runs once per image change, and big sheets (≈1500 × 1000 px) take about 0.5 s. It's cached afterwards.
- **Text-based class detection.** Editor visits find classes with a regular expression, not PSI. A class inside a multi-line string could be picked.
- **Shared tab containers.** With *…and behind panels* on, the editor area itself has no panel wallpaper (text editors get the editor wallpaper instead), so an empty editor area shows no flowers.
- **Labelled sheets need exactly six groups per character**, in the documented order (see `templates/README.md`).

## Build and release

| Command | Result |
|---|---|
| `./gradlew buildPlugin` | Plugin zip in `build/distributions/` |
| `./gradlew runIde` | Sandbox IDE with the plugin installed |
| `./gradlew verifyPlugin` | Runs the JetBrains Plugin Verifier against the recommended IDE versions |
| `./gradlew signPlugin` | Signed zip; needs `CERTIFICATE_CHAIN`, `PRIVATE_KEY` and `PRIVATE_KEY_PASSWORD` as environment variables |
| `./gradlew publishPlugin` | Uploads to the JetBrains Marketplace; also needs `PUBLISH_TOKEN` |

CI (`.github/workflows/build.yml`) builds every push and pull request. A tag `vX.Y.Z` additionally:
- creates a GitHub Release with the zip;
- publishes to the Marketplace once the four secrets above are set in GitHub.

A version with a suffix (`1.6.0-beta.1`) goes to the `beta` Marketplace channel. The README's *Publishing* section has the step-by-step signing setup.
