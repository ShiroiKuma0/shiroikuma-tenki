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

package org.breezyweather.ui.common.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.breezyweather.R
import org.breezyweather.ui.tenki.TextButton
import org.breezyweather.ui.theme.compose.themeRipple

/**
 * shiroikuma fork: pick SEVERAL sources for one feature and arrange the order they are drawn in.
 *
 * The single-source counterpart is [SourceViewWithContinents], which this deliberately mirrors —
 * same grouped list, same Triple(id, name, isAvailable) shape — so the two rows read as one family
 * in the "Weather sources" dialog. The difference is the ordered section on top: the list is the
 * order the charts get stacked in on the main view, first one being the location's main source.
 *
 * @param sourceList key is a @StringRes group heading, exactly as [getCompatibleSources] returns it
 * @param onValuesChanged never called with an empty list — a feature always keeps one source
 */
@Composable
fun MultiSourceViewWithContinents(
    title: String,
    selectedKeys: ImmutableList<String>,
    sourceList: ImmutableMap<Int, ImmutableList<Triple<String, String, Boolean>>>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
    onValuesChanged: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val dialogOpenState = remember { mutableStateOf(false) }

    // Every source we know a name for, so a selected-but-no-longer-compatible one still reads
    // as its name rather than its bare id.
    val namesById = sourceList.values.flatten().associate { it.first to it.second }
    val nameOf: (String) -> String = { id ->
        namesById[id] ?: context.getString(R.string.settings_weather_source_unavailable, id)
    }

    ListItem(
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = themeRipple(),
                onClick = { dialogOpenState.value = true },
                enabled = enabled
            )
            .padding(vertical = 8.dp),
        headlineContent = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Column {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                Text(
                    // The order matters, so the summary reads in it rather than alphabetically
                    text = selectedKeys.joinToString(context.getString(R.string.dot_separator)) { nameOf(it) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )

    if (dialogOpenState.value) {
        MultiSourceDialog(
            title = title,
            selectedKeys = selectedKeys,
            sourceList = sourceList,
            nameOf = nameOf,
            onClose = { dialogOpenState.value = false },
            onValuesChanged = onValuesChanged
        )
    }
}

@Composable
private fun MultiSourceDialog(
    title: String,
    selectedKeys: ImmutableList<String>,
    sourceList: ImmutableMap<Int, ImmutableList<Triple<String, String, Boolean>>>,
    nameOf: (String) -> String,
    onClose: () -> Unit,
    onValuesChanged: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    // A state list, so a drag or a checkbox redraws the dialog. Edited live: the caller sees each
    // change as it happens, like the single-source rows do.
    val selected = remember(selectedKeys) { selectedKeys.toMutableStateList() }

    AlertDialogNoPadding(
        onDismissRequest = onClose,
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GroupHeading(text = stringResource(R.string.settings_weather_sources_selected))
                ReorderableSourceList(
                    // The first one is the location's main source: it feeds the header, the
                    // widgets and the notification, so it is worth naming as such.
                    items = selected.mapIndexed { index, id ->
                        id to if (index == 0) {
                            context.getString(R.string.settings_weather_sources_primary, nameOf(id))
                        } else {
                            nameOf(id)
                        }
                    },
                    canRemove = selected.size > 1,
                    onMove = { from, to ->
                        selected.add(to, selected.removeAt(from))
                        onValuesChanged(selected.toList())
                    },
                    onRemove = { id ->
                        selected.remove(id)
                        onValuesChanged(selected.toList())
                    }
                )

                GroupHeading(text = stringResource(R.string.settings_weather_sources_available))
                sourceList.forEach { (groupNameRes, sources) ->
                    GroupHeading(text = stringResource(groupNameRes))
                    sources.forEach { (id, name, isAvailable) ->
                        val isSelected = id in selected
                        SourceCheckbox(
                            text = name,
                            checked = isSelected,
                            // The last one standing cannot be unchecked, and an unavailable
                            // source cannot be added — but one already selected can be removed.
                            enabled = if (isSelected) selected.size > 1 else isAvailable,
                            onCheckedChange = {
                                if (isSelected) {
                                    selected.remove(id)
                                } else {
                                    selected.add(id)
                                }
                                onValuesChanged(selected.toList())
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(
                    text = stringResource(R.string.action_close),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Composable
private fun GroupHeading(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.small_margin))
    ) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.large_margin)))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SourceCheckbox(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = themeRipple(),
                enabled = enabled,
                onClick = onCheckedChange
            )
            .padding(horizontal = dimensionResource(R.dimen.small_margin)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { onCheckedChange() }
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.small_margin)))
        Text(
            text = text,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private val ReorderRowHeight = 56.dp

/**
 * A short drag-to-reorder column.
 *
 * Deliberately a plain [Column] and not a lazy list: the selection is a handful of sources, and a
 * fixed row height is what lets the drag maths stay this simple — offset over row height is how
 * many places the finger has travelled.
 */
@Composable
private fun ReorderableSourceList(
    items: List<Pair<String, String>>,
    canRemove: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    val rowHeightPx = with(LocalDensity.current) { ReorderRowHeight.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // The gesture handler is keyed on the source id, not the index, so that reordering mid-drag
    // does not restart it. It reads the live list to find where the dragged row sits now.
    val currentItems by rememberUpdatedState(items)

    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { (id, name) ->
            val isDragged = draggingId == id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReorderRowHeight)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .background(
                        if (isDragged) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = dimensionResource(R.dimen.small_margin)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .semantics {
                            contentDescription =
                                context.getString(R.string.settings_weather_sources_reorder, name)
                        }
                        .pointerInput(id) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingId = id
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val from = currentItems.indexOfFirst { it.first == id }
                                    if (from < 0) return@detectDragGestures
                                    val to = from + (dragOffset / rowHeightPx).toInt()
                                    if (to != from && to in currentItems.indices) {
                                        // Keep the finger over the row it grabbed: the row has
                                        // moved by (to - from) places, so the offset owes that back
                                        dragOffset -= (to - from) * rowHeightPx
                                        onMove(from, to)
                                    }
                                }
                            )
                        }
                )
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.normal_margin)))
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (canRemove) {
                    IconButton(onClick = { onRemove(id) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
