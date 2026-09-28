package quest.saigon.town

import com.arangodb.ArangoDB
import com.arangodb.ArangoDBException
import com.arangodb.ArangoDatabase
import com.arangodb.ContentType
import com.arangodb.entity.BaseDocument
import com.arangodb.model.PersistentIndexOptions
import com.arangodb.serde.jackson.JacksonSerde
import quest.saigon.AppConfig
import java.time.LocalDate

class ArangoTownRepository(private val db: ArangoDatabase) : TownRepository {
    fun ensureSchema() {
        listOf(USERS, SESSIONS, AVATARS, POSTS, DAY_STATS).forEach { name ->
            val collection = db.collection(name)
            if (!collection.exists()) collection.create()
        }
        db.collection(USERS).ensurePersistentIndex(listOf("phone"), PersistentIndexOptions().unique(true))
        db.collection(POSTS).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(POSTS).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
    }

    override fun createUser(phone: String, passwordHash: String, name: String, createdAt: Long): UserRecord {
        val doc = BaseDocument()
        doc.addAttribute("phone", phone)
        doc.addAttribute("passwordHash", passwordHash)
        doc.addAttribute("name", name)
        doc.addAttribute("location", "")
        doc.addAttribute("intro", "")
        doc.addAttribute("profilePhotoPath", null)
        doc.addAttribute("language", "system")
        doc.addAttribute("createdAt", createdAt)
        val key = try {
            db.collection(USERS).insertDocument(doc).key
        } catch (error: ArangoDBException) {
            if (error.errorNum == 1210) throw TownException("phone_taken")
            throw error
        }
        return userFrom(doc.apply { this.key = key })
    }

    override fun findUserByPhone(phone: String): UserRecord? = first(
        "FOR u IN $USERS FILTER u.phone == @phone LIMIT 1 RETURN u",
        mapOf("phone" to phone),
    )?.let(::userFrom)

    override fun findUser(id: String): UserRecord? =
        db.collection(USERS).getDocument(id, BaseDocument::class.java)?.let(::userFrom)

    override fun updateUser(user: UserRecord) {
        val doc = BaseDocument(user.id)
        doc.addAttribute("phone", user.phone)
        doc.addAttribute("passwordHash", user.passwordHash)
        doc.addAttribute("name", user.name)
        doc.addAttribute("location", user.location)
        doc.addAttribute("intro", user.intro)
        doc.addAttribute("profilePhotoPath", user.profilePhotoPath)
        doc.addAttribute("language", user.language)
        doc.addAttribute("createdAt", user.createdAt)
        try {
            db.collection(USERS).replaceDocument(user.id, doc)
        } catch (error: ArangoDBException) {
            if (error.errorNum == 1210) throw TownException("phone_taken")
            throw error
        }
    }

    override fun createSession(token: String, userId: String, expiresAt: Long) {
        val doc = BaseDocument(token)
        doc.addAttribute("userKey", userId)
        doc.addAttribute("expiresAt", expiresAt)
        db.collection(SESSIONS).insertDocument(doc)
    }

    override fun findSession(token: String): SessionRecord? {
        val doc = db.collection(SESSIONS).getDocument(token, BaseDocument::class.java) ?: return null
        return SessionRecord(doc.key, string(doc, "userKey"), long(doc, "expiresAt"))
    }

    override fun deleteSession(token: String) {
        val collection = db.collection(SESSIONS)
        if (collection.documentExists(token)) collection.deleteDocument(token)
    }

    override fun insertAvatar(userId: String, prompt: String, createdAt: Long): AvatarRecord {
        val doc = BaseDocument()
        doc.addAttribute("userKey", userId)
        doc.addAttribute("prompt", prompt)
        doc.addAttribute("imagePath", null)
        doc.addAttribute("status", AvatarStatus.GENERATING)
        doc.addAttribute("error", null)
        doc.addAttribute("createdAt", createdAt)
        val key = db.collection(AVATARS).insertDocument(doc).key
        return avatarFrom(doc.apply { this.key = key })
    }

    override fun updateAvatar(id: String, status: String, imagePath: String?, error: String?) {
        val current = findAvatar(id) ?: return
        val doc = BaseDocument(id)
        doc.addAttribute("userKey", current.userId)
        doc.addAttribute("prompt", current.prompt)
        doc.addAttribute("imagePath", imagePath ?: current.imagePath)
        doc.addAttribute("status", status)
        doc.addAttribute("error", error)
        doc.addAttribute("createdAt", current.createdAt)
        db.collection(AVATARS).replaceDocument(id, doc)
    }

