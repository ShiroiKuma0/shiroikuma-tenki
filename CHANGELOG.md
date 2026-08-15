# Changelog

**白い熊 天気** is 白い熊's fork of
[Breezy Weather](https://github.com/breezy-weather/breezy-weather). This file carries **both**
histories: our fork releases first, then upstream's own changelog below, unchanged.

Fork releases are named `<upstream version>+NNN` and each says which upstream release it is built on.
Our block sits above upstream's so their new versions land further down the file and a rebase never
has to merge the two histories by hand.

---

## 白い熊 天気 6.2.1+035 — 2026-08-15

Built on upstream **v6.2.1**. ČHMÚ becomes a forecast source.

### ČHMÚ forecasts

- **Hourly, three days, for your exact coordinates.** ALADIN hour by hour — temperature,
  precipitation in millimetres, snow, wind with gusts and direction, pressure, cloud cover and
  humidity — read from the API behind ČHMÚ's own web pages rather than from the nearest listed town.
  Anywhere in Czechia works; there is no table of municipalities to be on.
- **The hours already behind you are measured, not modelled.** The left half of the hourly meteogram
  is rebuilt from the ten-minute stream of the nearest station — the same one the current observation
  comes from — folded into whole hours: temperature, humidity, rain, wind and sunshine. The hour
  still running is left to the model, since averaging the few readings that have arrived would read
  as a dip. Pressure stays the model's, because the station reports its own rather than one reduced
  to sea level.
- **Daily: three local days, then the national outlook.** The near days are built from this
  location's own hourly series, so they carry its rain. Past that the tab is filled with ČHMÚ's
  nine-day outlook, which is **one forecast for the whole country** — identical in Prague and on
  Sněžka — and which publishes no precipitation at all. Read the later days as the national picture.
- **Stacks with Open-Meteo's ALADIN.** ČHMÚ now appears in the Forecast multi-select, so it can be
  drawn under the `chmi_aladin_*` models: the same model through a different pipe, and where the two
  disagree it is the post-processing that differs.
- **Weather icons.** ČHMÚ's icon vocabulary is read as the two-digit grammar it is — tens for cloud,
  ones for what is falling, plus a hundred for night — so a code they add later still lands somewhere
  sensible. The wording comes from 白い熊 天気's own shared set, so it reads in the app's language
  rather than in Czech.

There is still no precipitation *probability*: ČHMÚ publishes none for a single location anywhere,
only the amount.

---

## 白い熊 天気 6.2.1+034 — 2026-08-14

Built on upstream **v6.2.1**. Two things: a location can draw **several forecast sources at once**,
and the trend charts are rebuilt as meteograms.

### Several forecast sources per location

- **The Forecast row takes a list.** "Weather sources" → Forecast opens a multi-select panel:
  checkboxes over the usual grouped source list, plus an ordered section with drag handles and a
  remove button. The last remaining source cannot be unchecked. Current, normals and the rest keep
  their single-select rows.
- **The first source stays the location's identity.** It is what `formattedId` is built from, so the
  duplicate-location check, the widgets and the notification are unaffected; dragging a different
  source to the top is what changes it, exactly as picking a different source did before.
- **Every selected source is fetched.** `RefreshHelper` groups the extra sources alongside the
  primary one — a source already used for another feature gains FORECAST and stays a single call —
  and runs each through the same completion the primary gets, including the back-fill that keeps
  today alive late in the day. Air quality and pollen are folded into every source's arrays, so all
  chart tabs work whichever source drew the bars.
- **A source added mid-window fetches immediately.** The cache-validity check is keyed per feature,
  not per source, which would otherwise have made a newly added source wait for the primary's
  forecast cache to expire.
- **Cached per source** — migration 26 rebuilds `dailys` and `hourlys` with the source in their
  unique key, so two sources can hold the same date. Existing rows keep `source = ''` and read back
  as the primary, so the charts draw from cache on the first launch after the upgrade.
- **One chart per source**, stacked in the arranged order inside the existing card: title, subtitle
  and tab row stay single, and one tab selection drives every chart. The source name only appears
  when more than one is selected. The footer credits them all.

### Meteogram trend charts

- **Filled, and coloured by temperature.** The area under the curve is drawn in the colour of the
  temperature at each point, from a single scale now shared with the details screen
  (`TemperatureColorScale`) — cold starts below 16°.
- **No grid, no axis, no "Normal" rules.** Alternating bands delimit hours and days instead, running
  the full height of the column including the labels and icons, with a thicker divider at midnight
  and a dashed marker at now. A few hours of history are knocked back to the left of it.
- **Each source scales to its own data**, not to the monthly normals, which used to stretch every
  axis to 14–26° whatever the forecast was.
- **The hourly window is what fits the screen.** Columns share the host's width, so the opening
  window fills it exactly and the visible peak reaches the top; everything beyond scrolls, riding the
  pane edge with its reading intact rather than being clipped away.
- **Daily is one continuous trace.** The high and low curves are merged: it rises through each day
  and falls through each night, eased so the extremes round over into peaks and troughs.
- **Rain along the foot of both**, at hourly resolution, as narrow bars tiling edge to edge. Sources
  that report no probability — MET Norway, for one — fall back to millimetres with their own ceiling.
  It is drawn under the readings, so a wet night never buries the figures.
- **Readings on plates**, anchored to the curve's extreme across their own width so they never clip
  the line, lifted in lightness so a hot reading stays red and legible on black. Hourly labels are
  two rows, the meridiem under the numeral.
- **Settable** — graph heights, and the hourly window's hours back and ahead, are sliders on the
  白い熊 天気 page.

### Fixes

- The trend charts' polylines are smooth curves rather than straight segments.
- `TenkiWeatherThemeDelegate.getThemeColors` returned the background for indices 1 and 2, which are
  the charts' line colours — every temperature curve was being drawn black on black.
- The hourly card is above the daily one by default.

## 白い熊 天気 6.2.1+013 — 2026-08-10

Built on upstream **v6.2.1**. Adds Czechia's national weather service, which upstream had never
evaluated — `docs/COVERAGE.md` listed the agency and left the status column empty.

### New weather source — ČHMÚ (Czechia)

Český hydrometeorologický ústav, as a **Current**, **Air quality**, **Alert** and **Temperature
normals** source for Czech locations. All of it is open data under CC BY 4.0, keyless, so the source
is free-net and works in both flavors.

- **Warnings** — the SIVS bulletin *and* the separate drought bulletin, both CAP 1.2, filtered to the
  location's ORP district.
  - ČHMÚ packs every warning into one document as its own `<info>`, twice over in Czech and English,
    and emits an explicit "no warning in force" block for every hazard it is *not* warning about.
    Only `Moderate` and above is kept — exactly SIVS levels yellow, orange and red — which drops the
    negatives without matching on Czech prose.
  - The alert id is hashed from the event rather than taken from the CAP identifier, because ČHMÚ
    mints a new identifier every re-issue, which would otherwise make every warning look new on
    every refresh.
  - Each warning carries ČHMÚ's synoptic `situation` paragraph appended to its description.
- **ORP boundaries, bundled and answered offline** — the bulletins geocode by `CISORP` with no
  polygon, so the 206 districts ship as a simplified GeoJSON read once per location, never on a
  refresh.
  - `tools/chmi/build_orp_geojson.py` cuts it from ČÚZK RÚIAN. The land register numbers ORPs
    differently from the statistical office, and it is the statistical office's number ČHMÚ uses, so
    the mapping is derived by Czech alphabetical rank within each kraj — where `ch` is one letter and
    sorts after `h` — and then **proved against ČHMÚ's own bulletin**: the 206 derived codes must
    equal the set ČHMÚ geocodes with, and every spelled-out area must agree with its geocode list.
    The script writes nothing if either check fails.
  - That check caught two irregularities on its first run: Prague is `1100`, not `1101`, and
    Moravskoslezský is prefixed `81` though its NUTS 3 code is CZ080.
  - Simplified to about 80 m, the boundaries place 99.6% of ČHMÚ's own 758 station coordinates in the
    same district as the unsimplified geometry.
- **Current observations** — the nearest station reporting ten-minute temperature, around 300 of
  them, giving temperature, humidity and wind (with ČHMÚ's variable-direction flag).
  - Deepened with **sea-level pressure, dew point and cloud cover** from the hourly synoptic stream
    wherever one of the three dozen professional stations is within 50 km. Where the ten-minute
    station is itself professional it answers for both, so the dew point comes off the same
    thermometer as the temperature.
  - Pressure is taken **only** from the reading ČHMÚ already reduces to sea level; the station-level
    pressure the ten-minute network reports is ignored on purpose.
  - Visibility and present weather are published as ČHMÚ code numbers with no code list, so they are
    left out rather than guessed at.
  - The daily card is captioned with ČHMÚ's **regional text forecast**, written by the duty
    forecaster. Filenames carry their issue time and the directory has no index, so candidates are
    built from the publishing schedule and tried newest first.
- **Air quality** — all six pollutants Breezy Weather tracks, in µg/m³, from the national monitoring
  network. The nearest station's registration ids are resolved once, so a refresh costs an 18 KB file
  rather than the 1.5 MB catalogue. **Background stations are preferred** over the kerbside and
  industrial ones, so the reading is the air the town breathes rather than a traffic canyon.
- **Temperature normals** — 1991–2020, with ČHMÚ's mean daily maximum and mean daily minimum mapping
  straight onto the daytime and nighttime pair.
- **No forecast, deliberately.** ČHMÚ's own point forecasts are prose and its ALADIN output is GRIB2,
  neither of which a phone can use.

### Open-Meteo

- **ČHMÚ ALADIN** added to the weather-model picker, in all three variants — Czechia at 1 km, Central
  Europe at 2.3 km, and seamless, which chooses between them and carries on past the three days
  ALADIN runs for with ECMWF. This is the only route to a Czech forecast computed by the Czech
  service.

### Documentation

- `docs/SOURCES.md` gains a ČHMÚ section with its feature table and the reasoning behind what is
  omitted; `docs/COVERAGE.md` fills in the Czechia row; the store descriptions list ČHMÚ in every
  language.

---

## 白い熊 天気 6.2.1+009 — 2026-08-10

Built on upstream **v6.2.1**. First published release of the fork, so this is everything built on top
of stock.

### Major features

- **The 白い熊 天気 UI page** — one page carrying every knob that shapes the app's look: colours,
  fonts, sizes, roundness, border and divider widths, indent step, row padding, group spacing. Each
  write lands in preferences *and* in Compose state, so dragging a slider repaints the app underneath
  you; the page is themed by the values it edits, which makes the app its own preview. Reachable from
  Settings (first item) or by long-pressing the settings cog on the locations screen.
  - Colours from an RGBA picker over a live mix preview, with a one-click row of the colours already
    in use.
  - Fonts from a picker that renders every candidate — including `.ttf`/`.otf` files imported into
    app storage — in its own glyphs.
  - Every border, divider and roundness slider reaches **0 meaning "draw nothing"**, never "the
    Material default".
  - A master switch hands the app back to upstream's Material theme.
- **Export / Import** — a settable backup folder (red until set, yellow once it is), the newest
  archive found there, and a category checklist: UI, app settings, weather source configuration (API
  keys, hence its own tick), locations, imported fonts. Archives are named
  `shiroikuma-tenki_<yyyy-MM-dd_HH-mm-ss>.zip`, the shape every sister app uses. The SAF write is
  **atomic** — streamed to a `.part` document and renamed only once the archive is complete, deleted
  on any failure or cancel. There is exactly one export implementation.
- **Two hand-cut weather-icon packs** — the twelve weather codes re-drawn in the house yellow from
  one 256-unit geometry model: **traced** (stroke-only line-art, the default) and **full** (the same
  silhouettes, filled). A filled shape is stroked as well at the same width, so the two packs
  register pixel for pixel. Upstream's set stays selectable as **Breezy Weather**.
  - 130 drawables per pack: the animated icon and its layers, the widget/notification minis in light,
    grey and dark, the notification small icon, the launcher-shortcut badge and adaptive foreground.
  - Upstream's animators are borrowed by name and the layer split matches what each one moves, so the
    icons still breathe, drift and fall.
  - Composites are laid out **disjoint** — line-art has no fill to hide an overlap behind, so in
    partly-cloudy the sun sits clear of the cloud rather than behind it.
  - The "grey" mini carries a black halo under the yellow, for the unknown widget ground it lands on.
  - The astro card's sun and moon are flattened to the house yellow; the crescent and the rayed disc
    still tell them apart.
  - All emitted by `tools/icon/emit_weather_icons.py`, the only source, which sweeps whatever it no
    longer writes.

### UI & theming

- **`BreezyWeatherTheme` is the single wrapping point** — all 39 call sites go through it, so
  providing `LocalTenkiUi` and swapping the `ColorScheme` / `Typography` / `Shapes` there themes the
  whole Compose app and repaints it live.
- **The palette is overridden in all four places upstream defines it** — `values`, `values-night`,
  `values-v31` and `values-night-v31`. The `-v31` pair points at Android's Material You *system*
  colours and beats an unqualified override on Android 12+, which is why a partial override left the
  toolbar white and the chips blue. The built APK carries no dynamic-colour variant left.
- **The View world is painted by hand from the same knobs**, since `BreezyWeatherTheme` cannot reach
  an XML view: the locations card, the main weather cards, the trend tab buttons, the chips, the
  snackbar.
- **Yellow borders everywhere** — drop-in `AlertDialog` / `TextButton` / `Button` / `OutlinedButton` /
  `FilledTonalButton` carrying the house border, buttons as full pills. 20 screens opted in by
  changing one import; no call site moved. `AlertDialogNoPadding` borders its own surface, and
  View-world dialogs get a black-and-yellow background through `materialAlertDialogTheme`.
- **Card list items carry the border too**, which is what makes every settings and About row read as
  pressable. On the UI page every tappable row sits in a bordered box, the indent kept *outside* it so
  the nesting still reads at a glance.
- **Pure black cards** — elevation 0 with the ground set outright. Material lightens an elevated
  surface by compositing the surface tint over it; that tint was the grey. Compose's
  `getWidgetSurfaceColor` skips the tint too, so Compose and View cards are the same black.
- **Tag chips** are yellow-outlined pills, black with yellow text; the selected one **reverses** to a
  yellow ground with black text. The check icon is dropped — the reversal already says which is on.
- **Wind arrows and their markers take the accent** instead of the Beaufort green-to-red scale, since
  the strength is printed as a number beside every arrow. Air quality and UV keep upstream's scales
  **on purpose**: there the colour *is* the reading.
- **The animated header is recoloured, not deleted.** A hardware layer with a duotone colour filter
  maps each pixel's brightness onto the Background → Accent ramp at composite time, so upstream's
  drifting cloudscape, rain and meteor shower come out black-to-yellow with their shape and motion
  intact — every weather implementor at once, without touching upstream's drawing code. Alpha is left
  alone, so transparent parts stay transparent.
- **"Weather scene brightness"** — a new Header group on the UI page, 50 % by default. **0** takes the
  flat view instead, so nobody pays for an animation they cannot see.
- **The + button** on the locations screen is black with a yellow mark and a yellow outline.
- `colorSurfaceInverse` is treated as a **foreground**: upstream uses it as the bright text colour on
  the weather cards and only the snackbar uses it as a ground, so here it is the yellow, and the
  snackbar is repainted in code.

### Integrations

- **The 保存復元 sister-app backup-automation contract** — 白い熊 自由作業盤 can drive this app's
  export headlessly as part of the one-run batch.
  - `TenkiAutomationAuth`: master switch **default OFF**, a 24-byte `SecureRandom` token generated
    lazily, compared constant-time, kept in its own preferences file so it can never travel inside a
    backup.
  - `StateExportReceiver` (exported, token-gated), three actions: `LIST_CATEGORIES` answers instantly
    with id/label lines (fonts off by default); `EXPORT_STATE` validates `items` and hands off;
    `CANCEL_EXPORT` signals the run and replies nothing — a cancel with nothing running is a silent
    no-op.
  - The export runs in a **foreground `dataSync` service** with a partial wakelock, because
    `goAsync()` does not extend the broadcast window and overrunning it gets the process ANR'd
    mid-write.
  - Exactly one terminal reply per request, guarded by an `AtomicBoolean`; a process-local running
    flag released in a `finally` and never persisted; progress broadcasts carry the category id and
    **real counts**, never a percentage.
  - **`MANAGE_EXTERNAL_STORAGE` is deliberately not held** — a weather app has no business with
    All-Files-Access. The contract's `path` extra is honoured only if that grant happens to exist;
    otherwise the export lands in the SAF folder set on the UI page, and with neither the reply is
    `ERROR:no-directory` / `ERROR:no-storage-access`.

### Identity & de-branding

- **Launcher icon**: upstream's four-blade pinwheel redrawn as stroke-only line-art, pure `#FFFF00` on
  black. `tools/icon/emit_launcher.py` cuts every asset from one geometry model — the adaptive
  background and foreground (which doubles as the monochrome layer), the legacy webp set at five
  densities plus the round variants, and the 512 px store icon. The placeholder foreground left
  `drawable-v24`: the vector needs no v24 feature and `minSdk` is 23.
- **App label** `白い熊 天気`, and `MainActivity`'s launcher label follows the brand rather than
  upstream's per-locale `app_name` ("Weather" / "Météo" / …).
- **Every user-visible "Breezy Weather" in all 43 translated locales** is now 白い熊 天気 — the
  data-sharing permission label and description, the location-permission dialog, the freenet
  disclaimer.
- README, INSTALL, PRIVACY, HELP, CONTRIBUTE, `docs/`, the store metadata and the issue templates all
  carry our name and our repo.
- **Upstream's CI workflows are dropped.** After the link rewrite their `github.repository ==` guard
  would have named *our* repo while still running `-Pbreezy` — the one build flag the licence forbids
  us to ship.
- **User-Agent fix the rename made necessary**: it is built from the brand name, and an HTTP header
  value must be ASCII, so a Japanese brand made OkHttp throw on every request to Met.no, NWS or
  Nominatim. It now carries the brand's ASCII part, falling back to the `applicationId`.

### Packaging & build

- `applicationId` `shiroikuma.tenki`; the code namespace stays `org.breezyweather`, so rebasing onto a
  new upstream release never turns into a mass rename.
- **Upstream's own fork path is used rather than fought**: the build never passes `-Pbreezy`, so it
  takes `app/src/res_fork/` for the brand assets, the `app.*` links from `gradle.properties`, and
  `config-fork/` for AboutLibraries. Upstream's licence forbids redistributing a modified APK with the
  brand config enabled, and `LICENSE_ADDITIONAL` requires modified versions to be marked as different
  from the original — which is what this fork does.
- **Versioning**: `versionName = "<upstream>+NNN"` (counter zero-padded to three digits),
  `versionCode = <upstream code> * 10000 + N`, both derived from upstream's own two literals so a
  rebase brings the new base in by itself. `BUILD_NUMBER` resets to 1 on every upstream sync.
- **Tracked against upstream release tags, not the branch tip** — every base is a state upstream
  itself called finished.
- Release signing from a gitignored `keystore.properties`; `buildFork` assembles the signed `basic`
  release, copies the arm64-v8a split to `~/tmp/`, and bumps the build counter.

---

# Old changelogs

- [Changelog for v5.x](docs/CHANGELOG_5.x.md)
- [Changelog for v4.x](docs/CHANGELOG_4.x.md)


# Version 6.2.1 (not yet released)

**Improvements and fixes**
- Cap temperature animation duration to 1 to 2 seconds
- Ensure pollutant and pollen concentrations are always positive to avoid a rare case of crash when some sources provide negative concentrations
- Notification widget - Daily - Fix reversed feels like setting

**Security**
- Enable Arm memory tagging (MTE) asynchronous mode

**Sources**
- AEMET - Fix current visibility and night wind speed units
- China - Use a server which is less likely to return invalid data when language is set to Chinese
- KNMI - Fix duplicate “Today” when migrating from another source
- KNMI - Fix precipitation probability being 100 times too low
- Recosanté - Removed source will no longer be available for choice in the source selection screens


# Version 6.2.0 (2026-05-01)

**New features**
- Add ability to disable the weather condition background on main screen. You can find it in Settings > Main screen.

**Improvements and fixes**
- Fix failing to refresh some forecast sources in some cases when the source is not reporting total precipitation
- Forbid editing locations when weather is refreshing to avoid race condition
- Fix address lookup not working after changing a source on adding a new location with coordinates input manually (from `geo:` intent)
- Fix some notifications marked as sensitive (will now show on lockscreen)
- Fix notification-widget “Use feels like” option only available when using temperature in statusbar
- Fix notification-widget not always using feels like temperature when the option is enabled
- Fix crash when relative humidity is 0% and dewpoint is missing

**Sources**
- Atmo France - Use new geocoding services to fix refresh issue for some locations
- China - Fix nowcasting refresh silently failing
- ECCC - Fix missing alert title
- FMI - Added as a new forecast, current, air quality, alert, normals source for Finland (@chunshek). Users on devices with hardened memory allocator (`hardened_malloc`) enabled, such as GrapheneOS may want to disable the feature for the app if they find the refresh to be too slow.
- Gadgetbridge - Improved weather codes mapping
- Gadgetbridge - Will now send again longitude, latitude and if it is current location with allowed apps
- HKO - Add daily weather summary + add next hours summary + update existing ones (@chunshek)
- Infoplaza - Added as a new worldwide forecast, current, minutely and normals source (@SKBotNL)
- KNMI - Add textual representation of weather conditions (@willem640)
- Météo-France/OpenWeather - Improve total precipitation computing (@chunshek)
- MET Éireann - Fix all hours being shifted by 1 hour, and 23:00 missing or shifted by 1 day
- Nominatim - Improve address lookup, and add support for districts for a more detailed result
- NWS - Exclude test alerts
- Pirate Weather - Fix weather texts showing temperatures in the wrong unit (@chunshek)
- SMG - Fix missing current data (@chunshek)
- SMG - Add daily min/max humidity (@chunshek)
- SMG - Add/Update weekly/next hours summary + add daily summary (@chunshek)
- SMHI - Fix failure to refresh by migrating to new API (@4eUeP)


# Version 6.1.3 (2026-02-12)

**Hotfix**
- Fix `freenet` flavor being released unbranded


# Version 6.1.2 (2026-02-12)

**Improvements and fixes**
- Remove minus sign when a number is rounded to 0 on Android < 11

**Sources**
- NWS - Fix incorrect alert end date in some cases

**Translations**
- Initial translation for Azerbaijani (Elçin)
- Translations updated

**Technical**
- By default, the app will now compile unbranded. It has always been a requirement to change the name of the app when creating forks, but this has now been simplified. The aim is to avoid the confusion between our app and forks distributing modified releases we do not endorse.


# Version 6.1.1 (2026-02-01)

**Hotfix**
- Fix crash on Android 11-13


# Version 6.1.0 (2026-02-01)

**New features**
- Content provider: allows (with your permission) other apps to query your weather data. [Read the announcement](https://github.com/ShiroiKuma0/shiroikuma-tenki/discussions/2089)
- New broadcast: you can use `shiroikuma.tenki.ACTION_UPDATE_NOTIFIER` (or `shiroikuma.tenki.debug.ACTION_UPDATE_NOTIFIER` with the debug build) to be notified of updated locations (most common use case is coupled with the content provider)

**Improvements and fixes**
- Fix crash on some devices when current weather is snow (@Cactric)
- Fix overlap of texts on double line charts when the line “below” goes above the “above” line (for example, a min. night temperature higher than the max. day temperature)
- Fix sun showing up on main screen when yesterday moon is still up and sun has not yet risen
- Fix wind speed being cut on main screen on some devices with some languages
- Fix overlap of rise/set times in sun & moon blocks with some languages if using 12-hour format
- Remove minus sign when a number is rounded to 0 on Android >= 11
- Fix missing precipitation probability in Daily Feels like tab

**Weather sources**
- BMD - Fix refresh error with recently added locations. Affected locations will need to be removed and re-added
- CWA - Fix bulletin (@chunshek)
- CWA - Add support for typhoon warnings (@chunshek)
- Koninklijk Nederlands Meteorologisch Instituut - Added as a forecast, current, alert and normals source for Netherlands (@willem640)
- Icelandic Met Office - Fix refresh error (@chunshek)
- IMS - Add support for rain quantity (@ntzb)
- Open-Meteo - Fix day/night temperatures sometimes being shifted by 1 day
- Pirate Weather - Fix refresh error

**Translations**
- Translations updated


# Version 6.0.12 (2025-11-11)

**This is the last release for Android Lollipop (5.0/5.1) users**

**Improvements and fixes**
- Daily/hourly forecast - Ensure the maximum value is always at a minimum defined value to ensure data is put in perspective, and remove threshold lines that weren’t very useful and cluttering the interface (wind, precipitation, cloud cover)
- Make 24-hour charts and nowcasting charts less prone to swipe to next screen
- Main screen - Fix moon icon disappearing past midnight
- Main screen - Fix blocks not appearing after fade in animation was interrupted due to fast scrolling
- Main screen - Fix animations re-appearing when scrolling (@min7-i)
- Fix current air quality disappearing when refreshing too fast

**Weather sources**
- China - Fix refresh error for some users (@kmod-midori)
- MET Éireann - Migrate to new API
- Nominatim - Add missing preference to change server instance
- Open-Meteo - Allow individual selection of new weather models: ECMWF IFS HRES 9 km, NCEP NAM U.S. Conus, MeteoSwiss
- OpenWeather - Fix current condition not translated
- Pirate Weather - Add support for thunderstorm icon (@cloneofghosts)
- Pollen Information AT - Add support as a pollen source for some European countries (@phileix)

**Translations**
- Translations updated


# Version 6.0.11-rc (2025-09-03)

**Improvements and fixes**
- Fix crash when entering Appearance settings using 12-hour format with scheduled dark mode
- Current location - Fix details sometimes not saved to database (previous location details restored on restart of the app)
- Remove animations in the pressure block as it caused flickering
- Change default distance unit for Germany to kilometer, as per DWD usage
- Change default speed unit for Netherlands to meter per second, as per KNMI usage
- Fix threshold value for scattered cloud cover (@cloneofghosts)

**Translations**
- Translations updated


# Version 6.0.10-rc (2025-09-01)

**Improvements and fixes**
- Add instructions to pull to refresh instead of leaving a blank screen when weather failed to load initially (@Amitesh-exp)

**Weather sources**
- ECCC - Technical changes
- NCDR - Fix error when there is no alert (@chunshek)

**Translations**
- Translations updated


# Version 6.0.9-beta (2025-08-31)

**Improvements and fixes**
- Clarify which dark mode is currently used at system level in Appearance settings, which may help Xiaomi device owners detect a potential bug in the MIUI dark mode implementation
- Freenet - Improve wording of messages about non-free network services
- Freenet - Display the names of non-free network services in source lists to let the user know about the availability of other sources in the Standard flavor
- Android 11+ - Fix unneeded zeros sometimes showing in fractions

**Weather sources**
- IP.SB / Baidu IP Location - Don’t require Android location to be on

**Translations**
- Translations updated

**Technical**
- Fallback to latest known current data rather than current hour forecast when last successful refresh was less than 30 min ago


# Version 6.0.8-beta (2025-08-27)

**Improvements and fixes**
- Minor changes to weather blocks to improve accessibility (text size, color contrast, etc.)
- Widgets - Round temperature values
- Nowcasting block - Fix truncated start and end values

**Translations**
- Translations updated


# Version 6.0.7-beta (2025-08-26)

**Translations**
- Translations updated
- Add missing distance, speed and precipitation unit translations on Android < 7

**Technical**
- Added timezone deduction based on subdivision codes (@chunshek)

# Version 6.0.6-alpha (2025-08-24)

**Improvements and fixes**
- Fix crash on startup on Android 5.0, 5.1 and 6.0
- Fix crash on Android 7.0/7.1 when formatting some units
- Widgets - Fix crash on Android 9.0 to 11.0 with font size set to something other than 100%

**Weather sources**
- [HERE] Removed following recent restrictions on free API

**Translations**
- Translations updated


# Version 6.0.5-alpha (2025-08-23)

This version is still an experimental one, with a significant rewrite of the refresh process core, especially on current locations. Weather data for all locations will be reset due to a major technical change in the database. A simple refresh will bring it back.

**Removed features**
- Mean daytime/nighttime temperatures as threshold lines. Use a normals source instead
- [Met Office UK] Removed address lookup feature
- Pressure unit - Kilogram force per square centimeter

**Improvements and fixes**
- Main screen - Allow to move small blocks by drag & drop
- Main screen - The number of items displayed at once in daily/hourly forecast now depends on display size and font scale (previously always 5 in portrait, and 7 in landscape)
- Main screen - Show “Negligible” inside Pollen block if there is no pollen today instead of an empty block
- Main screen - Allow up to 5 blocks on a row depending on width display size and font scale
- Main screen - Move refresh time out of app bar when scrolling
- Main screen - Fix settings not applying immediately
- Main screen - Fix shooting stars getting stuck in the corner in landscape
- Details - Add a bottom margin at the end of each page, so that it doesn’t overlap with the floating button
- Details - Don’t animate charts when “Other element animations” is disabled
- Details - Air quality - Add individual charts for each pollutant
- Details - Humidity/Dewpoint/Cloud cover - Show min/max of the day
- Details - Pressure/Visibility - Fix sometimes wrong daily value
- Details - Fallback to current value on Today screen when daily value is missing
- Details - Add visibility and cloud cover scales
- Details - Fix top X-axis sometimes showing “-” for some sources
- Details - Charts are now slightly wider following the removal of start and end paddings by removing midnight labels
- Alerts - Add “Translate” and “Share” to text select actions
- Nowcasting chart/Precipitation notification - Fix slightly wrong ending time of precipitation report
- Settings - Improve the location-based dark mode preference to make it easier to understand
- Sources - Add a “Recommended” section to the Source selection screen
- Refresh - Fix a rare crash when Android fails to send us the current location
- Refresh - Add an error when air quality forecast times don’t match hourly forecast times (observed in India, for example)
- Refresh - Ensure range of (almost) all values provided by sources, so you no longer have to freak out when seeing -999° with PirateWeather or 1015° with Meteo AM
- Data sharing - Fix crash when sending too many locations (will now retry with less locations)
- Widgets - Improve UX of custom subtitle documentation (@codewithdipesh)
- Widgets - Improve line height on many widgets
- Widgets - Weekly - Spread day/night temperatures on 2 lines if necessary
- Widgets - Minor fixes
- Wallpaper - Due to some people running outdated versions of 白い熊 天気 just to see some gimmicks on their wallpaper, we bring back wallpaper animations behind a dangerous disabled-by-default option. We STRONGLY advise against enabling them.

**Weather sources**
- [AccuWeather] Restrict pollen to USA, Canada and Europe as it’s only available there (@chunshek)
- [China] Fix reversed color and severity for alerts (@chunshek)
- [EKUK] Fix failure to refresh air quality
- [FOSS Public Alert Server] Add support for this experimental source for alerts (@chunshek)
- [GeoSphere AT] Fix missing info in warnings
- [GeoSphere AT] Use the newer better endpoint for air quality
- [JMA] Added Thai translations (@chunshek)
- [LVGMC] Fix current observations (@chunshek)
- [NCDR] Added as alert source for Taiwan (@chunshek)
- [NCEI] Added support for normals (@chunshek)
- [Nominatim] Added as another location search
- [NSLC] Added as address lookup source for Taiwan (@chunshek)
- [NWS] Alerts - Updated terminology for Extreme Heat (@chunshek)
- [Open-Meteo] Restrict pollen to Europe as it’s only available there (@chunshek)
- [Pirate Weather] Add support for daily/hourly summaries
- [Veðurstofa Íslands] Added as forecast, current, alert and address lookup source for Iceland (@chunshek)
- [WMO SWIC] Avoid missing alerts which expired date was updated
- [ANAM-BF, DCCMS, DMN, DWR, EMI, GMet, IGEBU, INM, Mali-Météo, Météo Benin, Météo Tchad, Météo Togo, Mettelsat, MSD, Pirate Weather, SMA (Seychelles), SMA (Sudan), SSMS] Add to  ̀freenet` flavor (was missing despite being FOSS)

**Translations**
- Initial translation added for Íslenska (@chunshek)
- Translations updated
- Alternate calendar: add Hebrew calendar
- Alternate calendar: add more defaults based on regional preferences

**Technical**
- Current location process refactoring: coordinates, forced refresh when coordinates changed from more than 5 km
- Address lookup process refactoring to prepare for future ability to add a location manually by coordinates
- Experimental offline timezone deduction for address lookup sources missing the info or for Nominatim search service (@chunshek)
- Unit conversion/formatting refactoring. **Known temporary issue:** Some distance, speed and precipitation units are no longer translated on Android < 7


# Version 6.0.4-alpha (2025-07-23)

**Improvements and fixes**
- Main screen - Improvements to some cut off texts with different display sizes
- Main screen - Improve the “two blocks per row” threshold when using custom font scale
- Details - Fix precipitation probability details being expressed in precipitation unit instead of %
- Fix missing normals every other refresh

**Translations**
- Translations updated


# Version 6.0.3-alpha (2025-07-22)

**New features**
- Redesign of main screen in Material 3 Expressive
- New information previously not shown on main screen: current wind gusts, clock (block not enabled by default)
- Redesign background animations/colors to better adapt to the selected dark mode and avoid saturated colors with bad contrast

**Removed features**
- Main screen - Details in header
- Main screen - Details block
- Custom weather and time per location
- Details of each different “feels like”. Will now just display the source-preferred feels like value, or if not available, our own computed feels like

**Improvements and fixes**
- Fix nowcasting chart not honoring precipitation unit override
- Details - Fix feels like toggle not remembered through days
- Main screen - Fix tapping daily/hourly feels like forecast opening conditions with feels like toggle off
- Details - Display normals as deviation directly under daytime/nighttime temperature
- Improve display of precipitation details
- Details - Make tooltips persistent until you click outside the bounds of the tooltip
- Details - Show current air quality on Today page when no daily air quality is available

**Translations**
- Translations updated


# Version 6.0.2-alpha (2025-07-19)

**Improvements and fixes**
- Fix crash in some cases on old Android devices
- Fix notification icons not showing
- Make main screen top icons feel more intuitive

**Weather sources**
- [Météo-France] Better formatting for warnings


# Version 6.0.1-alpha (2025-07-17)

**New features**
- Twilight dates (dawn and dusk)

**Removed features**
- Sun & Moon data from sources. Will now always be computed by 白い熊 天気 for consistency

**Improvements and fixes**
- Details page - Fix floating action button not updating in real time (@min7-i)
- Details page - Charts - Fix area fill in Right to Left languages (@chunshek)
- Details page - Fix jumping of the chart when tapping on it
- Details page - Workaround missing top padding in the FAB menu for small device heights (@min7-i)
- Details page - Conditions - move long weather condition description to a dedicated Daily summary card (especially noticeable with AccuWeather source)
- Details page - Sun & Moon - Fix glitched charts (@chunshek)
- Main screen - Attempt to make horizontal swipes in daily/hourly trends less prone to switch to prev/next locations
- Main screen - Move “Settings” icon to location list to be able to display icons on main screen without a submenu.
- Main screen - Better animation for main screen current temperature when using Fahrenheit or Kelvin
- Main screen - Use Material 3 Expressive buttons for forecast buttons
- Main screen - Fix sun & moon direction in RtL languages
- Main screen - Fix air quality direction in RtL languages
- Main screen - Fix missing hourly visibility in some cases
- Settings - Material 3 Expressive theme
- Settings - Add shortcuts to daily/trend configuration from cards configuration
- Fix tint of “Open in another app” icon in landscape mode
- Improve the formatting of today/tomorrow notification
- Live wallpaper - Fix wallpaper animating when switching between apps
- Fix specific language for the app not remembered after reboot
- UV - Better computing of missing hourly UV from day UV (@chunshek)

**Translations**
- Translations updated
- Default units are now based on system region. It does not support Android 16 “Measurement system” preference yet, as there seems to be no way to access this value for now.
- Better number formatting on Android >= 7
- Better measure formatting on Android >= 7


# Version 6.0.0-alpha (2025-06-26)

**New features**
- Complete overhaul of the daily details page to offer a better visualization of the data, and more explanations about the different types of weather data
- Past hourly forecast can now be viewed in the details page

**Removed features**
- Main screen hourly forecast card will now only show the next 24 hours, as the rest of the forecast can now be seen with more readability in the daily details page.
- The dedicated pollen page accessed when tapping on the pollen card now no longer exists, and was replaced by the pollen page in daily details.
- Tapping on the main screen air quality card no longer show more details, but open the air quality page in daily details instead.
- Tapping on an hourly item in the main screen hourly forecast no longer opens a dialog, but now opens the day details page of the currently selected type of data

**Improvements and fixes**
- Redesigned main screen footer to support links to the sources, a link to the privacy policy, and icons for the sources for which it is mandatory
- Fix crash when using “Open in another app” when no app on the phone is able to open it

**Weather sources**
- [ECCC] Added UV index

**Translations**
- Translations updated
