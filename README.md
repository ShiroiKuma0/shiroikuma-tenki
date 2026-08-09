<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 天気 icon" />

# 白い熊 天気

**A feature-rich weather app in the house black-yellow.**

A fork of [Breezy Weather](https://github.com/breezy-weather/breezy-weather) — forecast, observations,
nowcasting, air quality, pollen and alerts from more than 50 weather sources — renamed, re-iconed and
being brought into line with the rest of the house.

Installs **side-by-side** with Breezy Weather (app id `shiroikuma.tenki`).

**📥 [All releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-tenki/releases)**

</div>

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
