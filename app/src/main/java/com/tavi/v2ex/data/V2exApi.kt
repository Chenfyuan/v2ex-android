package com.tavi.v2ex.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * V2EX 官方接口。列表短缓存；已打开的主题正文和回复长缓存（7 天），弱网时优先用旧数据。
 */
class V2exApi(private val cacheDir: File, private val token: () -> String?) {

    @Volatile var rateRemaining: Int? = null; private set
    @Volatile var rateLimit: Int? = null; private set
    @Volatile var rateResetAt: Long? = null; private set

    private val mem = ConcurrentHashMap<String, Pair<Long, String>>()

    private fun cacheFile(key: String) = File(cacheDir, "api_" + Integer.toHexString(key.hashCode()))

    /**
     * @param ttlMs 新鲜期内直接用缓存
     * @param staleMs 过期后仍可作为弱网回退的最长时间（默认与 ttl 相同；主题/回复用 7 天）
     */
    private suspend fun get(
        path: String, ttlMs: Long, auth: Boolean = false, force: Boolean = false,
        staleMs: Long = ttlMs,
    ): String = withContext(Dispatchers.IO) {
        val key = (if (auth) "a:" else "") + path
        val now = System.currentTimeMillis()
        val f = cacheFile(key)
        fun readDisk(): String? = if (f.isFile) runCatching { f.readText() }.getOrNull() else null
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
        } catch (e: ApiException) {
            // 鉴权失败不要回退旧缓存（避免误显示）
            if (e.code == 401 || e.code == 403 && rateRemaining != 0) throw e
            val stale = mem[key]?.second ?: readDisk()
            if (stale != null && (mem[key]?.let { now - it.first < staleMs } == true ||
                    (f.isFile && now - f.lastModified() < staleMs))
            ) stale else throw e
        } catch (e: IOException) {
            val stale = mem[key]?.second ?: readDisk()
            if (stale != null && (f.isFile && now - f.lastModified() < staleMs || mem[key] != null)) {
                stale
            } else {
                throw ApiException(networkMsg(e), retryable = true)
            }
        }
    }

    private fun networkMsg(e: IOException) = when (e) {
        is UnknownHostException -> "无法连接网络，请检查网络后点「重试」。若刚才打开过，可先看看缓存内容。"
        is SocketTimeoutException -> "连接超时。网络较慢时会尽量使用本地缓存，请稍后重试。"
        else -> "网络连接失败，请稍后再试。已打开过的主题在弱网下仍可能可看。"
    }

    private fun rateLimitMsg(): String {
        val rem = rateRemaining
        val lim = rateLimit ?: 600
        val reset = rateResetAt
        val wait = if (reset != null && reset > System.currentTimeMillis() / 1000) {
            val sec = (reset - System.currentTimeMillis() / 1000).toInt().coerceAtLeast(1)
            when {
                sec < 60 -> "约 ${sec} 秒后恢复"
                else -> "约 ${sec / 60} 分钟后恢复"
            }
        } else "请稍后再试"
        return "请求过于频繁（本小时剩余 ${rem ?: 0}/$lim）。V2EX 限制每个 IP 每小时约 600 次。$wait。"
    }

    private fun http(method: String, path: String, auth: Boolean): String {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "V2Reader/0.3 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            if (auth) {
                val t = token() ?: throw ApiException("还没有填写个人访问令牌。请到「设置」里粘贴令牌后再试。", 401)
                conn.setRequestProperty("Authorization", "Bearer $t")
            }
            val code = conn.responseCode
            conn.getHeaderField("X-Rate-Limit-Remaining")?.toIntOrNull()?.let { rateRemaining = it }
            conn.getHeaderField("X-Rate-Limit-Limit")?.toIntOrNull()?.let { rateLimit = it }
            conn.getHeaderField("X-Rate-Limit-Reset")?.toLongOrNull()?.let { rateResetAt = it }
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code >= 400) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                val limited = rateRemaining == 0 || code == 429
                throw ApiException(
                    when {
                        code == 401 -> "个人访问令牌无效或已过期。请到 V2EX 网站重新创建令牌，再到「设置」里更新。"
                        limited -> rateLimitMsg()
                        code == 403 -> msg?.takeIf { it.isNotBlank() } ?: "没有权限访问该内容"
                        code == 404 -> "内容不存在或已被删除"
                        else -> msg?.takeIf { it.isNotBlank() } ?: "服务器出错了（错误码 $code），请稍后重试"
                    },
                    code,
                    retryable = limited || code >= 500,
                )
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun v2Result(body: String): Any? {
        val o = JSONObject(body)
        if (!o.optBoolean("success", true)) {
            val m = o.optString("message", "请求失败")
            val authFail = m.contains("token", true) || m.contains("授权") || m.contains("登录")
            throw ApiException(
                if (authFail) "个人访问令牌无效或已过期，请到「设置」更新令牌。" else m,
                if (authFail) 401 else 0,
            )
        }
        return o.opt("result")
    }

    suspend fun hot(force: Boolean = false) =
        JSONArray(get("/api/topics/hot.json", 5 * MIN, force = force, staleMs = DAY)).objects().map(::parseTopic)

    suspend fun latest(force: Boolean = false) =
        JSONArray(get("/api/topics/latest.json", 2 * MIN, force = force, staleMs = DAY)).objects().map(::parseTopic)

    suspend fun allNodes(force: Boolean = false) =
        JSONArray(get("/api/nodes/all.json", 24 * 60 * MIN, force = force, staleMs = 7 * DAY)).objects().mapNotNull(::parseNode)

    suspend fun node(name: String) =
        parseNode(JSONObject(get("/api/nodes/show.json?name=${enc(name)}", 60 * MIN, staleMs = 7 * DAY)))!!

    suspend fun nodeTopics(name: String, page: Int, force: Boolean = false): Pair<List<Topic>, Boolean> {
        if (token() != null) {
            val arr = v2Result(get("/api/v2/nodes/${enc(name)}/topics?p=$page", 2 * MIN, auth = true, force = force, staleMs = DAY)) as? JSONArray
            val list = arr?.objects()?.map(::parseTopic) ?: emptyList()
            return list to list.isNotEmpty()
        }
        val list = JSONArray(get("/api/topics/show.json?node_name=${enc(name)}", 2 * MIN, force = force, staleMs = DAY)).objects().map(::parseTopic)
        return list to false
    }

    suspend fun topic(id: Long, force: Boolean = false): Topic {
        val arr = JSONArray(get("/api/topics/show.json?id=$id", 30 * MIN, force = force, staleMs = 7 * DAY))
        if (arr.length() > 0) return parseTopic(arr.getJSONObject(0))
        if (token() != null) {
            (v2Result(get("/api/v2/topics/$id", 30 * MIN, auth = true, force = force, staleMs = 7 * DAY)) as? JSONObject)?.let { return parseTopic(it) }
        }
        throw ApiException("主题不存在，或需要登录才能查看", 404)
    }

    suspend fun replies(id: Long, force: Boolean = false): List<Reply> =
        JSONArray(get("/api/replies/show.json?topic_id=$id", 30 * MIN, force = force, staleMs = 7 * DAY)).objects().mapIndexed { i, o ->
            Reply(o.optLong("id"), o.optString("content_rendered"), parseMember(o.optJSONObject("member")), o.optLong("created"), i + 1)
        }

    suspend fun member(username: String) =
        parseMember(JSONObject(get("/api/members/show.json?username=${enc(username)}", 30 * MIN, staleMs = 7 * DAY)))
            ?: throw ApiException("用户不存在", 404)

    suspend fun memberTopics(username: String, force: Boolean = false) =
        JSONArray(get("/api/topics/show.json?username=${enc(username)}", 5 * MIN, force = force, staleMs = DAY)).objects().map(::parseTopic)

    suspend fun me(force: Boolean = false) =
        parseMember(v2Result(get("/api/v2/member", 10 * MIN, auth = true, force = force)) as? JSONObject)
            ?: throw ApiException("读取个人资料失败")

    suspend fun notifications(page: Int, force: Boolean = false): List<Notice> =
        (v2Result(get("/api/v2/notifications?p=$page", MIN, auth = true, force = force)) as? JSONArray)
            ?.objects()?.map(::parseNotice) ?: emptyList()

    suspend fun deleteNotification(id: Long) = withContext(Dispatchers.IO) {
        try { http("DELETE", "/api/v2/notifications/$id", auth = true) } catch (e: IOException) { throw ApiException(networkMsg(e), retryable = true) }
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
        private const val DAY = 24 * 60 * MIN
    }
}
