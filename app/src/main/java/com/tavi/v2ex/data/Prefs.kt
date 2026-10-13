package com.tavi.v2ex.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var token by mutableStateOf(sp.getString("token", null)?.takeIf { it.isNotBlank() }); private set
    var themeMode by mutableStateOf(sp.getInt("theme", 0)); private set   // 0 跟随系统 1 浅色 2 深色
    var dynamicColor by mutableStateOf(sp.getBoolean("dynamic", true)); private set
    var loadImages by mutableStateOf(sp.getBoolean("images", true)); private set

    fun updateToken(v: String?) { token = v?.trim()?.takeIf { it.isNotEmpty() }; sp.edit().putString("token", token).apply() }
    fun updateTheme(v: Int) { themeMode = v; sp.edit().putInt("theme", v).apply() }
    fun updateDynamic(v: Boolean) { dynamicColor = v; sp.edit().putBoolean("dynamic", v).apply() }
    fun updateImages(v: Boolean) { loadImages = v; sp.edit().putBoolean("images", v).apply() }
}
