package com.tavi.v2ex.data

import org.jsoup.Jsoup

/**
 * 只读解析 V2EX 公开网页列表（/recent?p=N、/go/{node}?p=N），不需要登录。
 * 解析失败返回空列表，由调用方回退到 JSON 接口。
 */
object WebParser {
    private val idRe = Regex("""/t/(\d+)""")

    fun parseTopicList(html: String, fallbackNode: Node? = null): List<Topic> {
        val doc = Jsoup.parse(html, "https://www.v2ex.com/")
        val out = LinkedHashMap<Long, Topic>()
        for (a in doc.select("a.topic-link")) {
            val id = idRe.find(a.attr("href"))?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val row = a.closest("table") ?: continue
            val img = row.selectFirst("img.avatar")
            val username = img?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: row.selectFirst("a[href^=/member/]")?.attr("href")?.substringAfterLast('/') ?: ""
            val avatar = fixUrl(img?.attr("src")).replace("_normal.png", "_large.png").replace("s=24", "s=73")
            val nodeA = row.selectFirst("a.node")
            val node = nodeA?.let { Node(it.attr("href").substringAfterLast('/'), it.text()) } ?: fallbackNode
            val replies = row.selectFirst("a.count_livid, a.count_orange")?.text()?.toIntOrNull() ?: 0
            out.putIfAbsent(id, Topic(
                id = id, title = a.text(), replies = replies,
                member = Member(username, avatar).takeIf { username.isNotEmpty() },
                node = node,
            ))
        }
        return out.values.toList()
    }
}
