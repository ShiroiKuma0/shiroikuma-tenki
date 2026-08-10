/*
 * 白い熊 天気 (shiroikuma-tenki) fork: drop-in replacements for the Material 3 dialogs and buttons
 * that carry the house border by default.
 *
 * A file opts in by changing one import — `androidx.compose.material3.AlertDialog` becomes
 * `org.breezyweather.ui.tenki.AlertDialog` — so call sites stay untouched and a screen upstream
 * adds later is styled the moment its imports are switched.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.ui.tenki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.breezyweather.tenki.LocalTenkiUi
import androidx.compose.material3.AlertDialog as Material3AlertDialog
import androidx.compose.material3.Button as Material3Button
import androidx.compose.material3.FilledTonalButton as Material3FilledTonalButton
import androidx.compose.material3.OutlinedButton as Material3OutlinedButton
import androidx.compose.material3.TextButton as Material3TextButton

/**
 * The house outline. Draws nothing when the border width is 0 — the knob's own "draw nothing" — and
 * nothing at all when the 白い熊 天気 UI is switched off, so upstream's look comes back whole.
 */
@Composable
fun Modifier.tenkiOutline(shape: Shape, color: Color? = null): Modifier {
    val ui = LocalTenkiUi.current
    if (!ui.enabled || ui.borderWidth <= 0) return this
    return border(ui.borderWidth.dp, color ?: Color(ui.borderColor), shape)
}

/** The border stroke for widgets that take one directly (cards, buttons). */
@Composable
fun tenkiBorderStroke(color: Color? = null): BorderStroke? {
    val ui = LocalTenkiUi.current
    if (!ui.enabled || ui.borderWidth <= 0) return null
    return BorderStroke(ui.borderWidth.dp, color ?: Color(ui.borderColor))
}

/** Fully round pills for every button, so a tappable thing never reads as plain text. */
@Composable
private fun pillShape(): Shape = RoundedCornerShape(50)

// ----------------------------------------------------------------------------- dialog

/**
 * `AlertDialog` with the house border. Same parameter list as the Material 3 one, so switching the
 * import is the whole change.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    Material3AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.tenkiOutline(shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

// ---------------------------------------------------------------------------- buttons

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = pillShape(),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = tenkiBorderStroke(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Material3TextButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = pillShape(),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = tenkiBorderStroke(MaterialTheme.colorScheme.outline),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Material3Button(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = pillShape(),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = tenkiBorderStroke() ?: ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Material3OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = pillShape(),
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = tenkiBorderStroke(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Material3FilledTonalButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)
