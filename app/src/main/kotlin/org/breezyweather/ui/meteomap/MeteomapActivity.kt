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

package org.breezyweather.ui.meteomap

import android.os.Bundle
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme

/**
 * shiroikuma fork: the Meteomap — ČHMÚ's radar and forecast maps for Czechia.
 *
 * Wrapped in [BreezyWeatherTheme] like every other screen: it is the single point where the house
 * palette is provided, and `LocalTenkiUi` does not exist outside it.
 */
@AndroidEntryPoint
class MeteomapActivity : BreezyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreezyWeatherTheme {
                MeteomapScreen(onBackPressed = { finish() })
            }
        }
    }

    companion object {
        const val KEY_FORMATTED_ID = "formatted_id"
    }
}
