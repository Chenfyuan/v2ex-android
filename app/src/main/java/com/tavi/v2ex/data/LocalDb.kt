package com.tavi.v2ex.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "history")
data class HistoryRow(
    @PrimaryKey val id: Long,
    val title: String,
    val member: String = "",
    val avatar: String = "",
    val nodeName: String = "",
    val nodeTitle: String = "",
    val replies: Int = 0,
    val thumb: String = "",
    val viewedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "topic_bookmarks")
data class TopicBookmark(
    @PrimaryKey val id: Long,
    val title: String,
    val member: String = "",
    val avatar: String = "",
    val nodeName: String = "",
    val nodeTitle: String = "",
    val replies: Int = 0,
    val thumb: String = "",
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "node_bookmarks")
data class NodeBookmark(
    @PrimaryKey val name: String,
    val title: String,
    val avatar: String = "",
    val topics: Int = 0,
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reading_pos")
data class ReadingPos(
    @PrimaryKey val topicId: Long,
    val floor: Int = 0,
    val listIndex: Int = 0,
    val offset: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "blocked_users")
data class BlockedUser(@PrimaryKey val username: String, val blockedAt: Long = System.currentTimeMillis())

@Entity(tableName = "blocked_nodes")
data class BlockedNode(@PrimaryKey val name: String, val title: String = "", val blockedAt: Long = System.currentTimeMillis())

@Dao
interface LocalDao {
    @Query("SELECT * FROM history ORDER BY viewedAt DESC LIMIT :limit")
    fun history(limit: Int = 200): Flow<List<HistoryRow>>

    @Query("SELECT * FROM history ORDER BY viewedAt DESC LIMIT :limit")
    suspend fun historyOnce(limit: Int = 200): List<HistoryRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(row: HistoryRow)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun removeHistory(id: Long)

    @Query("SELECT * FROM topic_bookmarks ORDER BY savedAt DESC")
    fun topicBookmarks(): Flow<List<TopicBookmark>>

    @Query("SELECT * FROM topic_bookmarks ORDER BY savedAt DESC")
    suspend fun topicBookmarksOnce(): List<TopicBookmark>

    @Query("SELECT EXISTS(SELECT 1 FROM topic_bookmarks WHERE id = :id)")
    fun isTopicBookmarked(id: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM topic_bookmarks WHERE id = :id)")
    suspend fun isTopicBookmarkedOnce(id: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTopic(row: TopicBookmark)

    @Query("DELETE FROM topic_bookmarks WHERE id = :id")
    suspend fun removeTopic(id: Long)

    @Query("SELECT * FROM node_bookmarks ORDER BY savedAt DESC")
    fun nodeBookmarks(): Flow<List<NodeBookmark>>

    @Query("SELECT * FROM node_bookmarks ORDER BY savedAt DESC")
    suspend fun nodeBookmarksOnce(): List<NodeBookmark>

    @Query("SELECT EXISTS(SELECT 1 FROM node_bookmarks WHERE name = :name)")
    fun isNodeBookmarked(name: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNode(row: NodeBookmark)

    @Query("DELETE FROM node_bookmarks WHERE name = :name")
    suspend fun removeNode(name: String)

    @Query("SELECT * FROM history WHERE title LIKE '%' || :q || '%' OR member LIKE '%' || :q || '%' OR nodeTitle LIKE '%' || :q || '%' LIMIT 50")
    suspend fun searchHistory(q: String): List<HistoryRow>

    @Query("SELECT * FROM topic_bookmarks WHERE title LIKE '%' || :q || '%' OR member LIKE '%' || :q || '%' OR nodeTitle LIKE '%' || :q || '%' LIMIT 50")
    suspend fun searchBookmarks(q: String): List<TopicBookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingPos(pos: ReadingPos)

    @Query("SELECT * FROM reading_pos WHERE topicId = :id")
    suspend fun readingPos(id: Long): ReadingPos?

    @Query("DELETE FROM reading_pos WHERE topicId = :id")
    suspend fun clearReadingPos(id: Long)

    @Query("SELECT username FROM blocked_users ORDER BY blockedAt DESC")
    fun blockedUsers(): Flow<List<String>>

    @Query("SELECT username FROM blocked_users")
    suspend fun blockedUsersOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockUser(u: BlockedUser)

    @Query("DELETE FROM blocked_users WHERE username = :username")
    suspend fun unblockUser(username: String)

    @Query("SELECT * FROM blocked_nodes ORDER BY blockedAt DESC")
    fun blockedNodes(): Flow<List<BlockedNode>>

    @Query("SELECT name FROM blocked_nodes")
    suspend fun blockedNodeNamesOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNode(n: BlockedNode)

    @Query("DELETE FROM blocked_nodes WHERE name = :name")
    suspend fun unblockNode(name: String)
}

@Database(
    entities = [HistoryRow::class, TopicBookmark::class, NodeBookmark::class, ReadingPos::class, BlockedUser::class, BlockedNode::class],
    version = 2, exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LocalDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "v2ex.db")
                .fallbackToDestructiveMigration(true)
                .build().also { inst = it }
        }
    }
}

