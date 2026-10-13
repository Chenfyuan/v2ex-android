package com.tavi.v2ex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavi.v2ex.data.Node
import com.tavi.v2ex.data.Topic
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

private data class Dest(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val Destinations = listOf(
    Dest("最热", Icons.Filled.LocalFireDepartment, Icons.Outlined.LocalFireDepartment),
    Dest("最新", Icons.Filled.Schedule, Icons.Outlined.Schedule),
    Dest("节点", Icons.Filled.GridView, Icons.Outlined.GridView),
)

/** 悬浮底栏外高：导航栏本体 + 上下边距，列表底部留白用 */
private val FloatingBarReserve = 88.dp

class HomeState {
    val haze = HazeState()
    val hot = Loadable<List<Topic>>()
    val latest = Loadable<List<Topic>>()
    val nodes = Loadable<List<Node>>()
    val lists = List(3) { LazyListState() }
    val pager = PagerState { Destinations.size }
    var query by mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Navigator, entry: Entry) {
    val s = entry.state("home") { HomeState() }
    val api = nav.api
    LaunchedEffect(Unit) {
        if (s.hot.data == null) entry.scope.load(s.hot) { api.hot() }
    }
    LaunchedEffect(s.pager.currentPage) {
        when (s.pager.currentPage) {
            1 -> if (s.latest.data == null) entry.scope.load(s.latest) { api.latest() }
            2 -> if (s.nodes.data == null) entry.scope.load(s.nodes) { api.allNodes() }
        }
    }
    val scope = rememberCoroutineScope()
    val titles = listOf("最热", "最新", "节点")
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(titles.getOrElse(s.pager.currentPage) { "V2EX" }, fontWeight = FontWeight.SemiBold)
                    },
                    actions = {
                        if (nav.prefs.token != null) IconButton(onClick = { nav.push(Route.Notices) }) {
                            Icon(Icons.Outlined.Notifications, "提醒")
                        }
                        IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Outlined.Settings, "设置") }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
        ) { pad ->
            HorizontalPager(
                state = s.pager,
                modifier = Modifier.padding(pad).fillMaxSize().hazeSource(s.haze),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> TopicListPane(s.hot, s.lists[0], nav,
                        { entry.scope.load(s.hot, true) { api.hot(force = true) } },
                        { entry.scope.load(s.hot) { api.hot() } })
                    1 -> TopicListPane(s.latest, s.lists[1], nav,
                        { entry.scope.load(s.latest, true) { api.latest(force = true) } },
                        { entry.scope.load(s.latest) { api.latest() } })
                    else -> NodesPane(s, nav) { entry.scope.load(s.nodes, true) { api.allNodes(force = true) } }
                }
            }
        }
        FloatingBottomBar(
            selected = s.pager.currentPage,
            haze = s.haze,
            onSelect = { i -> scope.launch { s.pager.animateScrollToPage(i) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 液态玻璃底栏：API 31+ 对背后内容做实时模糊（Haze / RenderEffect），
 * 旧系统自动退化为半透明磨砂色；再叠一层顶部高光和渐变描边，模拟玻璃边缘的反光。
 */
@Composable
fun FloatingBottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, haze: HazeState? = null) {
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
            // 顶部柔光：模拟玻璃表面的镜面反射
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.10f else 0.35f),
                    0.45f to Color.White.copy(alpha = 0f),
                ),
            )
            // 渐变描边：上缘亮、下缘淡，像玻璃边缘折射的光
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
            Destinations.forEachIndexed { i, d ->
                NavigationBarItem(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    icon = { Icon(if (selected == i) d.selected else d.unselected, contentDescription = d.label) },
                    label = { Text(d.label) },
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
    onRefresh: () -> Unit, onRetry: () -> Unit,
) {
    val data = l.data
    when {
        data == null && l.error != null -> CenterMessage(l.error!!, "重试", onRetry)
        data == null -> Loading()
        else -> PullToRefreshBox(isRefreshing = l.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            TopicList(data, state, onTopic = { nav.push(Route.TopicR(it.id, it)) }, onMember = { nav.push(Route.MemberR(it)) })
        }
    }
}

@Composable
fun TopicList(
    topics: List<Topic>, state: LazyListState = LazyListState(),
    onTopic: (Topic) -> Unit, onMember: (String) -> Unit, showNode: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    bottomSpace: Boolean = true,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(bottom = if (bottomSpace) FloatingBarReserve else 0.dp),
    ) {
        if (header != null) item { header() }
        items(topics, key = { it.id }) { t ->
            TopicRow(t, { onTopic(t) }, onMember, showNode)
            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        if (topics.isEmpty()) item { Box(Modifier.fillParentMaxHeight(0.6f)) { CenterMessage("这里还没有主题") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesPane(s: HomeState, nav: Navigator, onRefresh: () -> Unit) {
    val all = s.nodes.data
    when {
        all == null && s.nodes.error != null -> CenterMessage(s.nodes.error!!, "重试", onRefresh)
        all == null -> Loading()
        else -> {
            val q = s.query.trim()
            val shown = remember(all, q) {
                val sorted = all.sortedByDescending { it.topics }
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
