/*
 * 白い熊 天気 (shiroikuma-tenki) fork: our own weather-icon packs, in the house black-yellow.
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package org.breezyweather.tenki

import android.animation.Animator
import android.animation.AnimatorInflater
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import androidx.annotation.Size
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.core.content.res.ResourcesCompat
import breezyweather.domain.weather.reference.WeatherCode
import org.breezyweather.BreezyWeather
import org.breezyweather.R
import org.breezyweather.ui.common.images.MoonDrawable
import org.breezyweather.ui.common.images.SunDrawable
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.resource.utils.Constants
import org.breezyweather.ui.theme.resource.utils.ResourceUtils
import org.breezyweather.ui.theme.resource.utils.XmlHelper

/**
 * The 白い熊 天気 icon packs — upstream's twelve weather codes re-cut in the house yellow,
 * once as line-art ([Variant.TRACED]) and once filled solid ([Variant.FULL]).
 *
 * They live inside the app, next to the Breezy Weather set, rather than in an icon pack:
 * all of them are served from our own resources, told apart by the head each pack's
 * drawables carry, and by the three filters in `res_fork/xml/`.
 * `tools/icon/emit_weather_icons.py` writes the drawables and those filters together, so
 * they never drift apart.
 *
 * The filters name resources *without* the pack's head, so one set of them serves every
 * pack; [Variant.head] is put back on at lookup. The animators are upstream's — our filter
 * points each layer at the one that moves it the right way — so the icons still breathe,
 * drift and fall exactly as before.
 */
