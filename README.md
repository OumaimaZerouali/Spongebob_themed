# Spongebob Theme

<!-- Plugin description -->
A dark, underwater IntelliJ theme straight from the bottom of the ocean — plus a progress bar that's actually fun to watch.

- **Spongebob** UI theme and matching editor color scheme: deep-sea blues, sponge-yellow accents, coral keywords, seaweed strings and jellyfish-pink numbers.
- **Underwater progress bar** with water, drifting light rays and bubbles. A runner swims along the front while things load (and bounces back and forth for indeterminate tasks).
- **Bring your own runner**: pick any PNG, JPG or animated GIF in *Settings → Appearance & Behavior → Spongebob Theme*. Default is a little jellyfish.
<!-- Plugin description end -->

## Install

**From a release (easiest)**

1. Download the latest `.zip` from [Releases](https://github.com/OumaimaZerouali/spongebob_theme/releases/latest).
2. IntelliJ → *Settings → Plugins → ⚙️ → Install Plugin from Disk…* → choose the zip.
3. *Settings → Appearance & Behavior → Appearance → Theme* → **Spongebob**.

**From source**

```bash
./gradlew buildPlugin        # zip ends up in build/distributions/
./gradlew runIde             # try it in a sandbox IDE
```

Requires IntelliJ-based IDEs 2024.3 or newer (IDEA, WebStorm, PyCharm, …).

## Custom runner

*Settings → Appearance & Behavior → Spongebob Theme*

- **Runner image** — any PNG/JPG/animated GIF. It is scaled to the bar height and mirrored when it runs back left. A transparent background looks best; a side-view running sprite looks best of all.
- Untick the checkbox to get the normal progress bar back, while keeping the colors.

The repository only ships the built-in jellyfish. Images you choose stay on your machine.

## Palette

| Name | Hex | Used for |
|---|---|---|
| Ocean | `#0F2E40` | editor background |
| Deep | `#0B2533` | panels |
| Foam | `#E6F4F1` | text |
| Sponge | `#FFD93D` | accents, functions, caret |
| Coral | `#FF8A5B` | keywords |
| Seaweed | `#9BE36B` | strings |
| Jelly | `#F7A1C4` | numbers |
| Lagoon | `#5FD4E8` | types, links |
| Pineapple | `#F2B04C` | annotations |
| Urchin | `#C59BFF` | fields, constants |

## Disclaimer

Unofficial fan project, not affiliated with or endorsed by Nickelodeon or Paramount. No official artwork is included. Free and non-commercial.

## License

[MIT](LICENSE)
