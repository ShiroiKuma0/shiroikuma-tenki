#!/usr/bin/env python3
"""Cut the 白い熊 天気 weather-icon packs from one geometry model.

Upstream Breezy Weather ships filled, gradient-shaded weather icons. This draws the same
twelve weather codes in the house black-yellow — yellow (#FFFF00) on nothing, so the app's
black grounds show through — in two packs cut from the *same* geometry:

  traced   stroke-only line-art, like the launcher mark
  full     the same silhouettes, filled solid

A full mark is stroked as well as filled, at the same width, so its outer edge lands
exactly where the traced one's does and the two packs register pixel for pixel. Shapes
with no interior to fill — the sun's rays, the snowflakes, the fog bars, the wind gusts —
are strokes in both, and so come out identical.

Each pack gets every file shape the icon-provider contract asks for:

  app/src/res_fork/drawable/tenki_<pack>_weather_*.png        256 px, the animated set
                                                       (a composite plus its layers)
  app/src/res_fork/drawable/tenki_<pack>_weather_*_mini_*.png 192 px, the widget/notification
                                                       minis in light / grey / dark
  app/src/res_fork/drawable/tenki_<pack>_weather_*_mini_xml.xml  24 dp vector, the
                                                       notification small icon
  app/src/res_fork/drawable/tenki_<pack>_shortcuts_*.png      192 px launcher-shortcut badge
                                                       and 768 px adaptive foreground
  app/src/res_fork/xml/tenki_icon_provider_*_filter.xml   the three name filters
                                                       TenkiResourceProvider reads

The filters name resources *without* the `tenki_<pack>_` head, which the provider puts back
on, so one set of filters serves every pack.

Everything is built from one 256-unit design space, so a mark drawn once shows up in
the animated icon, the minis and the shortcut badge without being redrawn.

The layer split matches the animators upstream already ships (which our animator filter
reuses): layer 1 is what shakes or drifts, layers 2 and 3 are what falls or turns. Layer
1 is drawn on top — AnimatableIconView adds its image views in reverse.

Line-art has no fills to hide an overlap with, so the composites are laid out disjoint:
in "partly cloudy" the sun sits clear of the cloud rather than behind it.

Re-runnable: same input, same bytes out. Needs `rsvg-convert` and ImageMagick (`magick`).

Usage:  python3 tools/icon/emit_weather_icons.py [repo-root]
"""
import math
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(HERE))

INK = "#FFFF00"          # house yellow
PAPER = "#000000"        # house black

V = 256.0                # the design viewport, square
STROKE = 16.0            # stroke width of the animated set
STROKE_MINI = 20.0       # heavier, because a mini is read at widget size

DRAWABLE_DIR = "app/src/res_fork/drawable"
XML_DIR = "app/src/res_fork/xml"

# The packs, and the head every one of their drawables carries. Must match
# TenkiResourceProvider.Variant.
PACKS = {"traced": "tenki_traced_", "full": "tenki_full_"}

# Anything matching this in the drawable folder belongs to this script, and is swept away
# if a run no longer writes it. Narrow enough to leave tenki_dialog_background.xml alone.
OWNED = re.compile(r"^tenki_(?:traced_|full_)?(?:weather|shortcuts)_")

# A name no resource carries, so a filter entry pointing at it resolves to id 0 and the
# provider hands back null — which is how a mark says "I have no third layer".
MISSING = "missing"


# --------------------------------------------------------------------------- geometry


def n(v):
    """A number, as short as it can be written without losing the drawing."""
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pt(x, y):
    return f"{n(x)},{n(y)}"


