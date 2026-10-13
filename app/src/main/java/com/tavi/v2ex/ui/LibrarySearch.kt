package com.tavi.v2ex.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tavi.v2ex.data.*
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: Navigator, entry: Entry) {
    val dao = nav.dao
    val topics by dao.topicBookmarks().collectAsState(emptyList())
    val nodes by dao.nodeBookmarks().collectAsState(emptyList())
    val history by dao.history().collectAsState(emptyList())
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("收藏与历史") },
                    navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                val json = exportBackup(dao.topicBookmarksOnce(), dao.nodeBookmarksOnce(), dao.historyOnce())
                                val intent = Intent(Intent.ACTION_SEND).setType("application/json")
                                    .putExtra(Intent.EXTRA_TEXT, json)
                                    .putExtra(Intent.EXTRA_SUBJECT, "v2-reader-backup.json")
                                ctx.startActivity(Intent.createChooser(intent, "导出备份").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Icon(Icons.Outlined.FileUpload, "导出备份") }
                        TextButton(onClick = {
                            val st = entry.state("imp") { mutableStateOf(false) }
                            st.value = true
                        }) {
                            Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("导入")
                        }
                    },
                )
                PrimaryTabRow(pager.currentPage) {
                    listOf("主题收藏", "节点收藏", "浏览历史").forEachIndexed { i, t ->
                        Tab(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(t) })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        HorizontalPager(pager, Modifier.padding(pad).fillMaxSize()) { page ->
            when (page) {
                0 -> if (topics.isEmpty()) CenterMessage("还没有收藏主题") else LazyColumn {
                    items(topics, key = { it.id }) { b ->
                        TopicRow(b.toTopic(), { nav.push(Route.TopicR(b.id, b.toTopic())) }, { nav.push(Route.MemberR(it)) }, showThumb = nav.prefs.loadImages)
                        HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
                1 -> if (nodes.isEmpty()) CenterMessage("还没有收藏节点") else LazyColumn {
                    items(nodes, key = { it.name }) { n ->
                        ListItem(
                            headlineContent = { Text(n.title) },
                            supportingContent = { Text(n.name) },
                            leadingContent = { Avatar(n.avatar, 36.dp) },
                            trailingContent = {
                                IconButton(onClick = { scope.launch { dao.removeNode(n.name) } }) {
                                    Icon(Icons.Outlined.Delete, "取消收藏")
                                }
                            },
                            modifier = Modifier.clickable { nav.push(Route.NodeR(n.name, n.title)) },
                        )
                    }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }, Modifier.align(Alignment.End).padding(8.dp)) { Text("清空历史") }
                    }
                    if (history.isEmpty()) CenterMessage("还没有浏览记录")
                    else LazyColumn {
                        items(history, key = { it.id }) { h ->
                            TopicRow(h.toTopic(), { nav.push(Route.TopicR(h.id, h.toTopic())) }, { nav.push(Route.MemberR(it)) }, showThumb = nav.prefs.loadImages)
                            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空浏览历史？") },
        confirmButton = { TextButton(onClick = { confirmClear = false; scope.launch { dao.clearHistory(); snack.showSnackbar("已清空") } }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
    val showImport = entry.state("imp") { mutableStateOf(false) }
    if (showImport.value) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport.value = false },
            title = { Text("导入备份 JSON") },
            text = {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("粘贴导出的 JSON") })
            },
            confirmButton = {
                TextButton(onClick = {
                    showImport.value = false
                    scope.launch {
                        runCatching { importBackup(dao, text) }
                            .onSuccess { snack.showSnackbar("已导入 $it 条") }
                            .onFailure { snack.showSnackbar(it.message ?: "导入失败") }
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showImport.value = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: Navigator, entry: Entry) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val nodes = entry.state("nodes") { Loadable<List<Node>>() }
    val localTopics = entry.state("local") { mutableStateOf<List<Topic>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (nodes.data == null) entry.scope.load(nodes) { nav.api.allNodes() }
    }
    fun runSearch(q: String) {
        submitted = q.trim()
        if (submitted.isEmpty()) return
        scope.launch {
            val fromHist = nav.dao.searchHistory(submitted).map { it.toTopic() }
            val fromBm = nav.dao.searchBookmarks(submitted).map { it.toTopic() }
            localTopics.value = (fromBm + fromHist).distinctBy { it.id }
            // 纯数字当主题 ID
            submitted.toLongOrNull()?.let { id ->
                runCatching { nav.api.topic(id) }.onSuccess { t ->
                    localTopics.value = listOf(t) + localTopics.value.filter { it.id != t.id }
                }
            }
        }
    }
    val nodeHits = remember(nodes.data, submitted) {
        val all = nodes.data ?: emptyList()
        if (submitted.isEmpty()) emptyList()
        else all.filter { it.title.contains(submitted, true) || it.name.contains(submitted, true) }
            .sortedByDescending { it.topics }.take(40)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("搜索节点、本地收藏 / 历史，或主题 ID") },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            },
            navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                IconButton(onClick = { runSearch(query) }) { Icon(Icons.Outlined.Search, "搜索") }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "V2EX 官方接口没有全文搜索。这里会搜本地节点列表、收藏和浏览历史；也可以输入主题数字 ID。完整站内搜索请用浏览器。",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (submitted.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val enc = URLEncoder.encode(submitted, "UTF-8")
                            nav.openExternal("https://www.v2ex.com/?q=$enc")
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("在 V2EX 网站搜索「$submitted」")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (localTopics.value.isNotEmpty()) {
                item { Text("本地主题", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                items(localTopics.value, key = { "t${it.id}" }) { t ->
                    TopicRow(t, { nav.push(Route.TopicR(t.id, t)) }, { nav.push(Route.MemberR(it)) }, showThumb = nav.prefs.loadImages)
                }
            }
            if (nodeHits.isNotEmpty()) {
                item { Text("节点", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                items(nodeHits, key = { "n${it.name}" }) { n ->
                    ListItem(
                        headlineContent = { Text(n.title) },
                        supportingContent = { Text("${n.name} · ${n.topics} 主题") },
                        leadingContent = { Avatar(n.avatar, 36.dp) },
                        modifier = Modifier.clickable { nav.push(Route.NodeR(n.name, n.title)) },
                    )
                }
            }
            if (submitted.isNotEmpty() && localTopics.value.isEmpty() && nodeHits.isEmpty()) {
                item { Box(Modifier.fillParentMaxHeight(0.4f)) { CenterMessage("本地没有匹配结果") } }
            }
        }
    }
}
