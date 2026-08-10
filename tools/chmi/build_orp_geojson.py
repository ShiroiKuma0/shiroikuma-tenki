#!/usr/bin/env python3
"""Cut the ORP warning-area polygons the ČHMÚ source matches alerts against.

ČHMÚ publishes its warnings as CAP 1.2 at

    https://vystrahy-cr.chmi.cz/data/XOCZ50_OKPR.xml

and geocodes every `<area>` with `CISORP` — the ČSÚ code of a *správní obvod obce s
rozšířenou působností* (206 of them, Prague counting as one). The bulletin carries **no
`<polygon>`**, so a client holding only a latitude/longitude cannot tell which warnings
apply to it. This script bakes the missing geometry into the app, the same way
`NaturalEarthService` and `BreezyTimeZoneService` carry theirs:

    app/src/main/res/raw/chmi_orp.json

a GeoJSON FeatureCollection whose only property is the CISORP code, read once per location
in `ChmiService.requestLocationParameters` and never on a refresh.

## Where the pieces come from

Geometry is ČÚZK RÚIAN (the authoritative Czech register, open data), queried straight out
of its ArcGIS REST service in WGS84 so nothing here has to reproject S-JTSK.

The code is the awkward part: **RÚIAN numbers ORPs differently from ČSÚ**, and it is the
ČSÚ number that ČHMÚ puts in the CAP. RÚIAN gives Chrudim 981; ČHMÚ calls it 5304. The
ČSÚ scheme is `<kraj><NN>`, where NN counts the kraj's ORPs in Czech alphabetical order —
so Chrudim is the 4th of Pardubický kraj's fifteen, after Česká Třebová, Hlinsko and
Holice (`ch` is one letter in Czech and sorts after `h`, which is exactly the sort of
thing that goes wrong silently).

So the mapping is *derived* here and then **proved against ČHMÚ's own bulletin**:

  1. the 206 derived codes must equal, as a set, the 206 codes ČHMÚ actually geocodes with;
  2. many of ČHMÚ's `<areaDesc>` strings spell their ORPs out —
     "Jihočeský kraj (Blatná, Milevsko, Písek, Strakonice, Tábor, Vimperk, Vodňany)" —
     beside the matching `<geocode>` list, so each of those is an independent equation
     between names and codes. Every one of them must hold.

Two irregularities the derivation has to know about, both caught by that check the first
time it ran: Prague is `1100`, not `1101`, and Moravskoslezský kraj is prefixed `81`
though its NUTS 3 code is CZ080.

If either check fails the script exits non-zero and writes nothing — a wrong code here
would silently show a Czech user somebody else's warnings.

## Running it

    python3 tools/chmi/build_orp_geojson.py

Needs network access and a `cs_CZ.UTF-8` locale (for the collation); no third-party
Python packages. Pass `--offset` to trade accuracy for size. Checked against the 758
ČHMÚ station coordinates, the default 0.001° (~80 m) puts 99.6% of them in the same ORP
as the unsimplified boundaries do; 0.002° saves 40% of the bytes for 99.2%. The misses
are all points sitting on a boundary, where the two neighbouring ORPs are nearly always
under the same warning anyway.
"""

from __future__ import annotations

import argparse
import json
import locale
import re
import sys
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

RUIAN = "https://ags.cuzk.gov.cz/arcgis/rest/services/RUIAN/MapServer"
LAYER_ORP = 14
LAYER_OKRES = 15
LAYER_VUSC = 17
CAP_URL = "https://vystrahy-cr.chmi.cz/data/XOCZ50_OKPR.xml"

OUT = Path(__file__).resolve().parents[2] / "app/src/main/res/raw/chmi_orp.json"

# ČSÚ číselník 65 prefixes each kraj's ORPs with its own two-digit code. These are the
# kraj codes, keyed by the RÚIAN name — note Moravskoslezský is 81, not the 80 its NUTS 3
# code (CZ080) would suggest.
CSU_KRAJ = {
    "Hlavní město Praha": 11,
    "Středočeský kraj": 21,
    "Jihočeský kraj": 31,
    "Plzeňský kraj": 32,
    "Karlovarský kraj": 41,
    "Ústecký kraj": 42,
    "Liberecký kraj": 51,
    "Královéhradecký kraj": 52,
    "Pardubický kraj": 53,
    "Kraj Vysočina": 61,
    "Jihomoravský kraj": 62,
    "Olomoucký kraj": 71,
    "Zlínský kraj": 72,
    "Moravskoslezský kraj": 81,
}


def get(url: str, params: dict) -> bytes:
    full = url + "?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(full, timeout=180) as r:
        return r.read()


