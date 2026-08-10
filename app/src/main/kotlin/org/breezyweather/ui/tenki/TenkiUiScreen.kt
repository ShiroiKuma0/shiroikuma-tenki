/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the 白い熊 天気 UI page.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.ui.tenki

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.breezyweather.R
import org.breezyweather.tenki.ColorSlot
import org.breezyweather.tenki.LocalTenkiUi
import org.breezyweather.tenki.TenkiFonts
import org.breezyweather.tenki.TenkiUiState
import org.breezyweather.tenki.automation.TenkiAutomationAuth
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar

/**
 * The 白い熊 天気 UI page — every knob that shapes the app's look, in the kxkb page grammar:
 *
 *  * a top-level group opens with a full-width hairline, then a big bold heading carrying a
 *    **word-width** underline;
 *  * a sub-group repeats that one indent level in, smaller;
 *  * rows sit two levels in (three under a sub-group), with tight vertical padding — the only
 *    generous spacing in the page is between top-level groups, so grouping reads instantly;
 *  * every control previews itself, and since the page is themed by the very values it edits,
 *    the whole app is the preview.
 */
@Composable
fun TenkiUiScreen(onNavigateBack: () -> Unit) {
    val ui = LocalTenkiUi.current
    var showExportImport by remember { mutableStateOf(false) }

    Material3Scaffold(
        topBar = {
            FitStatusBarTopAppBar(
                title = "白い熊 天気 UI",
                onBackPressed = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(ui.background))
        ) {
            // ---------------------------------------------------------- 保存復元
            SectionHeader(ui, "Export / Import", first = true)
            NavigationRow(
                ui = ui,
                title = "Export / Import settings",
                summary = if (ui.exportDir.isBlank()) {
                    "No backup directory set"
                } else {
                    "Backup directory is set"
                },
                summaryIsWarning = ui.exportDir.isBlank(),
                onClick = { showExportImport = true }
            )
            // The 保存復元 automation rows belong here, under the export rows — a backup feature
            // lives where backup lives, and every sister app looks the same.
            AutomationRows(ui)

            // -------------------------------------------------------------- theme
            SectionHeader(ui, "Theme")
            ToggleRow(ui, "Use the 白い熊 天気 UI", ui.enabled) { ui.updateEnabled(it) }
            RowNote(
                ui,
                "Off hands the app back to upstream's Material theme (system colours, light/dark " +
                    "and dynamic colour). Everything below still applies to this page."
            )

            // ------------------------------------------------------------ colours
            SectionHeader(ui, "Colours")
            ColorRow(ui, "Background", ColorSlot.BACKGROUND)
            ColorRow(ui, "Surface (cards, sheets, dialogs)", ColorSlot.SURFACE)
            ColorRow(ui, "Text", ColorSlot.TEXT)
            ColorRow(ui, "Secondary text", ColorSlot.TEXT_DIM)
            ColorRow(ui, "Accent", ColorSlot.ACCENT)
            ColorRow(ui, "Border", ColorSlot.BORDER)
            ColorRow(ui, "Divider", ColorSlot.DIVIDER)
            ColorRow(ui, "Icons", ColorSlot.ICON)
            ColorRow(ui, "Warnings", ColorSlot.WARN)

            // --------------------------------------------------------- typography
            SectionHeader(ui, "Typography")
            FontRow(ui)
            SubHeader(ui, "Body")
            SliderRow(ui, "Size", ui.fontSize, 8..32, "sp", level2 = true) { ui.updateFontSize(it) }
            SliderRow(ui, "Weight", ui.fontWeight, 100..900, step = 100, level2 = true) {
                ui.updateFontWeight(it)
            }
            ToggleRow(ui, "Italic", ui.fontItalic, level2 = true) { ui.updateFontItalic(it) }
            SubHeader(ui, "Headings")
            SliderRow(ui, "Size", ui.headingSize, 12..40, "sp", level2 = true) {
                ui.updateHeadingSize(it)
            }
            SliderRow(ui, "Weight", ui.headingWeight, 100..900, step = 100, level2 = true) {
                ui.updateHeadingWeight(it)
            }
            SubHeader(ui, "Secondary lines")
            SliderRow(ui, "Size", ui.labelSize, 8..24, "sp", level2 = true) { ui.updateLabelSize(it) }
            SubHeader(ui, "Spacing")
            SliderRow(ui, "Letter spacing", ui.letterSpacing, 0..20, "/100 em", level2 = true) {
                ui.updateLetterSpacing(it)
            }
            SliderRow(ui, "Line height", ui.lineHeightPct, 100..200, "%", level2 = true) {
                ui.updateLineHeightPct(it)
            }
            TypePreview(ui)

            // ----------------------------------------------------- shape & border
            SectionHeader(ui, "Shape and borders")
            SliderRow(ui, "Corner roundness", ui.cornerRadius, 0..40, "dp") {
                ui.updateCornerRadius(it)
            }
            SliderRow(ui, "Border thickness", ui.borderWidth, 0..8, "dp") {
                ui.updateBorderWidth(it)
            }
            SliderRow(ui, "Divider thickness", ui.dividerWidth, 0..8, "dp") {
                ui.updateDividerWidth(it)
            }
            ShapePreview(ui)

            // -------------------------------------------------------------- icons
            SectionHeader(ui, "Icons")
            SliderRow(ui, "Size", ui.iconSize, 12..64, "dp") { ui.updateIconSize(it) }
            SliderRow(ui, "Roundness", ui.iconRoundness, 0..100, "%") { ui.updateIconRoundness(it) }
            IconPreview(ui)

            // ------------------------------------------------------------- header
            SectionHeader(ui, "Header")
            SliderRow(ui, "Weather scene brightness", ui.headerIntensity, 0..100, "%") {
                ui.updateHeaderIntensity(it)
            }
            RowNote(
                ui,
                "Upstream draws a live scene behind the temperature — clouds, rain, a meteor " +
                    "shower. It is kept and re-inked: brightness picks a point between the " +
                    "Background and Accent colours, so the clouds read yellow instead of white. " +
                    "0 turns the animation off and leaves a flat ground; high values make the " +
                    "clouds compete with the text on top of them."
            )

            // ------------------------------------------------------------ density
            SectionHeader(ui, "Density")
            SliderRow(ui, "Row padding", ui.rowPadding, 0..24, "dp") { ui.updateRowPadding(it) }
            SliderRow(ui, "Space between groups", ui.groupSpacing, 0..40, "dp") {
                ui.updateGroupSpacing(it)
            }
            SliderRow(ui, "Indent per level", ui.indentStep, 0..48, "dp") { ui.updateIndentStep(it) }

            // -------------------------------------------------------------- reset
            SectionHeader(ui, "Reset")
            NavigationRow(
                ui = ui,
                title = "Restore the black-yellow defaults",
                summary = "Every colour, font and size back to how 白い熊 天気 ships",
                onClick = { ui.resetToDefaults() }
            )
            Spacer(Modifier.height((ui.groupSpacing * 3).dp))
        }
    }

    if (showExportImport) {
        TenkiExportImportPanel(
            onDismiss = { showExportImport = false },
            onFinishedAndClose = {
                showExportImport = false
                onNavigateBack()
            }
        )
    }
}

