package com.tavi.v2ex.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import com.tavi.v2ex.data.LocalDao
import com.tavi.v2ex.data.Prefs
import com.tavi.v2ex.data.Topic
import com.tavi.v2ex.data.V2exApi
import kotlinx.coroutines.*

sealed interface Route {
    data object Home : Route
    data class TopicR(val id: Long, val preview: Topic? = null, val jumpFloor: Int? = null) : Route
    data class NodeR(val name: String, val title: String = name) : Route
    data class MemberR(val name: String) : Route
    data object Notices : Route
    data object Settings : Route
    data object Search : Route
    data object Library : Route
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

class Navigator(
    val api: V2exApi,
    val prefs: Prefs,
    val dao: LocalDao,
    private val context: Context,
) {
    val stack = mutableStateListOf(Entry(Route.Home))
    val current get() = stack.last()
    var forward by mutableStateOf(true); private set
    /** 提醒未读数（有令牌时轮询第一页数量） */
    var noticeCount by mutableIntStateOf(0)

    fun push(r: Route) { forward = true; stack.add(Entry(r)) }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex).scope.cancel() } }

    fun openUrl(url: String, currentTopicId: Long? = null, onFloor: ((Int) -> Unit)? = null) {
        // 同主题楼层锚点
        if (currentTopicId != null && onFloor != null) {
            Regex("""#reply(\d+)$""").find(url)?.let {
                onFloor(it.groupValues[1].toInt()); return
            }
        }
        routeFor(url)?.let { push(it); return }
        openExternal(url)
    }

    fun openExternal(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { }
    }

    fun share(title: String, url: String, node: String? = null, author: String? = null) {
        val body = buildString {
            append(title.trim())
            append('\n')
            if (!author.isNullOrBlank()) append("作者 @$author")
            if (!node.isNullOrBlank()) {
                if (!author.isNullOrBlank()) append(" · ")
                append(node)
            }
            if (!author.isNullOrBlank() || !node.isNullOrBlank()) append('\n')
            append(url)
            append("\n— 来自 V2 阅读")
        }
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, body)
        context.startActivity(Intent.createChooser(i, "分享主题").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun refreshNoticeCount() {
        if (prefs.token == null) { noticeCount = 0; return }
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching { api.notifications(1) }.onSuccess { noticeCount = it.size.coerceAtMost(99) }
        }
    }

    companion object {
        private val host = Regex("^https?://(www\\.)?v2ex\\.com", RegexOption.IGNORE_CASE)
        fun routeFor(url: String): Route? {
            if (!host.containsMatchIn(url)) return null
            val raw = url.replace(host, "")
            val path = raw.substringBefore('?').substringBefore('#')
            val frag = raw.substringAfter('#', "")
            Regex("^/t/(\\d+)").find(path)?.let {
                val floor = Regex("^reply(\\d+)$").find(frag)?.groupValues?.get(1)?.toIntOrNull()
                return Route.TopicR(it.groupValues[1].toLong(), jumpFloor = floor)
            }
            Regex("^/(member|u)/([\\w-]+)").find(path)?.let { return Route.MemberR(it.groupValues[2]) }
            Regex("^/go/([\\w-]+)").find(path)?.let { return Route.NodeR(it.groupValues[1]) }
            return null
        }
    }
}
