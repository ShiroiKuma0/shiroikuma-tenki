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

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.breezyweather.R
import org.breezyweather.sources.chmi.map.ChmiMapManifest
import java.util.TimeZone

/**
 * shiroikuma fork: the transport under the map — where in time you are, and how to move.
 *
 * The label is derived from the frame's own `dataRef`, which is UTC, and never from the manifest's
 * `startTime`: on the radar that field is written in local time without saying so, and following it
 * would put every caption an hour or two out.
 */
@Composable
fun MeteomapPlaybackBar(
    manifest: ChmiMapManifest?,
    index: Int,
    playing: Boolean,
    looping: Boolean,
    speed: MeteomapSpeed,
    series: List<Pair<Long, Double>>,
    colorAt: (Double) -> Int,
    timeZone: TimeZone,
    accent: Color,
    dim: Color,
    onSeek: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLoop: () -> Unit,
    onSpeed: (MeteomapSpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frames = manifest?.frames.orEmpty()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        MeteomapTimeline(
            frames = frames,
            index = index,
            nowIndex = manifest?.nowIndex ?: 0,
            series = series,
            colorAt = colorAt,
            accent = accent,
            dim = dim,
            timeZone = timeZone,
            onSeek = onSeek
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onTogglePlay, enabled = frames.size > 1) {
                Icon(
                    imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.meteomap_pause else R.string.meteomap_play
                    ),
                    tint = accent
                )
            }
            IconButton(onClick = onToggleLoop) {
                Icon(
                    imageVector = Icons.Outlined.Repeat,
                    contentDescription = stringResource(R.string.meteomap_loop),
                    // Off reads as dimmed rather than as a different glyph — there is only one
                    // sensible icon for looping, and two states to show with it.
                    tint = if (looping) accent else dim.copy(alpha = 0.5f)
                )
            }
            MeteomapSpeed.entries.forEach { option ->
                val selected = option == speed
                Text(
                    text = option.label,
                    color = if (selected) MaterialTheme.colorScheme.surface else accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            color = if (selected) accent else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onSpeed(option) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
