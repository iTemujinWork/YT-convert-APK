#!/usr/bin/env python3
"""
Builds the layered mascot source (docs/mascot/dalma.svg, 240 x 240) from the designer's
pose files (docs/spec/0.2.0/design/dalma/*.svg, 200 x 200). alap.svg is the base; örül,
álmos, hallgat-este and bocsánat lend their eyes, brows, mouths, sparkles, zz and the drop.
Transforms are baked into the coordinates, clip paths are dropped, everything is scaled by
1.2. Then run tools/svg2compose.py as usual.

  python3 tools/poses2mascot.py
"""
import math
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(__file__), "..")
POSES = os.path.join(ROOT, "docs", "spec", "0.2.0", "design", "dalma")
TARGET = os.path.join(ROOT, "docs", "mascot", "dalma.svg")
NS = "{http://www.w3.org/2000/svg}"
SCALE = 240 / 200
KEEP = ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "opacity", "fill-opacity", "stroke-opacity")
ORDER = [
    "hair_back", "body", "head", "blush", "nose",
    "mouth_smile", "mouth_happy", "mouth_sorry", "mouth_sleepy",
    "eye_l_open", "eye_l_closed", "eye_l_happy", "eye_l_sleepy",
    "eye_r_open", "eye_r_closed", "eye_r_happy", "eye_r_sleepy",
    "brow_l", "brow_r", "brow_l_sorry", "brow_r_sorry",
    "hair_front", "clip", "drop", "sparkles", "zz",
]


def matrix(transform):
    """A 2x3 affine from translate(x y) / rotate(a cx cy); identity otherwise."""
    m = [1.0, 0.0, 0.0, 1.0, 0.0, 0.0]
    for name, args in re.findall(r"(\w+)\(([^)]*)\)", transform or ""):
        v = [float(x) for x in re.split(r"[ ,]+", args.strip()) if x]
        if name == "translate":
            t = [1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0.0]
        elif name == "rotate":
            a = math.radians(v[0]); cx, cy = (v[1], v[2]) if len(v) > 2 else (0.0, 0.0)
            c, s = math.cos(a), math.sin(a)
            t = [c, s, -s, c, cx - c * cx + s * cy, cy - s * cx - c * cy]
        else:
            raise SystemExit(f"unsupported transform {name}")
        m = [m[0] * t[0] + m[2] * t[1], m[1] * t[0] + m[3] * t[1], m[0] * t[2] + m[2] * t[3], m[1] * t[2] + m[3] * t[3],
             m[0] * t[4] + m[2] * t[5] + m[4], m[1] * t[4] + m[3] * t[5] + m[5]]
    return m


def apply(m, x, y):
    return (m[0] * x + m[2] * y + m[4]) * SCALE, (m[1] * x + m[3] * y + m[5]) * SCALE