def polar(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def line(x1, y1, x2, y2):
    return f"M {pt(x1, y1)} L {pt(x2, y2)}"


def circle(cx, cy, r):
    """A full circle, as two half-arcs (SVG cannot arc 360° in one go)."""
    return (
        f"M {pt(cx - r, cy)} "
        f"A {n(r)},{n(r)} 0 1,0 {pt(cx + r, cy)} "
        f"A {n(r)},{n(r)} 0 1,0 {pt(cx - r, cy)} Z"
    )


def arc_to(cx, cy, r, a_from, a_to):
    """`A` command sweeping from angle a_from to a_to around (cx, cy).

    Angles are degrees in screen space (x right, y down), so a growing angle turns
    clockwise on screen.
    """
    sweep = 1 if a_to > a_from else 0
    large = 1 if abs(a_to - a_from) > 180 else 0
    x, y = polar(cx, cy, r, a_to)
    return f"A {n(r)},{n(r)} 0 {large},{sweep} {pt(x, y)}"


def circle_intersections(c1, r1, c2, r2):
    """The two points where circles (c1,r1) and (c2,r2) cross, upper one first."""
    (x1, y1), (x2, y2) = c1, c2
    dx, dy = x2 - x1, y2 - y1
    d = math.hypot(dx, dy)
    a = (d * d + r1 * r1 - r2 * r2) / (2 * d)
    h = math.sqrt(max(r1 * r1 - a * a, 0.0))
    mx, my = x1 + a * dx / d, y1 + a * dy / d
    px, py = -dy / d, dx / d
    p, q = (mx + h * px, my + h * py), (mx - h * px, my - h * py)
    return (p, q) if p[1] <= q[1] else (q, p)


def angle_of(centre, point):
    return math.degrees(math.atan2(point[1] - centre[1], point[0] - centre[0])) % 360.0


def arc_via(cx, cy, r, p_from, p_to, via_deg):
    """`A` command from p_from to p_to, taking whichever way round passes via_deg."""
    a0, a1 = angle_of((cx, cy), p_from), angle_of((cx, cy), p_to)
    forward = (a1 - a0) % 360.0
    if (via_deg - a0) % 360.0 <= forward:
        return arc_to(cx, cy, r, a0, a0 + forward)
    return arc_to(cx, cy, r, a0, a0 - (360.0 - forward))


# The cloud, drawn once here as the union outline of three lobes cut off by a flat base,
# then scaled and moved wherever a mark needs one. Numbers are in design space.
_LOBES = [((62.0, 138.0), 44.0), ((120.0, 108.0), 52.0), ((186.0, 142.0), 40.0)]
_BASE = 178.0


def _cloud_template():
    """The cloud outline as a path plus its bounding box, in template coordinates."""
    (lc, lr), (mc, mr), (rc, rr) = _LOBES
    start = (lc[0] - math.sqrt(lr * lr - (_BASE - lc[1]) ** 2), _BASE)
    end = (rc[0] + math.sqrt(rr * rr - (_BASE - rc[1]) ** 2), _BASE)
    lm, _ = circle_intersections(lc, lr, mc, mr)
    mr_, _ = circle_intersections(mc, mr, rc, rr)

    def clockwise(centre, radius, p_from, p_to):
        a0, a1 = angle_of(centre, p_from), angle_of(centre, p_to)
        if a1 <= a0:
            a1 += 360.0
        return arc_to(centre[0], centre[1], radius, a0, a1)

    d = " ".join(
        [
            f"M {pt(*start)}",
            clockwise(lc, lr, start, lm),
            clockwise(mc, mr, lm, mr_),
            clockwise(rc, rr, mr_, end),
            "Z",
        ]
    )
    left = min(c[0] - r for c, r in _LOBES)
    right = max(c[0] + r for c, r in _LOBES)
    top = min(c[1] - r for c, r in _LOBES)
    return d, (left, top, right - left, _BASE - top)


_CLOUD_D, _CLOUD_BOX = _cloud_template()


def transform_path(d, sx, sy, tx, ty):
    """Rewrite a path's coordinates through scale-then-translate.

    Only the commands this file emits are handled: M, L, A, Z.
    """
    out, i, tokens = [], 0, d.replace(",", " ").split()
    while i < len(tokens):
        cmd = tokens[i]
        i += 1
        if cmd == "Z":
            out.append("Z")
        elif cmd in ("M", "L"):
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            out.append(f"{cmd} {pt(x * sx + tx, y * sy + ty)}")
        elif cmd == "A":
            rx, ry, rot, large, sweep = (float(tokens[i + k]) for k in range(5))
            x, y = float(tokens[i + 5]), float(tokens[i + 6])
            i += 7
            out.append(
                f"A {n(rx * sx)},{n(ry * sy)} {n(rot)} {int(large)},{int(sweep)} "
                f"{pt(x * sx + tx, y * sy + ty)}"
            )
        else:
            raise ValueError(f"unhandled path command {cmd!r}")
    return " ".join(out)


def cloud(x, y, w):
    """A cloud whose outline's bounding box starts at (x, y) and is `w` wide."""
    bx, by, bw, bh = _CLOUD_BOX
    s = w / bw
    return transform_path(_CLOUD_D, s, s, x - bx * s, y - by * s)


def sun_rays(cx, cy, r_in, r_out, count=8, phase=0.0):
    step = 360.0 / count
    return [
        line(*polar(cx, cy, r_in, phase + i * step), *polar(cx, cy, r_out, phase + i * step))
        for i in range(count)
    ]


def crescent(cx, cy, r, bite_deg=-52.0, thickness=0.55, bite_r=0.95):
    """A moon: the disc at (cx, cy, r) with a second disc taken out of one side.

    `bite_deg` points at the side that is eaten (-52° = upper right), `thickness` is how
    much of the diameter the crescent keeps, `bite_r` the cutting disc's radius.
    """
    rc = bite_r * r
    d = thickness * 2 * r - r + rc  # so the crescent is exactly that thick on the axis
    bite = polar(cx, cy, d, bite_deg)
    far = bite_deg + 180.0
    p1, p2 = circle_intersections((cx, cy), r, bite, rc)
    return " ".join(
        [
            f"M {pt(*p1)}",
            arc_via(cx, cy, r, p1, p2, far),            # the long way round the outer disc
            arc_via(bite[0], bite[1], rc, p2, p1, far),  # back along the bite
            "Z",
        ]
    )


def drop(cx, y_tip, h, bulb=0.36):
    """A teardrop: apex at (cx, y_tip), `h` tall, its bulb `bulb`·h in radius."""
    r = bulb * h
    cy = y_tip + h - r
    alpha = math.degrees(math.acos(r / (cy - y_tip)))
    # Tangent points sit either side of the line from the bulb's centre up to the apex.
    left, right = 270.0 - alpha, 270.0 + alpha
    return " ".join(
        [
            f"M {pt(cx, y_tip)}",
            f"L {pt(*polar(cx, cy, r, left))}",
            arc_to(cx, cy, r, left, right - 360.0),  # down the left, round the bottom, up
            "Z",
        ]
    )


def flake(cx, cy, r):
    """A snowflake: three diameters crossing at 60°."""
    return [line(*polar(cx, cy, r, a), *polar(cx, cy, r, a + 180.0)) for a in (90.0, 30.0, 150.0)]


def bolt(cx, y_top, h):
    """A lightning bolt, as a closed zig-zag `h` tall centred on cx."""
    w = 0.74 * h
    pts = [
        (0.62, 0.00),
        (0.06, 0.58),
        (0.40, 0.58),
        (0.34, 1.00),
        (0.94, 0.40),
        (0.58, 0.40),
    ]
    x0 = cx - w / 2
    steps = [f"M {pt(x0 + pts[0][0] * w, y_top + pts[0][1] * h)}"]
    steps += [f"L {pt(x0 + px * w, y_top + py * h)}" for px, py in pts[1:]]
    steps.append("Z")
    return " ".join(steps)


def gust(x_left, y, x_hook, r):
    """A wind line running right, then curling forward over a hook of radius `r`."""
    # 240° of curl, so the free end lifts clear of the line instead of closing on it.
    return f"M {pt(x_left, y)} L {pt(x_hook, y)} " + arc_to(x_hook, y - r, r, 90.0, -150.0)


# ------------------------------------------------------------------------------ marks

# Each mark is a list of layers; each layer is a list of paths. Layer 1 comes first and
# is drawn on top.


def _precip_cloud():
    return cloud(30, 24, 196)


def _small_cloud():
    return cloud(12, 112, 176)


def _mark_clear_day():
    return [[circle(128, 128, 54)], sun_rays(128, 128, 78, 108)]


def _mark_clear_night():
    return [[crescent(128, 130, 98)]]


def _mark_partly_cloudy_day():
    return [
        [_small_cloud()],
        [circle(178, 74, 28)],
        sun_rays(178, 74, 42, 58),
    ]


def _mark_partly_cloudy_night():
    return [[_small_cloud()], [crescent(182, 74, 48)]]


def _mark_cloudy():
    return [[cloud(14, 116, 184)], [cloud(120, 28, 118)]]


def _mark_rain():
    return [
        [_precip_cloud()],
        [drop(68, 166, 46), drop(128, 166, 46)],
        [drop(190, 170, 58)],
    ]


def _mark_snow():
    return [
        [_precip_cloud()],
        flake(66, 190, 21) + flake(126, 190, 21),
        flake(192, 192, 27),
    ]


def _mark_sleet():
    return [
        [_precip_cloud()],
        [drop(70, 166, 46), drop(188, 170, 50)],
        flake(130, 192, 24),
    ]


def _mark_hail():
    return [
        [_precip_cloud()],
        [circle(72, 190, 18), circle(130, 190, 18)],
        [circle(192, 192, 23)],
    ]


def _mark_fog():
    bars = [
        (44, 212, 72),
        (22, 190, 106),
        (52, 234, 140),
        (30, 198, 174),
        (60, 216, 208),
    ]
    d = [line(x1, y, x2, y) for x1, x2, y in bars]
    return [[d[0], d[1]], [d[3], d[4]], [d[2]]]


def _mark_haze():
    dots = [
        [(58, 58, 19), (198, 70, 14), (120, 130, 25)],
        [(130, 56, 13), (54, 136, 16), (202, 142, 21)],
        [(192, 198, 15), (58, 200, 21), (128, 206, 12)],
    ]
    return [[circle(*d) for d in layer] for layer in dots]


def _mark_wind():
    return [[gust(34, 68, 150, 22), gust(22, 138, 186, 26), gust(42, 204, 140, 18)]]


def _mark_thunder():
    return [[cloud(33, 14, 190)], [bolt(128, 148, 88)]]


def _mark_thunderstorm():
    return [
        [cloud(24, 12, 184)],
        [bolt(100, 146, 86)],
        [drop(192, 152, 56)],
    ]


MARKS = {
    "clear_day": _mark_clear_day(),
    "clear_night": _mark_clear_night(),
    "partly_cloudy_day": _mark_partly_cloudy_day(),
    "partly_cloudy_night": _mark_partly_cloudy_night(),
    "cloudy": _mark_cloudy(),
    "rain": _mark_rain(),
    "snow": _mark_snow(),
    "sleet": _mark_sleet(),
    "hail": _mark_hail(),
    "fog": _mark_fog(),
    "haze": _mark_haze(),
    "wind": _mark_wind(),
    "thunder": _mark_thunder(),
    "thunderstorm": _mark_thunderstorm(),
}

# Which mark each (weather code, time of day) is drawn with. The codes are upstream's
# Constants.RESOURCES_* names; only clear and partly-cloudy differ day from night.
CODES = [
    "clear",
    "partly_cloudy",
    "cloudy",
    "rain",
    "snow",
    "wind",
    "fog",
    "haze",
    "sleet",
    "hail",
    "thunder",
    "thunderstorm",
]
SPLIT_DAY_NIGHT = {"clear", "partly_cloudy"}

# The animator each layer of a mark borrows from upstream's res/animator/.
ANIMATORS = {
    "clear_day": ["weather_clear_day_1", "weather_clear_day_2"],
    "clear_night": ["weather_clear_night_1"],
    "partly_cloudy_day": [
        "weather_partly_cloudy_day_1",
        "weather_partly_cloudy_day_2",
        "weather_partly_cloudy_day_3",
    ],
    "partly_cloudy_night": ["weather_partly_cloudy_night_1", "weather_partly_cloudy_night_2"],
    "cloudy": ["weather_cloudy_1", "weather_cloudy_2"],
    "rain": ["weather_rain_1", "weather_rain_2", "weather_rain_3"],
    "snow": ["weather_snow_1", "weather_snow_2", "weather_snow_3"],
    "sleet": ["weather_sleet_1", "weather_sleet_2", "weather_sleet_3"],
    "hail": ["weather_hail_1", "weather_hail_2", "weather_hail_3"],
    "fog": ["weather_fog_1", "weather_fog_2", "weather_fog_3"],
    "haze": ["weather_haze_1", "weather_haze_2", "weather_haze_3"],
    "wind": ["weather_wind_1"],
    "thunder": ["weather_thunder_1", "weather_thunder_2"],
    "thunderstorm": [
        "weather_thunderstorm_1",
        "weather_thunderstorm_2",
        "weather_thunderstorm_3",
    ],
}


def mark_of(code, daytime):
    if code in SPLIT_DAY_NIGHT:
        return f"{code}_{'day' if daytime else 'night'}"
    return code


# ------------------------------------------------------------------------------ output


def is_closed(path):
    """Whether a path encloses an area — only those get filled in the `full` pack."""
    return path.rstrip().endswith("Z")


def fill_for(path, pack, colour):
    return colour if pack == "full" and is_closed(path) else "none"


def svg(paths, stroke, ink, pack, size=V, ground=None, inset=1.0, halo=None):
    """The given design-space paths as an SVG string.

    `ground` fills behind — a colour, or "circle" for the shortcut badge. `inset` shrinks
    the artwork about the centre. `halo` draws every path once underneath in that colour
    at a wider stroke, so the mark survives an unknown background.
    """
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{n(size)}" height="{n(size)}" '
        f'viewBox="0 0 {n(V)} {n(V)}">'
    ]
    if ground == "circle":
        out.append(f'  <circle cx="{n(V / 2)}" cy="{n(V / 2)}" r="{n(V / 2)}" fill="{PAPER}"/>')
    elif ground:
        out.append(f'  <rect x="0" y="0" width="{n(V)}" height="{n(V)}" fill="{ground}"/>')

    shift = V / 2 * (1 - inset)
    out.append(f'  <g transform="translate({n(shift)},{n(shift)}) scale({n(inset)})">')
    for colour, width in ((halo, stroke + 10.0), (ink, stroke)):
        if colour is None:
            continue
        out.append(
            f'    <g stroke="{colour}" stroke-width="{n(width)}" '
            f'stroke-linecap="round" stroke-linejoin="round">'
        )
        out += [f'      <path d="{p}" fill="{fill_for(p, pack, colour)}"/>' for p in paths]
        out.append("    </g>")
    out += ["  </g>", "</svg>", ""]
    return "\n".join(out)


