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

package org.breezyweather.sources.chmi.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * ČHMÚ ships its tables as a comma-separated `header` line plus rows of positional,
 * mixed-type arrays:
 *
 * ```
 * "header": "STATION,ELEMENT,DT,VAL,FLAG,QUALITY"
 * "values": [["0-20000-0-11519","T","2026-08-10T17:00:00Z",33.2,"",5.0], …]
 * ```
 *
 * So a row is read by column *name* rather than by index, which also survives ČHMÚ
 * reordering or adding a column.
 */
@Serializable
data class ChmiDataTable(
    val header: String? = null,
    val values: List<List<JsonElement>>? = null,
) {
    private val columns: Map<String, Int> by lazy {
        header?.split(",")?.withIndex()?.associate { (i, name) -> name.trim() to i } ?: emptyMap()
    }

    fun rows(): List<List<JsonElement>> = values ?: emptyList()

    fun string(row: List<JsonElement>, column: String): String? {
        return cell(row, column)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
    }

    fun double(row: List<JsonElement>, column: String): Double? {
        return cell(row, column)?.jsonPrimitive?.doubleOrNull
    }

    private fun cell(row: List<JsonElement>, column: String): JsonElement? {
        return columns[column]?.let { row.getOrNull(it) }
    }
}