/**
 * The two contract rows: a master switch (default OFF — nothing is reachable from outside until
 * 白い熊 turns it on) and the token, abbreviated, copied on tap, regenerated on the right.
 */
@Composable
private fun AutomationRows(ui: TenkiUiState) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TenkiAutomationAuth.enabled(context)) }
    var token by remember { mutableStateOf(TenkiAutomationAuth.token(context)) }

    ToggleRow(ui, "Automation export", enabled) {
        TenkiAutomationAuth.setEnabled(context, it)
        enabled = it
    }
    RowNote(
        ui,
        "Lets 白い熊 自由作業盤 trigger this app's export through the token-gated intent."
    )
    Row(
        modifier = pressableRow(ui, false) {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("token", token))
            Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            RowTitle(ui, "Automation token", TenkiAutomationAuth.abbreviated(token))
        }
        Text(
            text = "Regenerate",
            color = Color(ui.warnColor),
            fontSize = ui.labelSize.sp,
            modifier = Modifier.clickable {
                token = TenkiAutomationAuth.regenerate(context)
                Toast.makeText(
                    context,
                    "New token — update every copy you pasted elsewhere",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

// ---------------------------------------------------------------------- headings

/** Top-level group heading: full-width hairline above, big bold title, word-width underline. */
@Composable
private fun SectionHeader(ui: TenkiUiState, title: String, first: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) {
            Spacer(Modifier.height(ui.groupSpacing.dp))
            if (ui.dividerWidth > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ui.dividerWidth.dp)
                        .background(Color(ui.dividerColor))
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = BASE_INDENT.dp, top = 8.dp, bottom = 2.dp)
                .width(IntrinsicSize.Min)
        ) {
            Text(
                text = title,
                color = Color(ui.accentColor),
                fontSize = ui.headingSize.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(2.5.dp)
                    .background(Color(ui.accentColor))
            )
        }
    }
}

