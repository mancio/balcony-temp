package com.balcony.temp

import android.content.Context

/**
 * Small wrapper around SharedPreferences that stores the user supplied database key
 * (the Firebase Realtime Database base URL). The key is entered on first run and can
 * be changed from Settings. It is never bundled with the app, so it stays private.
 */
object Prefs {

    private const val FILE = "balcony_prefs"
    private const val KEY_DB = "db_key"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The saved database key, or null when the app has not been configured yet. */
    fun getKey(context: Context): String? =
        prefs(context).getString(KEY_DB, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_DB, key.trim()).apply()
    }

    fun hasKey(context: Context): Boolean = getKey(context) != null
}
