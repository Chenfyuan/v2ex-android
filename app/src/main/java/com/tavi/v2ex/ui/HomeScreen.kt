package com.tavi.v2ex.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.tavi.v2ex.ui.glass.DampedDragAnimation
import com.tavi.v2ex.ui.glass.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.tavi.v2ex.data.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class Dest(val tab: HomeTab, val selected: ImageVector, val unselected: ImageVector)

private fun destFor(tab: HomeTab) = when (tab) {
    HomeTab.HOT -> Dest(tab, Icons.Filled.LocalFireDepartment, Icons.Outlined.LocalFireDepartment)
    HomeTab.LATEST -> Dest(tab, Icons.Filled.Schedule, Icons.Outlined.Schedule)
    HomeTab.NODES -> Dest(tab, Icons.Filled.GridView, Icons.Outlined.GridView)
    HomeTab.LIBRARY -> Dest(tab, Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks)
}

private val FloatingBarReserve = 96.dp

class HomeState(initialTabs: List<HomeTab>, val initialPage: Int) {
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
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.layerBackdrop(backdrop),
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
                modifier = Modifier.padding(pad).fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = true,
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
            pageOffset = { pager.currentPage + pager.currentPageOffsetFraction },
            backdrop = backdrop,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * iOS 26 风格液态玻璃底栏，基于 Kyant0/AndroidLiquidGlass（backdrop）的 LiquidBottomTabs 示例改编。
 * - 整条胶囊：vibrancy + blur + lens 折射背后内容
 * - 选中胶囊：包住整个「图标+文字」，可按住拖动，松手吸附；与 Pager 滑动同步
 * - API 31 以下无 RenderEffect：自动退化为半透明实色底 + 普通选中胶囊
 */
@Composable
fun FloatingBottomBar(
    tabs: List<HomeTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageOffset: () -> Float = { selected.toFloat() },
    backdrop: Backdrop? = null,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bd = backdrop ?: rememberLayerBackdrop()
    Box(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp + bottomInset)) {
        LiquidTabs(
            selectedTabIndex = { selected },
            pageOffset = pageOffset,
            onTabSelected = onSelect,
            backdrop = bd,
            tabsCount = tabs.size.coerceAtLeast(1),
        ) {
            val cs = MaterialTheme.colorScheme
            tabs.forEachIndexed { i, tab ->
                val d = destFor(tab)
                LiquidTab(onClick = { onSelect(i) }) {
                    Icon(if (selected == i) d.selected else d.unselected, tab.label, tint = cs.onSurface, modifier = Modifier.size(24.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = cs.onSurface, maxLines = 1)
                }
            }
        }
    }
}

private val LocalTabScale = staticCompositionLocalOf { { 1f } }

@Composable
private fun RowScope.LiquidTab(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalTabScale.current
    Column(
        Modifier
            .clip(Capsule())
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer { val s = scale(); scaleX = s; scaleY = s },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun LiquidTabs(
    selectedTabIndex: () -> Int,
    pageOffset: () -> Float,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (dark) Color(0xFF0091FF) else Color(0xFF0088FF)
    val supported = isRenderEffectSupported()
    val container = if (dark) Color(0xFF121212).copy(if (supported) 0.4f else 0.92f)
    else Color(0xFFFAFAFA).copy(if (supported) 0.4f else 0.94f)
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val tabWidth = with(density) { (maxW - 8.dp.toPx()) / tabsCount }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, maxW) {
            derivedStateOf {
                val f = (offsetAnimation.value / maxW).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * f.sign * EaseOut.transform(abs(f)) }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        var dragging by remember { mutableStateOf(false) }
        val last = (tabsCount - 1).toFloat().coerceAtLeast(0.0001f)
        val damped = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..last,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { dragging = true },
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    dragging = false
                    currentIndex = target
                    animateToValue(target.toFloat())
                    onTabSelected(target)
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, drag ->
                    updateValue((targetValue + drag.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, last))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + drag.x) }
                },
            )
        }
        LaunchedEffect(damped) {
            snapshotFlow { selectedTabIndex() }.collectLatest { i ->
                if (i != currentIndex) { currentIndex = i; if (!dragging) damped.animateToValue(i.toFloat()) }
            }
        }
        LaunchedEffect(damped) {
            snapshotFlow { pageOffset() }.collectLatest { p ->
                if (!dragging && abs(p - p.fastRoundToInt()) > 0.001f) damped.updateValue(p)
            }
        }
        val highlight = remember(animationScope, damped) {
            InteractiveHighlight(animationScope = animationScope, position = { size, _ ->
                Offset(
                    if (isLtr) (damped.value + 0.5f) * tabWidth + panelOffset
                    else size.width - (damped.value + 0.5f) * tabWidth + panelOffset,
                    size.height / 2f,
                )
            })
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                    layerBlock = {
                        val s = lerp(1f, 1f + 16.dp.toPx() / size.width, damped.pressProgress)
                        scaleX = s; scaleY = s
                    },
                    shadow = { Shadow(alpha = if (dark) 0.6f else 0.25f) },
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        CompositionLocalProvider(LocalTabScale provides { lerp(1f, 1.2f, damped.pressProgress) }) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val p = damped.pressProgress
                            vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx() * p, 24.dp.toPx() * p)
                        },
                        highlight = { Highlight.Default.copy(alpha = damped.pressProgress) },
                        onDrawSurface = { drawRect(container) },
                    )
                    .then(highlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) damped.value * tabWidth + panelOffset
                    else size.width - (damped.value + 1f) * tabWidth + panelOffset
                }
                .then(highlight.gestureModifier)
                .then(damped.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val p = damped.pressProgress
                        lens(10.dp.toPx() * p, 14.dp.toPx() * p, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = damped.pressProgress) },
                    shadow = { Shadow(alpha = damped.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * damped.pressProgress, alpha = damped.pressProgress) },
                    layerBlock = {
                        scaleX = damped.scaleX; scaleY = damped.scaleY
                        val v = damped.velocity / 10f
                        scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val p = damped.pressProgress
                        drawRect(if (dark) Color.White.copy(0.1f) else Color.Black.copy(0.1f), alpha = 1f - p)
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount),
        )
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
        val atEnd by remember(state) {
            derivedStateOf {
                (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 4
            }
        }
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
                        else -> ListEndFooter(
                            "已显示全部 ${data?.size ?: 0} 条。V2EX 接口不支持继续翻页，下拉可刷新获取最新",
                            "刷新", onRefresh,
                        )
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
