package com.tavi.v2ex.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * V2EX 官方接口：
 * - v1 公开接口（/api/...json），无需登录
 * - API 2.0（/api/v2/...），需要个人访问令牌
 * 默认每个 IP 每小时 600 次，所以所有请求都做内存 + 磁盘缓存，出错时回退到旧缓存。
 */
class V2exApi(private val cacheDir: File, private val token: () -> String?) {

    @Volatile var rateRemaining: Int? = null; private set
    @Volatile var rateLimit: Int? = null; private set

    private val mem = ConcurrentHashMap<String, Pair<Long, String>>()

    private fun cacheFile(key: String) = File(cacheDir, "api_" + Integer.toHexString(key.hashCode()))

    private suspend fun get(path: String, ttlMs: Long, auth: Boolean = false, force: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val key = (if (auth) "a:" else "") + path
            val now = System.currentTimeMillis()
            val f = cacheFile(key)
            if (!force) {
                mem[key]?.let { if (now - it.first < ttlMs) return@withContext it.second }
                if (f.isFile && now - f.lastModified() < ttlMs) {
                    return@withContext f.readText().also { mem[key] = f.lastModified() to it }
                }
            }
            try {
                val body = http("GET", path, auth)
                mem[key] = now to body
                runCatching { f.writeText(body) }
                body
            } catch (e: IOException) {
                if (f.isFile) f.readText() else throw ApiException("网络连接失败，请稍后再试")
            }
        }

    private fun http(method: String, path: String, auth: Boolean): String {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "V2Reader/0.1 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            if (auth) {
                val t = token() ?: throw ApiException("请先在设置里填写个人访问令牌", 401)
                conn.setRequestProperty("Authorization", "Bearer $t")
            }
            val code = conn.responseCode
            conn.getHeaderField("X-Rate-Limit-Remaining")?.toIntOrNull()?.let { rateRemaining = it }
            conn.getHeaderField("X-Rate-Limit-Limit")?.toIntOrNull()?.let { rateLimit = it }
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code >= 400) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw ApiException(
                    when (code) {
                        401 -> "令牌无效或已过期"
                        403 -> if (rateRemaining == 0) "请求太频繁，V2EX 限制每小时 600 次，请稍后再试" else (msg ?: "没有权限")
                        404 -> "内容不存在或已被删除"
                        429 -> "请求太频繁，请稍后再试"
                        else -> msg?.takeIf { it.isNotBlank() } ?: "服务器错误（$code）"
                    }, code,
                )
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun v2Result(body: String): Any? {
        val o = JSONObject(body)
        if (!o.optBoolean("success", true)) throw ApiException(o.optString("message", "请求失败"))
        return o.opt("result")
    }

    // ---------- v1 公开接口 ----------
    suspend fun hot(force: Boolean = false) =
        JSONArray(get("/api/topics/hot.json", 5 * MIN, force = force)).objects().map(::parseTopic)

    suspend fun latest(force: Boolean = false) =
        JSONArray(get("/api/topics/latest.json", 2 * MIN, force = force)).objects().map(::parseTopic)

    suspend fun allNodes(force: Boolean = false) =
        JSONArray(get("/api/nodes/all.json", 24 * 60 * MIN, force = force)).objects().mapNotNull(::parseNode)

    suspend fun node(name: String) =
        parseNode(JSONObject(get("/api/nodes/show.json?name=${enc(name)}", 60 * MIN)))!!

    /** 节点主题：有令牌时走 API 2.0（可翻页），否则 v1 只返回最新 10 条 */
    suspend fun nodeTopics(name: String, page: Int, force: Boolean = false): Pair<List<Topic>, Boolean> {
        if (token() != null) {
            val arr = v2Result(get("/api/v2/nodes/${enc(name)}/topics?p=$page", 2 * MIN, auth = true, force = force)) as? JSONArray
            val list = arr?.objects()?.map(::parseTopic) ?: emptyList()
            return list to (list.size >= 20)
        }
        val list = JSONArray(get("/api/topics/show.json?node_name=${enc(name)}", 2 * MIN, force = force)).objects().map(::parseTopic)
        return list to false
    }

    suspend fun topic(id: Long, force: Boolean = false): Topic {
        val arr = JSONArray(get("/api/topics/show.json?id=$id", 2 * MIN, force = force))
        if (arr.length() > 0) return parseTopic(arr.getJSONObject(0))
        if (token() != null) {
            (v2Result(get("/api/v2/topics/$id", 2 * MIN, auth = true, force = force)) as? JSONObject)?.let { return parseTopic(it) }
        }
        throw ApiException("主题不存在或需要登录才能查看", 404)
    }

    /** v1 一次返回全部回复，在界面里分页展示 */
    suspend fun replies(id: Long, force: Boolean = false): List<Reply> =
        JSONArray(get("/api/replies/show.json?topic_id=$id", 2 * MIN, force = force)).objects().mapIndexed { i, o ->
            Reply(o.optLong("id"), o.optString("content_rendered"), parseMember(o.optJSONObject("member")), o.optLong("created"), i + 1)
        }

    suspend fun member(username: String) =
        parseMember(JSONObject(get("/api/members/show.json?username=${enc(username)}", 30 * MIN)))
            ?: throw ApiException("用户不存在", 404)

    suspend fun memberTopics(username: String, force: Boolean = false) =
        JSONArray(get("/api/topics/show.json?username=${enc(username)}", 5 * MIN, force = force)).objects().map(::parseTopic)

    // ---------- API 2.0（需要令牌） ----------
    suspend fun me(force: Boolean = false) =
        parseMember(v2Result(get("/api/v2/member", 10 * MIN, auth = true, force = force)) as? JSONObject)
            ?: throw ApiException("读取个人资料失败")

    suspend fun notifications(page: Int, force: Boolean = false): List<Notice> =
        (v2Result(get("/api/v2/notifications?p=$page", MIN, auth = true, force = force)) as? JSONArray)
            ?.objects()?.map(::parseNotice) ?: emptyList()

    suspend fun deleteNotification(id: Long) = withContext(Dispatchers.IO) {
        try { http("DELETE", "/api/v2/notifications/$id", auth = true) } catch (e: IOException) { throw ApiException("网络连接失败") }
        mem.keys.removeAll { it.contains("/api/v2/notifications") }
        Unit
    }

    fun clearCache() {
        mem.clear()
        cacheDir.listFiles()?.filter { it.name.startsWith("api_") }?.forEach { it.delete() }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        const val BASE = "https://www.v2ex.com"
        private const val MIN = 60_000L
    }
}