    override fun findAvatar(id: String): AvatarRecord? =
        db.collection(AVATARS).getDocument(id, BaseDocument::class.java)?.let(::avatarFrom)

    override fun avatarsFor(userId: String): List<AvatarRecord> =
        queryList(
            "FOR a IN $AVATARS FILTER a.userKey == @userKey AND a.status != @hidden SORT a.createdAt DESC RETURN a",
            mapOf("userKey" to userId, "hidden" to AvatarStatus.REMOVED),
        ).map(::avatarFrom)

    override fun hasGeneratingAvatar(userId: String): Boolean = first(
        "FOR a IN $AVATARS FILTER a.userKey == @userKey AND a.status == @status LIMIT 1 RETURN a",
        mapOf("userKey" to userId, "status" to AvatarStatus.GENERATING),
    ) != null

    override fun deleteAvatar(id: String) {
        db.collection(AVATARS).deleteDocument(id)
    }

    override fun avatarReferenced(id: String): Boolean = first(
        "FOR p IN $POSTS FILTER p.avatarKey == @id LIMIT 1 RETURN p",
        mapOf("id" to id),
    ) != null

    override fun insertPost(post: PostRecord): PostRecord {
        val doc = BaseDocument()
        doc.addAttribute("userKey", post.userId)
        doc.addAttribute("avatarKey", post.avatarId)
        doc.addAttribute("photoPath", post.photoPath)
        doc.addAttribute("message", post.message)
        doc.addAttribute("location", post.location)
        doc.addAttribute("createdAt", post.createdAt)
        doc.addAttribute("views", post.views)
        doc.addAttribute("replyClicks", post.replyClicks)
        doc.addAttribute("shareClicks", post.shareClicks)
        val key = db.collection(POSTS).insertDocument(doc).key
        return post.copy(id = key)
    }

