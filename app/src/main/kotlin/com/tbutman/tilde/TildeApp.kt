package com.tbutman.tilde

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the chosen theme (Settings → Theme) before any screen opens, and keeps track of whether a
 * Tilde screen is open, for the tap service (see TapGate).
 */
class TildeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme(Prefs(this).theme)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed++ }
            override fun onActivityPaused(activity: Activity) {
                resumed--
                // Whatever changed in the app (cards, what's shared), the widgets show it.
                TildeWidget.updateAll(activity.applicationContext)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        /** How many Tilde screens are in front (0 or 1, briefly 2 while switching). */
        @Volatile private var resumed = 0

        /** Whether a Tilde screen is open and in front. */
        val open: Boolean get() = resumed > 0

        fun applyTheme(theme: String) = AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Prefs.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }
}
