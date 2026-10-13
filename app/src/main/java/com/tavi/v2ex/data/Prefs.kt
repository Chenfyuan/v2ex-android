package com.tavi.v2ex.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 首页底栏可选目的地 */
enum class HomeTab(val id: String, val label: String) {
    HOT("hot", "最热"),
    LATEST("latest", "最新"),
    NODES("nodes", "节点"),
    LIBRARY("library", "收藏");

    companion object {
        fun fromId(id: String) = entries.find { it.id == id }
        fun parseOrder(raw: String): List<HomeTab> {
            val parsed = raw.split(',').mapNotNull { fromId(it.trim()) }.distinct()
            return parsed.ifEmpty { listOf(HOT, LATEST, NODES) }
        }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var token by mutableStateOf(sp.getString("token", null)?.takeIf { it.isNotBlank() }); private set
    var myUsername by mutableStateOf(sp.getString("me", null)); private set
    var themeMode by mutableIntStateOf(sp.getInt("theme", 0)); private set
    var dynamicColor by mutableStateOf(sp.getBoolean("dynamic", true)); private set
    var loadImages by mutableStateOf(sp.getBoolean("images", true)); private set
    var fontScale by mutableFloatStateOf(sp.getFloat("fontScale", 1f)); private set
    var lineHeight by mutableFloatStateOf(sp.getFloat("lineHeight", 1.5f)); private set
    var fontWeight by mutableIntStateOf(sp.getInt("fontWeight", 0)); private set
    /** 底栏顺序，逗号分隔 id */
    var homeOrder by mutableStateOf(sp.getString("homeOrder", "hot,latest,nodes")!!); private set
    var defaultTab by mutableStateOf(sp.getString("defaultTab", "hot")!!); private set

    fun homeTabs(): List<HomeTab> = HomeTab.parseOrder(homeOrder)
    fun defaultTabIndex(): Int {
        val tabs = homeTabs()
        val i = tabs.indexOfFirst { it.id == defaultTab }
        return if (i >= 0) i else 0
    }

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
    fun updateHomeOrder(tabs: List<HomeTab>) {
        homeOrder = tabs.joinToString(",") { it.id }
        sp.edit().putString("homeOrder", homeOrder).apply()
        if (HomeTab.fromId(defaultTab) !in tabs) updateDefaultTab(tabs.first().id)
    }
    fun updateDefaultTab(id: String) { defaultTab = id; sp.edit().putString("defaultTab", id).apply() }
}
