#!/usr/bin/env python3
"""
Turns the AI-made Dalma pictures and clips (plain magenta #FF00FF background) into what
the app ships: transparent PNG stills, transparent animated WebP loops, and a mask of
the hair clip so the code can paint the download progress onto it.

  python3 tools/dalma-assets.py <folder with dalma_*.png and dalma_*.mp4>

Needs ffmpeg on the PATH and: pip install pillow numpy
Writes into app/src/main/assets/dalma/ ; the app uses these when they exist and falls
back to the vector Dalma otherwise.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# Overridable for test fixtures: DALMA_OUT, DALMA_STILL_SIZE, DALMA_CLIP_SIZE, DALMA_FPS
OUT = os.environ.get("DALMA_OUT") or os.path.join(ROOT, "app", "src", "main", "assets", "dalma")
STILL_SIZE = int(os.environ.get("DALMA_STILL_SIZE", "1024"))
CLIP_SIZE = int(os.environ.get("DALMA_CLIP_SIZE", "640"))
FPS = int(os.environ.get("DALMA_FPS", "24"))

STILLS = {"alap": "dalma_alap", "fordul": "dalma_fordul", "orul": "dalma_orul", "bocsanat": "dalma_bocsanat", "almos": "dalma_almos", "ikon": "dalma_ikon"}
CLIPS = {"fordul": "dalma_fordul", "vissza_orul": "dalma_vissza_orul", "pislog": "dalma_pislog", "almos": "dalma_almos", "koszon": "dalma_koszon", "korbenez": "dalma_korbenez"}
MINT = np.array([0x8F, 0xD3, 0xBE], dtype=np.float32)


def key_magenta(rgb):
    """Alpha from the distance to pure magenta, with the magenta tint pulled out of soft edges."""
    rgb = rgb.astype(np.float32)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    # How magenta a pixel is: red and blue high, green low.
    magenta = np.clip((np.minimum(r, b) - g) / 255.0, 0, 1)
    alpha = np.clip((0.55 - magenta) / 0.35, 0, 1)
    # Despill: on the edges, bring red and blue down towards green.
    spill = np.clip((np.minimum(r, b) - g) * 0.6, 0, None)
    r2 = np.where(magenta > 0.08, r - spill, r)
    b2 = np.where(magenta > 0.08, b - spill, b)
    out = np.stack([np.clip(r2, 0, 255), g, np.clip(b2, 0, 255), alpha * 255], axis=-1)
    return out.astype(np.uint8)


def load_rgb(path):
    return np.array(Image.open(path).convert("RGB"))


def fit(image, size):
    image.thumbnail((size, size), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(image, ((size - image.width) // 2, size - image.height))
    return canvas


def clip_mask(rgba):
    """
    The hair clip: the largest mint-coloured blob around the head. The hoodie is mint too,
    so only the upper part of the figure counts, and a blob the size of a sweater is skipped.
    """
    rgb = rgba[..., :3].astype(np.float32)
    dist = np.sqrt(((rgb - MINT) ** 2).sum(axis=-1))
    mint = (dist < 46) & (rgba[..., 3] > 128)
    h, w = mint.shape
    visible = np.where(rgba[..., 3] > 128)
    if len(visible[0]) == 0:
        return None, None
    top, bottom = int(visible[0].min()), int(visible[0].max())
    head_limit = top + int((bottom - top) * 0.6)
    mint[head_limit:, :] = False
    figure_pixels = len(visible[0])
    # Connected components without scipy: a simple flood fill.
    labels = np.zeros((h, w), dtype=np.int32)
    current = 0
    best = (0, None)
    for y in range(h):
        for x in range(w):
            if mint[y, x] and labels[y, x] == 0:
                current += 1
                stack = [(y, x)]
                labels[y, x] = current
                count = 0
                while stack:
                    cy, cx = stack.pop()
                    count += 1
                    for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                        if 0 <= ny < h and 0 <= nx < w and mint[ny, nx] and labels[ny, nx] == 0:
                            labels[ny, nx] = current
                            stack.append((ny, nx))
                if best[0] < count <= figure_pixels * 0.06:
                    best = (count, current)
    if best[1] is None:
        return None, None
    mask = labels == best[1]
    ys, xs = np.where(mask)
    box = [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]
    return mask, box


def write_still(name, source):
    rgba = key_magenta(load_rgb(source))
    image = fit(Image.fromarray(rgba, "RGBA"), STILL_SIZE)
    image.save(os.path.join(OUT, f"{name}.png"), optimize=True)
    return np.array(image)


def write_clip(name, source):
    work = tempfile.mkdtemp()
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", source, "-vf", f"fps={FPS}", os.path.join(work, "f%04d.png")], check=True)
        frames = sorted(f for f in os.listdir(work) if f.endswith(".png"))
        for frame in frames:
            path = os.path.join(work, frame)
            rgba = key_magenta(load_rgb(path))
            fit(Image.fromarray(rgba, "RGBA"), CLIP_SIZE).save(path)
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", os.path.join(work, "f%04d.png"),
             "-c:v", "libwebp_anim", "-lossless", "0", "-q:v", "82", "-loop", "0", "-pix_fmt", "yuva420p",
             os.path.join(OUT, f"{name}.webp")],
            check=True,
        )
        return len(frames)
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main(folder):
    os.makedirs(OUT, exist_ok=True)
    manifest = {"stills": {}, "clips": {}, "clip_mask": None}
    for name, base in STILLS.items():
        source = os.path.join(folder, base + ".png")
        if not os.path.isfile(source):
            print(f"missing {base}.png (skipped)")
            continue
        rgba = write_still(name, source)
        manifest["stills"][name] = f"{name}.png"
        if name == "fordul":
            mask, box = clip_mask(rgba)
            if mask is not None:
                mask_image = np.zeros(rgba.shape, dtype=np.uint8)
                mask_image[mask] = [255, 255, 255, 255]
                Image.fromarray(mask_image, "RGBA").save(os.path.join(OUT, "clip_mask.png"), optimize=True)
                manifest["clip_mask"] = {"file": "clip_mask.png", "box": box, "size": STILL_SIZE}
                print(f"hair clip found at {box}")
            else:
                print("WARNING: no mint hair clip found in dalma_fordul.png; the progress fill will be hidden")
        print(f"still {name}")
    for name, base in CLIPS.items():
        source = os.path.join(folder, base + ".mp4")
        if not os.path.isfile(source):
            print(f"missing {base}.mp4 (skipped)")
            continue
        frames = write_clip(name, source)
        manifest["clips"][name] = {"file": f"{name}.webp", "frames": frames, "fps": FPS}
        print(f"clip {name}: {frames} frames")
    with open(os.path.join(OUT, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1])
