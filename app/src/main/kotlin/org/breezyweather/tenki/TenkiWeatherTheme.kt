/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the header behind the temperature — upstream's weather
 * scene, re-inked in the house black-yellow.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import org.breezyweather.ui.theme.weatherView.WeatherThemeDelegate
import org.breezyweather.ui.theme.weatherView.WeatherView

/**
 * Turns any drawing into the house two-tone: a pixel's **brightness** picks a point on the
 * `background → accent` ramp, so upstream's blue-to-white cloudscape comes out as a black-to-yellow
 * one, its rain, sun and meteor shower with it. Nothing has to know which weather is on screen —
 * the scene keeps its shape and motion and only its ink changes.
 *
 * @param intensity 0..100, how far a fully bright pixel travels towards the accent. Low values keep
 *   the header a dark texture the yellow text can sit on; high values make the clouds compete with it.
 */
fun duotoneFilter(background: Int, accent: Int, intensity: Int): ColorMatrixColorFilter {
    val t = intensity.coerceIn(0, 100) / 100f

    // out_c = ground_c + luminance * t * (accent_c - ground_c), computed per channel. The last
    // column of a ColorMatrix row is a constant in 0..255, which is exactly the ground.
    fun row(groundChannel: Int, accentChannel: Int): FloatArray {
        val k = t * (accentChannel - groundChannel) / 255f
        return floatArrayOf(0.299f * k, 0.587f * k, 0.114f * k, 0f, groundChannel.toFloat())
    }

    val red = row(Color.red(background), Color.red(accent))
    val green = row(Color.green(background), Color.green(accent))
    val blue = row(Color.blue(background), Color.blue(accent))
    return ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                red[0], red[1], red[2], red[3], red[4],
                green[0], green[1], green[2], green[3], green[4],
                blue[0], blue[1], blue[2], blue[3], blue[4],
                // Alpha untouched: where the scene is transparent it stays transparent, and the
                // view's own ground shows through instead of a flat rectangle of ink.
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/**
 * A [WeatherView] that draws nothing but the house ground — what the header becomes when the
 * intensity knob is at 0. No animation at all, which is also the cheapest thing the header can be.
 */
@SuppressLint("ViewConstructor")
class TenkiFlatWeatherView(context: Context) : View(context), WeatherView {

    private var currentWeatherKind: Int = WeatherView.WEATHER_KIND_NULL

    init {
        setBackgroundColor(TenkiUiState.getInstance(context).background)
    }

    override fun setWeather(weatherKind: Int, daytime: Boolean, darkMode: Boolean) {
        currentWeatherKind = weatherKind
        // Re-read on every bind, so dragging the Background colour repaints the header too.
        setBackgroundColor(TenkiUiState.getInstance(context).background)
    }

    override fun onScroll(scrollY: Int) = Unit

    override val weatherKind: Int
        get() = currentWeatherKind

    override fun setDrawable(drawable: Boolean) = Unit

    override fun setDoAnimate(animate: Boolean) = Unit

    override fun setGravitySensorEnabled(enabled: Boolean) = Unit
}

/**
 * Upstream's weather scene, wrapped so it can be re-inked.
 *
 * The scene is drawn on a plain canvas inside a `View`, so a **hardware layer** with a colour filter
 * on its paint recolours the whole thing at composite time — every implementor at once, present and
 * future, without touching a line of upstream's drawing code.
 *
 * The filter is re-applied on `setWeather` and `setDrawable`, both of which run when the home
 * fragment binds or resumes, so coming back from the UI page picks up a changed knob.
 */
@SuppressLint("ViewConstructor")
class TenkiWeatherHeaderView(
    context: Context,
    private val inner: WeatherView,
) : FrameLayout(context), WeatherView {

    init {
        addView(
            inner as View,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        reink()
    }

    private fun reink() {
        val ui = TenkiUiState.getInstance(context)
        setBackgroundColor(ui.background)
        (inner as View).setLayerType(
            LAYER_TYPE_HARDWARE,
            Paint().apply {
                colorFilter = duotoneFilter(ui.background, ui.accentColor, ui.headerIntensity)
            }
        )
    }

    override fun setWeather(weatherKind: Int, daytime: Boolean, darkMode: Boolean) {
        reink()
        inner.setWeather(weatherKind, daytime, darkMode)
    }

    override fun onScroll(scrollY: Int) = inner.onScroll(scrollY)

    override val weatherKind: Int
        get() = inner.weatherKind

    override fun setDrawable(drawable: Boolean) {
        reink()
        inner.setDrawable(drawable)
    }

    override fun setDoAnimate(animate: Boolean) = inner.setDoAnimate(animate)

    override fun setGravitySensorEnabled(enabled: Boolean) = inner.setGravitySensorEnabled(enabled)
}

/**
 * Wraps upstream's weather theme delegate and answers with the house colours while the 白い熊 天気
 * UI is on: a black-yellow header, yellow on top of it, and no "light background" state — which is
 * what decides the toolbar title, the navigation icon, the system bars and the trend chart labels.
 *
 * Switch the UI off and every call falls straight through to [delegate], blue gradient and all.
 */
class TenkiWeatherThemeDelegate(
    private val delegate: WeatherThemeDelegate,
) : WeatherThemeDelegate {

    override fun getWeatherView(context: Context): WeatherView {
        if (!TenkiViewTheme.isEnabled(context)) return delegate.getWeatherView(context)
        // Intensity 0 means "no scene at all" — don't pay for an animation nobody can see.
        if (TenkiViewTheme.state(context).headerIntensity <= 0) return TenkiFlatWeatherView(context)
        return TenkiWeatherHeaderView(context, delegate.getWeatherView(context))
    }

    override fun getThemeColors(context: Context, weatherKind: Int, daylight: Boolean): IntArray {
        if (!TenkiViewTheme.isEnabled(context)) {
            return delegate.getThemeColors(context, weatherKind, daylight)
        }
        val ui = TenkiViewTheme.state(context)
        // [0] is the accent the refresh spinner and the trend charts pick up; [1]/[2] are the
        // header's own gradient stops, which here are simply the ground.
        return intArrayOf(ui.accentColor, ui.background, ui.background)
    }

    override fun isLightBackground(context: Context, weatherKind: Int, daylight: Boolean): Boolean =
        if (TenkiViewTheme.isEnabled(context)) {
            false
        } else {
            delegate.isLightBackground(context, weatherKind, daylight)
        }

    override fun getBackgroundColor(context: Context, weatherKind: Int, daylight: Boolean): Int =
        if (TenkiViewTheme.isEnabled(context)) {
            TenkiViewTheme.state(context).background
        } else {
            delegate.getBackgroundColor(context, weatherKind, daylight)
        }

    override fun getOnBackgroundColor(context: Context, weatherKind: Int, daylight: Boolean): Int =
        if (TenkiViewTheme.isEnabled(context)) {
            TenkiViewTheme.state(context).textColor
        } else {
            delegate.getOnBackgroundColor(context, weatherKind, daylight)
        }
}
