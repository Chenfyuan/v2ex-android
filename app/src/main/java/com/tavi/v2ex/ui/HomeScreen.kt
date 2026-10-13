package com.tavi.v2ex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavi.v2ex.data.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private data class Dest(val tab: HomeTab, val selected: ImageVector, val unselected: ImageVector)

private fun destFor(tab: HomeTab) = when (tab) {
    HomeTab.HOT -> Dest(tab, Icons.Filled.LocalFireDepartment, Icons.Outlined.LocalFireDepartment)
    HomeTab.LATEST -> Dest(tab, Icons.Filled.Schedule, Icons.Outlined.Schedule)
    HomeTab.NODES -> Dest(tab, Icons.Filled.GridView, Icons.Outlined.GridView)
    HomeTab.LIBRARY -> Dest(tab, Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks)
}

private val FloatingBarReserve = 88.dp

class HomeState(initialTabs: List<HomeTab>, val initialPage: Int) {
    val haze = HazeState()
    val hot = Loadable<List<Topic>>()
    val latest = Loadable<List<Topic>>()
    val nodes = Loadable<List<Node>>()
    var tabs by mutableStateOf(initialTabs)
    val lists = List(8) { LazyListState() }
    var query by mutableStateOf("")
    var latestPage by mutableIntStateOf(1)
    var latestHasMore by mutableStateOf(false)
    var latestLoadingMore by mutableStateOf(false)
    var latestMoreError by mutableStateOf<String?>(null)
}

/** 最新：网页增强时走 /recent?p=N，失败回退 latest.json */
fun loadLatest(s: HomeState, nav: Navigator, entry: Entry, force: Boolean) {
    entry.scope.load(s.latest, force) {
        s.latestMoreError = null
        if (nav.prefs.webPaging) {
            runCatching { nav.api.recentWeb(1, force) }.getOrNull()?.let {
                s.latestPage = 1; s.latestHasMore = true
                return@load it
            }
        }
        s.latestPage = 1; s.latestHasMore = false
        nav.api.latest(force)
    }
}

