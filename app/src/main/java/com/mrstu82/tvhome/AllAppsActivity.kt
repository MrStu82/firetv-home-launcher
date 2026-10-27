package com.mrstu82.tvhome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ScrollView

class AllAppsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Fonts.load(this)
        val root = FrameLayout(this).apply { setBackgroundColor(DARK) }
        root.addView(text("All apps", Fonts.light, 84), lp(-2, -2).apply { leftMargin = u(112); topMargin = u(88) })
        val grid = GridLayout(this).apply { columnCount = 7; clipChildren = false; clipToPadding = false
            setPadding(0, u(24), 0, u(80)) }
        val seen = mutableSetOf(packageName)
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .filter { seen.add(it.activityInfo.packageName) }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .forEach { ri ->
                val intent = launchIntent(this, ri.activityInfo.packageName) ?: return@forEach
                grid.addView(appTile(intent, ri.loadLabel(packageManager).toString()), GridLayout.LayoutParams().apply {
                    width = u(196); height = u(110); setMargins(0, u(14), u(20), u(14))
                })
            }
        val scroll = ScrollView(this).apply { clipChildren = false; clipToPadding = false; addView(grid) }
        root.addView(scroll, lp(-1, -1).apply { leftMargin = u(112); rightMargin = u(80); topMargin = u(220) })
        setContentView(root)
        grid.getChildAt(0)?.requestFocus()
    }
}