/** One level under a section: same shape, smaller, no full-width rule. */
@Composable
private fun SubHeader(ui: TenkiUiState, title: String) {
    Column(
        modifier = Modifier
            .padding(start = (BASE_INDENT + ui.indentStep).dp, top = 6.dp, bottom = 2.dp)
            .width(IntrinsicSize.Min)
    ) {
        Text(
            text = title,
            color = Color(ui.accentColor),
            fontSize = (ui.headingSize - 3).coerceAtLeast(10).sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(1.5.dp)
                .background(Color(ui.accentColor))
        )
    }
}

// -------------------------------------------------------------------------- rows

private const val BASE_INDENT = 12

private fun rowIndent(ui: TenkiUiState, level2: Boolean) =
    (BASE_INDENT + ui.indentStep * (if (level2) 3 else 2)).dp

/**
 * Every tappable row sits in a bordered box. On a black ground with yellow text, an unboxed row
 * looks exactly like a label — the box is the only thing that says "this can be pressed". The
 * indent stays OUTSIDE the box, so the nesting still reads at a glance.
 */
@Composable
private fun pressableRow(ui: TenkiUiState, level2: Boolean, onClick: () -> Unit): Modifier {
    val shape = RoundedCornerShape(ui.cornerRadius.dp)
    return Modifier
        .fillMaxWidth()
        .padding(
            start = rowIndent(ui, level2),
            end = 16.dp,
            top = (ui.rowPadding / 2).dp,
            bottom = (ui.rowPadding / 2).dp
        )
        .clip(shape)
        .then(
            if (ui.borderWidth > 0) {
                Modifier.border(ui.borderWidth.dp, Color(ui.borderColor), shape)
            } else {
                Modifier
            }
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = (ui.rowPadding + 2).dp)
}

@Composable
private fun RowTitle(ui: TenkiUiState, title: String, summary: String?, warn: Boolean = false) {
    Text(text = title, color = Color(ui.textColor), fontSize = ui.fontSize.sp)
    if (summary != null) {
        Text(
            text = summary,
            color = if (warn) Color(ui.warnColor) else Color(ui.textDimColor),
            fontSize = ui.labelSize.sp
        )
    }
}

/** An explanatory line under a row, at the row's own indent. */
@Composable
private fun RowNote(ui: TenkiUiState, text: String, level2: Boolean = false) {
    Text(
        text = text,
        color = Color(ui.textDimColor),
        fontSize = ui.labelSize.sp,
        modifier = Modifier.padding(
            start = rowIndent(ui, level2),
            end = 16.dp,
            bottom = ui.rowPadding.dp
        )
    )
}

@Composable
private fun NavigationRow(
    ui: TenkiUiState,
    title: String,
    summary: String? = null,
    summaryIsWarning: Boolean = false,
    level2: Boolean = false,
    onClick: () -> Unit,
) {
    Column(modifier = pressableRow(ui, level2, onClick)) {
        RowTitle(ui, title, summary, summaryIsWarning)
    }
}

@Composable
private fun ToggleRow(
    ui: TenkiUiState,
    title: String,
    checked: Boolean,
    level2: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = pressableRow(ui, level2) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) { RowTitle(ui, title, null) }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(ui.background),
                checkedTrackColor = Color(ui.accentColor),
                uncheckedThumbColor = Color(ui.textDimColor),
                uncheckedTrackColor = Color(ui.background),
                uncheckedBorderColor = Color(ui.borderColor)
            )
        )
    }
}

/** A slider row: title, the live value, and a track that reaches 0 wherever 0 means "none". */
@Composable
private fun SliderRow(
    ui: TenkiUiState,
    title: String,
    value: Int,
    range: IntRange,
    unit: String = "",
    step: Int = 1,
    level2: Boolean = false,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = rowIndent(ui, level2),
                end = 16.dp,
                top = ui.rowPadding.dp,
                bottom = ui.rowPadding.dp
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value$unit",
                color = Color(ui.accentColor),
                fontSize = ui.labelSize.sp
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (step > 1) ((range.last - range.first) / step) - 1 else 0,
            colors = SliderDefaults.colors(
                thumbColor = Color(ui.accentColor),
                activeTrackColor = Color(ui.accentColor),
                inactiveTrackColor = Color(ui.textDimColor).copy(alpha = 0.4f)
            )
        )
    }
}

