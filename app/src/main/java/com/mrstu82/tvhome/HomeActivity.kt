package com.mrstu82.tvhome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

class HomeActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var wall: ImageView
    private lateinit var caption: TextView
    private lateinit var clock: TextClock
    private lateinit var weather: LinearLayout
    private lateinit var row: LinearLayout
    private var fetchedAt = 0L
    private var fetchedFor = ""
    private var tilesKey = ""
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val rotate = object : Runnable {
        override fun run() {
            showWall()
            Bing.refresh(this@HomeActivity, prefs) { runOnUiThread(::showWall) }
            handler.postDelayed(this, Bing.ROTATE_MS - System.currentTimeMillis() % Bing.ROTATE_MS + 1000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Fonts.load(this); prefs = Prefs(this)
        val root = FrameLayout(this)
        wall = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; colorFilter = Wallpaper.muted() }
        root.addView(wall, lp(-1, -1))
        root.addView(View(this).apply { background = Scrim() }, lp(-1, -1))

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = TextClock(this).apply {
            typeface = Fonts.light; setTextColor(INK); includeFontPadding = false; fontFeatureSettings = "lnum"; isSingleLine = true; letterSpacing = -.01f
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(200).toFloat())
            setShadowLayer(u(24).toFloat(), 0f, u(2).toFloat(), 0x59000000)
        }
        left.addView(clock, LinearLayout.LayoutParams(u(1000), -2))
        val date = TextClock(this).apply {
            typeface = Fonts.italic; setTextColor(0xDBF3EFE8.toInt()); includeFontPadding = false; fontFeatureSettings = "lnum"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(42).toFloat())
            setShadowLayer(u(18).toFloat(), 0f, u(2).toFloat(), 0x73000000)
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
        }
        left.addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = u(-11) })
        root.addView(left, lp(u(1000), -2).apply { leftMargin = u(112); topMargin = u(51) })

        weather = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        root.addView(weather, lp(-2, -2, Gravity.TOP or Gravity.END).apply { rightMargin = u(112); topMargin = u(92) })

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        row = LinearLayout(this).apply { gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
        bottom.addView(row, LinearLayout.LayoutParams(-1, u(150)))
        val foot = FrameLayout(this).apply { clipChildren = false }
        caption = text("", Fonts.italic, 24, 0xCCF3EFE8.toInt(), shadow = true)
        foot.addView(caption, lp(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        foot.addView(cog(), lp(u(64), u(64), Gravity.END or Gravity.CENTER_VERTICAL))
        bottom.addView(foot, LinearLayout.LayoutParams(-1, u(72)).apply { topMargin = u(26) })
        root.addView(bottom, lp(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = u(112); rightMargin = u(112); bottomMargin = u(64)
        })
        root.clipChildren = false
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        prefs.bypassFireHome = false
        val fmt = if (prefs.clock24) "HH:mm" else "h:mm"
        clock.format12Hour = fmt; clock.format24Hour = fmt
        handler.removeCallbacks(rotate); rotate.run()
        buildTiles()
        prefs.weatherJson?.let { runCatching { showWeather(OpenMeteo.parse(it)) } }
        refreshWeather()
    }

    override fun onPause() { super.onPause(); handler.removeCallbacks(rotate) }

    private fun showWall() {
        val w = prefs.wall(this)
        if (w == null) { wall.setImageDrawable(null); wall.setBackgroundColor(DARK); caption.text = "" }
        else { wall.setImageBitmap(Wallpaper.bitmap(this, w)); caption.text = w.caption }
    }

    /** Rebuilds only when the visible app list changes, so resuming Home stays warm (no art reload). */
    private fun buildTiles() {
        val apps = prefs.order.filter { it.pkg !in prefs.hidden }.mapNotNull { app -> launchIntent(this, app.pkg)?.let { app to it } }
        val key = apps.joinToString(",") { it.first.pkg }
        if (key == tilesKey) return
        tilesKey = key
        val keep = row.findFocus()?.let { row.indexOfChild(it) } ?: 0
        row.removeAllViews()
        val gap = u(20)
        apps.forEach { (app, intent) ->
            row.addView(appTile(intent, app.name), LinearLayout.LayoutParams(u(196), u(110)).apply { marginEnd = gap })
        }
        row.addView(allAppsTile(), LinearLayout.LayoutParams(u(110), u(110)))
        row.getChildAt(keep.coerceIn(0, row.childCount - 1)).let { t -> row.post { t.requestFocus() } }
    }

    private fun allAppsTile() = FrameLayout(this).apply {
        isFocusable = true; contentDescription = "All apps"
        background = rounded(0, u(10), u(1).coerceAtLeast(1), 0x2EF3EFE8)
        val inner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; alpha = .82f }
        inner.addView(ImageView(context).apply { setImageResource(R.drawable.ic_grid) }, LinearLayout.LayoutParams(u(22), u(22)))
        inner.addView(text("All apps", Fonts.sans, 15).apply { letterSpacing = .07f },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = u(12) })
        addView(inner, lp(-2, -2, Gravity.CENTER))
        val glow = View(context).apply { background = rounded(0, u(10), u(3), INK); alpha = 0f }
        addView(glow, lp(-1, -1))
        setOnFocusChangeListener { v, f ->
            v.animateFocus(f, 1.06f, u(14).toFloat(), glow = glow); inner.animate().alpha(if (f) 1f else .82f).setDuration(FOCUS_MS).start()
        }
        setOnClickListener { startActivity(Intent(context, AllAppsActivity::class.java)) }
    }

    private fun cog() = ImageView(this).apply {
        isFocusable = true; contentDescription = "Settings"
        setImageResource(R.drawable.ic_cog); alpha = .78f
        setPadding(u(12), u(12), u(12), u(12))
        setOnFocusChangeListener { v, f ->
            v.animateFocus(f, 1.12f, u(4).toFloat()); v.animate().alpha(if (f) 1f else .78f).setDuration(FOCUS_MS).start()
            v.background = if (f) rounded(0x33F3EFE8, u(32)) else null
        }
        setOnClickListener { startActivity(Intent(context, SettingsActivity::class.java)) }
    }

    private fun refreshWeather() {
        val city = prefs.city
        if (city.name == fetchedFor && System.currentTimeMillis() - fetchedAt < 30 * 60_000L) return
        fetchedFor = city.name; fetchedAt = System.currentTimeMillis()
        Thread {
            runCatching { OpenMeteo.fetch(city) }.onSuccess { json ->
                runCatching { OpenMeteo.parse(json) }.onSuccess { w ->
                    prefs.weatherJson = json
                    runOnUiThread { showWeather(w) }
                }
            }.onFailure { fetchedAt = 0L }
        }.start()
    }

    private fun showWeather(w: Weather) {
        weather.removeAllViews()
        val now = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        now.addView(ImageView(this).apply { setImageResource(OpenMeteo.icon(w.code)); alpha = .85f },
            LinearLayout.LayoutParams(u(46), u(46)))
        now.addView(text("${w.temp}°", Fonts.light, 64, shadow = true),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = u(16) })
        weather.addView(now, LinearLayout.LayoutParams(-2, -2))
        weather.addView(text("${OpenMeteo.describe(w.code)} · ${prefs.city.name}", Fonts.sans, 22, 0xEBF3EFE8.toInt(), shadow = true)
            .apply { letterSpacing = .014f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = u(5) })
        val days = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        w.days.forEachIndexed { i, d ->
            days.addView(text(d.label, Fonts.sans, 17, 0xBFF3EFE8.toInt(), shadow = true).apply { letterSpacing = .118f },
                LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = u(40) })
            days.addView(ImageView(this).apply { setImageResource(OpenMeteo.icon(d.code)); alpha = .8f },
                LinearLayout.LayoutParams(u(22), u(22)).apply { marginStart = u(10); marginEnd = u(10) })
            days.addView(text("${d.max}° / ${d.min}°", Fonts.sans, 22, 0xEBF3EFE8.toInt(), shadow = true))
        }
        weather.addView(days, LinearLayout.LayoutParams(-2, -2).apply { topMargin = u(19) })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?) =
        if (keyCode == KeyEvent.KEYCODE_BACK) true else super.onKeyDown(keyCode, event)
}
