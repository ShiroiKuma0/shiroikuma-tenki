<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 天気 icon" />

# 白い熊 天気

**A feature-rich weather app in the house black-yellow.**

A fork of [Breezy Weather](https://github.com/breezy-weather/breezy-weather) — forecast, observations,
nowcasting, air quality, pollen and alerts from more than 50 weather sources — with **major
additions**: a live theming page that repaints the app as you drag a slider, a black-yellow repaint
that reaches every surface upstream draws, two hand-cut weather-icon packs, and headless backup
automation for the 保存復元 batch.

Installs **side-by-side** with Breezy Weather (app id `shiroikuma.tenki`).

**📥 Latest release: [`6.2.1+009`](https://github.com/ShiroiKuma0/shiroikuma-tenki/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-tenki/releases)

</div>

---

## 🎨 The 白い熊 天気 UI page

Every knob that shapes the app's look, on one page — colours, fonts, sizes, roundness, border and
divider widths, indent step, row padding, group spacing. It is not a settings screen that takes
effect on restart: each write lands in preferences *and* in Compose state, so dragging a slider
repaints the app underneath you. The page is themed by the very values it edits, which makes the app
its own preview.

Colours come from an RGBA picker with a one-click row of colours already in use; fonts from a picker
that renders every candidate — including `.ttf`/`.otf` files you import — in its own glyphs. The
border, divider and roundness sliders all reach **0 meaning "draw nothing"**, not "the Material
default".

Reach it from Settings (first item) or by **long-pressing the settings cog** on the locations screen.

At the top of the page sits **Export / Import**: a settable backup folder — red until it is set,
yellow once it is — the newest archive found there, and a category checklist (UI, app settings,
weather source configuration, locations, imported fonts). Exports are written atomically, `.part`
first and renamed only when the archive is complete, as `shiroikuma-tenki_<yyyy-MM-dd_HH-mm-ss>.zip`,
the same filename shape every sister app uses.

---

## 🌗 Black-yellow, all the way down

The repaint reaches past Compose. `BreezyWeatherTheme` is the single wrapping point every screen
goes through, so swapping the `ColorScheme`, `Shapes` and `Typography` there themes the whole Compose
app at once — but an XML view cannot be reached that way, so the locations card, the main weather
cards, the trend tab buttons, the chips and the snackbar are painted by hand from the *same* knobs.

Upstream defines its `md_theme_*` palette four times over — `values`, `values-night`, and again in
`values-v31` / `values-night-v31`, where it points at Android's Material You **system** colours. On
any phone running Android 12 or newer the qualified pair wins, so an override that only touches
`values` does nothing at all. All four are overridden here, and the built APK carries no
dynamic-colour variant left to beat us.

Cards sit at elevation 0 with their ground set outright, because Material lightens an elevated
surface by compositing a tint over it — that tint was the grey. Tag chips are yellow-outlined pills
that **reverse** to a yellow ground when selected, which is the only state difference a two-colour
palette can carry. Wind arrows take the accent rather than the Beaufort green-to-red scale, since the
number is printed beside every one of them. Air quality and UV keep upstream's scales on purpose:
there the colour **is** the reading.

The animated header is upstream's own weather scene — drifting cloudscape, rain, meteor shower —
recoloured rather than deleted. A hardware layer with a duotone filter maps every pixel's brightness
onto the Background → Accent ramp at composite time, so what upstream drew blue-to-white comes out
black-to-yellow with its shape and motion intact, for every weather implementor at once. How far a
bright pixel travels is a slider; 0 drops the scene entirely and takes the flat view instead.

---

## ⛅ Three weather-icon packs

The twelve weather codes, re-drawn in the house yellow, in two packs cut from one geometry model:
**traced** is stroke-only line-art like the launcher mark, **full** fills the same silhouettes.
Filling is one rule — a path that closes gets a fill — and a filled shape is stroked as well at the
same width, so its outer edge lands exactly where the traced one's does and the two packs register
pixel for pixel. Upstream's set stays, listed as **Breezy Weather**, so the whole change is one tap
away from being undone.

Each pack is complete: the animated icon and its layers, the widget and notification minis in light,
grey and dark, the notification small icon, and the launcher-shortcut badge and adaptive foreground —
130 drawables apiece, all emitted by `tools/icon/emit_weather_icons.py`, which is the only source.

The animators are upstream's, borrowed by name, and the layer split matches what each one moves, so
the icons still breathe, drift and fall. Line-art has no fill to hide an overlap behind, so the
composites are laid out disjoint: in partly-cloudy the sun sits clear of the cloud rather than behind
it, which upstream can get away with and line-art cannot.

---

## 🤖 On the 保存復元 batch

The app implements the sister-app backup-automation contract, so 白い熊 自由作業盤 can drive its
export headlessly as part of the one-run batch across every app: a token-gated intent in, the export
run in a foreground service (never in the receiver, which would be ANR'd mid-write), progress
reported with real counts rather than a percentage, and one terminal reply carrying the path, the
byte count and the human size.

The switch defaults to **off** and the token lives outside every backup category — nothing is
reachable from another app until it is turned on and the token copied across. Both rows sit in the
Export/Import section of the UI page, where backup lives.

---

## 🖤 The house look

The launcher icon is upstream's four-blade pinwheel redrawn as stroke-only line-art, pure `#FFFF00`
on black — the same treatment every sister app's icon gets. The adaptive icon, the round icon, the
monochrome (Material You) layer and the whole legacy mipmap set are all cut from one geometry model
by `tools/icon/emit_launcher.py`, so the mark is identical at every density and re-runnable byte for
byte.

The app carries our name everywhere it used to carry upstream's: launcher label, About screen,
notification channels, the data-sharing permission dialog, and every link out of the app.

---

## 🍴 How this fork is built

Breezy Weather is unusually fork-friendly, and this fork uses that path rather than fighting it:

- Upstream gates its own branding behind a `-Pbreezy` Gradle property. **We never pass it**, so the
  build automatically takes `app/src/res_fork/` (our icon and brand name) instead of
  `app/src/res_breezy/`, reads the `app.*` links from `gradle.properties` instead of the `breezy.*`
  ones, and takes its AboutLibraries config from `config-fork/`.
- Upstream's licence forbids redistributing a modified APK with the brand config enabled, and
  `LICENSE_ADDITIONAL` requires modified versions to be marked as different from the original — which
  is exactly what this fork does.
- The code namespace stays `org.breezyweather`. Only the `applicationId` differs, so rebasing onto a
  new upstream release never turns into a mass rename.

| | |
| --- | --- |
| Upstream | [breezy-weather/breezy-weather](https://github.com/breezy-weather/breezy-weather) (LGPL-3.0) |
| Tracked by | upstream **release tags** — `main` mirrors the newest tag, `custom` carries our work |
| App id | `shiroikuma.tenki` |
| Flavor | `basic` (upstream's "standard" — all sources), arm64-v8a |
| Version | `<upstream version>+NNN`, e.g. `6.2.1+001`; `versionCode = <upstream code> * 10000 + N` |

---

## 🔧 Building

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork
```

`buildFork` assembles the signed `basic` release, copies the arm64-v8a split to `~/tmp/` as
`shiroikuma-tenki_<version>_arm64-v8a.apk`, and bumps the build counter. Release signing reads a
gitignored `keystore.properties` at the repo root.

---

## 📄 License

LGPL-3.0, inherited from upstream — see [`LICENSE`](LICENSE) and
[`LICENSE_ADDITIONAL`](LICENSE_ADDITIONAL). The trademarks, logos and brand of the Breezy Weather
project are **not** covered by that licence and are not used here: this app has its own name, its own
icon and its own signing key. All credit for the weather app itself goes to the Breezy Weather
contributors.
