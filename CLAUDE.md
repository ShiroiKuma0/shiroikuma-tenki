# CLAUDE.md — guide for Claude Code in this repo

**shiroikuma-tenki** — 白い熊's fork of [Breezy Weather](https://github.com/breezy-weather/breezy-weather),
a feature-rich FOSS weather app with 50+ sources (Kotlin, Jetpack Compose + some View/XML, Hilt, Room;
LGPL-3.0). Renamed to `shiroikuma.tenki` / **白い熊 天気** so it installs side-by-side with upstream.

This repo (`ShiroiKuma0/shiroikuma-tenki`) is a fork. We track upstream **release tags** on `main` and
layer our customizations on `custom`.

## Read this first

Before any work, read **`.claude/skills/build-apk/SKILL.md`** (canonical build + delivery) and
**`.claude/skills/upstream-new-version/SKILL.md`** (upstream sync + rebase, with the mandatory
proceed-gated upstream-changes table). Publishing a release uses the **global** `/publish-version`
skill — this repo has no local copy.

## Fork workflow — READ THIS FIRST

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-tenki` (push here).
- `upstream` → `https://github.com/breezy-weather/breezy-weather` (fetch only; its push URL is `DISABLED`).
- `main` — mirrors the newest upstream **release tag** (`v6.2.1`, `v6.2.2`, …). No fork work here.
- `custom` — all our work, and the GitHub default branch so the repo page lands on the fork.

**Upstream tracking: release TAGS, not the branch tip** (白い熊, 2026-08-09). Breezy tags every
release and bumps `versionCode`/`versionName` with it, while `upstream/main` keeps moving daily
(it already declares the *next* version). Basing on tags means every base is a state upstream itself
called finished, and the version literal really moves on each sync — so the plain `+NNN` tail is
enough and the global **`git-versioning`** skill does **not** apply here.

### Our customizations (install identity + build)