def fmt(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def shapes_of(path):
    """Every drawable shape of a pose file in document order, with its transform baked in."""
    root = ET.parse(path).getroot()
    out = []
    counter = [0]

    def walk(node, m, group):
        for child in node:
            tag = child.tag.replace(NS, "")
            if tag in ("defs", "clipPath", "animate", "animateTransform", "title", "desc"):
                continue  # the designer's own idle motion; the app animates on its own
            if tag == "g":
                gid = group
                if gid is None:
                    gid = counter[0]; counter[0] += 1
                walk(child, matrix(child.get("transform")) if child.get("transform") else m, gid)
                continue
            if child.get("clip-path"):
                continue  # the fringe's shadow lives inside a clip the app cannot draw
            attrs = {k: child.get(k) for k in KEEP if child.get(k) is not None}
            if "fill" not in attrs:  # SVG's default fill is black; a stroke-only line means none
                attrs["fill"] = "none" if attrs.get("stroke") else "#000000"
            if "stroke-width" in attrs:
                attrs["stroke-width"] = fmt(float(attrs["stroke-width"]) * SCALE)
            key = (tag, child.get("d") or f"{child.get('cx')},{child.get('cy')},{child.get('r')},{child.get('rx')},{child.get('ry')}", attrs.get("fill"), attrs.get("stroke"))
            if tag == "path":
                tokens = re.findall(r"[MLQCZ]|-?\d*\.?\d+", child.get("d"))
                pts, d, i = [], [], 0
                while i < len(tokens):
                    t = tokens[i]
                    if t in "MLQCZ":
                        d.append(t); i += 1
                    else:
                        x, y = apply(m, float(t), float(tokens[i + 1])); pts.append((x, y)); d.append(f"{fmt(x)} {fmt(y)}"); i += 2
                out.append({"tag": "path", "key": key, "attrs": attrs, "d": " ".join(d), "pts": pts, "group": group})
            elif tag in ("ellipse", "circle"):
                x, y = apply(m, float(child.get("cx")), float(child.get("cy")))
                rx = float(child.get("rx") or child.get("r")) * SCALE
                ry = float(child.get("ry") or child.get("r")) * SCALE
                out.append({"tag": "ellipse", "key": key, "attrs": attrs, "cx": x, "cy": y, "rx": rx, "ry": ry, "pts": [(x, y)], "group": group})
            elif tag == "text" and child.text and child.text.strip() == "z":
                # The sleepy "z" letters: the app draws no text, so a z is a stroke of its size.
                size = float(child.get("font-size", "12"))
                x0, y0 = float(child.get("x")), float(child.get("y"))
                w, h = size * 0.55, size * 0.62
                corners = [(x0, y0 - h), (x0 + w, y0 - h), (x0, y0), (x0 + w, y0)]
                pts = [apply(m, x, y) for x, y in corners]
                d = "M " + " L ".join(f"{fmt(x)} {fmt(y)}" for x, y in pts)
                attrs = {"fill": "none", "stroke": child.get("fill", "#8FD3BE"), "stroke-width": fmt(size * 0.11 * SCALE),
                         "stroke-linecap": "round", "stroke-linejoin": "round"}
                if child.get("opacity"):
                    attrs["opacity"] = child.get("opacity")
                out.append({"tag": "path", "key": (tag, f"{x0},{y0},{size}", attrs["stroke"], None), "attrs": attrs, "d": d, "pts": pts, "group": group})
            else:
                raise SystemExit(f"{os.path.basename(path)}: cannot take a <{tag}>")

    counter = [0]  # top-level groups, in order of appearance: hair back, fringe, clip
    walk(root, matrix(None), None)
    return out


def centre(shape):
    xs = [p[0] for p in shape["pts"]]; ys = [p[1] for p in shape["pts"]]
    return (min(xs) + max(xs)) / 2 / SCALE, (min(ys) + max(ys)) / 2 / SCALE, (max(xs) - min(xs)) / SCALE


def side(cx):
    return "l" if cx < 100 else "r"


def base_layers(shapes):
    layers = {name: [] for name in ORDER}
    stage = "body"
    for s in shapes:
        cx, cy, width = centre(s)
        # The hair clip sits inside the fringe group: the mint triangle and its white tick, high on the right.
        if cy < 70 and cx > 110 and (s["attrs"].get("fill") == "#8FD3BE" or s["attrs"].get("stroke") == "#F4FBF8"):
            if s["attrs"].get("fill") == "#8FD3BE" and not any(t.get("id") == "clip_shape" for t in layers["clip"]):
                s["id"] = "clip_shape"
            layers["clip"].append(s); continue
        if s["group"] == 0:
            layers["hair_back"].append(s); continue
        if s["group"] == 1:
            layers["hair_front"].append(s); stage = "face"; continue
        if stage == "body":
            if s["attrs"].get("fill") == "#F6E3D6" and width > 50:
                layers["head"].append(s); stage = "hair"
            else:
                layers["body"].append(s)
        elif stage == "hair":
            layers["hair_front"].append(s)
        else:
            if s["attrs"].get("fill") == "#F2B3A6":
                layers["blush"].append(s)
            elif s["attrs"].get("stroke") == "#DDB3A0":
                layers["nose"].append(s)
            elif cy < 86:
                layers[f"brow_{side(cx)}"].append(s)
            elif cy < 102:
                layers[f"eye_{side(cx)}_open"].append(s)
            else:
                layers["mouth_smile"].append(s)
    return layers


VARIANTS = {
    "orul.svg": ("happy", "sparkles"),
    "almos.svg": ("sleepy", "zz"),
    "hallgat-este.svg": ("closed", None),
    "bocsanat.svg": ("sorry", "drop"),
}


def add_variants(layers, base_keys):
    for name, (mood, afar) in VARIANTS.items():
        for s in shapes_of(os.path.join(POSES, name)):
            if s["key"] in base_keys:
                continue
            cx, cy, _ = centre(s)
            if cx < 66 or cx > 134:
                if afar: layers[afar].append(s)
            elif cy < 86:
                if mood == "sorry": layers[f"brow_{side(cx)}_sorry"].append(s)
            elif cy < 102:
                if mood in ("happy", "sleepy", "closed"): layers[f"eye_{side(cx)}_{mood}"].append(s)
            else:
                if mood in ("happy", "sorry", "sleepy"): layers[f"mouth_{mood}"].append(s)
    return layers


def write(layers):
    lines = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240">',
             "  <!-- Generated by tools/poses2mascot.py from docs/spec/0.2.0/design/dalma/*.svg: the designer's",
             "       poses, baked into one layered drawing. Edit the poses, not this file. -->"]
    for name in ORDER:
        lines.append(f'  <g id="{name}">')
        for s in layers[name]:
            attrs = " ".join(f'{k}="{v}"' for k, v in s["attrs"].items())
            ident = f' id="{s["id"]}"' if s.get("id") else ""
            if s["tag"] == "path":
                lines.append(f'    <path{ident} {attrs} d="{s["d"]}"/>')
            else:
                lines.append(f'    <ellipse{ident} {attrs} cx="{fmt(s["cx"])}" cy="{fmt(s["cy"])}" rx="{fmt(s["rx"])}" ry="{fmt(s["ry"])}"/>')
        lines.append("  </g>")
    lines.append("</svg>")
    with open(TARGET, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    base = shapes_of(os.path.join(POSES, "alap.svg"))
    layers = add_variants(base_layers(base), {s["key"] for s in base})
    write(layers)
    print("layers:", ", ".join(f"{n}({len(layers[n])})" for n in ORDER))
    empty = [n for n in ORDER if not layers[n]]
    if empty:
        print("EMPTY:", empty)
