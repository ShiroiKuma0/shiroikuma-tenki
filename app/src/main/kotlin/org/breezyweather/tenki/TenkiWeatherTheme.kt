/*
 * 白い熊 天気 (shiroikuma-tenki) fork: the header behind the temperature — black, not weather.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import org.breezyweather.ui.theme.weatherView.WeatherThemeDelegate
import org.breezyweather.ui.theme.weatherView.WeatherView

/**
 * A [WeatherView] that draws nothing but the house ground.
 *
 * Upstream's `MaterialWeatherView` animates a per-condition gradient — the blue sky behind the
 * temperature, clouds drifting over it, rain, a meteor shower. It is lovely and it is the one thing
 * on the main screen that no colour resource can reach, because the gradient is computed inside the
 * animator rather than read from the theme. In the house look the header is simply black, so this
 * replaces the whole animator with a flat fill: nothing to tint, nothing to animate, and the battery
 * that the animation used to cost goes back.
 */
@SuppressLint("ViewConstructor")
class TenkiWeatherView(context: Context) : View(context), WeatherView {

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
 * Wraps upstream's weather theme delegate and answers with the house colours while the 白い熊 天気
 * UI is on: a black header, yellow on top of it, and no "light background" state — which is what
 * decides the toolbar title, the navigation icon, the system bars and the trend chart labels.
 *
 * Switch the UI off and every call falls straight through to [delegate], gradient and all.
 */
class TenkiWeatherThemeDelegate(
    private val delegate: WeatherThemeDelegate,
) : WeatherThemeDelegate {

    override fun getWeatherView(context: Context): WeatherView =
        if (TenkiViewTheme.isEnabled(context)) {
            TenkiWeatherView(context)
        } else {
            delegate.getWeatherView(context)
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