| What | Value | Where |
| --- | --- | --- |
| applicationId | `shiroikuma.tenki` | `app/build.gradle.kts` → `defaultConfig` |
| namespace (R/BuildConfig pkg) | `org.breezyweather` (**never rename**) | `app/build.gradle.kts` |
| App label / brand | `白い熊 天気` | `brand_name` in `app/src/res_fork/values/strings.xml` |
| Launcher label | `@string/brand_name` (not upstream's per-locale `app_name`) | `app/src/main/AndroidManifest.xml` → `MainActivity` |
| App icon | black-yellow traced four-blade pinwheel (yellow `#FFFF00` line-art on black), cut by `tools/icon/emit_launcher.py` | `app/src/res_fork/**` (`drawable/ic_launcher_{background,foreground}.xml`, `mipmap-*/ic_launcher*.webp`), `app/src/main/ic_launcher-playstore.png`, `design/shiroikuma-tenki-icon.svg` |
| Version tail | `versionName = "<upstream>+NNN"`, `versionCode = <upstream code>*10000+N` | `app/build.gradle.kts` fork blocks |
| Signing | gitignored `keystore.properties` → `~/.android-keystores/shiroikuma-tenki.jks` (alias `tenki`) | `app/build.gradle.kts` `signingConfigs` |
| Fork links | our repo everywhere the app links out | `gradle.properties` → the `app.*` block |
| De-branding | our name + our GitHub links everywhere user-visible | About / Help / Settings screens, `values*/strings.xml`, root docs |
| 白い熊 天気 UI | the black-yellow theming page + Export/Import | `tenki/`, `ui/tenki/`, wired into `ui/theme/compose/Theme.kt` |
| User-Agent | the brand's ASCII part (a header value must be ASCII) | `BreezyWeather.kt` → `asciiBrandName` |

### The 白い熊 天気 UI page

Our one feature so far, in the kxkb page grammar (see also the sister implementations in
`~/git/shiroikuma-mise` and `~/git/shiroikuma-kojiki`, which this follows deliberately).

| File | Role |
| --- | --- |
| `tenki/TenkiUiConfig.kt` | SharedPreferences store (`tenki_ui`) for every knob, seeded to the house black-yellow; `toJson`/`fromJson` for the backup |
| `tenki/TenkiUiState.kt` | observable mirror + the derived `ColorScheme` / `Shapes` / `Typography`; process singleton, provided as `LocalTenkiUi` |
| `tenki/TenkiFonts.kt` | built-in families + `.ttf`/`.otf` import into app storage; also the View-world `Typeface` |
| `tenki/TenkiBackup.kt` | the category ZIP — `writeZip(categories, OutputStream, onProgress, isCancelled)` is the ONE export implementation; atomic `.part`-then-rename on the SAF path |
| `ui/tenki/TenkiUiScreen.kt` | the page itself: section headings with word-width underlines, two-level indents, tight rows, a preview under every group |
| `ui/tenki/TenkiDialogs.kt` | RGBA colour picker with recent-colour swatches, font picker rendering each font in its own glyphs, and the Export/Import panel |

- **`BreezyWeatherTheme` is the single wrapping point** — all 39 call sites go through it, so
  providing `LocalTenkiUi` and swapping the `ColorScheme`/`Typography`/`Shapes` there themes the
  whole Compose app and repaints it live while a slider is dragged.
- **Scope, honestly:** this paints **Compose**. Breezy still draws its main weather screen, cards
  and widgets with XML views and its own `ThemeManager`, and those do not follow the knobs yet.
  Bringing them across is the next stage of the work.
- Every border/divider/roundness slider **reaches 0 meaning "draw nothing"**, never "the Material
  default".
- Entry points: Settings → the first item, and a **long press on the settings cog** in the
  locations screen (`ManagementFragment`) → `IntentHelper.startTenkiUiSettingsActivity`.

**Upstream is fork-friendly by design.** It gates its own branding behind a `-Pbreezy` Gradle
property (`buildSrc/.../BuildConfig.kt` → `Config.isBreezy`): without it the build picks
`app/src/res_fork/` (our icon + `brand_name`) instead of `app/src/res_breezy/`, reads the `app.*`
links out of `gradle.properties` instead of the `breezy.*` ones, and takes AboutLibraries config
from `config-fork/`. **Never pass `-Pbreezy`** — upstream's licence forbids redistributing modified
APKs with the brand config enabled, and `LICENSE_ADDITIONAL` requires modified versions to be marked
as different from the original.

### Versioning & APK naming

- The upstream base lives in `app/build.gradle.kts` `defaultConfig` as upstream's own
  `versionCode = 60201` / `versionName = "6.2.1"` literals. Our fork lines sit **immediately after**
  them and multiply/append, so a rebase brings the new base in automatically.
  **Never hand-edit those two literals.**
- `BUILD_NUMBER` (in `gradle.properties`) is our per-build `N`:
  `versionName = "<upstream name>+<N zero-padded to 3>"` (e.g. `6.2.1+001`),
  `versionCode = <upstream code> * 10000 + N` (plain integer, e.g. `602010001`).
  The `buildFork` task bumps `BUILD_NUMBER` after every successful build; `/upstream-new-version`
  resets it to `1` on every sync, so `+N` always reads as "our Nth build on this upstream base".
- APK: `shiroikuma-tenki_<versionName>_arm64-v8a.apk`, copied to `~/tmp/`. Upstream splits the
  release per ABI (plus a universal APK); we ship the **arm64-v8a** split. The versionName contains
  no `_`, so the `shiroikuma-tenki_*.apk` globs in `/adb-push`, `/scp` and `/publish-version` resolve.

### Build commands

```bash
# Our build: signed basic (standard) release → ~/tmp + bump BUILD_NUMBER (use this)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null
# Release APKs only (no copy / no bump)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleBasicRelease
```

We ship the **`basic`** flavor — upstream calls it "standard" in its release assets and recommends
it; it carries all ~50 sources. The `freenet` flavor (free-network sources only, `_freenet`
versionName suffix) is upstream's and we don't build it.

### Toolchain

- JDK **21** at `/usr/lib/jvm/java-21-openjdk-amd64` (the host default `java` is JDK 11; Gradle 9.x
  aborts on it — always set `JAVA_HOME`).
- Android SDK at `~/android-sdk`; `compileSdk 37`, `targetSdk 36`, `minSdk 23`,
  `buildToolsVersion 36.0.0`. Gradle wrapper 9.5.1, AGP 9.2.1, Kotlin 2.4.0.
- Gradle needs `local.properties` with `sdk.dir=/home/shiroikuma/android-sdk` (gitignored).

## Architecture (upstream Breezy Weather)

Multi-module Gradle build: `:app` (UI, sources, widgets, notifications), `:data`, `:domain`,
`:maps-utils`, `:ui-weather-view`, `:weather-unit`, `:work`, plus `buildSrc` build logic.

| Area | Where |
| --- | --- |
| Application class, DI entry | `app/src/main/kotlin/org/breezyweather/BreezyWeather.kt` |
| Main UI (Compose) | `app/src/main/kotlin/org/breezyweather/ui/main/` |
| Settings / About screens | `app/src/main/kotlin/org/breezyweather/ui/settings/`, `ui/about/AboutScreen.kt` |
| Weather sources (50+) | `app/src/main/kotlin/org/breezyweather/sources/<source>/` |
| Flavor-specific sources | `app/src/src_nonfreenet/` (basic), `app/src/src_freenet/` (freenet) |
| Widgets / notifications | `app/src/main/kotlin/org/breezyweather/remoteviews/` |
| Brand-swappable resources | `app/src/res_fork/` (ours) vs `app/src/res_breezy/` (upstream's) |
| Build logic, SDK levels | `buildSrc/src/main/kotlin/breezy/buildlogic/` |
| Our icon source | `design/shiroikuma-tenki-icon.svg` |

## Hard rules

- **Never pass `-Pbreezy`** (see above) — it would ship upstream's brand, which the licence forbids.
- **Never rename the `org.breezyweather` code namespace** — only `applicationId` differs; renaming
  would make every rebase a mass-conflict.
- **Never commit/push unprompted.** Build, deliver, and stop; 白い熊 tests. Commit + push only on
  their explicit **"Push"**. After a rebase, `custom` needs `git push --force-with-lease origin custom`.
- `keystore.properties`, `local.properties` and `*.jks` are gitignored — never commit them.
- **Always run `adb`, `scp` and `git status`/`git diff` with `dangerouslyDisableSandbox: true`**
  (the sandbox blocks adb's server socket and invents phantom untracked files at the repo root).
- **After ANY functional change, build and deliver automatically** — the global `/after-build`
  standing authorization; never wait for "build it". Every build bumps `BUILD_NUMBER`; never
  overwrite or delete an older APK, in `~/tmp/` or on the phone.
- On a new upstream release tag, run the **`upstream-new-version`** skill — it presents the
  proceed-gated feature table **before** any rebasing, then rebases, resets `BUILD_NUMBER=1`, and
  builds the new `+001`.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer, nor a "🤖 Generated with Claude Code" /
Anthropic-attribution line, to commit messages or PR bodies in this repo. End the message at the last
line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
