#!/usr/bin/env python3
"""
Checks that a mascot SVG follows the layer contract the app relies on.

  python3 tools/check-mascot.py docs/mascot/dalma.svg

Exit code 0 when everything the app needs is there; the report lists what is missing.
"""
import re
import sys
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"
REQUIRED = [
    "hair_back", "body", "head", "fringe_shadow", "blush", "nose", "hair_cap", "hair_front", "clip", "face_clip",
    "eye_l_open", "eye_r_open", "eye_l_closed", "eye_r_closed",
    "eye_l_happy", "eye_r_happy", "eye_l_sleepy", "eye_r_sleepy",
    "brow_l", "brow_r", "brow_l_sorry", "brow_r_sorry",
    "mouth_smile", "mouth_happy", "mouth_sorry", "mouth_sleepy",
    "drop", "sparkles", "zz",
]
ALLOWED_SHAPES = {"path", "ellipse", "circle", "rect", "line", "polygon", "polyline"}


def main(path):
    problems = []
    root = ET.parse(path).getroot()
    if root.get("viewBox") != "0 0 240 240":
        problems.append(f"viewBox must be '0 0 240 240', got {root.get('viewBox')!r}")
    top = [child for child in root if child.tag == NS + "g"]
    ids = [g.get("id") for g in top]
    for name in REQUIRED:
        if name not in ids:
            problems.append(f"missing layer <g id=\"{name}\">")
    for g in top:
        if g.get("id") is None:
            problems.append("a top-level <g> has no id")
        if g.get("transform"):
            problems.append(f"layer {g.get('id')} has a transform (apply it to the coordinates instead)")
        for node in g.iter():
            tag = node.tag.replace(NS, "")
            if tag in ("text", "image", "use", "filter", "linearGradient", "radialGradient", "mask", "clipPath"):
                problems.append(f"layer {g.get('id')} uses <{tag}>, which the app cannot draw")
            if tag in ALLOWED_SHAPES:
                for attr in ("fill", "stroke"):
                    value = node.get(attr)
                    if value and value.startswith("url("):
                        problems.append(f"layer {g.get('id')} uses a gradient/pattern ({attr}={value})")
                    if value and not value.startswith("#") and value != "none":
                        problems.append(f"layer {g.get('id')} uses a named color ({attr}={value}); use #RRGGBB")
                if node.get("style"):
                    problems.append(f"layer {g.get('id')} uses a style attribute; use plain attributes")
            if tag == "g" and node is not g and node.get("transform"):
                problems.append(f"layer {g.get('id')} has a nested transform; flatten it")
    clip = next((g for g in top if g.get("id") == "clip"), None)
    if clip is not None:
        shape = clip.find(f"{NS}path[@id='clip_shape']")
        if shape is None:
            problems.append("layer clip needs exactly one <path id=\"clip_shape\"> (the solid triangle)")
        else:
            d = shape.get("d", "")
            if len(re.findall(r"[MmLlCcQqAaZzHhVvSsTt]", d)) > 24:
                problems.append("clip_shape should be a simple rounded triangle (too many path commands)")
    text = open(path).read()
    if "<text" in text:
        problems.append("<text> is not allowed anywhere: draw letters as paths")
    for p in problems:
        print("PROBLEM:", p)
    print(f"{len(top)} layers:", ", ".join(ids))
    print("OK" if not problems else f"{len(problems)} problem(s)")
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "docs/mascot/dalma.svg"))
