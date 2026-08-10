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

package org.breezyweather.sources.chmi

import io.reactivex.rxjava3.core.Observable
import org.breezyweather.sources.common.xml.CapAlert
import retrofit2.http.GET

/**
 * ČHMÚ's warning service, https://vystrahy-cr.chmi.cz/
 *
 * This host, rather than the open data portal, because the portal's mirror of the same
 * bulletins is written to a timestamped filename with no index to discover it by.
 */
interface ChmiCapApi {

    /**
     * The whole SIVS bulletin — every warning in force for Czechia — as one CAP 1.2
     * document. It carries an ETag, so a refresh that finds it unchanged costs a 304.
     */
    @GET("data/XOCZ50_OKPR.xml")
    fun getAlerts(): Observable<CapAlert>

    /**
     * The drought bulletin, issued separately from HAMR. Same shape, far smaller, and it
     * describes a standing condition rather than an event, so its warnings can persist for
     * weeks.
     */
    @GET("data/XOCZ70_OKPR.xml")
    fun getDroughtAlerts(): Observable<CapAlert>
}
