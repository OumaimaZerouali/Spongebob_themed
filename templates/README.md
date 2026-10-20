# Pixel art templates

![Guide](GUIDE.png)

## Specs

| What | Size | Notes |
|---|---|---|
| **Runner frame** | 16 × 16 px | Draw facing **right**. Bottom row (y = 15) is the ground. Transparent background. |
| **Runner sheet** | N × 16 wide, 16 high | Frames side by side, left → right, no spacing or padding. 6–8 frames is a nice run cycle. |
| **Fill tile** | 16 × 16 px (any width) | Repeated over the loaded part and slowly scrolled. Left and right columns should match. |
| **Track tile** | 16 × 16 px (any width) | Repeated over the empty part. |

- Draw at 1x. The plugin scales with nearest-neighbour, so pixels stay sharp (2× on Retina).
- Wider frames (e.g. 24 × 16) work: set **Frames in sheet** to the number of frames.
- An animated GIF works too: it is played as-is (no sheet settings needed).
- Determinate tasks: the runner's right edge sits on the current progress.
  Indeterminate tasks: it swims back and forth and is mirrored on the way back.

## Files

| File | Use |
|---|---|
| `blank-runner-sheet-8x16x16.png` | Empty 128 × 16 canvas for an 8-frame runner |
| `blank-fill-tile-16.png` / `blank-track-tile-16.png` | Empty 16 × 16 tile canvases |
| `example-runner-jellyfish-8f.png` | Working 8-frame example |
| `example-fill-tile-water.png` / `example-track-tile-seafloor.png` | Working tile examples |

Open a blank in Aseprite, LibreSprite, Piskel or Pixelorama (in Aseprite: *Import Sprite Sheet*, 16 × 16 grid),
draw, export as PNG, then pick it in **Settings → Appearance & Behavior → Spongebob Theme** and press **Apply**.
Editing the file and pressing Apply again reloads it.

## Character sheets (animations)

For more than running, use one sheet with **labels** above each group, like
[`example-labelled-sheet.png`](example-labelled-sheet.png):

```
NAME
◎ IDLE            ◎ WALK / RUN
[frames…]         [frames…]
◎ SIT        ◎ JUMP        ◎ WAVE / CELEBRATE     ◎ OTHER / EMOTES
[frames…]    [frames…]     [frames…]              [♥ ? z !  poses]
```

- Per character exactly **six groups, in this order**: idle, run, sit, jump, wave, emote. Add more characters below.
- Any background colour works (solid or transparent). Labels can be any text: their position marks where a group starts.
- Small marks right above a sprite (♥, ?, zZ) stay attached to that frame.
- Emotes are separate poses: one is picked and held. Jump plays once. Idle and sit play slower.

In Settings pick the character (or **Random**), turn on **Random breaks**, and set **Editor visits**.

## Decoration

Any small image (e.g. a flower) for the **Decoration** setting. It is scattered over the empty part of the bar,
drifts slowly, gets a theme colour per copy (optional) and spins now and then.

Tick **Also use it as wallpaper behind the code** to scatter the same image over the editor background
(and optionally behind panels). Opacity 10–20% keeps code readable.
