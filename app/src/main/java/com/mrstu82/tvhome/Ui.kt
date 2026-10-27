package com.mrstu82.tvhome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

const val INK = 0xFFF3EFE8.toInt()
const val DARK = 0xFF16161A.toInt()
const val FIRE_HOME = "com.amazon.tv.launcher"

data class App(val pkg: String, val name: String, val tint: Int, val useIcon: Boolean = false)

val APPS = listOf(
    App("uk.co.bbc.iplayer", "BBC iPlayer", 0xFFC2477F.toInt(), useIcon = true),
    App("com.amazon.firetv.youtube", "YouTube", 0xFFC24A42.toInt()),
    App("com.stremio.one", "Stremio", 0xFF7E6BC4.toInt()),
    App("com.bskyb.nowtv.beta", "NOW", 0xFF4FAE9C.toInt()),
    App("com.netflix.ninja", "Netflix", 0xFFB8322F.toInt()),
    App("air.ITVMobilePlayer", "ITVX", 0xFFC9CF6A.toInt()),
    App("com.channel4.ondemand", "Channel 4", 0xFFA9A9A9.toInt(), useIcon = true),
)

data class City(val name: String, val lat: Double, val lon: Double)

val CITIES = listOf(
    City("London", 51.5072, -0.1276), City("Manchester", 53.4808, -2.2426),
    City("Birmingham", 52.4862, -1.8904), City("Bristol", 51.4545, -2.5879),
    City("Edinburgh", 55.9533, -3.1883), City("Cardiff", 51.4816, -3.1791),
    City("Belfast", 54.5973, -5.9301),
)

/** A cached Bing photo, or the bundled photograph when [file] is null. */
data class Wall(val file: java.io.File?, val caption: String)

class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    var plainWallpaper by bool("plain", false)
    var dailyRotation by bool("daily", true)
    var clock24 by bool("clock24", true)
    var bypassFireHome by bool("bypass", false)
    var city: City
        get() = CITIES.firstOrNull { it.name == p.getString("city", null) } ?: CITIES[0]
        set(v) = p.edit().putString("city", v.name).apply()
    var order: List<App>
        get() {
            val saved = p.getString("order", "")!!.split(',')
            return APPS.sortedBy { saved.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        }
        set(v) = p.edit().putString("order", v.joinToString(",") { it.pkg }).apply()
    var hidden: Set<String>
        get() = p.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("hidden", v).apply()
    var weatherJson: String?
        get() = p.getString("weather", null)
        set(v) = p.edit().putString("weather", v).apply()
    var bingJson: String?
        get() = p.getString("bing", null)
        set(v) = p.edit().putString("bing", v).apply()
    var bingAt: Long
        get() = p.getLong("bingAt", 0L)
        set(v) = p.edit().putLong("bingAt", v).apply()

    private fun bool(key: String, def: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = p.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) =
            p.edit().putBoolean(key, value).apply()
    }

    fun wall(ctx: Context): Wall? = if (plainWallpaper) null else Bing.wall(ctx, this)
}

/** Mock pixels (1920-wide design) to device pixels. */
fun Context.u(px: Number): Int = (px.toFloat() * resources.displayMetrics.widthPixels / 1920f).toInt()

object Fonts {
    lateinit var light: Typeface; lateinit var serif: Typeface; lateinit var italic: Typeface
    lateinit var sansLight: Typeface; lateinit var sans: Typeface; lateinit var sansMedium: Typeface
    fun load(ctx: Context) {
        if (::serif.isInitialized) return
        val r = ctx.resources
        light = r.getFont(R.font.corm_light); serif = r.getFont(R.font.corm_regular)
        italic = r.getFont(R.font.corm_italic); sansLight = r.getFont(R.font.inter_light)
        sans = r.getFont(R.font.inter_regular); sansMedium = r.getFont(R.font.inter_medium)
    }
}

fun Context.text(s: CharSequence, face: Typeface, mockPx: Number, color: Int = INK, shadow: Boolean = false) =
    TextView(this).apply {
        text = s; typeface = face; setTextColor(color); includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_PX, u(mockPx).toFloat())
        fontFeatureSettings = "lnum"
        if (shadow) setShadowLayer(u(18).toFloat(), 0f, u(2).toFloat(), 0x73000000)
    }

fun rounded(fill: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(fill); cornerRadius = radius.toFloat(); if (stroke > 0) setStroke(stroke, strokeColor)
}

fun lp(w: Int, h: Int, gravity: Int = Gravity.TOP or Gravity.START) = FrameLayout.LayoutParams(w, h, gravity)

private val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)
const val FOCUS_MS = 200L

/** Eased lift / scale / glow shared by every focusable surface. */
fun View.animateFocus(focused: Boolean, scale: Float, lift: Float, shiftX: Float = 0f, glow: View? = null) {
    animate().scaleX(if (focused) scale else 1f).scaleY(if (focused) scale else 1f)
        .translationY(if (focused) -lift else 0f).translationX(if (focused) shiftX else 0f)
        .translationZ(if (focused) lift else 0f)
        .setDuration(FOCUS_MS).setInterpolator(EASE).start()
    glow?.animate()?.alpha(if (focused) 1f else 0f)?.setDuration(FOCUS_MS)?.setInterpolator(EASE)?.start()
}

fun launchIntent(ctx: Context, pkg: String): Intent? =
    (ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: ctx.packageManager.getLaunchIntentForPackage(pkg))
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The package's own TV banner, or its icon when it ships none. */
fun appArt(ctx: Context, intent: Intent): Pair<Drawable?, Boolean> {
    val pm = ctx.packageManager
    APPS.find { it.pkg == (intent.`package` ?: intent.component?.packageName) }?.let { app -> officialArt(ctx, app)?.let { return it to true } }
    val banner = runCatching { pm.getActivityBanner(intent) }.getOrNull()
        ?: runCatching { pm.getApplicationBanner(intent.`package` ?: intent.component!!.packageName) }.getOrNull()
    if (banner != null) return trimmed(banner) to true
    return runCatching { pm.getActivityIcon(intent) }.getOrNull() to false
}

