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

/**
 * The envelope every file on ČHMÚ's open data portal is wrapped in.
 *
 * The metadata around it (zaznamID, datovyZdrojID, datovyTokID, datumVytvoreni, verzeDat)
 * is not needed here.
 */
@Serializable
data class ChmiDataResult(
    val data: ChmiDataCollection? = null,
)
