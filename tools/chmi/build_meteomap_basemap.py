#!/usr/bin/env python3
"""Build `meteomap_basemap.json` — the ground under the Meteomap that ČHMÚ does not draw.

ČHMÚ's frames are bare fields: colour and nothing else, meant to sit under the website's own
basemap. The country itself we already have (`chmi_orp.json`, shipped for alert geocoding), but
without its neighbours the map reads as an island floating in a void, and without water it has no
landmarks at all.

Source: **Natural Earth**, which is explicitly public domain — no attribution required, no licence
to honour. Fetched at build time and baked into a single small GeoJSON so the app never touches a
tile server.

Run from the repo root:  python3 tools/chmi/build_meteomap_basemap.py
"""
import json
import math
import os
import urllib.request

import numpy as np
import matplotlib.pyplot as plt
from PIL import Image, ImageDraw
from scipy.ndimage import binary_closing, binary_fill_holes, gaussian_filter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app/src/main/res/raw/meteomap_basemap.json")
ORP = os.path.join(ROOT, "app/src/main/res/raw/chmi_orp.json")

BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"
COUNTRIES = BASE + "ne_50m_admin_0_countries.geojson"
RIVERS = BASE + "ne_10m_rivers_europe.geojson"
LAKES = BASE + "ne_10m_lakes_europe.geojson"   # 50m has none at all in this region

# A little wider than the ALADIN and radar extents (11.267–19.638 / 48.047–51.467) so lines run
# off the edge of the map rather than stopping short of it.
BOX = (10.6, 47.6, 20.3, 51.9)

# How hard to simplify. The map is at most a phone screen wide, so a tenth of a degree of detail
# is far below what can be seen; these are tuned to keep the file small.
EPS_COUNTRY = 0.010
EPS_RIVER = 0.008
EPS_LAKE = 0.006

MIN_RIVER_POINTS = 4

# How close a neighbour's outline has to run to the Czech frontier before it counts as the same
# line, and how far an end may be pulled to meet that frontier. See inter_country().
NEAR_FRONTIER = 0.06
ANCHOR_REACH = 0.45
MIN_LAKE_AREA = 0.0006          # square degrees; drops the ponds


def fetch(url):
    print(f"  fetching {os.path.basename(url)} …")
    request = urllib.request.Request(url, headers={"User-Agent": "shiroikuma-tenki/basemap"})
    return json.loads(urllib.request.urlopen(request, timeout=180).read())


def rdp(points, eps):
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        i0, i1 = stack.pop()
        if i1 <= i0 + 1:
            continue
        (x0, y0), (x1, y1) = points[i0], points[i1]
        dx, dy = x1 - x0, y1 - y0
        span = math.hypot(dx, dy)
        best, best_d = -1, 0.0
        for i in range(i0 + 1, i1):
            x, y = points[i]
            d = (
                abs(dy * x - dx * y + x1 * y0 - y1 * x0) / span
                if span > 0
                else math.hypot(x - x0, y - y0)
            )
            if d > best_d:
                best, best_d = i, d
        if best_d > eps:
            keep[best] = True
            stack += [(i0, best), (best, i1)]
    return [p for p, k in zip(points, keep) if k]


def inside(point, box):
    x, y = point
    return box[0] <= x <= box[2] and box[1] <= y <= box[3]