/**
 * Priority apps: the highest-resolution banner (or icon, where the banner's logo is tiny) from the app's own
 * APK, unmodified and only scaled. Bitmaps decode at native pixels and are mipmapped for a clean downscale.
 */
private fun officialArt(ctx: Context, app: App): Drawable? = runCatching {
    val ai = ctx.packageManager.getApplicationInfo(app.pkg, 0)
    val id = if (app.useIcon) ai.icon else ai.banner
    val hi = ctx.createPackageContext(app.pkg, 0).createConfigurationContext(
        Configuration().apply { densityDpi = DisplayMetrics.DENSITY_XXXHIGH }).resources
    val v = TypedValue().also { hi.getValue(id, it, true) }
    if (v.string?.endsWith(".xml") == true) {
        val d = hi.getDrawable(id, null)
        (if (d is AdaptiveIconDrawable) LayerDrawable(arrayOf(d.background, d.foreground)) else d).also(::mipmap)
    } else {
        BitmapDrawable(ctx.resources, BitmapFactory.decodeResource(hi, id, BitmapFactory.Options().apply { inScaled = false })
            .apply { setHasMipMap(true) })
    }
}.getOrNull()

private fun mipmap(d: Drawable?) {
    when (d) {
        is BitmapDrawable -> d.bitmap?.setHasMipMap(true)
        is LayerDrawable -> for (i in 0 until d.numberOfLayers) mipmap(d.getDrawable(i))
    }
}

/**
 * Some official banners are a small logo on a wide flat field; crop to the logo
 * (keeping 16:9 and the banner's own field colour) so it reads at sofa distance.
 */
private fun trimmed(d: Drawable): Drawable {
    val w = 320; val h = 180
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, w, h); d.draw(Canvas(bmp))
    val bg = bmp.getPixel(2, 2)
    var l = w; var t = h; var r = -1; var b = -1
    for (y in 0 until h) for (x in 0 until w) {
        val p = bmp.getPixel(x, y)
        val diff = maxOf(abs(((p shr 16) and 255) - ((bg shr 16) and 255)), abs(((p shr 8) and 255) - ((bg shr 8) and 255)),
            abs((p and 255) - (bg and 255)), abs((p ushr 24) - (bg ushr 24)))
        if (diff > 40) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
    }
    if (r < 0 || (r - l + 1) * (b - t + 1) > w * h * 0.30) return d
    val cw = maxOf((r - l + 1) * 1.5f, (b - t + 1) * 1.5f * 16 / 9)
    val ch = cw * 9 / 16
    val cx = (l + r) / 2f; val cy = (t + b) / 2f
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
        drawColor(bg)
        drawBitmap(bmp, Rect((cx - cw / 2).toInt(), (cy - ch / 2).toInt(), (cx + cw / 2).toInt(), (cy + ch / 2).toInt()),
            Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return BitmapDrawable(Resources.getSystem(), out)
}

/** 196x110 tile carrying an app's own logo art, with the shared focus animation. */
fun Activity.appTile(intent: Intent, label: String): FrameLayout {
    val r = u(10)
    val tile = FrameLayout(this).apply {
        isFocusable = true; clipToOutline = true
        background = rounded(0x61141418, r, u(1).coerceAtLeast(1), 0x24F3EFE8)
        contentDescription = label
    }
    val (art, isBanner) = appArt(this, intent)
    if (art != null) {
        tile.addView(ImageView(this).apply {
            setImageDrawable(art)
            scaleType = if (isBanner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (!isBanner) setPadding(u(22), u(22), u(22), u(22))
        }, lp(-1, -1))
    } else {
        tile.addView(text(label, Fonts.sans, 24), lp(-2, -2, Gravity.CENTER))
    }
    val glow = View(this).apply { background = rounded(0, r, u(3), INK); alpha = 0f }
    tile.addView(glow, lp(-1, -1))
    tile.setOnFocusChangeListener { v, f -> v.animateFocus(f, 1.06f, u(14).toFloat(), glow = glow) }
    tile.setOnClickListener { runCatching { startActivity(intent) } }
    return tile
}

/** Wallpaper decoded once per photo, muted to sit behind type. */
object Wallpaper {
    private var cache: Pair<Wall, Bitmap>? = null
    fun bitmap(ctx: Context, w: Wall): Bitmap {
        cache?.let { if (it.first == w) return it.second }
        cache = null
        val b = w.file?.let { BitmapFactory.decodeFile(it.path) } ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.wall_clouds)
        return b.also { cache = w to it }
    }
    fun muted(): ColorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
        setSaturation(0.72f)
        postConcat(ColorMatrix(floatArrayOf(.96f, 0f, 0f, 0f, 5f, 0f, .96f, 0f, 0f, 5f, 0f, 0f, .96f, 0f, 5f, 0f, 0f, 0f, 1f, 0f)))
    })
}

/** Top/bottom vertical scrim plus soft radial pools under the clock and the weather. */
class Scrim : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(0x80000000.toInt(), 0, 0x1A000000, 0xC7000000.toInt()),
            floatArrayOf(0f, .34f, .6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        pool(c, .18f * w, .28f * h, .40f * w, 0x6B000000)
        pool(c, .85f * w, .18f * h, .30f * w, 0x73000000)
    }
    private fun pool(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.shader = RadialGradient(x, y, r, color, 0, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, paint)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
