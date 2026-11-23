package com.facilityhub.app.data

import android.content.Context
import android.content.pm.ApplicationInfo

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("facilityhub_session", Context.MODE_PRIVATE)

    // FH-009 / R7: solo en compilaciones de depuracion se registran metricas de red (ver NetworkProvider).
    val depurable: Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    var token: String?
        get() = prefs.getString("token", null)
        set(value) { prefs.edit().putString("token", value).apply() }

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) { prefs.edit().putString("user_name", value).apply() }

    var role: String?
        get() = prefs.getString("role", null)
        set(value) { prefs.edit().putString("role", value).apply() }

    fun clear() = prefs.edit().clear().apply()
}
