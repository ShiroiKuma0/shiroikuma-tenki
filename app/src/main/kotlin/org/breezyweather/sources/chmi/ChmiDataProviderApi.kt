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
import org.breezyweather.sources.chmi.json.ChmiMeteogramResult
import org.breezyweather.sources.chmi.json.ChmiOutlookResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The API behind ČHMÚ's own web pages, https://data-provider.chmi.cz/
 *
 * `www.chmi.cz` is a Liferay portal whose weather pages render empty and fill themselves from
 * here, so this — not the HTML — is where the numbers are. It needs no key, token or cookie,
 * and the same API serves ČHMÚ's own phone app.
 *
 * It is not a documented contract and carries no version in its path, so both calls are
 * treated as best-effort: a failure leaves the other one's data standing rather than failing
 * the whole forecast.
 */
interface ChmiDataProviderApi {

    /**
     * The ALADIN meteogram: 73 hourly steps from 00:00 UTC of the current day, so the horizon
     * ahead shrinks as the day goes on and the early rows are already in the past.
     *
     * [longitude] and [latitude] are answered with the nearest grid point, anywhere in the
     * ALADIN domain — Czechia plus a thin margin. Outside it the reply is an error object
     * rather than an HTTP failure, which deserializes to an empty [ChmiMeteogramResult].
     *
     * Note the axis order: `x` is the longitude and `y` the latitude.
     */
    @GET("api/graphs/graf.meteogram")
    fun getMeteogram(
        @Query("x") longitude: Double,
        @Query("y") latitude: Double,
    ): Observable<ChmiMeteogramResult>

    /**
     * The nine-day outlook behind https://www.chmi.cz/predpoved-pocasi/tyden — two rows a day,
     * the earlier carrying the night's minimum and the later that afternoon's maximum.
     *
     * It is a **national** forecast: it accepts `x`/`y` but ignores them, and returns identical
     * values for Prague and for the summit of Sněžka. Only used past the meteogram's horizon.
     */
    @GET("api/graphs/graf.forecast")
    fun getOutlook(): Observable<ChmiOutlookResult>
}
