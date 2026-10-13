package com.tavi.v2ex.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import com.tavi.v2ex.data.Prefs
import com.tavi.v2ex.data.Topic
import com.tavi.v2ex.data.V2exApi
import kotlinx.coroutines.*

sealed interface Route {
    data object Home : Route
    data class TopicR(val id: Long, val preview: Topic? = null) : Route
    data class NodeR(val name: String, val title: String = name) : Route
    data class MemberR(val name: String) : Route
    data object Notices : Route
    data object Settings : Route
    data class Image(val url: String) : Route
}

class Entry(val route: Route) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val states = HashMap<String, Any>()
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> state(key: String, init: () -> T): T = states.getOrPut(key, init) as T
}

class Loadable<T> {
    var data by mutableStateOf<T?>(null)
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

fun <T> CoroutineScope.load(l: Loadable<T>, refresh: Boolean = false, block: suspend () -> T) {
    if (l.loading) return
    l.loading = true; l.refreshing = refresh; l.error = null
    launch {
        try { l.data = block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { l.error = e.message ?: "加载失败" }
        finally { l.loading = false; l.refreshing = false }
    }
}

class Navigator(val api: V2exApi, val prefs: Prefs, private val context: Context) {
    val stack = mutableStateListOf(Entry(Route.Home))
    val current get() = stack.last()
    var forward by mutableStateOf(true); private set

    fun push(r: Route) { forward = true; stack.add(Entry(r)) }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex).scope.cancel() } }

    /** 站内链接在 App 里打开，其他交给浏览器 */
    fun openUrl(url: String) {
        routeFor(url)?.let { push(it); return }
        openExternal(url)
    }

    fun openExternal(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { }
    }

    fun share(title: String, url: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        context.startActivity(Intent.createChooser(i, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        private val host = Regex("^https?://(www\\.)?v2ex\\.com", RegexOption.IGNORE_CASE)
        fun routeFor(url: String): Route? {
            if (!host.containsMatchIn(url)) return null
            val path = url.replace(host, "").substringBefore('?').substringBefore('#')
            Regex("^/t/(\\d+)").find(path)?.let { return Route.TopicR(it.groupValues[1].toLong()) }
            Regex("^/(member|u)/([\\w-]+)").find(path)?.let { return Route.MemberR(it.groupValues[2]) }
            Regex("^/go/([\\w-]+)").find(path)?.let { return Route.NodeR(it.groupValues[1]) }
            return null
        }
    }
}
