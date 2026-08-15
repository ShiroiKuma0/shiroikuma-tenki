#!/usr/bin/env python3
"""Emit `ic_meteomap.xml` — Czechia in line-art, sun over Bohemia, storm over Moravia.

The border is traced from the very ORP boundaries the app already ships for alert geocoding
(`app/src/main/res/raw/chmi_orp.json`): the 206 districts are filled into one silhouette, the
seams welded shut, the national border pulled out as a contour and simplified. So the emblem is
the same geometry the Meteomap draws, not a hand copy of it.

Both motifs are then **fit-checked against what actually ships**: the emitted path data is
rasterised and shrunk until no pixel of it lands outside the country eroded by the border width
plus a margin. Neither can touch the border, whatever is changed above.

Run from the repo root:  python3 tools/chmi/emit_meteomap_icon.py
"""
import json
import math
import os

import numpy as np
import matplotlib.pyplot as plt
from PIL import Image, ImageDraw
from scipy.ndimage import (
    binary_closing,
    binary_erosion,
    binary_fill_holes,
    distance_transform_edt,
    gaussian_filter,
)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ORP = os.path.join(ROOT, "app/src/main/res/raw/chmi_orp.json")
OUT = os.path.join(ROOT, "app/src/main/res/drawable/ic_meteomap.xml")

V = 960                  # viewport, square
BORDER_W = 20            # the border reads heavier than the motifs inside it
MOTIF_W = 13
CLEARANCE = 34           # empty ground demanded between a motif and the border
COUNTRY_WIDTH = 0.94     # of the viewport

# Chosen by 白い熊: sun a little right of the roomiest point in Bohemia, storm well into
# Moravia at 82% — the two halves each occupied, neither motif near the border.
SUN_SHIFT = 0.03
STORM_SHIFT = 0.10
STORM_SCALE = 0.82


# ------------------------------------------------------------------ the border
def rdp(points, eps):
    keep = np.zeros(len(points), bool)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        i0, i1 = stack.pop()
        if i1 <= i0 + 1:
            continue
        a, b = points[i0], points[i1]
        ab = b - a
        n = math.hypot(*ab)
        seg = points[i0 + 1:i1]
        dist = np.abs(np.cross(ab, seg - a)) / n if n > 0 else np.hypot(*(seg - a).T)
        j = int(dist.argmax())
        if dist[j] > eps:
            keep[i0 + 1 + j] = True
            stack += [(i0, i0 + 1 + j), (i0 + 1 + j, i1)]
    return points[keep]


def border(sigma=6.0, eps=8.0, raster=3000):
    rings = []
    for feature in json.load(open(ORP))["features"]:
        geometry = feature["geometry"]
        polygons = (
            [geometry["coordinates"]] if geometry["type"] == "Polygon" else geometry["coordinates"]
        )
        for polygon in polygons:
            for ring in polygon:
                rings.append(np.array(ring, dtype=float))

    lon0 = min(r[:, 0].min() for r in rings)
    lon1 = max(r[:, 0].max() for r in rings)
    lat0 = min(r[:, 1].min() for r in rings)
    lat1 = max(r[:, 1].max() for r in rings)
    height = int(
        raster * (lat1 - lat0) / (lon1 - lon0) / math.cos(math.radians((lat0 + lat1) / 2))
    )
    # A margin of empty raster all round: a shape touching the edge makes the contour run along
    # the border rather than round the country, which traces a triangle.
    margin = 40
    canvas = Image.new("L", (raster + 2 * margin, height + 2 * margin), 0)
    draw = ImageDraw.Draw(canvas)
    for ring in rings:
        xy = [
            (
                margin + (x - lon0) / (lon1 - lon0) * (raster - 1),
                margin + (lat1 - y) / (lat1 - lat0) * (height - 1),
            )
            for x, y in ring
        ]
        if len(xy) > 2:
            draw.polygon(xy, fill=255)
    # Close the hairline seams between adjacent districts before tracing, so the contour follows
    # the national border rather than wandering between them. Blur alone would need to be so
    # strong that it melts the As spur and the Sluknov hook into a lump.
    filled = binary_fill_holes(binary_closing(np.array(canvas) > 127, np.ones((3, 3))))
    segments = [
        s
        for level in plt.contour(gaussian_filter(filled.astype(float), sigma), levels=[0.5]).allsegs
        for s in level
    ]
    plt.close("all")
    return rdp(max(segments, key=len), eps)