def query(layer: int, params: dict) -> dict:
    base = {"where": "1=1", "returnGeometry": "false", "f": "json"}
    return json.loads(get(f"{RUIAN}/{layer}/query", {**base, **params}))


def derive_cisorp(orps: list[dict]) -> dict[str, int]:
    """Map ORP name -> ČSÚ CISORP code, by Czech-alphabetical rank within its kraj."""
    try:
        locale.setlocale(locale.LC_COLLATE, "cs_CZ.UTF-8")
    except locale.Error:
        sys.exit("cs_CZ.UTF-8 locale is required for Czech collation (locale-gen cs_CZ.UTF-8)")

    kraj_name = {f["attributes"]["kod"]: f["attributes"]["nazev"] for f in query(LAYER_VUSC, {"outFields": "kod,nazev"})["features"]}
    okres_kraj = {f["attributes"]["kod"]: f["attributes"]["vusc"] for f in query(LAYER_OKRES, {"outFields": "kod,vusc"})["features"]}

    by_kraj: dict[int, list[str]] = defaultdict(list)
    for a in orps:
        # Prague has no parent okres; every other ORP reaches its kraj through one.
        vusc = a["vusc"] if a["vusc"] is not None else okres_kraj[a["okres"]]
        by_kraj[CSU_KRAJ[kraj_name[vusc]]].append(a["nazev"])

    codes: dict[str, int] = {}
    for prefix, names in by_kraj.items():
        # Prague is the sole ORP of kraj 11 and ČSÚ numbers it 1100, not 1101.
        first = 0 if prefix == 11 else 1
        for i, name in enumerate(sorted(names, key=locale.strxfrm), start=first):
            codes[name] = prefix * 100 + i
    return codes


def verify(codes: dict[str, int]) -> None:
    """Prove the derived codes against ČHMÚ's live bulletin, or die trying."""
    with urllib.request.urlopen(CAP_URL, timeout=180) as r:
        cap = r.read().decode("utf-8")

    in_cap = {int(c) for c in re.findall(r"<valueName>CISORP</valueName>\s*<value>(.*?)</value>", cap)}
    derived = set(codes.values())
    if derived != in_cap:
        sys.exit(
            f"derived codes do not match ČHMÚ's: missing {sorted(in_cap - derived)}, extra {sorted(derived - in_cap)}"
        )

    checked = 0
    for area in re.findall(r"<area>(.*?)</area>", cap, re.S):
        desc = re.search(r"<areaDesc>(.*?)</areaDesc>", area, re.S).group(1).strip()
        listed = re.match(r"^(.*?)\s*\((.*)\)$", desc)
        if not listed:
            continue  # a whole kraj, or an agglomeration — no names to check against
        names = [n.strip() for n in listed.group(2).split(",")]
        if any(n not in codes for n in names):
            continue  # an agglomeration naming something that is not an ORP
        expected = {codes[n] for n in names}
        actual = {int(c) for c in re.findall(r"<valueName>CISORP</valueName>\s*<value>(.*?)</value>", area)}
        if expected != actual:
            sys.exit(f"ČHMÚ disagrees for {desc!r}: expected {sorted(expected)}, got {sorted(actual)}")
        checked += 1

    print(f"  verified: 206 codes match ČHMÚ's set, and {checked} spelled-out areas agree")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--offset", type=float, default=0.001, help="simplification tolerance in degrees (default 0.001 ≈ 80 m)")
    args = ap.parse_args()

    print("Reading ORPs from ČÚZK RÚIAN…")
    attrs = [f["attributes"] for f in query(LAYER_ORP, {"outFields": "kod,nazev,okres,vusc"})["features"]]
    print(f"  {len(attrs)} ORPs")

    print("Deriving ČSÚ CISORP codes…")
    codes = derive_cisorp(attrs)
    verify(codes)

    print(f"Fetching geometry (offset {args.offset}°)…")
    raw = json.loads(
        get(
            f"{RUIAN}/{LAYER_ORP}/query",
            {
                "where": "1=1",
                "outFields": "nazev",
                "returnGeometry": "true",
                "outSR": "4326",
                "f": "geojson",
                "maxAllowableOffset": str(args.offset),
                "geometryPrecision": "4",
            },
        )
    )

    features = []
    for f in raw["features"]:
        name = f["properties"]["nazev"]
        features.append(
            {
                "type": "Feature",
                # The name is not read by the app; it is here so a human can diff the file.
                "properties": {"cisorp": str(codes[name]), "name": name},
                "geometry": f["geometry"],
            }
        )
    features.sort(key=lambda f: f["properties"]["cisorp"])
    if len(features) != 206:
        sys.exit(f"expected 206 features, got {len(features)}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps({"type": "FeatureCollection", "features": features}, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"Wrote {OUT.relative_to(Path(__file__).resolve().parents[2])} — {OUT.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
