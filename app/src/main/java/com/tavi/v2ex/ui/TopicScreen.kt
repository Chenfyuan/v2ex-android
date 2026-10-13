package com.tavi.v2ex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tavi.v2ex.data.Reply
import com.tavi.v2ex.data.Topic

class TopicState(preview: Topic?) {
    val topic = Loadable<Topic>().apply { data = preview?.takeIf { it.contentHtml.isNotEmpty() || it.member != null } }
    val replies = Loadable<List<Reply>>()
    val list = LazyListState()
    var shown by mutableIntStateOf(PAGE)
    companion object { const val PAGE = 50 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(nav: Navigator, entry: Entry, id: Long, preview: Topic?) {
    val s = entry.state("topic") { TopicState(preview) }
    val api = nav.api
    fun refresh(force: Boolean) {
        entry.scope.load(s.topic, force) { api.topic(id, force) }
        entry.scope.load(s.replies) { api.replies(id, force) }
    }
    LaunchedEffect(Unit) { if (s.replies.data == null) refresh(false) }
    val url = "https://www.v2ex.com/t/$id"
    val t = s.topic.data
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(t?.node?.title ?: "主题", maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = t?.node != null) { t?.node?.let { nav.push(Route.NodeR(it.name, it.title)) } }) },
            navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                IconButton(onClick = { nav.share(t?.title ?: "", url) }) { Icon(Icons.Outlined.Share, "分享") }
                IconButton(onClick = { nav.openExternal(url) }) { Icon(Icons.Outlined.OpenInBrowser, "在浏览器打开") }
            },
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                t == null && s.topic.error != null -> CenterMessage(s.topic.error!!, "重试") { refresh(false) }
                t == null -> Loading()
                else -> PullToRefreshBox(isRefreshing = s.topic.refreshing, onRefresh = { refresh(true) }) {
                    TopicDetail(
                        t, s.replies.data, s.replies.loading, s.replies.error, s.shown, s.list,
                        loadImages = nav.prefs.loadImages,
                        onMore = { s.shown += TopicState.PAGE },
                        onRetryReplies = { entry.scope.load(s.replies) { api.replies(id) } },
                        onLink = nav::openUrl, onImage = { nav.push(Route.Image(it)) },
                        onMember = { nav.push(Route.MemberR(it)) },
                    )
                }
            }
        }
    }
}

@Composable
fun TopicDetail(
    t: Topic, replies: List<Reply>?, repliesLoading: Boolean, repliesError: String?, shown: Int,
    list: LazyListState, loadImages: Boolean,
    onMore: () -> Unit, onRetryReplies: () -> Unit,
    onLink: (String) -> Unit, onImage: (String) -> Unit, onMember: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val visible = replies?.take(shown) ?: emptyList()
    val reachedEnd by remember { derivedStateOf {
        val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= list.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(reachedEnd, replies?.size) { if (reachedEnd && replies != null && shown < replies.size) onMore() }

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "head") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(t.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                t.member?.let { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onMember(m.username) }) {
                        Avatar(m.avatar, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(m.username, style = MaterialTheme.typography.labelLarge)
                            Text("${relTime(t.created)} · ${t.replies} 条回复", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
                if (t.contentHtml.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    HtmlContent(t.contentHtml, onLink, onImage, loadImages)
                }
                t.supplements.forEachIndexed { i, sup ->
                    Spacer(Modifier.height(12.dp))
                    Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("附言 ${i + 1} · ${relTime(sup.created)}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            HtmlContent(sup.contentHtml, onLink, onImage, loadImages, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp), thickness = 8.dp, color = cs.surfaceContainerLow)
            Text(
                if (replies != null) "${replies.size} 条回复" else "回复",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp),
            )
        }
        items(visible, key = { it.id }) { r -> ReplyRow(r, r.member?.username == t.member?.username, loadImages, onLink, onImage, onMember) }
        item(key = "foot") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                when {
                    repliesLoading -> CircularProgressIndicator(Modifier.size(28.dp))
                    repliesError != null -> TextButton(onClick = onRetryReplies) { Text("回复加载失败，点击重试") }
                    replies != null && shown < replies.size -> TextButton(onClick = onMore) { Text("加载更多回复") }
                    replies.isNullOrEmpty() -> Text("还没有回复", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    else -> Text("已经到底了", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(r: Reply, isOp: Boolean, loadImages: Boolean, onLink: (String) -> Unit, onImage: (String) -> Unit, onMember: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(r.member?.avatar, 32.dp, Modifier.clickable { r.member?.let { onMember(it.username) } })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.member?.username ?: "", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { r.member?.let { onMember(it.username) } })
                if (isOp) NodeLabel("楼主")
                Text(relTime(r.created), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("#${r.floor}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            HtmlContent(r.contentHtml, onLink, onImage, loadImages, style = MaterialTheme.typography.bodyMedium)
        }
    }
    HorizontalDivider(Modifier.padding(start = 58.dp), color = cs.outlineVariant.copy(alpha = 0.4f))
}