class TenkiResourceProvider(
    private val variant: Variant = Variant.TRACED,
) : ResourceProvider() {

    /** A pack: what settings stores for it, what its drawables are called, what it is called. */
    enum class Variant(
        val id: String,
        val head: String,
        @StringRes val labelRes: Int,
    ) {
        TRACED(
            "shiroikuma.tenki.icons.traced",
            "tenki_traced_",
            R.string.tenki_icon_provider_traced
        ),
        FULL(
            "shiroikuma.tenki.icons.full",
            "tenki_full_",
            R.string.tenki_icon_provider_full
        ),
        ;

        companion object {
            /** What the one pack was called before there were two of them. */
            private const val LEGACY_ID = "shiroikuma.tenki.icons"

            fun of(packageName: String?): Variant? {
                if (packageName == null) return null
                return entries.firstOrNull { it.id == packageName }
                    ?: TRACED.takeIf { packageName == LEGACY_ID }
            }
        }
    }

    private val mContext: Context = BreezyWeather.instance
    override var providerName: String? = mContext.getString(variant.labelRes)
    override val providerIcon: Drawable?
        get() = getDrawable("weather_partly_cloudy_day")

    private val mDrawableFilter = filterMap(R.xml.tenki_icon_provider_drawable_filter)
    private val mAnimatorFilter = filterMap(R.xml.tenki_icon_provider_animator_filter)
    private val mShortcutFilter = filterMap(R.xml.tenki_icon_provider_shortcut_filter)

    private fun filterMap(@XmlRes resId: Int): Map<String, String> {
        return try {
            XmlHelper.getFilterMap(mContext.resources.getXml(resId))
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * The identity stored in settings. It is not a real package — the drawables are ours —
     * so [getDrawableUri] hands out the app's own package instead.
     */
    override val packageName: String
        get() = variant.id

    override fun getDrawableUri(resName: String): Uri {
        return ResourceUtils.getDrawableUri(mContext.packageName, "drawable", variant.head + resName)
    }

    private fun getDrawable(resName: String): Drawable? {
        return try {
            ResourcesCompat.getDrawable(
                mContext.resources,
                ResourceUtils.getResId(mContext, variant.head + resName, "drawable"),
                null
            )
        } catch (e: Exception) {
            null
        }
    }

    // weather icon.
    override fun getWeatherIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getWeatherIconName(code, dayTime))!!
    }

    override fun getWeatherIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return getDrawableUri(getWeatherIconName(code, dayTime))
    }

    @Size(3)
    override fun getWeatherIcons(code: WeatherCode?, dayTime: Boolean): Array<Drawable?> {
        return arrayOf(
            getDrawable(getWeatherIconName(code, dayTime, 1)),
            getDrawable(getWeatherIconName(code, dayTime, 2)),
            getDrawable(getWeatherIconName(code, dayTime, 3))
        )
    }

    private fun getWeatherIconName(code: WeatherCode?, daytime: Boolean): String {
        return filtered(mDrawableFilter, innerWeatherName(code, daytime))
    }

    private fun getWeatherIconName(
        code: WeatherCode?,
        daytime: Boolean,
        @IntRange(from = 1, to = 3) index: Int,
    ): String {
        return filtered(mDrawableFilter, innerWeatherName(code, daytime) + Constants.SEPARATOR + index)
    }

    // animator. These are upstream's, so they are looked up unprefixed.
    @Size(3)
    override fun getWeatherAnimators(code: WeatherCode?, dayTime: Boolean): Array<Animator?> {
        return arrayOf(
            getAnimator(getWeatherAnimatorName(code, dayTime, 1)),
            getAnimator(getWeatherAnimatorName(code, dayTime, 2)),
            getAnimator(getWeatherAnimatorName(code, dayTime, 3))
        )
    }

    private fun getAnimator(resName: String): Animator? {
        return try {
            AnimatorInflater.loadAnimator(mContext, ResourceUtils.getResId(mContext, resName, "animator"))
        } catch (e: Exception) {
            null
        }
    }

    private fun getWeatherAnimatorName(
        code: WeatherCode?,
        daytime: Boolean,
        @IntRange(from = 1, to = 3) index: Int,
    ): String {
        return filtered(mAnimatorFilter, innerWeatherName(code, daytime) + Constants.SEPARATOR + index)
    }

    // minimal.
    override fun getMinimalLightIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniName(code, dayTime, Constants.LIGHT))!!
    }

    override fun getMinimalLightIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return getDrawableUri(getMiniName(code, dayTime, Constants.LIGHT))
    }

    override fun getMinimalGreyIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniName(code, dayTime, Constants.GREY))!!
    }

    override fun getMinimalGreyIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return getDrawableUri(getMiniName(code, dayTime, Constants.GREY))
    }

    override fun getMinimalDarkIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniName(code, dayTime, Constants.DARK))!!
    }

    override fun getMinimalDarkIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return getDrawableUri(getMiniName(code, dayTime, Constants.DARK))
    }

    override fun getMinimalXmlIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniName(code, dayTime, Constants.XML))!!
    }

    override fun getMinimalIcon(code: WeatherCode?, dayTime: Boolean): Icon {
        return Icon.createWithResource(mContext, getMinimalXmlIconId(code, dayTime))
    }

    @DrawableRes
    fun getMinimalXmlIconId(code: WeatherCode?, dayTime: Boolean): Int {
        return ResourceUtils.getResId(
            mContext,
            variant.head + getMiniName(code, dayTime, Constants.XML),
            "drawable"
        )
    }

    private fun getMiniName(code: WeatherCode?, daytime: Boolean, tone: String): String {
        return filtered(
            mDrawableFilter,
            innerWeatherName(code, daytime) + Constants.SEPARATOR + Constants.MINI +
                Constants.SEPARATOR + tone
        )
    }

    // shortcut.
    override fun getShortcutsIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getShortcutsIconName(code, dayTime))!!
    }

    override fun getShortcutsForegroundIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(
            getShortcutsIconName(code, dayTime) + Constants.SEPARATOR + Constants.FOREGROUND
        )!!
    }

    private fun getShortcutsIconName(code: WeatherCode?, daytime: Boolean): String {
        return filtered(
            mShortcutFilter,
            Constants.getShortcutsName(code) + Constants.SEPARATOR +
                if (daytime) Constants.DAY else Constants.NIGHT
        )
    }

    // sun and moon.
    // Upstream draws both in code, in their own colours. Flattening them to the house
    // yellow keeps the astro card in the packs' palette; the shapes still tell them apart.
    override val sunDrawable: Drawable
        get() = SunDrawable().apply { colorFilter = PorterDuffColorFilter(INK, PorterDuff.Mode.SRC_IN) }

    override val moonDrawable: Drawable
        get() = MoonDrawable().apply { colorFilter = PorterDuffColorFilter(INK, PorterDuff.Mode.SRC_IN) }

    companion object {
        private const val INK = 0xFFFFFF00.toInt()

        private fun filtered(filter: Map<String, String>, key: String): String {
            val value = filter[key]
            return if (value.isNullOrEmpty()) key else value
        }

        private fun innerWeatherName(code: WeatherCode?, daytime: Boolean): String {
            return Constants.getResourcesName(code) + Constants.SEPARATOR +
                if (daytime) Constants.DAY else Constants.NIGHT
        }
    }
}
