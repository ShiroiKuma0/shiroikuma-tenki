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

package org.breezyweather.common.options

import android.content.Context
import org.breezyweather.R
import org.breezyweather.common.utils.UnitUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

enum class UpdateInterval(
    override val id: String,
    val interval: Duration?,
) : BaseEnum {

    INTERVAL_NEVER("never", null),
    INTERVAL_0_30("0:30", 30.minutes),
    INTERVAL_1_00("1:00", 1.hours),
    INTERVAL_1_30("1:30", 1.5.hours),
    INTERVAL_2_00("2:00", 2.hours),
    INTERVAL_3_00("3:00", 3.hours),
    INTERVAL_6_00("6:00", 6.hours),
    INTERVAL_12_00("12:00", 12.hours),
    INTERVAL_24_00("24:00", 24.hours),
    ;

    companion object {

        /** The most [validity] ever adds on top of the interval. */
        private val VALIDITY_MARGIN_MAXIMUM = 30.minutes

        fun getInstance(
            value: String,
        ) = entries.firstOrNull {
            it.id == value
        } ?: INTERVAL_1_30
    }

    override val valueArrayId = R.array.automatic_refresh_rate_values
    override val nameArrayId = R.array.automatic_refresh_rates

    override fun getName(context: Context) = UnitUtils.getName(context, this)

    /**
     * How long data already fetched is good enough to open the screen on.
     *
     * Deliberately **longer** than [interval], and that margin is the whole point. The background
     * job is scheduled one interval after the last refresh, so with the two equal the data falls out
     * of validity at the very moment the job is due — and every minute the job runs late, which
     * under Doze (and worse under a vendor skin) is most of them, is a minute in which opening the
     * app finds nothing valid and refreshes in the foreground while you wait. The margin covers that
     * lateness, so the screen opens on what the job has already fetched.
     *
     * Half an interval, capped at half an hour: proportionate where the interval is short enough
     * that a fixed margin would double it, bounded where it is long.
     *
     * With background updates off there is no job to wait for and nothing to cover, so that case
     * keeps its plain hour and a half.
     */
    val validity: Duration
        get() = interval?.let { it + (it / 2).coerceAtMost(VALIDITY_MARGIN_MAXIMUM) } ?: 1.5.hours
}
