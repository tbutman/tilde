package com.tbutman.tilde

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Applies the chosen theme (Settings → Theme) before any screen opens. Dark is the default. */
class TildeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme(Prefs(this).theme)
    }

    companion object {
        fun applyTheme(theme: String) = AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Prefs.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }
}
