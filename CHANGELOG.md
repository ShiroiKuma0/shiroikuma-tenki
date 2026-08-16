# Changelog

**白い熊 天気** is 白い熊's fork of
[Breezy Weather](https://github.com/breezy-weather/breezy-weather). This file carries **both**
histories: our fork releases first, then upstream's own changelog below, unchanged.

Fork releases are named `<upstream version>+NNN` and each says which upstream release it is built on.
Our block sits above upstream's so their new versions land further down the file and a rebase never
has to merge the two histories by hand.

---

## 白い熊 天気 6.2.1+057 — 2026-08-16

Built on upstream **v6.2.1**.

- **The daily graph marks now.** The hourly card has carried a dashed rule at the current moment ever
  since the trend charts were redrawn as meteograms; the daily card had nothing, so a trace running
  continuously across the week never said how far along today's rise or tonight's fall the week had
  already got. The daily columns now carry the same rule — same width, same dash, same strength —
  drawn through the plotting area and over the fill rather than under it, on every daily tab and on
  every stacked source at once.
- **It is placed on the axis the trace itself implies, not on the calendar's.** A day's column runs
  06:00 to 06:00, because that is what its two knots mean: a daily forecast's day half is 06:00–17:59
  and its night half 18:00–05:59 of the morning after, so their centres — noon and midnight — are
  exactly the quarter and three-quarter points the trace peaks and troughs at. Measuring the calendar
  day instead would put noon halfway down the fall, when noon is the top of the rise, and the rule
  would cross the curve at a temperature that is not happening. The visible consequence is
  deliberate: between midnight and 06:00 the rule sits near the right-hand edge of *yesterday's*
  column. That is where the trace is — the night we are in is the one that column falls through — so
  the rule still meets the curve at now.
- A column takes its width from the next column's own midnight rather than from a fixed twenty-four
  hours, so a day the clocks change on is 23 or 25 hours wide and the rule stays in step with it; and
  the rule is held half a stroke clear of both edges, since 06:00 lands on a column boundary every
  morning and a line drawn exactly there would be clipped down its middle.

---

## 白い熊 天気 6.2.1+056 — 2026-08-16

Built on upstream **v6.2.1**.

- **The stacked forecast charts stand on one column axis.** A source added today has no history, so
  its first column was the current hour while the source above it opened three hours earlier — the
  same x meant a different hour on each chart, which is the one thing a stack of them exists to
  avoid. Each card now takes the union of every selected source's hours, or of their local days, and
  hands a source the columns it does not reach as blanks: the hour label and the banding are there,
  the curve and the icon are not, so missing history reads as missing rather than as a chart that
  starts somewhere else. Only the range OUTSIDE a source's own hours is filled — MET Norway drops to
  six-hourly past its second day, and forcing its interior onto a neighbour's hourly grid would break
  its curve into dots. A blank column opens nothing when tapped, and the day handed to the details
  screen has the blanks in front of it subtracted again.
- **A swipe on one of a card's charts carries the others with it.** One shared axis lasts exactly
  until a chart is scrolled on its own. A drag, and the fling after it, now travels to the card's
  other charts; a programmatic scroll — the column each chart opens on, the restore-view button —
  does not, so opening a card no longer drags its neighbours along. **Scroll stacked graphs
  together** on the theming page turns it off.
- **The columns no longer hang out of the bottom of their own chart.** A column's height was a fixed
  dimension while the chart's height is a setting here, and the two only agreed at the one value the
  setting shipped with. Move the slider and every column stayed the size it always was, hanging out
  of the bottom of a shorter chart where the plotting floor, the coldest hours and their readings
  were drawn below the edge and clipped away — which reads as the curve disappearing at both ends,
  since the ends of a day are its cold end. A column now measures to the height of the chart hosting
  it, which is also what makes the two graph-height sliders do anything at all for the first time.
- **The temperature scale is fitted to the columns on the screen.** It was worked out once, when the
  card was built, from a window derived from the settings — right until the first pinch or the first
  swipe, after which the hours brought into view fell outside it and ran off the bottom. Each chart
  now reports which columns it is drawing, from where the columns actually are rather than from the
  layout manager's padded idea of visible, and the scale follows: wherever you scroll or pinch to,
  the warmest thing in view reaches the top of the pane and the coldest sits on the floor. The new
  scale is handed straight to the columns already drawn, so a scroll costs an invalidate instead of
  rebinding every column, text and icons and all.
- **The daily graph is sized by how many days you want across it.** A day column was a fixed width,
  so how much of the week fitted on the screen depended on the screen. **Days across the daily
  graph** on the theming page decides it instead — nine by default — and the column is the card's
  width divided by that. Every daily tab shares the width, so switching tabs still keeps its place.
- **Both graphs are shorter**: the hourly by a third, the daily by a fifth. A shipped default only
  applies to a setting nobody has ever written, so the store lets go of those two once on this
  upgrade; a height set deliberately before it is back to the new default, and anything set from here
  on stays.
- **A day a source publishes with only one of its halves is filled in rather than run flat.** ČHMÚ's
  national outlook files a night's minimum under the day it *precedes*, so its last day arrives with
  a maximum and no minimum — and a trace with nowhere to fall held the peak level to the end of the
  week, which read as an afternoon that never cooled off. The missing half now takes the day-to-night
  swing of the nearest days that have both, preferring the ones before it, so the curve comes down
  the way that source's own week comes down. It is drawn faded and carries no reading: the shape is
  ours, the figures stay theirs.
- **The daily trace ends at the night instead of running level to the column's edge.** With no day
  after the last one there is nothing for the fall to reach, and carrying the night's value across
  the remaining quarter of the column drew a fall that stopped falling.
- **A reading beside a column a source does not have no longer flies to the top of the chart.** A
  missing knot was treated as the very top of the view when the reading was placed, rather than as
  this column's own value, which is how the fill has always treated it.

---

## 白い熊 天気 6.2.1+050 — 2026-08-15

Built on upstream **v6.2.1**.

- **Every daily chart opens on today.** Only the longest one did. A list never scrolls past the end
  of its own content, so a source carrying few enough days to fit on the screen had nothing to scroll
  and opened on the day of history sitting in front of today, while Open-Meteo's sixteen days had
  room to spare and landed where it was told. Each chart now reserves as much empty room after its
  last column as the columns from today on leave unused, which is what gives the scroll somewhere to
  go — and it lands by putting today at the left edge outright rather than asking for it to be
  brought on screen, which leaves a column that is already visible exactly where it stands, as today
  on a short chart always is. The past stays one swipe to the left, and the hourly card lands the
  same way.
- **Restoring the default view no longer widens one chart and leaves its neighbours narrow.** The
  zoom is one stored level for the whole card, so the first chart to put it back found it already at
  the default — and every chart after it re-measured nothing, keeping the columns it was last drawn
  with beside one that had just widened. A pinch parted them the same way, re-measuring only the
  chart under the fingers. Both now re-measure every chart on the card.

---

## 白い熊 天気 6.2.1+048 — 2026-08-15

Built on upstream **v6.2.1**.

- **The Meteomap is no longer stuck an hour behind.** Frame URLs name the minute they depict and can
  never change, so they are cached for a week — but the same rewrite was landing on the *manifest*,
  which is the index of which frames exist and the one thing that does change (ČHMÚ marks it
  `max-age=60`). Once fetched, the radar froze at that hour for a week, across app updates, since
  the cache lives in `cacheDir`. The manifest is now exempt, and read straight off the network so a
  cache still holding the week-long entry is stepped over rather than waited out.
- **The map keeps up while you watch it.** The manifest is re-read on ČHMÚ's own published interval
  — 180 s for the radar — but only while the screen is in front. The playhead holds its frame by
  name rather than by number, so a newly published frame does not shuffle the map a step backwards;
  sitting on "now" follows the new now.
- **The timeline's hour labels sit on the hour.** They were placed every `frames/10` — forty minutes
  apart on the five-minute radar — and printed only the hour, so the axis read `06 06 07 08 08 09 10
  10 11`, naming one hour twice, missing another, and lining up with none of the rules above them.
- If a manifest cannot be fetched at all, the last one is kept rather than blanking the map, as the
  playback already does with a frame.

---

## 白い熊 天気 6.2.1+047 — 2026-08-15

Built on upstream **v6.2.1**.

- **The third countries' borders are the accent yellow now, at 4 dp** rather than a thin navy. The
  navy read as one more river on the device; the accent is the one colour on the map that is never
  weather and never water, so the frontiers stop competing with the Vltava.
- **They are drawn over the water, not under it.** At this weight a river crossing a boundary was
  notching it in blue.
- Round caps and joins on those runs — the geometry is coarse, and a miter joint spikes out of every
  sharp vertex.

---

## 白い熊 天気 6.2.1+046 — 2026-08-15

Built on upstream **v6.2.1**.

- **The third countries' borders are drawn only where they meet each other**, in a dark navy at
  2.7 dp. Their frontiers with Czechia are no longer in the data at all: that line is already drawn
  in the accent, and painting it twice only thickened it and put a halo round the country.
- **Those runs now reach the Czech border.** Cutting the coincident stretch left each one ending a
  few kilometres short. Both thresholds are measured rather than guessed — ends cut by the frontier
  sit 0.07–0.19° from it, ends cut by the edge of the map at 0.89° and beyond — so a cut end is
  pulled onto the frontier and an edge end is left alone.
- **The ground no longer spills off the map.** It is cut a little wider than the frame so its lines
  run off the edge rather than stopping short, but nothing clipped it back, so the rivers and
  borders were drawing over the tab row and across the timeline.

---

## 白い熊 天気 6.2.1+045 — 2026-08-15

Built on upstream **v6.2.1**.

- **The city reading and name are bigger, and both are sliders** on the 白い熊 天気 UI page —
  21 dp and 13 dp by default, up from 15 and 9.
- **The neighbouring borders are cased grey-black-grey**: three strokes of falling width leaving a
  dark band between two pale edges, the way an atlas draws an administrative boundary. In the accent
  they read as more of Czechia; in grey and black they read as a line somebody agreed on rather than
  as something the weather is doing.

---

## 白い熊 天気 6.2.1+044 — 2026-08-15

Built on upstream **v6.2.1**.

- **Each city label sits on its own dark plate.** Outlining alone was not enough — the numerals land
  on whatever the field is doing beneath them, and yellow on a yellow-orange heatwave stays hard to
  read however thickly it is outlined. Same trick the trend charts' readings already use.
- **The neighbouring borders are marked properly**: 1.5 dp at 92% rather than a 0.8 dp hairline at
  60%. They were being drawn all along — the data parses, the layer renders — but at that weight
  against a dimmed field they amounted to nothing, which is the same as not drawing them.

---

## 白い熊 天気 6.2.1+043 — 2026-08-15

Built on upstream **v6.2.1**.

- **Temperature labels at twelve towns** on the forecast map — Praha, Brno, Ostrava, Plzeň, Liberec,
  Olomouc, České Budějovice, Hradec Králové, Ústí nad Labem, Jihlava, Karlovy Vary and Zlín, chosen
  for even coverage rather than for population. They change as the playback runs.

  **The reading costs no request**: it is decoded out of the frame already on screen, at the pixel
  the town projects onto, by the same colour-to-value machinery that repaints the map. Checked
  against ČHMÚ's own point forecast for the same hour, all twelve agreed within half a degree.
  The sampling happens *before* the repaint, since our own ramp cannot be inverted.
- **Every label switches on and off** on the 白い熊 天気 UI page. The setting stores the ones turned
  **off**, so a town added in a later build appears without anybody having to enable it.
- **The neighbouring borders can actually be seen.** They have been drawn since +041, but in the
  accent at 30% alpha — invisible over a bright field. Now 60%.

---

## 白い熊 天気 6.2.1+042 — 2026-08-15

Built on upstream **v6.2.1**.

- **A long press on either of the weather screen's toolbar buttons** — the locations icon at the
  left, the overflow at the right — opens the 白い熊 天気 UI page, matching the settings cog on the
  locations screen. `Toolbar` builds both buttons itself and hands out no reference to them, so they
  are found among its children; the overflow does not exist until a menu item is visible, so the
  binding is repeated whenever that changes.

---

## 白い熊 天気 6.2.1+041 — 2026-08-15

Built on upstream **v6.2.1**. The Meteomap gets its bearings.

- **The map is no longer floating in a void.** Neighbouring countries are outlined faintly, and the
  rivers and lakes are picked out in blue — cut from **Natural Earth**, which is public domain, by
  `tools/chmi/build_meteomap_basemap.py` into a 37 KB bundled file. Still no tiles fetched from
  anyone.
- **Prague stands out**: filled and outlined brightest of everything on the map.
- **The national border is drawn apart from the districts** — traced from the union of the same ORP
  polygons — so the country reads as one shape with its internal detail kept quiet, instead of 206
  rings at one weight making a thicket.
- **The emblem is twice the size**, at 152 dp.
- **A clock over the map's top corner**, big, in the accent and outlined in black so it survives
  whatever the field is doing underneath. 24-hour by default, switchable on the 白い熊 天気 UI page.
- **A strip under the map**, in place of the plain slider: the location's own hourly forecast for
  that layer, filled column by column in the **layer's own colours** — a miniature of the field
  above. Day boundaries and hour marks are ruled through the chart **and** the track together, so a
  bump in the curve ties to a place on the slider and you can see at a glance which hour the
  temperature climbs or the rain arrives. Rain and radar plot blue.
- **Forecast is now the first tab**, and the radar is **1 Hour Radar** — it opens on the present and
  plays the nowcast hour first, only then looping round into the six hours of history behind it.

---

## 白い熊 天気 6.2.1+040 — 2026-08-15

Built on upstream **v6.2.1**. The app gets a map.

### Meteomap

An emblem **beside the temperature** on the weather screen — only for Czech locations, the maps
being ČHMÚ's — opens a full-screen animated map with two tabs.

- **Radar**: observed reflectivity at 5-minute steps, six hours back, with ČHMÚ's own **one-hour
  nowcast** running on past the present.
- **Forecast**: ALADIN hour by hour for three days — temperature, rain, snow, cloud cover, wind,
  humidity and sunshine, each its own layer.
- Time slider, play/pause, loop toggle and five speeds; pinch to zoom and drag to pan.

**The fields are repainted in our colours, and exactly.** ČHMÚ publishes the scale it painted each
field with, printed ticks included, so a pixel's colour is turned back into the reading it stands
for and then coloured again from the app's own palette. Temperature uses the very stops the hourly
meteogram uses — a colour on the map and a colour on the chart mean the same degree. Rain, snow and
radar are blue.

**The basemap is ours.** Czechia is drawn from the 206 ORP district boundaries the app already
carries for alert geocoding, so **no map tiles are fetched from anyone** and the country comes out in
the house black and yellow without asking. Weather beyond the border is drawn as well, only dimmed:
a shower crossing from Saxony matters long before it arrives, so clipping it away would hide the
thing worth watching.

The emblem is generated by `tools/chmi/emit_meteomap_icon.py` from those same boundaries — Czechia
in line-art with the sun over Bohemia and a storm over Moravia — so the icon and the map are the
same geometry rather than a hand copy of it. Both motifs are fit-checked against the path data that
actually ships: each is rasterised and shrunk until no pixel of it lands outside the border.

Data © ČHMÚ, CC BY 4.0. `docs/RADAR.md` said the app would never have a radar; for Czechia it now
does, and the page says so.

---

## 白い熊 天気 6.2.1+038 — 2026-08-15

Built on upstream **v6.2.1**.

- **A restore-default-view button at the top right of both trend cards.** It puts the columns back
  to the width the settings ask for and the chart back to the hour or the day it opens on — the
  pinch zoom and the scroll, nothing else. **It fetches nothing**, and it leaves the selected tab
  where you put it, that being a deliberate choice rather than part of the view.
- Its icon is four corner brackets rather than a circular arrow: in a weather app a circular arrow
  reads as "fetch the forecast again", which is precisely what this does not do.

---

## 白い熊 天気 6.2.1+037 — 2026-08-15

Built on upstream **v6.2.1**. Readings no longer run off the top of the chart, and both trend cards
pinch.

- **A reading can no longer be cut off at the top.** Two things were doing it. An hour hotter than
  the opening window's range was clamped to the very top of the drawable rather than to the top of
  the plotting area, leaving its numerals no room at all and slicing them against the edge; the
  clamp now stops at the margin that is reserved for exactly that reading. And the fallback that
  tucks a reading under the top edge measured the font's declared ascent, which a face whose digits
  overshoot their own metrics would exceed — it now measures the glyphs actually being drawn.
- **The degree readings are a quarter smaller** on both cards, which also buys back the headroom.
- **Both trend cards pinch-zoom.** Spreading two fingers widens the columns and fits fewer hours or
  days on the screen; pinching them together fits more. The columns are re-measured rather than the
  canvas scaled, so the labels, icons and readings keep their own size instead of blowing up with
  the chart. The hourly card refits its temperature scale to whatever the zoom now shows.
- The zoom is **remembered**, separately for the hourly and the daily card — a zoom held only by the
  chart would be lost the moment the card scrolled off the screen and came back.

---

## 白い熊 天気 6.2.1+036 — 2026-08-15

Built on upstream **v6.2.1**. The trend charts gain a past, and the hourly and daily cards stop
having to agree about their sources.

### Scroll back into what already happened

- **A month of history is kept**, where before a refresh carried forward only what was stored back
  to yesterday 00:00. Both trend cards can now be scrolled left through what the weather actually
  did. This accumulates from this build onwards — it cannot recover days that were never fetched.
- **The hourly card opens three hours back and scrolls both ways.** It plots the whole stored
  series rather than a clipped window; the configured hours of history now decide only where the
  card *lands*. The daily card already opened on today and simply has more behind it now.
- **Every hourly tab is a meteogram now.** Precipitation, wind, humidity, pressure, UV, cloud
  cover, visibility, feels-like and air quality were all still drawing upstream's "now to +24 h"
  with no history at all, while only temperature had been rebuilt. They now share one series and
  one column width, so switching tabs keeps your place instead of jumping to another hour.
- **The hourly graph opens on 3 + 20 hours** rather than 3 + 9.

### Hourly and daily pick their own forecast sources

- **"Weather sources" now has an Hourly forecast row and a Daily forecast row**, each with its own
  multi-select and its own drag order. A source can be in one and not the other — which is the
  point, since a source that is excellent hour by hour is not always the one you want deciding the
  week. ČHMÚ is exactly that case: local for three days, national beyond them.
- The **first hourly source stays the location's identity**, as before, so the duplicate-location
  check and the widgets are unaffected. The daily list carries no such obligation and may leave the
  identity source out entirely.
- A location that predates the split draws the same sources on both cards, and every source either
  list asks for is fetched once.

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


# Version 6.2.2 (unreleased)

**Improvements and fixes**
- Main screen - Add precipitation probability to hourly feels like tab
- Main screen - Make the sun dark instead of bright when the user explicitly asked for dark mode

**Sources**
- BMKG - Fix refresh errors
- Met Office (UK) - Fix shifted day sequence in locations with a different timezone than UTC
- NCEI - Fix parsing error

**Technical**
- [Breezy Update Notifier] Fix broadcast when the location list is changed from the app

**Translations**
- Initial translation for Creoles and pidgins (English based) (En Oh)
- Initial translation for Hawaiian (En Oh)
- Initial translation for Kannada (Vinay)
- Initial translation for Urdu (haseebwt)
- Translations updated


# Version 6.2.1 (2026-06-07)

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

**Translations**
- Translations updated


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
