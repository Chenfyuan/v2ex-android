package com.tavi.v2ex.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tavi.v2ex.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, nav: Navigator, actions: @Composable RowScope.() -> Unit = {}) =
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = actions,
    )

// ---------------- 节点 ----------------
class NodeState {
    val info = Loadable<Node>()
    val topics = Loadable<List<Topic>>()
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    val list = LazyListState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScreen(nav: Navigator, entry: Entry, name: String, title: String) {
    val s = entry.state("node") { NodeState() }
    val api = nav.api
    fun refresh(force: Boolean) {
        entry.scope.load(s.info) { api.node(name) }
        entry.scope.load(s.topics, force) {
            val (l, more) = api.nodeTopics(name, 1, force); s.page = 1; s.hasMore = more; l
        }
    }
    LaunchedEffect(Unit) { if (s.topics.data == null) refresh(false) }
    val atEnd by remember { derivedStateOf {
        (s.list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= s.list.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(atEnd) {
        if (atEnd && s.hasMore && !s.loadingMore && s.topics.data != null) {
            s.loadingMore = true
            entry.scope.launch {
                runCatching { api.nodeTopics(name, s.page + 1) }.onSuccess { (l, more) ->
                    s.page += 1; s.hasMore = more; s.topics.data = (s.topics.data ?: emptyList()) + l.filter { n -> s.topics.data?.none { it.id == n.id } != false }
                }.onFailure { s.hasMore = false }
                s.loadingMore = false
            }
        }
    }
    val info = s.info.data
    Scaffold(topBar = {
        BackBar(info?.title ?: title, nav) {
            IconButton(onClick = { nav.openExternal("https://www.v2ex.com/go/$name") }) { Icon(Icons.Outlined.OpenInBrowser, "在浏览器打开") }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val data = s.topics.data
            when {
                data == null && s.topics.error != null -> CenterMessage(s.topics.error!!, "重试") { refresh(false) }
                data == null -> Loading()
                else -> PullToRefreshBox(isRefreshing = s.topics.refreshing, onRefresh = { refresh(true) }) {
                    TopicList(data, s.list, { nav.push(Route.TopicR(it.id, it)) }, { nav.push(Route.MemberR(it)) }, showNode = false, bottomSpace = false,
                        header = {
                            Column(Modifier.padding(16.dp, 8.dp)) {
                                if (info != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(info.avatar, 48.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(info.title, style = MaterialTheme.typography.titleMedium)
                                            Text("${info.topics} 个主题 · ${info.stars} 人收藏", style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (info.header.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        HtmlContent(info.header, nav::openUrl, { nav.push(Route.Image(it)) }, nav.prefs.loadImages,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (nav.prefs.token == null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("未设置令牌时只能看到最新 10 个主题，在设置里填写令牌后可以继续往下翻。",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        })
                }
            }
        }
    }
}

// ---------------- 会员 ----------------
class MemberState { val member = Loadable<Member>(); val topics = Loadable<List<Topic>>(); val list = LazyListState() }

@Composable
fun MemberScreen(nav: Navigator, entry: Entry, name: String) {
    val s = entry.state("member") { MemberState() }
    LaunchedEffect(Unit) {
        if (s.member.data == null) {
            entry.scope.load(s.member) { nav.api.member(name) }
            entry.scope.load(s.topics) { nav.api.memberTopics(name) }
        }
    }
    Scaffold(topBar = {
        BackBar(name, nav) {
            IconButton(onClick = { nav.openExternal("https://www.v2ex.com/member/$name") }) { Icon(Icons.Outlined.OpenInBrowser, "在浏览器打开") }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val m = s.member.data
            when {
                m == null && s.member.error != null -> CenterMessage(s.member.error!!)
                m == null -> Loading()
                else -> TopicList(s.topics.data ?: emptyList(), s.list, { nav.push(Route.TopicR(it.id, it)) }, {}, bottomSpace = false, header = {
                    MemberHeader(m, nav::openUrl)
                    if (s.topics.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    s.topics.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                })
            }
        }
    }
}

@Composable
fun MemberHeader(m: Member, onLink: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(m.avatar, 88.dp)
        Spacer(Modifier.height(12.dp))
        Text(m.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (m.tagline.isNotBlank()) Text(m.tagline, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val joined = if (m.created > 0) "，加入于 " + SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(m.created * 1000)) else ""
        Text("V2EX 第 ${m.id} 号会员$joined", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        val links = buildList {
            if (m.location.isNotBlank()) add(m.location to null)
            if (m.website.isNotBlank()) add(m.website to (if (m.website.startsWith("http")) m.website else "https://${m.website}"))
            if (m.github.isNotBlank()) add("GitHub: ${m.github}" to "https://github.com/${m.github}")
        }
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { (label, url) -> AssistChip(onClick = { url?.let(onLink) }, label = { Text(label, maxLines = 1) }) }
            }
        }
        if (m.bio.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(m.bio, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(8.dp))
        Text("最近主题", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
    }
}

// ---------------- 提醒（API 2.0） ----------------
class NoticeState {
    val items = Loadable<List<Notice>>()
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    val list = LazyListState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(nav: Navigator, entry: Entry) {
    val s = entry.state("notice") { NoticeState() }
    val api = nav.api
    val snack = remember { SnackbarHostState() }
    fun refresh(force: Boolean) = entry.scope.load(s.items, force) {
        api.notifications(1, force).also { s.page = 1; s.hasMore = it.isNotEmpty() }
    }
    LaunchedEffect(Unit) { if (s.items.data == null) refresh(false) }
    val atEnd by remember { derivedStateOf {
        (s.list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= s.list.layoutInfo.totalItemsCount - 2
    } }
    LaunchedEffect(atEnd) {
        if (atEnd && s.hasMore && !s.loadingMore && s.items.data != null) {
            s.loadingMore = true
            entry.scope.launch {
                runCatching { api.notifications(s.page + 1) }.onSuccess { l ->
                    s.page += 1; s.hasMore = l.isNotEmpty(); s.items.data = (s.items.data ?: emptyList()) + l
                }.onFailure { s.hasMore = false }
                s.loadingMore = false
            }
        }
    }
    Scaffold(topBar = { BackBar("提醒", nav) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val data = s.items.data
            when {
                data == null && s.items.error != null -> CenterMessage(s.items.error!!, "重试") { refresh(false) }
                data == null -> Loading()
                else -> PullToRefreshBox(isRefreshing = s.items.refreshing, onRefresh = { refresh(true) }) {
                    LazyColumn(Modifier.fillMaxSize(), state = s.list) {
                        items(data, key = { it.id }) { n ->
                            NoticeRow(n, nav.prefs.loadImages, nav::openUrl, { nav.push(Route.Image(it)) }) {
                                entry.scope.launch {
                                    runCatching { api.deleteNotification(n.id) }
                                        .onSuccess { s.items.data = s.items.data?.filter { it.id != n.id } }
                                        .onFailure { snack.showSnackbar(it.message ?: "删除失败") }
                                }
                            }
                        }
                        if (data.isEmpty()) item { Box(Modifier.fillParentMaxHeight(0.7f)) { CenterMessage("没有新提醒") } }
                    }
                }
            }
        }
    }
}

@Composable
fun NoticeRow(n: Notice, loadImages: Boolean, onLink: (String) -> Unit, onImage: (String) -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 4.dp, 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                HtmlContent(n.textHtml, onLink, onImage, loadImages, style = MaterialTheme.typography.bodyMedium)
                Text(relTime(n.created), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "删除提醒", tint = cs.onSurfaceVariant) }
        }
        if (n.payloadHtml.isNotBlank()) {
            Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(end = 12.dp, top = 6.dp)) {
                HtmlContent(n.payloadHtml, onLink, onImage, loadImages, Modifier.fillMaxWidth().padding(10.dp), MaterialTheme.typography.bodyMedium)
            }
        }
    }
    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("删除这条提醒？") }, text = { Text("会同时从 V2EX 网站上删除，无法恢复。") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
    )
}

// ---------------- 设置 ----------------
@Composable
fun SettingsScreen(nav: Navigator, entry: Entry) {
    val p = nav.prefs
    val api = nav.api
    var input by remember { mutableStateOf(p.token ?: "") }
    val me = entry.state("me") { Loadable<Member>() }
    var cacheMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (p.token != null && me.data == null) entry.scope.load(me) { api.me() } }
    Scaffold(topBar = { BackBar("设置", nav) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("账号")
            Text("V2EX 不提供第三方登录。在网站「设置 › 个人访问令牌」里创建一个令牌粘贴到这里，就能看提醒、翻页浏览节点。令牌只保存在本机。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input, onValueChange = { input = it }, singleLine = true, label = { Text("个人访问令牌") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    p.updateToken(input); me.data = null
                    if (p.token != null) entry.scope.load(me) { api.me(force = true) }
                }) { Text("保存并验证") }
                OutlinedButton(onClick = { nav.openExternal("https://www.v2ex.com/settings/tokens") }) { Text("去创建令牌") }
            }
            when {
                me.loading -> Text("正在验证…", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                me.error != null -> Text(me.error!!, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                me.data != null -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me.data!!.avatar, 36.dp); Spacer(Modifier.width(10.dp))
                    Text("已登录：${me.data!!.username}", Modifier.weight(1f))
                    TextButton(onClick = { p.updateToken(null); input = ""; me.data = null }) { Text("退出") }
                }
            }

            SectionTitle("外观")
            val modes = listOf("跟随系统", "浅色", "深色")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, m ->
                    SegmentedButton(selected = p.themeMode == i, onClick = { p.updateTheme(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(m) }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) SwitchRow("动态取色", "跟随壁纸颜色", p.dynamicColor, p::updateDynamic)
            SwitchRow("自动加载图片", "关闭后点按才加载，省流量", p.loadImages, p::updateImages)

            SectionTitle("数据")
            val remain = api.rateRemaining
            Text(
                if (remain != null) "本小时剩余接口次数：$remain / ${api.rateLimit ?: 600}" else "V2EX 限制每个 IP 每小时 600 次请求，本 App 会缓存数据以节省次数。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { api.clearCache(); cacheMsg = "缓存已清除" }) { Text("清除缓存") }
            cacheMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            SectionTitle("关于")
            Text("V2 阅读 0.1.2 · 非官方 V2EX 客户端，数据来自 V2EX 公开接口。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable private fun SectionTitle(t: String) =
    Text(t, Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

@Composable private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.layout { m, c -> val p = 16.dp.roundToPx(); val pl = m.measure(c.copy(maxWidth = c.maxWidth + 2 * p, minWidth = c.minWidth)); layout(c.maxWidth, pl.height) { pl.place(-p, 0) } },
    )

// ---------------- 看图 ----------------
@Composable
fun ImageScreen(nav: Navigator, url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val animScale by animateFloatAsState(scale, label = "zoom")
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = url, contentDescription = "图片", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                        onTap = { nav.pop() })
                }
                .graphicsLayer { scaleX = animScale; scaleY = animScale; translationX = offset.x; translationY = offset.y },
        )
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            IconButton(onClick = { nav.openExternal(url) }) { Icon(Icons.Outlined.OpenInBrowser, "在浏览器打开", tint = Color.White) }
            IconButton(onClick = nav::pop) { Icon(Icons.Filled.Close, "关闭", tint = Color.White) }
        }
    }
}