fun loadMoreLatest(s: HomeState, nav: Navigator, entry: Entry) {
    if (!s.latestHasMore || s.latestLoadingMore || s.latestMoreError != null) return
    s.latestLoadingMore = true
    entry.scope.launch {
        runCatching { nav.api.recentWeb(s.latestPage + 1) }
            .onSuccess { l ->
                val old = s.latest.data ?: emptyList()
                val ids = old.map { it.id }.toHashSet()
                val fresh = l.filter { it.id !in ids }
                s.latestPage += 1
                s.latestHasMore = l.isNotEmpty()
                s.latest.data = old + fresh
            }
            .onFailure { s.latestMoreError = it.message ?: "加载失败" }
        s.latestLoadingMore = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Navigator, entry: Entry) {
    val tabs = nav.prefs.homeTabs()
    val s = entry.state("home") { HomeState(tabs, nav.prefs.defaultTabIndex().coerceIn(0, (tabs.size - 1).coerceAtLeast(0))) }
    LaunchedEffect(nav.prefs.homeOrder) {
        val neu = nav.prefs.homeTabs()
        if (neu.map { it.id } != s.tabs.map { it.id }) s.tabs = neu
    }
    val pager = rememberPagerState(initialPage = s.initialPage) { s.tabs.size.coerceAtLeast(1) }
    val api = nav.api
    val blockedUsers by nav.dao.blockedUsers().collectAsState(emptyList())
    val blockedNodes by nav.dao.blockedNodes().collectAsState(emptyList())
    val userSet = remember(blockedUsers) { blockedUsers.toSet() }
    val nodeSet = remember(blockedNodes) { blockedNodes.map { it.name }.toSet() }

    LaunchedEffect(Unit) {
        if (s.hot.data == null) entry.scope.load(s.hot) { api.hot() }
        nav.refreshNoticeCount()
    }
    LaunchedEffect(pager.currentPage, s.tabs) {
        when (s.tabs.getOrNull(pager.currentPage)) {
            HomeTab.LATEST -> if (s.latest.data == null) loadLatest(s, nav, entry, false)
            HomeTab.NODES -> if (s.nodes.data == null) entry.scope.load(s.nodes) { api.allNodes() }
            else -> Unit
        }
    }
    val scope = rememberCoroutineScope()
    val title = s.tabs.getOrNull(pager.currentPage)?.label ?: "V2EX"
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { nav.push(Route.Search) }) { Icon(Icons.Outlined.Search, "搜索") }
                        if (HomeTab.LIBRARY !in s.tabs) {
                            IconButton(onClick = { nav.push(Route.Library) }) { Icon(Icons.Outlined.Bookmarks, "收藏与历史") }
                        }
                        if (nav.prefs.token != null) IconButton(onClick = { nav.push(Route.Notices) }) {
                            BadgedIcon(nav.noticeCount) { Icon(Icons.Outlined.Notifications, "提醒") }
                        }
                        IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Outlined.Settings, "设置") }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
        ) { pad ->
            HorizontalPager(
                state = pager,
                modifier = Modifier.padding(pad).fillMaxSize().hazeSource(s.haze),
                beyondViewportPageCount = 1,
                key = { s.tabs.getOrNull(it)?.id ?: it },
            ) { page ->
                when (s.tabs.getOrNull(page)) {
                    HomeTab.HOT -> TopicListPane(
                        s.hot, s.lists[0], nav, userSet, nodeSet,
                        { entry.scope.load(s.hot, true) { api.hot(force = true) } },
                        { entry.scope.load(s.hot) { api.hot() } },
                    )
                    HomeTab.LATEST -> TopicListPane(
                        s.latest, s.lists[1], nav, userSet, nodeSet,
                        { loadLatest(s, nav, entry, true) },
                        { loadLatest(s, nav, entry, false) },
                        paging = PagingUi(s.latestHasMore, s.latestLoadingMore, s.latestMoreError,
                            onMore = { loadMoreLatest(s, nav, entry) },
                            onRetry = { s.latestMoreError = null; loadMoreLatest(s, nav, entry) }),
                    )
                    HomeTab.NODES -> NodesPane(s, nav, nodeSet) {
                        entry.scope.load(s.nodes, true) { api.allNodes(force = true) }
                    }
                    HomeTab.LIBRARY -> HomeLibraryPane(nav, userSet, nodeSet)
                    null -> CenterMessage("未知标签")
                }
            }
        }
        FloatingBottomBar(
            tabs = s.tabs,
            selected = pager.currentPage.coerceIn(0, (s.tabs.size - 1).coerceAtLeast(0)),
            haze = s.haze,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun FloatingBottomBar(
    tabs: List<HomeTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    haze: HazeState? = null,
) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(28.dp)
    val tint = cs.surfaceContainer.copy(alpha = if (dark) 0.55f else 0.62f)
    val fallback = cs.surfaceContainer.copy(alpha = 0.94f)
    val glassModifier = if (haze != null) Modifier.hazeEffect(haze) {
        blurRadius = 28.dp
        tints = listOf(HazeTint(tint))
        noiseFactor = 0.04f
        fallbackTint = HazeTint(fallback)
    } else Modifier.background(fallback)
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp + bottomInset)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(shape)
            .then(glassModifier)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.10f else 0.35f),
                    0.45f to Color.White.copy(alpha = 0f),
                ),
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.28f else 0.75f),
                        Color.White.copy(alpha = if (dark) 0.06f else 0.20f),
                    ),
                ),
                shape,
            ),
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
            modifier = Modifier.height(64.dp),
        ) {
            tabs.forEachIndexed { i, tab ->
                val d = destFor(tab)
                NavigationBarItem(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    icon = { Icon(if (selected == i) d.selected else d.unselected, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(indicatorColor = cs.secondaryContainer.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListPane(
    l: Loadable<List<Topic>>, state: LazyListState, nav: Navigator,
    blockedUsers: Set<String>, blockedNodes: Set<String>,
    onRefresh: () -> Unit, onRetry: () -> Unit,
    paging: PagingUi? = null,
) {
    val data = l.data?.filterBlocked(blockedUsers, blockedNodes)
    if (paging != null) {
        val atEnd by remember(state) { derivedStateOf {
            (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 4
        } }
        LaunchedEffect(atEnd, paging.hasMore, paging.loading, paging.error, l.data?.size) {
            if (atEnd && paging.hasMore && !paging.loading && paging.error == null && l.data != null) paging.onMore()
        }
    }
    when {
        l.data == null && l.error != null -> CenterMessage(l.error!!, "重试", onRetry)
        l.data == null -> Loading()
        else -> PullToRefreshBox(isRefreshing = l.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            TopicList(
                data ?: emptyList(), state,
                onTopic = { nav.push(Route.TopicR(it.id, it)) },
                onMember = { nav.push(Route.MemberR(it)) },
                showThumb = nav.prefs.loadImages,
                footer = {
                    when {
                        paging?.loading == true -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                        paging?.error != null -> ListEndFooter(paging.error, "重试", paging.onRetry)
                        paging?.hasMore == true -> ListEndFooter("继续上滑加载更多")
                        else -> ListEndFooter("已显示全部 ${data?.size ?: 0} 条。V2EX 接口不支持继续翻页，下拉可刷新获取最新", "刷新", onRefresh)
                    }
                },
            )
        }
    }
}

@Composable
fun TopicList(
    topics: List<Topic>, state: LazyListState = LazyListState(),
    onTopic: (Topic) -> Unit, onMember: (String) -> Unit, showNode: Boolean = true,
    showThumb: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    bottomSpace: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(bottom = if (bottomSpace) FloatingBarReserve else 0.dp),
    ) {
        if (header != null) item { header() }
        items(topics, key = { it.id }) { t ->
            TopicRow(t, { onTopic(t) }, onMember, showNode, showThumb)
            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        if (topics.isEmpty()) item { Box(Modifier.fillParentMaxHeight(0.6f)) { CenterMessage("这里还没有主题") } }
        else if (footer != null) item(key = "footer") { footer() }
    }
}

@Composable
fun ListEndFooter(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesPane(s: HomeState, nav: Navigator, blockedNodes: Set<String>, onRefresh: () -> Unit) {
    val all = s.nodes.data?.filterBlockedNodes(blockedNodes)
    when {
        s.nodes.data == null && s.nodes.error != null -> CenterMessage(s.nodes.error!!, "重试", onRefresh)
        s.nodes.data == null -> Loading()
        else -> {
            val q = s.query.trim()
            val shown = remember(all, q) {
                val sorted = (all ?: emptyList()).sortedByDescending { it.topics }
                if (q.isEmpty()) sorted else sorted.filter { it.title.contains(q, true) || it.name.contains(q, true) }
            }
            PullToRefreshBox(isRefreshing = s.nodes.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = s.lists[2],
                    contentPadding = PaddingValues(bottom = FloatingBarReserve),
                ) {
                    item {
                        OutlinedTextField(
                            value = s.query, onValueChange = { s.query = it }, singleLine = true,
                            placeholder = { Text("搜索节点") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(shown, key = { it.name }) { n ->
                        ListItem(
                            headlineContent = { Text(n.title) },
                            supportingContent = { Text(n.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { Avatar(n.avatar, 36.dp) },
                            trailingContent = { Text("${n.topics}", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.clickable { nav.push(Route.NodeR(n.name, n.title)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeLibraryPane(nav: Navigator, blockedUsers: Set<String>, blockedNodes: Set<String>) {
    val topics by nav.dao.topicBookmarks().collectAsState(emptyList())
    val filtered = remember(topics, blockedUsers, blockedNodes) {
        topics.map { it.toTopic() }.filterBlocked(blockedUsers, blockedNodes)
    }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { nav.push(Route.Library) }, Modifier.padding(horizontal = 8.dp)) {
            Text("管理收藏与历史")
        }
        TopicList(
            filtered, onTopic = { nav.push(Route.TopicR(it.id, it)) },
            onMember = { nav.push(Route.MemberR(it)) }, showThumb = nav.prefs.loadImages,
        )
    }
}

class PagingUi(
    val hasMore: Boolean, val loading: Boolean, val error: String?,
    val onMore: () -> Unit, val onRetry: () -> Unit,
)
