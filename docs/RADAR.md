# Radar

## 🇨🇿 Czechia: the Meteomap

For Czech locations 白い熊 天気 has its **own** map, reached from the **Meteomap** card on the weather screen. ČHMÚ publishes both observed radar and its ALADIN forecast fields as open data under CC BY 4.0, and the app animates them over a border it draws itself:

- **Radar** — 5-minute steps, six hours of history, plus ČHMÚ's own one-hour nowcast running past the present.
- **Forecast** — hourly for three days: temperature, rain, snow, cloud cover, wind, humidity and sunshine.
- A time slider, play/pause, a loop toggle and five speeds; pinch to zoom, drag to pan.

The fields arrive in ČHMÚ's colours and are **repainted in ours**. That is exact rather than decorative: ČHMÚ publishes the scale it painted with, ticks and all, so each pixel's colour is turned back into the reading it stands for and coloured again from the app's own palette — temperature uses the very stops the hourly meteogram uses, so a colour on the map and a colour on the chart mean the same degree. Everything that falls out of the sky is blue.

The basemap is Czechia's 206 ORP districts, the same boundaries the app already carries for alert geocoding. No map tiles are fetched, from anyone. Weather beyond the border is drawn too, only dimmed, because a shower crossing from Saxony matters well before it arrives.

## Everywhere else

Outside Czechia there is no radar feature, because there is no free radar API we can use that can compete with the other free websites listed below, and we don't intend to become a paid app, so it's best to just add to your homescreen a bookmark to one of the free websites below, that will be more feature-complete. Alternatively, if you are looking for a paid open source app, consider subscribing to OsmAnd Pro (also mentioned below).

Below is a list of suggested alternatives (you can submit a pull request to add more).


## Open source apps and Websites

Websites below often offer closed source apps as well, but adding a bookmark to your home screen is usually more privacy-conscious.

<table>
<thead>
    <tr>
        <th>Name</th>
        <th>Model</th>
        <th>Forecast length</th>
        <th>Sources</th>
    </tr>
</thead>
<tbody>
    <tr>
        <td><a href="https://osmand.net/">OsmAnd</a><br />(<a href="https://osmand.net/docs/user/plugins/weather/">Weather plugin</a>)</td>
        <td>Paid, subscription</td>
        <td>
            <ul>
                <li>1-hour step</li>
                <li>7 days</li>
            </ul>
        </td>
        <td>
            <ul>
                <li>GFS</li>
                <li>ECMWF</li>
            </ul>
        </td>
    </tr>
    <tr>
        <td><a href="https://www.rainviewer.com/weather-radar-map-live.html">RainViewer</a></td>
        <td>Free</td>
        <td>
            <ul>
                <li>10-min step</li>
                <li>Past 2 hours</li>
            </ul>
        </td>
        <td>
            <a href="https://www.rainviewer.com/sources.html">Full list</a>
        </td>
    </tr>
    <tr>
        <td><a href="https://www.ventusky.com/">Ventusky</a></td>
        <td>Free</td>
        <td>
            <ul>
                <li>1-hour step (3-hour and 6-hour steps after a few days)</li>
                <li>15 days</li>
                <li>Archive back to 1979</li>
            </ul>
        </td>
        <td>
            <ul>
                <li><strong>NOAA</strong>: GFS, HRRR, RTOFS, NBM</li>
                <li><strong>DWD</strong>: ICON, ICON (EU), ICON (DE)</li>
                <li>CMC: GEM</li>
                <li>FMI: SILAM</li>
                <li>ECMWF</li>
                <li>Met Office: UKMO, UKMO (UK)</li>
                <li>Météo France: AROME</li>
                <li>Meteorologisk institutt: MEPS (NO), WAVEWATCH (NO)</li>
            </ul>
        </td>
    </tr>
    <tr>
        <td><a href="https://www.windy.com/">Windy.com</a></td>
        <td>Freemium</td>
        <td>
            <ul>
                <li>Free:
                    <ul>
                        <li>3-hour step</li>
                        <li>6 days</li>
                    </ul>
                </li>
                <li>Paid:
                    <ul>
                        <li>1-hour step</li>
                        <li>10 days</li>
                        <li>1-year archive</li>
                    </ul>
                </li>
            </ul>
        </td>
        <td>
            <ul>
                <li>ECMWF 9 km</li>
                <li>UKV 2 km</li>
                <li>GFS 22 km</li>
                <li>ICON-D2 2.2 km, ICON-EU 7 km, ICON 13 km</li>
                <li>NEMS 4 km</li>
                <li>AROME 1.3 km</li>
            </ul>
        </td>
    </tr>
    <tr>
        <td><a href="https://severeweather.wmo.int/">WMO Severe Weather Information Centre</a></td>
        <td>Free</td>
        <td>N/A</td>
        <td><a href="https://severeweather.wmo.int/sources.html">136 issuing organizations</a></td>
    </tr>
</tbody>
</table>


## Features

| Name                                                                    | Precipitation | Temperature | Wind | Clouds | Pressure | Waves | Air quality | Other                                                                            |
|-------------------------------------------------------------------------|---------------|-------------|------|--------|----------|-------|-------------|----------------------------------------------------------------------------------|
| [OsmAnd](https://osmand.net/)                                           | ✅             | ✅           | ✅    | ✅      | ✅        | ❌     | ❌           |                                                                                  |
| [RainViewer](https://www.rainviewer.com/weather-radar-map-live.html)    | ✅             | ❌           | ❌    | ❌      | ❌        | ❌     | ❌           |                                                                                  |
| [Ventusky](https://www.ventusky.com/)                                   | ✅             | ✅           | ✅    | ✅      | ✅        | ✅     | ✅           |                                                                                  |
| [Windy.com](https://www.windy.com/)                                     | ✅             | ✅           | ✅    | ✅      | ✅        | ✅     | ✅           | Extreme forecast, Weather warnings, Outdoor map, Drought monitoring, Fire danger |
| [WMO Severe Weather Information Centre](https://severeweather.wmo.int/) | ❌             | ❌           | ❌    | ❌      | ❌        | ❌     | ❌           | Severe Weather                                                                   |