fun Topic.toHistory() = HistoryRow(id, title, member?.username.orEmpty(), member?.avatar.orEmpty(),
    node?.name.orEmpty(), node?.title.orEmpty(), replies, firstImageUrl(contentHtml), System.currentTimeMillis())

fun Topic.toBookmark() = TopicBookmark(id, title, member?.username.orEmpty(), member?.avatar.orEmpty(),
    node?.name.orEmpty(), node?.title.orEmpty(), replies, firstImageUrl(contentHtml), System.currentTimeMillis())

fun Node.toBookmark() = NodeBookmark(name, title, avatar, topics)

fun HistoryRow.toTopic() = Topic(id, title, "", replies,
    Member(member, avatar).takeIf { member.isNotEmpty() },
    Node(nodeName, nodeTitle).takeIf { nodeName.isNotEmpty() }, thumb = thumb)

fun TopicBookmark.toTopic() = Topic(id, title, "", replies,
    Member(member, avatar).takeIf { member.isNotEmpty() },
    Node(nodeName, nodeTitle).takeIf { nodeName.isNotEmpty() }, thumb = thumb)

fun exportBackup(topics: List<TopicBookmark>, nodes: List<NodeBookmark>, history: List<HistoryRow>): String {
    fun t(x: TopicBookmark) = JSONObject().put("id", x.id).put("title", x.title).put("member", x.member)
        .put("avatar", x.avatar).put("nodeName", x.nodeName).put("nodeTitle", x.nodeTitle)
        .put("replies", x.replies).put("thumb", x.thumb).put("savedAt", x.savedAt)
    fun n(x: NodeBookmark) = JSONObject().put("name", x.name).put("title", x.title)
        .put("avatar", x.avatar).put("topics", x.topics).put("savedAt", x.savedAt)
    fun h(x: HistoryRow) = JSONObject().put("id", x.id).put("title", x.title).put("member", x.member)
        .put("avatar", x.avatar).put("nodeName", x.nodeName).put("nodeTitle", x.nodeTitle)
        .put("replies", x.replies).put("thumb", x.thumb).put("viewedAt", x.viewedAt)
    return JSONObject()
        .put("version", 1)
        .put("exportedAt", System.currentTimeMillis())
        .put("topics", JSONArray().also { topics.forEach { r -> it.put(t(r)) } })
        .put("nodes", JSONArray().also { nodes.forEach { r -> it.put(n(r)) } })
        .put("history", JSONArray().also { history.forEach { r -> it.put(h(r)) } })
        .toString(2)
}

suspend fun importBackup(dao: LocalDao, json: String): Int {
    val o = JSONObject(json)
    var n = 0
    o.optJSONArray("topics")?.let { arr ->
        for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            dao.saveTopic(TopicBookmark(x.getLong("id"), x.optString("title"), x.optString("member"),
                x.optString("avatar"), x.optString("nodeName"), x.optString("nodeTitle"),
                x.optInt("replies"), x.optString("thumb"), x.optLong("savedAt", System.currentTimeMillis())))
            n++
        }
    }
    o.optJSONArray("nodes")?.let { arr ->
        for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            dao.saveNode(NodeBookmark(x.getString("name"), x.optString("title"), x.optString("avatar"),
                x.optInt("topics"), x.optLong("savedAt", System.currentTimeMillis())))
            n++
        }
    }
    o.optJSONArray("history")?.let { arr ->
        for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            dao.upsertHistory(HistoryRow(x.getLong("id"), x.optString("title"), x.optString("member"),
                x.optString("avatar"), x.optString("nodeName"), x.optString("nodeTitle"),
                x.optInt("replies"), x.optString("thumb"), x.optLong("viewedAt", System.currentTimeMillis())))
            n++
        }
    }
    return n
}

fun List<Topic>.filterBlocked(users: Set<String>, nodes: Set<String>) =
    filter { t ->
        t.member?.username !in users && (t.node?.name ?: "") !in nodes
    }

fun List<Node>.filterBlockedNodes(nodes: Set<String>) = filter { it.name !in nodes }