def clip_polygon(ring, box):
    """Sutherland–Hodgman against the four edges of the box."""
    def clip(points, keep, intersect):
        out = []
        for i in range(len(points)):
            a, b = points[i - 1], points[i]
            ka, kb = keep(a), keep(b)
            if kb:
                if not ka:
                    out.append(intersect(a, b))
                out.append(b)
            elif ka:
                out.append(intersect(a, b))
        return out

    x0, y0, x1, y1 = box

    def lerp(a, b, t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    ring = clip(ring, lambda p: p[0] >= x0, lambda a, b: lerp(a, b, (x0 - a[0]) / (b[0] - a[0])))
    if not ring:
        return []
    ring = clip(ring, lambda p: p[0] <= x1, lambda a, b: lerp(a, b, (x1 - a[0]) / (b[0] - a[0])))
    if not ring:
        return []
    ring = clip(ring, lambda p: p[1] >= y0, lambda a, b: lerp(a, b, (y0 - a[1]) / (b[1] - a[1])))
    if not ring:
        return []
    return clip(ring, lambda p: p[1] <= y1, lambda a, b: lerp(a, b, (y1 - a[1]) / (b[1] - a[1])))


def clip_line(coords, box):
    """Split a line into the runs that touch the box, keeping one point either side."""
    runs, run = [], []
    for i, point in enumerate(coords):
        near = inside(point, box) or (i > 0 and inside(coords[i - 1], box))
        if near:
            run.append(point)
        elif run:
            runs.append(run)
            run = []
    if run:
        runs.append(run)
    return runs


def rings_of(geometry):
    kind = geometry.get("type")
    if kind == "Polygon":
        return [geometry["coordinates"]]
    if kind == "MultiPolygon":
        return geometry["coordinates"]
    return []


def lines_of(geometry):
    kind = geometry.get("type")
    if kind == "LineString":
        return [geometry["coordinates"]]
    if kind == "MultiLineString":
        return geometry["coordinates"]
    return []


def area(ring):
    total = 0.0
    for i in range(len(ring)):
        x0, y0 = ring[i - 1]
        x1, y1 = ring[i]
        total += x0 * y1 - x1 * y0
    return abs(total) / 2


def round_ring(ring, places=4):
    return [[round(x, places), round(y, places)] for x, y in ring]


def inter_country(outlines, border):
    """Only where third countries meet EACH OTHER, running right up to the Czech frontier.

    Every neighbour's outline also follows the Czech border, and drawing that stretch lays a second
    heavy line over the one already drawn in the accent. So the coincident part is cut out — and
    then each cut end is pulled onto the nearest point of the frontier, because otherwise the run
    stops a few kilometres short and leaves a gap at the tripoint.

    The two thresholds are measured rather than guessed: ends cut by the frontier sit 0.07-0.19 deg
    from it, ends cut by the edge of the map at 0.89 deg and beyond, so anything in between tells
    the two apart.
    """
    ring = np.array(border, dtype=float)
    runs = []
    for outline in outlines:
        points = np.array(outline, dtype=float)
        distance = np.sqrt(
            ((points[:, None, 0] - ring[None, :, 0]) ** 2)
            + ((points[:, None, 1] - ring[None, :, 1]) ** 2)
        ).min(1)
        run = []
        for i, far in enumerate(distance > NEAR_FRONTIER):
            if far:
                run.append(tuple(points[i]))
            else:
                if len(run) > 1:
                    runs.append(run)
                run = []
        if len(run) > 1:
            runs.append(run)

    anchored = []
    for run in runs:
        out = list(run)
        for end in (0, -1):
            point = np.array(out[end])
            nearest = ring[np.argmin(((ring - point) ** 2).sum(1))]
            if math.hypot(*(nearest - point)) > ANCHOR_REACH:
                continue
            if end == 0:
                out.insert(0, tuple(nearest))
            else:
                out.append(tuple(nearest))
        anchored.append(out)
    return anchored


def czech_border(sigma=4.0, eps=0.004, raster=2600):
    """The national border, traced from the union of the 206 ORP districts.

    Drawn separately from the districts so the country can read as one shape with its internal
    detail kept quiet — stroking all 206 rings at the same weight makes a thicket.
    """
    rings = []
    for feature in json.load(open(ORP))["features"]:
        for polygon in rings_of(feature["geometry"]):
            for ring in polygon:
                rings.append(np.array(ring, dtype=float))
    lon0 = min(r[:, 0].min() for r in rings)
    lon1 = max(r[:, 0].max() for r in rings)
    lat0 = min(r[:, 1].min() for r in rings)
    lat1 = max(r[:, 1].max() for r in rings)
    height = int(
        raster * (lat1 - lat0) / (lon1 - lon0) / math.cos(math.radians((lat0 + lat1) / 2))
    )
    margin = 30
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
    # Weld the hairline seams between adjacent districts before tracing, or the contour wanders
    # between them instead of following the border.
    filled = binary_fill_holes(binary_closing(np.array(canvas) > 127, np.ones((3, 3))))
    segments = [
        s
        for level in plt.contour(gaussian_filter(filled.astype(float), sigma), levels=[0.5]).allsegs
        for s in level
    ]
    plt.close("all")
    pixels = max(segments, key=len)
    degrees = [
        (
            lon0 + (px - margin) / (raster - 1) * (lon1 - lon0),
            lat1 - (py - margin) / (height - 1) * (lat1 - lat0),
        )
        for px, py in pixels
    ]
    return rdp(degrees, eps)


def main():
    features = []

    print("czech border…")
    border_ring = czech_border()
    border_ring = [tuple(p) for p in border_ring]
    features.append({
        "type": "Feature",
        "properties": {"kind": "border"},
        "geometry": {"type": "Polygon", "coordinates": [round_ring(border_ring, 5)]},
    })

    print("third-country boundaries…")
    outlines = []
    for feature in fetch(COUNTRIES)["features"]:
        props = feature.get("properties", {})
        iso = props.get("ISO_A2") or props.get("ISO_A2_EH") or ""
        # Czechia is drawn from our own ORP boundaries, in far more detail than this.
        if iso == "CZ":
            continue
        for polygon in rings_of(feature["geometry"]):
            for ring in polygon[:1]:                     # outline only, holes are invisible here
                clipped = clip_polygon([tuple(p) for p in ring], BOX)
                if len(clipped) < 4:
                    continue
                simplified = rdp(clipped, EPS_COUNTRY)
                if len(simplified) >= 4:
                    outlines.append(simplified)
    for run in inter_country(outlines, border_ring):
        features.append({
            "type": "Feature",
            "properties": {"kind": "boundary"},
            "geometry": {"type": "LineString", "coordinates": round_ring(run, 5)},
        })

    print("rivers…")
    for feature in fetch(RIVERS)["features"]:
        for line in lines_of(feature["geometry"]):
            for run in clip_line([tuple(p) for p in line], BOX):
                simplified = rdp(run, EPS_RIVER)
                if len(simplified) < MIN_RIVER_POINTS:
                    continue
                features.append({
                    "type": "Feature",
                    "properties": {"kind": "river"},
                    "geometry": {"type": "LineString", "coordinates": round_ring(simplified)},
                })

    print("lakes…")
    for feature in fetch(LAKES)["features"]:
        for polygon in rings_of(feature["geometry"]):
            for ring in polygon[:1]:
                clipped = clip_polygon([tuple(p) for p in ring], BOX)
                if len(clipped) < 4 or area(clipped) < MIN_LAKE_AREA:
                    continue
                simplified = rdp(clipped, EPS_LAKE)
                if len(simplified) < 4:
                    continue
                features.append({
                    "type": "Feature",
                    "properties": {"kind": "lake"},
                    "geometry": {"type": "Polygon", "coordinates": [round_ring(simplified)]},
                })

    collection = {"type": "FeatureCollection", "features": features}
    with open(OUT, "w") as handle:
        json.dump(collection, handle, separators=(",", ":"))
    counts = {}
    for f in features:
        counts[f["properties"]["kind"]] = counts.get(f["properties"]["kind"], 0) + 1
    print(f"wrote {OUT}  {os.path.getsize(OUT) // 1024} KB  {counts}")


if __name__ == "__main__":
    main()
