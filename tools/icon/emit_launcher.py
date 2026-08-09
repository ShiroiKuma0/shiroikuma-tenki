#!/usr/bin/env python3
"""Cut every launcher asset for 白い熊 天気 from one geometry model.

The mark is Breezy Weather's four-blade pinwheel, re-drawn as stroke-only line-art
in the house black-yellow (#FFFF00 on #000000): four half-discs of radius R, their
centres at distance D from the middle along the four cardinal directions, each one
turned a quarter-turn against the next so the blades chase each other.

Writes:
  design/shiroikuma-tenki-icon.svg                        the source drawing (512 px)
  app/src/res_fork/drawable/ic_launcher_background.xml     solid black adaptive background
  app/src/res_fork/drawable/ic_launcher_foreground.xml     the yellow pinwheel (also the
                                                           monochrome layer, per the
                                                           adaptive-icon XML)
  app/src/res_fork/mipmap-{m,h,xh,xxh,xxx}dpi/ic_launcher{,_round}.webp   the legacy set
  app/src/res_fork/drawable/ic_launcher{,_round}.webp                     192 px copies
  app/src/main/ic_launcher-playstore.png                                  512 px store icon

Re-runnable: same input, same bytes out. Needs `rsvg-convert` and ImageMagick (`magick`).

Usage:  python3 tools/icon/emit_launcher.py [repo-root]
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(HERE))

INK = "#FFFF00"          # house yellow
PAPER = "#000000"        # house black

# Blade geometry, as ratios of the artwork's radius (E = half the artwork's width).
# Taken from upstream's own vector: blade radius / centre offset = 29.7 / 32.3.
R_OVER_D = 0.92
STROKE_OVER_E = 0.13

# The legacy/mipmap renders inset the artwork to 78 % of the square; the adaptive
# foreground fills the 72 dp safe zone of the 108 dp viewport instead.
LEGACY_E = 200.0         # in a 512 viewBox
ADAPTIVE_E = 33.0        # in a 108 viewport (33 + stroke/2 = 35.1 < 36 = safe radius)


def blade_paths(cx, cy, e):
    """The four half-discs, as closed sub-paths (arc + chord)."""
    d = e / (1.0 + R_OVER_D)
    r = R_OVER_D * d
    f = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
    return [
        # right blade, upper half
        f"M {f(cx + d - r)},{f(cy)} A {f(r)},{f(r)} 0 0,1 {f(cx + d + r)},{f(cy)} Z",
        # bottom blade, right half
        f"M {f(cx)},{f(cy + d - r)} A {f(r)},{f(r)} 0 0,1 {f(cx)},{f(cy + d + r)} Z",
        # left blade, lower half
        f"M {f(cx - d + r)},{f(cy)} A {f(r)},{f(r)} 0 0,1 {f(cx - d - r)},{f(cy)} Z",
        # top blade, left half
        f"M {f(cx)},{f(cy - d + r)} A {f(r)},{f(r)} 0 0,1 {f(cx)},{f(cy - d - r)} Z",
    ]


def svg(size, e, background):
    """The mark as an SVG string. `background` is a colour, "circle" or None."""
    c = size / 2.0
    stroke = STROKE_OVER_E * e
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 {size} {size}">'
    ]
    if background == "circle":
        out.append(f'  <circle cx="{c}" cy="{c}" r="{c}" fill="{PAPER}"/>')
    elif background:
        out.append(f'  <rect x="0" y="0" width="{size}" height="{size}" fill="{background}"/>')
    out.append(
        f'  <g fill="none" stroke="{INK}" stroke-width="{stroke:g}" '
        f'stroke-linecap="round" stroke-linejoin="round">'
    )
    out += [f'    <path d="{p}"/>' for p in blade_paths(c, c, e)]
    out += ["  </g>", "</svg>", ""]
    return "\n".join(out)


VECTOR_HEADER = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="108dp"\n'
    '    android:height="108dp"\n'
    '    android:viewportWidth="108"\n'
    '    android:viewportHeight="108">\n'
)


def foreground_vector():
    stroke = STROKE_OVER_E * ADAPTIVE_E
    body = "".join(
        f'    <path\n'
        f'        android:pathData="{p}"\n'
        f'        android:fillColor="#00000000"\n'
        f'        android:strokeColor="{INK}"\n'
        f'        android:strokeWidth="{stroke:g}"\n'
        f'        android:strokeLineCap="round"\n'
        f'        android:strokeLineJoin="round" />\n'
        for p in blade_paths(54.0, 54.0, ADAPTIVE_E)
    )
    return VECTOR_HEADER + body + "</vector>\n"


def background_vector():
    return (
        VECTOR_HEADER
        + f'    <path\n        android:fillColor="{PAPER}"\n'
        '        android:pathData="M0,0h108v108h-108z" />\n'
        "</vector>\n"
    )


def write(relative_path, text):
    full = os.path.join(REPO, relative_path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as fh:
        fh.write(text)
    print("wrote", relative_path)


def render(svg_text, relative_path, size, fmt):
    """SVG text -> PNG (rsvg-convert) -> optionally WebP (ImageMagick)."""
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
        if fmt == "png":
            subprocess.run(["magick", tmp_png, "-strip", full], check=True)
        else:
            subprocess.run(
                ["magick", tmp_png, "-strip", "-define", "webp:lossless=true", full], check=True
            )
    print("wrote", relative_path)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    # 1. the source drawing
    write("design/shiroikuma-tenki-icon.svg", svg(512, LEGACY_E, PAPER))

    # 2. the adaptive icon's two layers (the foreground doubles as the monochrome layer)
    write("app/src/res_fork/drawable/ic_launcher_background.xml", background_vector())
    write("app/src/res_fork/drawable/ic_launcher_foreground.xml", foreground_vector())
    # Upstream's placeholder foreground sat in drawable-v24; ours needs no v24 feature and
    # minSdk is 23, so it lives in plain drawable/ and the v24 copy goes away.
    stale = os.path.join(REPO, "app/src/res_fork/drawable-v24/ic_launcher_foreground.xml")
    if os.path.exists(stale):
        os.remove(stale)
        os.rmdir(os.path.dirname(stale))
        print("removed app/src/res_fork/drawable-v24/ic_launcher_foreground.xml")

    # 3. the legacy raster set
    square = svg(512, LEGACY_E, PAPER)
    round_ = svg(512, LEGACY_E, "circle")
    for density, size in DENSITIES.items():
        render(square, f"app/src/res_fork/mipmap-{density}/ic_launcher.webp", size, "webp")
        render(round_, f"app/src/res_fork/mipmap-{density}/ic_launcher_round.webp", size, "webp")
    render(square, "app/src/res_fork/drawable/ic_launcher.webp", 192, "webp")
    render(round_, "app/src/res_fork/drawable/ic_launcher_round.webp", 192, "webp")

    # 4. the store icon
    render(square, "app/src/main/ic_launcher-playstore.png", 512, "png")


if __name__ == "__main__":
    main()
