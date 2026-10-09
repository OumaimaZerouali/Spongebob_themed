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