RING = border()
_x, _y = RING[:, 0], RING[:, 1]
_w, _h = _x.max() - _x.min(), _y.max() - _y.min()
_k = V * COUNTRY_WIDTH / _w
PTS = [
    (
        (V - _w * _k) / 2 + (px - _x.min()) * _k,
        V / 2 - _h * _k / 2 + (py - _y.min()) * _k,
    )
    for px, py in zip(_x, _y)
]

_mask = Image.new("L", (V, V), 0)
ImageDraw.Draw(_mask).polygon(PTS, fill=255)
FILLED = np.array(_mask) > 127
SAFE = binary_erosion(FILLED, np.ones((3, 3)), iterations=(BORDER_W // 2 + CLEARANCE) // 2)
DISTANCE = distance_transform_edt(SAFE)
LEFT_X = min(p[0] for p in PTS)
RIGHT_X = max(p[0] for p in PTS)
WIDTH = RIGHT_X - LEFT_X
MID_X = (LEFT_X + RIGHT_X) / 2


def roomiest(left):
    half = SAFE.copy()
    xs = np.arange(V)[None, :]
    half &= (xs < MID_X) if left else (xs >= MID_X)
    dist = distance_transform_edt(half)
    iy, ix = np.unravel_index(dist.argmax(), dist.shape)
    return float(ix), float(iy)


def at_column(x):
    """The roomiest y in one column, and the room there."""
    col = int(round(max(0, min(V - 1, x))))
    ys = DISTANCE[:, col]
    iy = int(ys.argmax())
    return (float(col), float(iy)), float(ys[iy])


# ------------------------------------------------------------------ the motifs
def sun(cx, cy, s):
    """A disc with twelve short rays."""
    r = s * 0.50
    out = [
        f"M{cx - r:.1f},{cy:.1f}"
        f"A{r:.1f},{r:.1f} 0 1,1 {cx + r:.1f},{cy:.1f}"
        f"A{r:.1f},{r:.1f} 0 1,1 {cx - r:.1f},{cy:.1f}Z"
    ]
    for i in range(12):
        a = 2 * math.pi * i / 12
        x0, y0 = cx + math.cos(a) * r * 1.35, cy + math.sin(a) * r * 1.35
        x1, y1 = cx + math.cos(a) * r * 1.69, cy + math.sin(a) * r * 1.69
        out.append(f"M{x0:.1f},{y0:.1f}L{x1:.1f},{y1:.1f}")
    return out


def storm(cx, cy, s):
    """A cloud with a bolt under it."""
    w = s * 0.64                       # the cloud's half width
    ox, oy = cx, cy - s * 0.28
    cloud = (
        f"M{ox - w:.1f},{oy:.1f}"
        f"A{w * 0.622:.1f},{w * 0.622:.1f} 0 0,1 {ox + w * 0.203:.1f},{oy - w * 0.392:.1f}"
        f"A{w * 0.500:.1f},{w * 0.500:.1f} 0 0,1 {ox + w:.1f},{oy:.1f}"
        f"A{w * 0.392:.1f},{w * 0.392:.1f} 0 0,1 {ox + w * 0.959:.1f},{oy + w * 0.392:.1f}"
        f"L{ox - w * 0.959:.1f},{oy + w * 0.392:.1f}"
        f"A{w * 0.392:.1f},{w * 0.392:.1f} 0 0,1 {ox - w:.1f},{oy:.1f}Z"
    )
    k = s * 0.020
    bx, by = cx, cy + s * 0.34
    shape = [(0, -22), (-13, 6), (-1, 6), (-8, 30), (16, -2), (2, -2), (9, -22)]
    pts = [(bx + a * k, by + b * k) for a, b in shape]
    bolt = "M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + "Z"
    return [cloud, bolt]


# ------------------------------------------------------- fit against what ships
def sample_arc(x0, y0, x1, y1, r, sweep, large):
    """A circular arc as points, so emitted path data can be rasterised and checked."""
    dx, dy = x1 - x0, y1 - y0
    span = math.hypot(dx, dy)
    if span == 0:
        return [(x0, y0)]
    r = max(r, span / 2)
    h = math.sqrt(max(r * r - (span / 2) ** 2, 0))
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    ux, uy = -dy / span, dx / span
    sign = 1 if (sweep != large) else -1
    cx, cy = mx + sign * h * ux, my + sign * h * uy
    a0 = math.atan2(y0 - cy, x0 - cx)
    a1 = math.atan2(y1 - cy, x1 - cx)
    if sweep and a1 < a0:
        a1 += 2 * math.pi
    if not sweep and a1 > a0:
        a1 -= 2 * math.pi
    return [
        (cx + r * math.cos(a0 + (a1 - a0) * t / 32), cy + r * math.sin(a0 + (a1 - a0) * t / 32))
        for t in range(33)
    ]


def stroke(draw, paths, width):
    """Rasterise emitted path data — the same strings that go into the drawable."""
    import re

    for path in paths:
        pts = []
        cur = (0.0, 0.0)
        for cmd, args in re.findall(r"([MLAZ])([^MLAZ]*)", path):
            nums = [float(v) for v in re.findall(r"-?\d+(?:\.\d+)?", args)]
            if cmd == "M":
                cur = (nums[0], nums[1])
                pts.append(cur)
            elif cmd == "L":
                for i in range(0, len(nums), 2):
                    cur = (nums[i], nums[i + 1])
                    pts.append(cur)
            elif cmd == "A":
                rx, _ry, _rot, large, sweep, x, y = nums[:7]
                pts += sample_arc(cur[0], cur[1], x, y, rx, sweep == 1, large == 1)[1:]
                cur = (x, y)
            elif cmd == "Z" and pts:
                pts.append(pts[0])
        if len(pts) > 1:
            draw.line(pts, fill=255, width=width, joint="curve")


def fitted(motif, cx, cy, room, scale=1.0):
    """Shrink until nothing the motif draws lands outside the safe ground."""
    size = room * 2.0 * scale
    for _ in range(30):
        probe = Image.new("L", (V, V), 0)
        stroke(ImageDraw.Draw(probe), motif(cx, cy, size), MOTIF_W)
        if not ((np.array(probe) > 0) & ~SAFE).any():
            return motif(cx, cy, size)
        size *= 0.92
    raise SystemExit("could not fit a motif inside the border")


TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!--
    shiroikuma fork: the Meteomap emblem — Czechia in line-art, the sun over Bohemia and a storm
    over Moravia.

    GENERATED by tools/chmi/emit_meteomap_icon.py from app/src/main/res/raw/chmi_orp.json, the
    same boundaries the map itself is drawn from. Do not hand-edit: change the script and re-run.
    Border simplified to {count} points; both motifs verified clear of it.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
{paths}</vector>
"""

PATH = """    <path
        android:pathData="{data}"
        android:fillColor="#00000000"
        android:strokeColor="@android:color/white"
        android:strokeWidth="{width}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
"""


def main():
    country = (
        "M"
        + "L".join(f"{x:.1f},{y:.1f}" for x, y in PTS)
        + "Z"
    )
    sun_x, _ = roomiest(left=True)
    storm_x, _ = roomiest(left=False)
    (sx, sy), sun_room = at_column(sun_x + WIDTH * SUN_SHIFT)
    (tx, ty), storm_room = at_column(storm_x + WIDTH * STORM_SHIFT)

    paths = [PATH.format(data=country, width=BORDER_W)]
    for data in fitted(sun, sx, sy, sun_room):
        paths.append(PATH.format(data=data, width=MOTIF_W))
    for data in fitted(storm, tx, ty, storm_room, STORM_SCALE):
        paths.append(PATH.format(data=data, width=MOTIF_W))

    with open(OUT, "w") as handle:
        handle.write(TEMPLATE.format(count=len(PTS), paths="".join(paths)))
    print(f"wrote {OUT} ({len(PTS)} border points, {len(paths)} paths)")


if __name__ == "__main__":
    main()