    override fun feed(cursor: String?, limit: Int): FeedPage {
        val parsed = Cursors.parse(cursor)
        val bind = mapOf(
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
        val query = """
            FOR p IN $POSTS
              FILTER @hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey)
              SORT p.createdAt DESC, p._key DESC
              LIMIT @limit
              LET author = DOCUMENT("$USERS", p.userKey)
              LET avatar = DOCUMENT("$AVATARS", p.avatarKey)
              RETURN {
                id: p._key,
                authorName: author.name,
                authorPhotoPath: author.profilePhotoPath,
                authorLocation: author.location,
                authorIntro: author.intro,
                message: p.message,
                location: p.location,
                photoPath: p.photoPath,
                avatarPath: avatar.imagePath,
                createdAt: p.createdAt
              }
        """.trimIndent()
        val rows = queryList(query, bind)
        val page = rows.take(limit)
        val items = page.map { row ->
            FeedItem(
                id = string(row, "id"),
                authorName = string(row, "authorName"),
                authorPhotoPath = row.getAttribute("authorPhotoPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                message = string(row, "message"),
                location = string(row, "location"),
                photoPath = string(row, "photoPath"),
                avatarPath = row.getAttribute("avatarPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                createdAt = long(row, "createdAt"),
                authorLocation = string(row, "authorLocation"),
                authorIntro = string(row, "authorIntro"),
            )
        }
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return FeedPage(items, next)
    }

    override fun findPost(id: String): PostRecord? =
        db.collection(POSTS).getDocument(id, BaseDocument::class.java)?.let { doc ->
            PostRecord(
                id = doc.key,
                userId = string(doc, "userKey"),
                avatarId = string(doc, "avatarKey"),
                photoPath = string(doc, "photoPath"),
                message = string(doc, "message"),
                location = string(doc, "location"),
                createdAt = long(doc, "createdAt"),
                views = long(doc, "views"),
                replyClicks = long(doc, "replyClicks"),
                shareClicks = long(doc, "shareClicks"),
            )
        }

    override fun postsByUser(userId: String, cursor: String?, limit: Int): OwnedPostPage {
        val parsed = Cursors.parse(cursor)
        val bind = mapOf(
            "userKey" to userId,
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
        val query = """
            FOR p IN $POSTS
              FILTER p.userKey == @userKey
              FILTER @hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey)
              SORT p.createdAt DESC, p._key DESC
              LIMIT @limit
              LET avatar = DOCUMENT("$AVATARS", p.avatarKey)
              RETURN {
                id: p._key,
                message: p.message,
                location: p.location,
                photoPath: p.photoPath,
                avatarPath: avatar.imagePath,
                createdAt: p.createdAt,
                views: p.views == null ? 0 : p.views,
                replyClicks: p.replyClicks == null ? 0 : p.replyClicks,
                shareClicks: p.shareClicks == null ? 0 : p.shareClicks
              }
        """.trimIndent()
        val rows = queryList(query, bind)
        val page = rows.take(limit)
        val items = page.map { row ->
            OwnedPost(
                id = string(row, "id"),
                message = string(row, "message"),
                location = string(row, "location"),
                photoPath = string(row, "photoPath"),
                avatarPath = row.getAttribute("avatarPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                createdAt = long(row, "createdAt"),
                views = long(row, "views"),
                replyClicks = long(row, "replyClicks"),
                shareClicks = long(row, "shareClicks"),
            )
        }
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return OwnedPostPage(items, next)
    }

    override fun recordView(id: String) {
        bump(id, "views")
    }

    override fun recordReplyClick(id: String) {
        bump(id, "replyClicks")
    }

    override fun recordShareClick(id: String) {
        bump(id, "shareClicks")
    }

    override fun deletePost(id: String) {
        db.collection(POSTS).deleteDocument(id)
    }

    override fun recordVisit(userId: String?, at: Long) {
        val day = SaigonDays.dayKey(at)
        if (userId == null) {
            bumpDaily(day, "visitorVisits")
            return
        }
        exec(
            """
            UPSERT { _key: @day }
            INSERT {
              _key: @day,
              visitorVisits: 0,
              userVisits: 1,
              uniqueUserIds: [@userId],
              replyClicks: 0,
              shareClicks: 0,
              postViews: 0
            }
            UPDATE {
              userVisits: (OLD.userVisits == null ? 0 : OLD.userVisits) + 1,
              uniqueUserIds: (
                @userId IN (OLD.uniqueUserIds == null ? [] : OLD.uniqueUserIds)
                  ? (OLD.uniqueUserIds == null ? [] : OLD.uniqueUserIds)
                  : APPEND(OLD.uniqueUserIds == null ? [] : OLD.uniqueUserIds, @userId)
              )
            } IN $DAY_STATS
            RETURN NEW
            """.trimIndent(),
            mapOf("day" to day, "userId" to userId),
        )
    }

    override fun recordDailyEvent(field: String, at: Long) {
        bumpDaily(SaigonDays.dayKey(at), field)
    }

    override fun statsByDay(now: Long): List<DayStats> {
        val days = SaigonDays.range(now)
        val from = SaigonDays.startMillis(days.first())
        val to = SaigonDays.startMillis(LocalDate.parse(days.last()).plusDays(1).toString())
        val postsByDay = countsByDay(POSTS, from, to)
        val usersByDay = countsByDay(USERS, from, to)
        val stored = queryList(
            "FOR d IN $DAY_STATS FILTER d._key >= @fromDay AND d._key <= @toDay RETURN d",
            mapOf("fromDay" to days.first(), "toDay" to days.last()),
        ).associateBy { it.key }
        return days.map { day ->
            val doc = stored[day]
            DayStats(
                day = day,
                visitorVisits = long(doc, "visitorVisits"),
                userVisits = long(doc, "userVisits"),
                uniqueUserVisits = uniqueUserCount(doc),
                posts = postsByDay[day] ?: 0L,
                newUsers = usersByDay[day] ?: 0L,
                replyClicks = long(doc, "replyClicks"),
                shareClicks = long(doc, "shareClicks"),
                postViews = long(doc, "postViews"),
            )
        }
    }

    private fun bumpDaily(day: String, field: String) {
        val fieldName = DAILY_FIELDS.find { it == field } ?: return
        val visitor = if (fieldName == "visitorVisits") 1 else 0
        val users = if (fieldName == "userVisits") 1 else 0
        val replies = if (fieldName == "replyClicks") 1 else 0
        val shares = if (fieldName == "shareClicks") 1 else 0
        val views = if (fieldName == "postViews") 1 else 0
        exec(
            """
            UPSERT { _key: @day }
            INSERT {
              _key: @day,
              visitorVisits: $visitor,
              userVisits: $users,
              uniqueUserIds: [],
              replyClicks: $replies,
              shareClicks: $shares,
              postViews: $views
            }
            UPDATE { $fieldName: (OLD.$fieldName == null ? 0 : OLD.$fieldName) + 1 }
            IN $DAY_STATS
            RETURN NEW
            """.trimIndent(),
            mapOf("day" to day),
        )
    }

    private fun countsByDay(collection: String, from: Long, to: Long): Map<String, Long> {
        val query = if (collection == POSTS) {
            """
            FOR d IN $POSTS
              FILTER d.createdAt >= @from AND d.createdAt < @to
              COLLECT day = DATE_FORMAT(d.createdAt + 25200000, "%yyyy-%mm-%dd")
              WITH COUNT INTO n
              RETURN { day, n }
            """.trimIndent()
        } else {
            """
            FOR d IN $USERS
              FILTER d.createdAt >= @from AND d.createdAt < @to
              COLLECT day = DATE_FORMAT(d.createdAt + 25200000, "%yyyy-%mm-%dd")
              WITH COUNT INTO n
              RETURN { day, n }
            """.trimIndent()
        }
        return queryList(query, mapOf("from" to from, "to" to to)).associate { string(it, "day") to long(it, "n") }
    }

    private fun uniqueUserCount(doc: BaseDocument?): Long {
        val raw = doc?.getAttribute("uniqueUserIds") ?: return 0L
        return when (raw) {
            is Collection<*> -> raw.size.toLong()
            is Array<*> -> raw.size.toLong()
            else -> 0L
        }
    }

    private fun bump(id: String, field: String) {
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH { $field: (p.$field == null ? 0 : p.$field) + 1 } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to id),
        )
    }

    private fun exec(query: String, bind: Map<String, Any?>) {
        db.query(query, BaseDocument::class.java, bind).use { cursor ->
            while (cursor.hasNext()) cursor.next()
        }
    }

    private fun first(query: String, bind: Map<String, Any?>): BaseDocument? =
        db.query(query, BaseDocument::class.java, bind).use { cursor ->
            if (cursor.hasNext()) cursor.next() else null
        }

    private fun queryList(query: String, bind: Map<String, Any?>): List<BaseDocument> {
        val rows = mutableListOf<BaseDocument>()
        db.query(query, BaseDocument::class.java, bind).use { cursor ->
            while (cursor.hasNext()) rows += cursor.next()
        }
        return rows
    }

    private fun userFrom(doc: BaseDocument) = UserRecord(
        id = doc.key,
        phone = string(doc, "phone"),
        passwordHash = string(doc, "passwordHash"),
        name = string(doc, "name"),
        location = string(doc, "location"),
        profilePhotoPath = doc.getAttribute("profilePhotoPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        language = string(doc, "language").ifBlank { "system" },
        createdAt = long(doc, "createdAt"),
        intro = string(doc, "intro"),
    )

    private fun avatarFrom(doc: BaseDocument) = AvatarRecord(
        id = doc.key,
        userId = string(doc, "userKey"),
        prompt = string(doc, "prompt"),
        imagePath = doc.getAttribute("imagePath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        status = string(doc, "status"),
        error = doc.getAttribute("error")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        createdAt = long(doc, "createdAt"),
    )

    private fun string(doc: BaseDocument, name: String): String = doc.getAttribute(name)?.toString().orEmpty()

    private fun long(doc: BaseDocument?, name: String): Long =
        (doc?.getAttribute(name) as? Number)?.toLong() ?: 0L

    companion object {
        private const val USERS = "users"
        private const val SESSIONS = "sessions"
        private const val AVATARS = "avatars"
        private const val POSTS = "posts"
        private const val DAY_STATS = "dayStats"
        private val DAILY_FIELDS = setOf("visitorVisits", "userVisits", "replyClicks", "shareClicks", "postViews")

        fun open(config: AppConfig): ArangoTownRepository {
            val client = ArangoDB.Builder()
                .host(config.arango.host, config.arango.port)
                .user(config.arango.user)
                .password(config.arango.password)
                .serde(JacksonSerde.of(ContentType.JSON))
                .build()
            val database = client.db(config.arango.database)
            if (!database.exists()) database.create()
            return ArangoTownRepository(database).also { it.ensureSchema() }
        }
    }
}
