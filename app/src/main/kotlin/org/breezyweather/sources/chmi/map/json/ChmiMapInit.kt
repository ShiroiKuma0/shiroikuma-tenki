/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.sources.chmi.map.json

import kotlinx.serialization.Serializable

/**
 * One map product's manifest, from `data-provider.chmi.cz/api/map/init/<topic>`.
 *
 * It names every frame that exists, the ground the frames cover, and the scale they were painted
 * with — which is what lets the readings be recovered and repainted in our own colours.
 */
@Serializable
data class ChmiMapInit(
    val mapSetup: ChmiMapSetup? = null,
    val timelines: List<ChmiMapTimeline>? = null,
    val boundingBoxes: List<ChmiMapBoundingBox>? = null,
    val legends: List<ChmiMapLegend>? = null,
    val layers: List<ChmiMapLayer>? = null,
)

@Serializable
data class ChmiMapSetup(
    /** Seconds. 180 on the radar; absent on the forecast products, which change hourly. */
    val refreshInterval: Int? = null,
)

@Serializable
data class ChmiMapTimeline(
    val id: String? = null,
    /**
     * ⚠ Not to be trusted for the clock: on the radar manifest this has **no `Z` and is local
     * time**, while the forecast manifests carry `Z`. Every frame's own `dataRef` is UTC, so the
     * times shown to the reader are derived from those instead.
     */
    val startTime: String? = null,
    /** Which frame the site opens on — for the radar, the last observed one before the nowcast. */
    val defaultStep: Int? = null,
    val steps: List<ChmiMapStep>? = null,
    /** Where the observed frames end and the forecast begins. */
    val predictionStart: String? = null,
)

@Serializable
data class ChmiMapStep(
    val intervalValue: Int? = null,
    /** `m`, `h` or `d`. */
    val intervalUnit: String? = null,
    val count: Int? = null,
)

/** The ground a layer's frames cover, in degrees. The frames themselves are EPSG:3857. */
@Serializable
data class ChmiMapBoundingBox(
    val id: String? = null,
    val xmin: Double? = null,
    val ymin: Double? = null,
    val xmax: Double? = null,
    val ymax: Double? = null,
)

@Serializable
data class ChmiMapLayer(
    val id: String? = null,
    /** `image` for the frame sequences; the rest are basemaps and vector overlays we do not use. */
    val type: String? = null,
    val dataRefBase: String? = null,
    val legend: String? = null,
    val boundingBox: String? = null,
    val opacity: Double? = null,
    val dataParts: List<ChmiMapDataPart>? = null,
)

/** One frame. [dataRef] is `yyyyMMddHHmm` in **UTC**, and is also the frame's URL suffix. */
@Serializable
data class ChmiMapDataPart(
    val dataRef: String? = null,
)

/**
 * The scale a product was painted with.
 *
 * Two shapes: `scale` legends list colours with the occasional printed tick label ("40 °C"), and
 * the radar's `composite` legend nests a series of dBZ bands instead.
 */
@Serializable
data class ChmiMapLegend(
    val id: String? = null,
    val type: String? = null,
    val label: String? = null,
    val items: List<ChmiMapLegendItem>? = null,
)

@Serializable
data class ChmiMapLegendItem(
    val id: String? = null,
    /** A printed tick, e.g. `40 °C` or `10 mm/h`. Empty on the entries that only carry a colour. */
    val label: String? = null,
    /** `#RRGGBBAA`. Empty on the entries that only carry a tick. */
    val color: String? = null,
    val series: List<ChmiMapLegendSeries>? = null,
)

@Serializable
data class ChmiMapLegendSeries(
    val name: String? = null,
    val entries: List<ChmiMapLegendEntry>? = null,
)

/** A band of the radar scale: `barva` is Czech for colour, and the bounds are in dBZ. */
@Serializable
data class ChmiMapLegendEntry(
    val min: Double? = null,
    val max: Double? = null,
    val barva: String? = null,
    val desc: String? = null,
)

/** One frame's payload: a PNG as a `data:` URI. */
@Serializable
data class ChmiMapFrame(
    val img: String? = null,
)
