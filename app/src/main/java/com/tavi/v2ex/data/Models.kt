package com.tavi.v2ex.data

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

data class Member(
    val username: String,
    val avatar: String = "",
    val tagline: String = "",
    val bio: String = "",
    val website: String = "",
    val github: String = "",
    val location: String = "",
    val created: Long = 0,
    val id: Long = 0,
)

data class Node(
    val name: String,
    val title: String,
    val topics: Int = 0,
    val header: String = "",
    val avatar: String = "",
    val stars: Int = 0,
)

data class Topic(
    val id: Long,
    val title: String,
    val contentHtml: String = "",
    val replies: Int = 0,
    val member: Member? = null,
    val node: Node? = null,
    val created: Long = 0,
    val lastTouched: Long = 0,
    val lastReplyBy: String = "",
    val supplements: List<Supplement> = emptyList(),
    val thumb: String = "",
)

data class Supplement(val created: Long, val contentHtml: String)

data class Reply(
    val id: Long,
    val contentHtml: String,
    val member: Member?,
    val created: Long,
    val floor: Int,
)

data class Notice(
    val id: Long,
    val textHtml: String,
    val payloadHtml: String,
    val created: Long,
)

class ApiException(message: String, val code: Int = 0) : Exception(message)

internal fun fixUrl(u: String?): String = when {
    u.isNullOrBlank() -> ""
    u.startsWith("//") -> "https:$u"
    u.startsWith("/") -> "https://www.v2ex.com$u"
    else -> u
}

fun firstImageUrl(html: String): String {
    if (html.isBlank()) return ""
    return runCatching {
        Jsoup.parseBodyFragment(html, "https://www.v2ex.com/").select("img").firstOrNull()?.let {
            fixUrl(it.absUrl("src").ifBlank { it.attr("src") })
        } ?: ""
    }.getOrDefault("")
}

private fun JSONObject.s(k: String) = optString(k, "").let { if (it == "null") "" else it }

internal fun parseMember(o: JSONObject?): Member? = o?.let {
    Member(
        username = it.s("username"),
        avatar = fixUrl(it.s("avatar_large").ifBlank { it.s("avatar_normal") }.ifBlank { it.s("avatar") }),
        tagline = it.s("tagline"), bio = it.s("bio"), website = it.s("website"),
        github = it.s("github"), location = it.s("location"),
        created = it.optLong("created"), id = it.optLong("id"),
    )
}

internal fun parseNode(o: JSONObject?): Node? = o?.let {
    Node(
        name = it.s("name"), title = it.s("title").ifBlank { it.s("name") },
        topics = it.optInt("topics"), header = it.s("header"),
        avatar = fixUrl(it.s("avatar_large").ifBlank { it.s("avatar") }), stars = it.optInt("stars"),
    )
}

internal fun parseTopic(o: JSONObject): Topic {
    val html = o.s("content_rendered")
    return Topic(
        id = o.optLong("id"),
        title = o.s("title"),
        contentHtml = html,
        replies = o.optInt("replies"),
        member = parseMember(o.optJSONObject("member")),
        node = parseNode(o.optJSONObject("node")),
        created = o.optLong("created"),
        lastTouched = o.optLong("last_touched"),
        lastReplyBy = o.s("last_reply_by"),
        supplements = o.optJSONArray("supplements")?.objects()?.map {
            Supplement(it.optLong("created"), it.s("content_rendered"))
        } ?: emptyList(),
        thumb = firstImageUrl(html),
    )
}

internal fun parseNotice(o: JSONObject) = Notice(
    id = o.optLong("id"), textHtml = o.s("text"),
    payloadHtml = o.s("payload_rendered").ifBlank { o.s("payload") }, created = o.optLong("created"),
)

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
