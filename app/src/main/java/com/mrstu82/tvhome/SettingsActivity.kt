package com.mrstu82.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private lateinit var order: LinearLayout
    private val refreshers = mutableListOf<() -> Unit>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Fonts.load(this); prefs = Prefs(this)
        val root = FrameLayout(this).apply { setBackgroundColor(DARK) }
        prefs.wall(this)?.let { w ->
            val src = Wallpaper.bitmap(this, w)
            val small = Bitmap.createScaledBitmap(src, 48, 48 * src.height / src.width, true)
            root.addView(ImageView(this).apply {
                setImageBitmap(small); scaleType = ImageView.ScaleType.CENTER_CROP; colorFilter = Wallpaper.muted()
            }, lp(-1, -1))
        }
        root.addView(View(this).apply { setBackgroundColor(0x9E0C0C0F.toInt()) }, lp(-1, -1))
        root.addView(text("Settings", Fonts.light, 84), lp(-2, -2).apply { leftMargin = u(112); topMargin = u(88) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        root.addView(list, lp(u(820), -2).apply { leftMargin = u(112); topMargin = u(250) })
        row("Wallpaper source", { if (prefs.plainWallpaper) "Plain" else "Landscape photographs" }) {
            prefs.plainWallpaper = !prefs.plainWallpaper
        }
        row("Wallpaper rotation", { if (prefs.dailyRotation) "Daily" else "Off" }) {
            prefs.dailyRotation = !prefs.dailyRotation
        }
        row("Weather location", { prefs.city.name }) {
            prefs.city = CITIES[(CITIES.indexOf(prefs.city) + 1) % CITIES.size]
        }
        row("Clock", ::clockValue, chevron = false) { prefs.clock24 = !prefs.clock24 }
        row("App order & visibility", { "${APPS.size - prefs.hidden.size} shown" }) {
            order.getChildAt(0)?.requestFocus()
        }
        fireHomeRow()

        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        pane.addView(text("APP ORDER & VISIBILITY", Fonts.sansMedium, 16, 0xB3F3EFE8.toInt()).apply { letterSpacing = .18f })
        pane.addView(text("OK shows or hides  ·  Rewind / Fast-forward moves", Fonts.sansLight, 20, 0x99F3EFE8.toInt()),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = u(14); bottomMargin = u(22) })
        order = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        pane.addView(order)
        root.addView(pane, lp(u(760), -2, Gravity.TOP or Gravity.END).apply { rightMargin = u(112); topMargin = u(262) })
        buildOrder()

        root.addView(text("OK select  ·  BACK return", Fonts.sans, 18, 0x99F3EFE8.toInt()),
            lp(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = u(112); bottomMargin = u(80) })
        root.clipChildren = false
        setContentView(root)
        list.getChildAt(0).requestFocus()
    }

    private fun clockValue(): CharSequence = SpannableStringBuilder().apply {
        listOf("12-hour" to !prefs.clock24, "24-hour" to prefs.clock24).forEachIndexed { i, (s, on) ->
            if (i > 0) append("    ")
            val start = length; append(s)
            if (on) setSpan(UnderlineSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            else setSpan(ForegroundColorSpan(0x80888888.toInt()), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** One settings row: label left, italic value right, light plate when focused. */
    private fun row(label: String, value: () -> CharSequence, chevron: Boolean = true, onOk: () -> Unit) {
        val r = surface(u(86), u(28))
        val name = text(label, Fonts.sans, 26)
        val v = text("", Fonts.italic, 30)
        r.addView(name, lp(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        r.addView(v, lp(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        val refresh = { v.text = if (chevron) "${value()}  ›" else value() }
        refresh(); refreshers += refresh
        r.setOnFocusChangeListener { view, f -> plate(view, f, name, v) }
        r.setOnClickListener { onOk(); refreshers.forEach { it() } }
        list.addView(r, LinearLayout.LayoutParams(-1, u(86)))
    }

    private fun fireHomeRow() {
        val r = surface(u(86), u(28))
        r.background = rounded(0, u(8), u(2), 0x66F3EFE8)
        val name = text("Open Fire TV home", Fonts.sans, 26)
        val icon = ImageView(this).apply { setImageResource(R.drawable.ic_external) }
        r.addView(name, lp(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        r.addView(icon, lp(u(32), u(32), Gravity.END or Gravity.CENTER_VERTICAL))
        r.setOnFocusChangeListener { view, f ->
            plate(view, f, name); icon.setColorFilter(if (f) DARK else INK)
            if (!f) view.background = rounded(0, u(8), u(2), 0x66F3EFE8)
        }
        r.setOnClickListener {
            prefs.bypassFireHome = true
            runCatching {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(FIRE_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { launchIntent(this, FIRE_HOME)?.let(::startActivity) }
        }
        list.addView(r, LinearLayout.LayoutParams(-1, u(86)).apply { topMargin = u(26) })
    }

    private fun buildOrder() {
        val keep = order.findFocus()?.tag as? String
        order.removeAllViews()
        prefs.order.forEachIndexed { i, app ->
            val shown = app.pkg !in prefs.hidden
            val r = surface(u(64), u(22)).apply { tag = app.pkg }
            val line = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val num = text("${i + 1}", Fonts.serif, 28, 0xB3F3EFE8.toInt())
            line.addView(num, LinearLayout.LayoutParams(u(44), -2))
            line.addView(View(this).apply { setBackgroundColor(app.tint) }, LinearLayout.LayoutParams(u(22), u(2)).apply { marginEnd = u(20) })
            val name = text(app.name, Fonts.sans, 24, if (shown) INK else 0x80F3EFE8.toInt())
            line.addView(name)
            r.addView(line, lp(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
            val state = text(if (shown) "SHOWN" else "HIDDEN", Fonts.sansMedium, 15, if (shown) 0xB3F3EFE8.toInt() else 0x66F3EFE8)
                .apply { letterSpacing = .16f }
            r.addView(state, lp(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
            r.setOnFocusChangeListener { view, f -> plate(view, f, num, name, state) }
            r.setOnClickListener {
                prefs.hidden = if (shown) prefs.hidden + app.pkg else prefs.hidden - app.pkg
                buildOrder(); refreshers.forEach { it() }
            }
            r.setOnKeyListener { _, code, e ->
                val dir = when (code) { KeyEvent.KEYCODE_MEDIA_REWIND -> -1; KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> 1; else -> 0 }
                if (dir == 0 || e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener dir != 0
                val o = prefs.order.toMutableList(); val j = i + dir
                if (j in o.indices) { o.add(j, o.removeAt(i)); prefs.order = o; buildOrder() }
                true
            }
            order.addView(r, LinearLayout.LayoutParams(-1, u(64)))
        }
        keep?.let { k -> (0 until order.childCount).map(order::getChildAt).firstOrNull { it.tag == k }?.requestFocus() }
    }

    private fun surface(h: Int, pad: Int) = FrameLayout(this).apply {
        isFocusable = true; minimumHeight = h; setPadding(pad, 0, pad, 0)
        background = divider()
    }

    private fun divider() = android.graphics.drawable.LayerDrawable(arrayOf(rounded(0x24F3EFE8, 0))).apply {
        setLayerInset(0, 0, 0, 0, 0); setLayerHeight(0, 1); setLayerGravity(0, Gravity.BOTTOM)
    }

    private fun plate(view: View, focused: Boolean, vararg labels: TextView) {
        view.background = if (focused) rounded(0xEBF3EFE8.toInt(), u(8)) else divider()
        labels.forEach { it.setTextColor(if (focused) DARK else INK) }
        view.animateFocus(focused, 1f, 0f, shiftX = u(10).toFloat())
    }
}
