package com.mrstu82.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS will not let a third-party app be the default home, so when enabled in
 * Accessibility this brings our launcher forward when Home is pressed and, as a backstop
 * (Fire OS key policy still starts its own home after the consumed key), at once whenever the
 * Fire TV home appears. "Open Fire TV home" in Settings sets a bypass that lasts until another app is opened.
 */
class HomeButtonService : AccessibilityService() {
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return false
        // A real Home press always means tvhome: clear any bypass so the window backstop below
        // also redirects when Fire OS policy starts its own home on top of ours.
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) { Prefs(this).bypassFireHome = false; goHome() }
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        val prefs = Prefs(this)
        if (pkg == FIRE_HOME && !prefs.bypassFireHome) goHome()
        if (pkg != FIRE_HOME && pkg != packageName) prefs.bypassFireHome = false
    }

    private fun goHome() = startActivity(Intent(this, HomeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))

    override fun onInterrupt() {}
}
