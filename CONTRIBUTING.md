# Contributing

Thanks for wanting to help! This guide gets you from a fresh clone to a working change.
For how the plugin works inside, read [docs/TECHNICAL.md](docs/TECHNICAL.md).

## What you need

| Tool | Version | Check with |
|---|---|---|
| Git | any recent | `git --version` |
| JDK | 21 | `java -version` |
| IntelliJ IDEA | Community or Ultimate, 2024.3 or newer | |
| Gradle | not needed: use the included `./gradlew` wrapper | |

If Gradle can't find a JDK 21, install one (for example `sdk install java 21-tem` with SDKMAN) or point IntelliJ to it under *Settings → Build Tools → Gradle → Gradle JVM*.

## Get the code

```bash
git clone https://github.com/OumaimaZerouali/Spongebob_themed.git
cd Spongebob_themed
```

Open the folder in IntelliJ IDEA and let it import the Gradle project. The first sync downloads the IntelliJ platform (about 1 GB), so give it a few minutes.

## Build and try it

```bash
./gradlew runIde        # sandbox IDE with the plugin installed
./gradlew buildPlugin   # installable zip in build/distributions/
```

The run configurations **Run Plugin**, **Run Tests** and **Run Verifications** (in `.run/`) do the same from inside IntelliJ.

The sandbox keeps its own settings in `.intellijPlatform/sandbox/`, so you can't break your real IDE. To try things quickly:
- pick a sheet from `templates/` under *Settings → Appearance & Behavior → Spongebob Theme*;
- use *Tools → Spongebob Theme: Visit This Editor* to see an editor visit right away.

## Tests and checks

There are no automated tests yet: `./gradlew test` runs but finds nothing. Good first candidates are `SpriteSlicer` (feed it the sheets in `templates/`), `RunnerBrain` and the wallpaper tile rendering.

Before opening a pull request, run:

```bash
./gradlew buildPlugin
./gradlew verifyPlugin   # checks compatibility and deprecated/internal API use (downloads several IDEs)
```

Then try your change in `runIde`, with the built-in jellyfish and with a sheet from `templates/`.

## Where things are

| You want to change… | Look in |
|---|---|
| Colours of the IDE or editor | `src/main/resources/themes/` and `Palette.kt` |
| How the progress bar looks | `progress/SpongeProgressBarUI.kt` |
| When the runner takes breaks | `progress/RunnerBrain.kt` |
| How sprite sheets are read | `progress/SpriteSlicer.kt`, `progress/RunnerSprite.kt` |
| Editor visits | `buddy/EditorVisit.kt`, `buddy/EditorVisits.kt` |
| Flowers in the bar and the wallpaper | `progress/FlowerField.kt`, `progress/FlowerWallpaper.kt` |
| Settings page | `settings/SpongeConfigurable.kt` (UI), `settings/SpongeSettings.kt` (stored values) |
| What's registered in the IDE | `src/main/resources/META-INF/plugin.xml` |

## Making a change

1. Branch from `main`: `git checkout -b fix/short-description` or `feature/short-description`.
2. Keep to the style around you: small classes, a KDoc line on what each class does, comments that explain *why*.
3. Stay on public IntelliJ API. The Marketplace verifier rejects `@ApiStatus.Internal` code, so check its report.
4. Add a line under `## [Unreleased]` in `CHANGELOG.md`.
5. Write commit messages as a short imperative sentence ("Keep the wallpaper out of image viewers").
6. Open a pull request with a short description of what you changed and a screenshot or GIF for anything visual. CI builds the plugin for every pull request.

## Artwork

Only add artwork to `src/` or `templates/` that you made yourself or are allowed to share. The plugin ships with original art only (the jellyfish and the template examples).

For your own sprites, use a `my-sprites/` folder. If you don't want to commit them, add `my-sprites/` to `.gitignore`.

## Releasing (maintainers)

1. Bump `pluginVersion` in `gradle.properties` and move the `Unreleased` changelog items under the new version.
2. Commit, then tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. CI creates the GitHub Release and, once the secrets are set, publishes to the Marketplace. Signing setup is in the README's *Publishing* section.

## When you get stuck

| Problem | Fix |
|---|---|
| `Unsupported class file major version` or a toolchain error | Gradle runs on the wrong JDK: set Gradle JVM to 21. |
| `./gradlew: Permission denied` | `chmod +x gradlew` |
| Sandbox won't start: "process … is still running" | A previous sandbox is still open or crashed: close it, or delete `.intellijPlatform/sandbox/*/config_runIde/.lock`. |
| Changes in `plugin.xml` don't show up | Stop the sandbox and run `./gradlew runIde` again; hot reload doesn't cover everything. |
| Progress bar looks normal in the sandbox | Check that *Use the underwater progress bar* is on, and that no other plugin replaces the progress bar. |

Still stuck? Open an issue on GitHub with what you ran and what you saw.
