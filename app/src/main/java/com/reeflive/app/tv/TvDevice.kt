package com.reeflive.app.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** True on Fire TV / Android TV / Google TV (TV UI mode, leanback, or Amazon's Fire TV feature flag). */
fun isTv(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val pm = context.packageManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("amazon.hardware.fire_tv")
}