def vector(paths, stroke, ink, pack, viewport=24.0):
    """The given design-space paths as an Android vector drawable."""
    s = viewport / V
    body = "".join(
        f"    <path\n"
        f'        android:pathData="{transform_path(p, s, s, 0, 0)}"\n'
        f'        android:fillColor="{"#00000000" if fill_for(p, pack, ink) == "none" else ink}"\n'
        f'        android:strokeColor="{ink}"\n'
        f'        android:strokeWidth="{n(stroke * s)}"\n'
        f'        android:strokeLineCap="round"\n'
        f'        android:strokeLineJoin="round" />\n'
        for p in paths
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{n(viewport)}dp"\n'
        f'    android:height="{n(viewport)}dp"\n'
        f'    android:viewportWidth="{n(viewport)}"\n'
        f'    android:viewportHeight="{n(viewport)}">\n' + body + "</vector>\n"
    )


def write(relative_path, text):
    full = os.path.join(REPO, relative_path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as fh:
        fh.write(text)
    return relative_path


def render(svg_text, relative_path, size):
    """SVG text -> PNG (rsvg-convert) -> stripped PNG (ImageMagick)."""
    full = os.path.join(REPO, relative_path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with tempfile.TemporaryDirectory() as scratch:
        tmp_svg = os.path.join(scratch, "render.svg")
        tmp_png = os.path.join(scratch, "render.png")
        with open(tmp_svg, "w") as fh:
            fh.write(svg_text)
        subprocess.run(
            ["rsvg-convert", "-w", str(size), "-h", str(size), tmp_svg, "-o", tmp_png], check=True
        )
        subprocess.run(["magick", tmp_png, "-strip", full], check=True)
    return relative_path


FILTER_HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n\n'


def filter_xml(entries):
    body = "".join(f'    <item name="{k}" value="{v}" />\n' for k, v in entries)
    return FILTER_HEADER + body + "\n</resources>\n"


def emit_filters():
    drawable, animator, shortcut = [], [], []
    for code in CODES:
        for daytime in (True, False):
            when = "day" if daytime else "night"
            mark = mark_of(code, daytime)
            layers = MARKS[mark]
            key = f"weather_{code}_{when}"
            drawable.append((key, f"weather_{mark}"))
            for i in range(1, 4):
                have = i <= len(layers)
                # A mark with one layer *is* its composite, so layer 1 points at it.
                if have and len(layers) == 1:
                    value = f"weather_{mark}"
                elif have:
                    value = f"weather_{mark}_{i}"
                else:
                    value = MISSING
                drawable.append((f"{key}_{i}", value))
            for tone in ("light", "grey", "dark", "xml"):
                drawable.append((f"{key}_mini_{tone}", f"weather_{mark}_mini_{tone}"))

            anims = ANIMATORS[mark]
            for i in range(1, 4):
                animator.append(
                    (f"{key}_{i}", anims[i - 1] if i <= len(anims) else MISSING)
                )

            shortcut.append((f"shortcuts_{code}_{when}", f"shortcuts_{mark}"))
        drawable.append(("", ""))  # keeps the generated file readable, one code per block
        animator.append(("", ""))
        shortcut.append(("", ""))

    def render_filter(entries):
        body = "".join(
            "\n" if not k else f'    <item name="{k}" value="{v}" />\n' for k, v in entries
        )
        return FILTER_HEADER + body + "</resources>\n"

    written = [
        write(f"{XML_DIR}/tenki_icon_provider_drawable_filter.xml", render_filter(drawable)),
        write(f"{XML_DIR}/tenki_icon_provider_animator_filter.xml", render_filter(animator)),
        write(f"{XML_DIR}/tenki_icon_provider_shortcut_filter.xml", render_filter(shortcut)),
    ]
    return written


def emit_pack(pack, head):
    written = []
    for mark, layers in MARKS.items():
        composite = [p for layer in layers for p in layer]

        # The animated set: the whole mark, then one file per layer if there are several.
        written.append(
            render(
                svg(composite, STROKE, INK, pack),
                f"{DRAWABLE_DIR}/{head}weather_{mark}.png",
                256,
            )
        )
        if len(layers) > 1:
            for i, layer in enumerate(layers, start=1):
                written.append(
                    render(
                        svg(layer, STROKE, INK, pack),
                        f"{DRAWABLE_DIR}/{head}weather_{mark}_{i}.png",
                        256,
                    )
                )

        # The minis. "light" and "dark" name the text they sit beside, so they are the
        # ink that reads against it; "grey" is the any-background case and carries a
        # black halo under the yellow.
        for tone, ink, halo in (
            ("light", INK, None),
            ("dark", PAPER, None),
            ("grey", INK, PAPER),
        ):
            written.append(
                render(
                    svg(composite, STROKE_MINI, ink, pack, inset=0.94, halo=halo),
                    f"{DRAWABLE_DIR}/{head}weather_{mark}_mini_{tone}.png",
                    192,
                )
            )
        written.append(
            write(
                f"{DRAWABLE_DIR}/{head}weather_{mark}_mini_xml.xml",
                vector(composite, STROKE_MINI, INK, pack),
            )
        )

        # The launcher shortcut: a black badge, and the adaptive foreground that the
        # launcher masks itself. Both inset well inside their square.
        written.append(
            render(
                svg(composite, STROKE_MINI, INK, pack, ground="circle", inset=0.56),
                f"{DRAWABLE_DIR}/{head}shortcuts_{mark}.png",
                192,
            )
        )
        written.append(
            render(
                svg(composite, STROKE_MINI, INK, pack, ground=PAPER, inset=0.40),
                f"{DRAWABLE_DIR}/{head}shortcuts_{mark}_foreground.png",
                768,
            )
        )
    return written


def sweep(written):
    """Delete drawables this script owns but no longer writes — e.g. a renamed pack."""
    kept = {os.path.basename(p) for p in written}
    folder = os.path.join(REPO, DRAWABLE_DIR)
    gone = []
    for name in sorted(os.listdir(folder)):
        if OWNED.match(name) and name not in kept:
            os.remove(os.path.join(folder, name))
            gone.append(f"{DRAWABLE_DIR}/{name}")
    return gone


def main():
    written = []
    for pack, head in PACKS.items():
        written += emit_pack(pack, head)
    for path in sweep(written):
        print("removed", path)
    written += emit_filters()
    for path in written:
        print("wrote", path)
    print(f"{len(written)} files, {len(PACKS)} packs")


if __name__ == "__main__":
    main()
