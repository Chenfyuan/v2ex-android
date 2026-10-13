package com.tavi.v2ex.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var token by mutableStateOf(sp.getString("token", null)?.takeIf { it.isNotBlank() }); private set
    var myUsername by mutableStateOf(sp.getString("me", null)); private set
    var themeMode by mutableIntStateOf(sp.getInt("theme", 0)); private set
    var dynamicColor by mutableStateOf(sp.getBoolean("dynamic", true)); private set
    var loadImages by mutableStateOf(sp.getBoolean("images", true)); private set
    /** 正文相对放大倍数，1f = 默认 */
    var fontScale by mutableFloatStateOf(sp.getFloat("fontScale", 1f)); private set
    var lineHeight by mutableFloatStateOf(sp.getFloat("lineHeight", 1.5f)); private set
    /** 0 常规 1 中等 2 半粗 */
    var fontWeight by mutableIntStateOf(sp.getInt("fontWeight", 0)); private set

    fun updateToken(v: String?) {
        token = v?.trim()?.takeIf { it.isNotEmpty() }
        sp.edit().putString("token", token).apply()
        if (token == null) updateMe(null)
    }
    fun updateMe(v: String?) { myUsername = v; sp.edit().putString("me", v).apply() }
    fun updateTheme(v: Int) { themeMode = v; sp.edit().putInt("theme", v).apply() }
    fun updateDynamic(v: Boolean) { dynamicColor = v; sp.edit().putBoolean("dynamic", v).apply() }
    fun updateImages(v: Boolean) { loadImages = v; sp.edit().putBoolean("images", v).apply() }
    fun updateFontScale(v: Float) { fontScale = v; sp.edit().putFloat("fontScale", v).apply() }
    fun updateLineHeight(v: Float) { lineHeight = v; sp.edit().putFloat("lineHeight", v).apply() }
    fun updateFontWeight(v: Int) { fontWeight = v; sp.edit().putInt("fontWeight", v).apply() }
}
