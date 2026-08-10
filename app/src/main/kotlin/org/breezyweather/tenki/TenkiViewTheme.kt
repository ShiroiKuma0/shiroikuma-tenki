/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the same knobs, applied to the View world.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.widget.ImageViewCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/**
 * A good part of this app is still XML views — the locations list, the snackbar, the weather cards —
 * and `BreezyWeatherTheme` cannot reach any of it. These helpers paint those views from the very
 * same [TenkiUiState] the Compose side reads, so one slider moves both worlds.
 *
 * Every helper is a no-op when the 白い熊 天気 UI is switched off, leaving upstream's look intact.
 */
object TenkiViewTheme {

    fun state(context: Context): TenkiUiState = TenkiUiState.getInstance(context)

    fun isEnabled(context: Context): Boolean = state(context).enabled

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * The locations list row: black ground, yellow title, dimmed-yellow body, yellow icons, and the
     * house outline on the card — which is also the affordance that says the row can be pressed.
     */
    fun paintLocationCard(
        context: Context,
        card: MaterialCardView?,
        item: View?,
        titles: List<TextView?> = emptyList(),
        bodies: List<TextView?> = emptyList(),
        icons: List<ImageView?> = emptyList(),
    ) {
        val ui = state(context)
        if (!ui.enabled) return

        item?.setBackgroundColor(ui.surface)
        card?.apply {
            setCardBackgroundColor(ui.surface)
            strokeColor = ui.borderColor
            strokeWidth = dp(context, ui.borderWidth)
            radius = dp(context, ui.cornerRadius).toFloat()
        }
        titles.forEach { it?.setTextColor(ui.textColor) }
        bodies.forEach { it?.setTextColor(ui.textDimColor) }
        icons.forEach { icon ->
            icon?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(ui.iconColor)) }
        }
    }

    /**
     * A weather card on the main screen. Material tints a card's surface by its elevation, which on
     * black comes out a washed grey — so the elevation goes to 0 and the ground is set outright,
     * leaving pure black with the house outline.
     */
    fun paintMainCard(context: Context, card: MaterialCardView?) {
        val ui = state(context)
        if (!ui.enabled || card == null) return
        card.apply {
            elevation = 0f
            setCardBackgroundColor(ui.surface)
            strokeColor = ui.borderColor
            strokeWidth = dp(context, ui.borderWidth)
            radius = dp(context, ui.cornerRadius).toFloat()
        }
    }

    /**
     * A tag chip — the Conditions / Air quality / Wind row over a trend card.
     *
     * Unselected: black with a yellow outline and yellow text. Selected: reversed, yellow ground
     * with black text, which is the only state difference that survives a two-colour palette.
     */
    fun paintChip(context: Context, chip: Chip?, checked: Boolean) {
        val ui = state(context)
        if (!ui.enabled || chip == null) return
        chip.apply {
            chipBackgroundColor =
                ColorStateList.valueOf(if (checked) ui.accentColor else ui.background)
            setTextColor(if (checked) ui.background else ui.textColor)
            chipStrokeColor = ColorStateList.valueOf(ui.borderColor)
            chipStrokeWidth = dp(context, if (ui.borderWidth > 0) ui.borderWidth else 1).toFloat()
            // The tick would be black-on-yellow noise: the reversal already says "selected".
            isCheckedIconVisible = false
            rippleColor = ColorStateList.valueOf(ui.accentColor)
        }
    }

    /**
     * The snackbar ("flash"): upstream paints it with the *inverse* surface, which on our black
     * ground is a light-grey slab with black text. Black ground, yellow text, yellow outline — and
     * the outline is drawn on the CardView itself so it survives the card's own corner radius.
     */
    fun paintSnackbar(context: Context, root: View?) {
        val ui = state(context)
        if (!ui.enabled || root == null) return

        val card = root.findFirstCardView()
        card?.apply {
            setCardBackgroundColor(ui.background)
            radius = dp(context, ui.cornerRadius).toFloat()
            foreground = if (ui.borderWidth > 0) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(context, ui.cornerRadius).toFloat()
                    setStroke(dp(context, ui.borderWidth), ui.borderColor)
                    setColor(android.graphics.Color.TRANSPARENT)
                }
            } else {
                null
            }
        }
        root.findViewById<TextView>(org.breezyweather.R.id.snackbar_text)?.setTextColor(ui.textColor)
        root.findViewById<TextView>(org.breezyweather.R.id.snackbar_action)?.setTextColor(ui.accentColor)
    }

    private fun View.findFirstCardView(): CardView? {
        if (this is CardView) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findFirstCardView()?.let { return it }
            }
        }
        return null
    }
}