/** A colour row: the swatch is the preview, tapping it opens the RGBA picker. */
@Composable
private fun ColorRow(ui: TenkiUiState, title: String, slot: ColorSlot, level2: Boolean = false) {
    var showPicker by remember { mutableStateOf(false) }
    val color = ui.colorOf(slot)

    Row(
        modifier = pressableRow(ui, level2) { showPicker = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            RowTitle(ui, title, "#%08X".format(color))
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(ui.cornerRadius.dp))
                .background(Color(color))
                .border(
                    width = (if (ui.borderWidth > 0) ui.borderWidth else 1).dp,
                    color = Color(ui.borderColor),
                    shape = RoundedCornerShape(ui.cornerRadius.dp)
                )
        )
    }

    if (showPicker) {
        TenkiColorPickerDialog(
            title = title,
            initial = color,
            onDismiss = { showPicker = false },
            onPicked = {
                ui.updateColor(slot, it)
                showPicker = false
            }
        )
    }
}

/** The font row: shows the current family rendered in its own glyphs. */
@Composable
private fun FontRow(ui: TenkiUiState) {
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = pressableRow(ui, false) { showPicker = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "Font", color = Color(ui.textColor), fontSize = ui.fontSize.sp)
            Text(
                text = TenkiFonts.displayName(context, ui.fontFamilyId),
                color = Color(ui.textDimColor),
                fontSize = ui.labelSize.sp,
                fontFamily = TenkiFonts.family(context, ui.fontFamilyId)
            )
        }
    }

    if (showPicker) {
        TenkiFontPickerDialog(onDismiss = { showPicker = false })
    }
}

// ---------------------------------------------------------------------- previews

@Composable
private fun PreviewFrame(ui: TenkiUiState, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = rowIndent(ui, false), end = 16.dp, top = 4.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(ui.cornerRadius.dp))
            .background(Color(ui.surface))
            .then(
                if (ui.borderWidth > 0) {
                    Modifier.border(
                        ui.borderWidth.dp,
                        Color(ui.borderColor),
                        RoundedCornerShape(ui.cornerRadius.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(10.dp)
    ) { content() }
}

@Composable
private fun TypePreview(ui: TenkiUiState) {
    val context = LocalContext.current
    val family = TenkiFonts.family(context, ui.fontFamilyId)
    PreviewFrame(ui) {
        Column {
            Text(
                text = "白い熊 天気 — Heading",
                color = Color(ui.accentColor),
                fontSize = ui.headingSize.sp,
                fontWeight = FontWeight(ui.headingWeight.coerceIn(100, 900)),
                fontFamily = family
            )
            Text(
                text = "Body text — 見本 AaBbCc 0123 · 18°C",
                color = Color(ui.textColor),
                fontSize = ui.fontSize.sp,
                fontWeight = FontWeight(ui.fontWeight.coerceIn(100, 900)),
                fontStyle = if (ui.fontItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = family
            )
            Text(
                text = "Secondary line — 補足",
                color = Color(ui.textDimColor),
                fontSize = ui.labelSize.sp,
                fontFamily = family
            )
        }
    }
}

@Composable
private fun ShapePreview(ui: TenkiUiState) {
    PreviewFrame(ui) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(56.dp, 32.dp)
                    .clip(RoundedCornerShape(ui.cornerRadius.dp))
                    .background(Color(ui.background))
                    .border(
                        ui.borderWidth.dp,
                        Color(ui.borderColor),
                        RoundedCornerShape(ui.cornerRadius.dp)
                    )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Corner ${ui.cornerRadius}dp · border ${ui.borderWidth}dp",
                    color = Color(ui.textColor),
                    fontSize = ui.labelSize.sp
                )
                Spacer(Modifier.height(4.dp))
                if (ui.dividerWidth > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ui.dividerWidth.dp)
                            .background(Color(ui.dividerColor))
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPreview(ui: TenkiUiState) {
    val shape = RoundedCornerShape(percent = (ui.iconRoundness / 2).coerceIn(0, 50))
    PreviewFrame(ui) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(ui.iconSize.dp)
                    .clip(shape)
                    .background(Color(ui.accentColor).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    tint = Color(ui.iconColor),
                    modifier = Modifier.size((ui.iconSize * 0.75f).dp)
                )
            }
            Text(
                text = "${ui.iconSize}dp · ${ui.iconRoundness}% round",
                color = Color(ui.textColor),
                fontSize = ui.labelSize.sp
            )
        }
    }
}
