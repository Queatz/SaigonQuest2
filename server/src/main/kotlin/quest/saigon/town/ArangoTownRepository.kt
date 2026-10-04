package quest.saigon.town

import com.arangodb.ArangoDB
import com.arangodb.ArangoDBException
import com.arangodb.ArangoDatabase
import com.arangodb.ContentType
import com.arangodb.entity.BaseDocument
import com.arangodb.model.PersistentIndexOptions
import com.arangodb.serde.jackson.JacksonSerde
import quest.saigon.AppConfig
import quest.saigon.Limits
import java.time.LocalDate

class ArangoTownRepository(private val db: ArangoDatabase) : TownRepository {
    fun ensureSchema() {
        listOf(USERS, SESSIONS, AVATARS, MUSIC, POSTS, COMMENTS, COMMENT_WATCHES, NOTIFICATIONS, ADMINS, DAY_STATS).forEach { name ->
            val collection = db.collection(name)
            if (!collection.exists()) collection.create()
        }
        db.collection(USERS).ensurePersistentIndex(listOf("phone"), PersistentIndexOptions().unique(true))
        db.collection(USERS).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(POSTS).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(POSTS).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
        db.collection(MUSIC).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(MUSIC).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
        db.collection(COMMENTS).ensurePersistentIndex(listOf("postKey", "createdAt"), PersistentIndexOptions())
        db.collection(COMMENT_WATCHES).ensurePersistentIndex(listOf("postKey", "userKey"), PersistentIndexOptions().unique(true))
        db.collection(NOTIFICATIONS).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
        db.collection(NOTIFICATIONS).ensurePersistentIndex(listOf("userKey", "readAt"), PersistentIndexOptions())
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
        doc.addAttribute("paused", false)
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
        doc.addAttribute("paused", user.paused)
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

    override fun insertMusic(
        userId: String,
        prompt: String,
        name: String,
        gender: String?,
        instrumental: Boolean,
        createdAt: Long,
    ): MusicRecord {
        val doc = BaseDocument()
        doc.addAttribute("userKey", userId)
        doc.addAttribute("prompt", prompt)
        doc.addAttribute("name", name)
        doc.addAttribute("audioPath", null)
        doc.addAttribute("status", MusicStatus.GENERATING)
        doc.addAttribute("error", null)
        doc.addAttribute("gender", gender)
        doc.addAttribute("instrumental", instrumental)
        doc.addAttribute("lyrics", null)
        doc.addAttribute("createdAt", createdAt)
        val key = db.collection(MUSIC).insertDocument(doc).key
        return musicFrom(doc.apply { this.key = key })
    }

    override fun updateMusic(id: String, status: String, audioPath: String?, error: String?, name: String?, lyrics: String?) {
        val current = findMusic(id) ?: return
        val doc = BaseDocument(id)
        doc.addAttribute("userKey", current.userId)
        doc.addAttribute("prompt", current.prompt)
        doc.addAttribute("name", name?.takeIf { it.isNotBlank() } ?: current.name)
        doc.addAttribute("audioPath", audioPath ?: current.audioPath)
        doc.addAttribute("status", status)
        doc.addAttribute("error", error)
        doc.addAttribute("gender", current.gender)
        doc.addAttribute("instrumental", current.instrumental)
        doc.addAttribute("lyrics", lyrics ?: current.lyrics)
        doc.addAttribute("createdAt", current.createdAt)
        db.collection(MUSIC).replaceDocument(id, doc)
    }

    override fun findMusic(id: String): MusicRecord? =
        db.collection(MUSIC).getDocument(id, BaseDocument::class.java)?.let(::musicFrom)

    override fun musicsFor(userId: String): List<MusicRecord> =
        queryList(
            "FOR m IN $MUSIC FILTER m.userKey == @userKey AND m.status != @hidden SORT m.createdAt DESC RETURN m",
            mapOf("userKey" to userId, "hidden" to MusicStatus.REMOVED),
        ).map(::musicFrom)

    override fun searchMusic(query: String, cursor: String?, limit: Int): MusicPage {
        val parsed = Cursors.parse(cursor)
        val needle = query.trim().lowercase()
        val like = if (needle.isEmpty()) "" else "%${escapeLike(needle)}%"
        val bind = mapOf(
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
            "ready" to MusicStatus.READY,
            "hasQuery" to needle.isNotEmpty(),
            "like" to like,
        )
        val aql = """
            FOR m IN $MUSIC
              FILTER m.status == @ready AND m.audioPath != null AND m.audioPath != ""
              FILTER @hasQuery == false OR LIKE(LOWER(m.name), @like, true) OR LIKE(LOWER(m.prompt), @like, true)
              FILTER @hasCursor == false OR m.createdAt < @cursorAt OR (m.createdAt == @cursorAt AND m._key < @cursorKey)
              SORT m.createdAt DESC, m._key DESC
              LIMIT @limit
              RETURN m
        """.trimIndent()
        val rows = queryList(aql, bind)
        val items = rows.take(limit).map(::musicFrom)
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return MusicPage(items, next)
    }

    override fun hasGeneratingMusic(userId: String): Boolean = first(
        "FOR m IN $MUSIC FILTER m.userKey == @userKey AND m.status == @status LIMIT 1 RETURN m",
        mapOf("userKey" to userId, "status" to MusicStatus.GENERATING),
    ) != null

    override fun deleteMusic(id: String) {
        db.collection(MUSIC).deleteDocument(id)
    }

    override fun musicReferenced(id: String): Boolean = first(
        "FOR p IN $POSTS FILTER p.musicKey == @id LIMIT 1 RETURN p",
        mapOf("id" to id),
    ) != null

    override fun insertPost(post: PostRecord): PostRecord {
        val doc = BaseDocument()
        doc.addAttribute("userKey", post.userId)
        doc.addAttribute("avatarKey", post.avatarId)
        doc.addAttribute("photoPath", post.photoPath)
        doc.addAttribute("extraPhotoPaths", post.extraPhotoPaths)
        doc.addAttribute("message", post.message)
        doc.addAttribute("location", post.location)
        doc.addAttribute("createdAt", post.createdAt)
        doc.addAttribute("views", post.views)
        doc.addAttribute("replyClicks", post.replyClicks)
        doc.addAttribute("shareClicks", post.shareClicks)
        doc.addAttribute("musicKey", post.musicId)
        doc.addAttribute("quickReplies", post.quickReplies)
        doc.addAttribute("commentCount", post.commentCount)
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
              LET track = DOCUMENT("$MUSIC", p.musicKey)
              LET preview = (
                FOR c IN $COMMENTS
                  FILTER c.postKey == p._key
                  SORT c.createdAt DESC, c._key DESC
                  LIMIT ${Limits.COMMENT_PREVIEW}
                  LET commenter = DOCUMENT("$USERS", c.userKey)
                  LET parent = (c.replyToKey == null || c.replyToKey == "") ? null : DOCUMENT("$COMMENTS", c.replyToKey)
                  LET parentAuthor = parent == null ? null : DOCUMENT("$USERS", parent.userKey)
                  RETURN {
                    id: c._key,
                    postId: c.postKey,
                    userId: c.userKey,
                    authorName: commenter.name,
                    authorPhotoPath: commenter.profilePhotoPath,
                    authorLocation: commenter.location,
                    authorIntro: commenter.intro,
                    text: c.text,
                    createdAt: c.createdAt,
                    replyToId: parent == null ? null : parent._key,
                    replyToAuthorName: parentAuthor == null ? null : parentAuthor.name,
                    replyToText: parent == null ? null : parent.text
                  }
              )
              RETURN {
                id: p._key,
                authorId: p.userKey,
                authorName: author.name,
                authorPhotoPath: author.profilePhotoPath,
                authorLocation: author.location,
                authorIntro: author.intro,
                message: p.message,
                location: p.location,
                photoPath: p.photoPath,
                extraPhotoPaths: p.extraPhotoPaths == null ? [] : p.extraPhotoPaths,
                avatarPath: avatar.imagePath,
                createdAt: p.createdAt,
                musicName: track.name,
                musicPath: track.audioPath,
                quickReplies: p.quickReplies == null ? [] : p.quickReplies,
                commentCount: p.commentCount == null ? 0 : p.commentCount,
                previewComments: preview
              }
        """.trimIndent()
        val rows = queryList(query, bind)
        val page = rows.take(limit)
        val items = page.map { row ->
            FeedItem(
                id = string(row, "id"),
                authorId = string(row, "authorId"),
                authorName = string(row, "authorName"),
                authorPhotoPath = row.getAttribute("authorPhotoPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                message = string(row, "message"),
                location = string(row, "location"),
                photoPath = string(row, "photoPath"),
                avatarPath = row.getAttribute("avatarPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                createdAt = long(row, "createdAt"),
                authorLocation = string(row, "authorLocation"),
                authorIntro = string(row, "authorIntro"),
                musicName = row.getAttribute("musicName")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                musicPath = row.getAttribute("musicPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                quickReplies = stringList(row, "quickReplies"),
                commentCount = long(row, "commentCount"),
                previewComments = commentList(row, "previewComments"),
                extraPhotoPaths = stringList(row, "extraPhotoPaths"),
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
                musicId = string(doc, "musicKey").takeIf { it.isNotBlank() && it != "null" },
                quickReplies = stringList(doc, "quickReplies"),
                commentCount = long(doc, "commentCount"),
                extraPhotoPaths = stringList(doc, "extraPhotoPaths"),
            )
        }

    override fun feedItem(id: String): FeedItem? {
        val bind = mapOf("id" to id, "preview" to Limits.COMMENT_PREVIEW)
        val query = """
            FOR p IN $POSTS
              FILTER p._key == @id
              LIMIT 1
              LET author = DOCUMENT("$USERS", p.userKey)
              LET avatar = DOCUMENT("$AVATARS", p.avatarKey)
              LET track = DOCUMENT("$MUSIC", p.musicKey)
              LET preview = (
                FOR c IN $COMMENTS
                  FILTER c.postKey == p._key
                  SORT c.createdAt DESC, c._key DESC
                  LIMIT @preview
                  LET commenter = DOCUMENT("$USERS", c.userKey)
                  LET parent = (c.replyToKey == null || c.replyToKey == "") ? null : DOCUMENT("$COMMENTS", c.replyToKey)
                  LET parentAuthor = parent == null ? null : DOCUMENT("$USERS", parent.userKey)
                  RETURN {
                    id: c._key,
                    postId: c.postKey,
                    userId: c.userKey,
                    authorName: commenter.name,
                    authorPhotoPath: commenter.profilePhotoPath,
                    authorLocation: commenter.location,
                    authorIntro: commenter.intro,
                    text: c.text,
                    createdAt: c.createdAt,
                    replyToId: parent == null ? null : parent._key,
                    replyToAuthorName: parentAuthor == null ? null : parentAuthor.name,
                    replyToText: parent == null ? null : parent.text
                  }
              )
              RETURN {
                id: p._key,
                authorId: p.userKey,
                authorName: author.name,
                authorPhotoPath: author.profilePhotoPath,
                authorLocation: author.location,
                authorIntro: author.intro,
                message: p.message,
                location: p.location,
                photoPath: p.photoPath,
                extraPhotoPaths: p.extraPhotoPaths == null ? [] : p.extraPhotoPaths,
                avatarPath: avatar.imagePath,
                createdAt: p.createdAt,
                musicName: track.name,
                musicPath: track.audioPath,
                quickReplies: p.quickReplies == null ? [] : p.quickReplies,
                commentCount: p.commentCount == null ? 0 : p.commentCount,
                previewComments: preview
              }
        """.trimIndent()
        val row = first(query, bind) ?: return null
        return FeedItem(
            id = string(row, "id"),
            authorId = string(row, "authorId"),
            authorName = string(row, "authorName"),
            authorPhotoPath = row.getAttribute("authorPhotoPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            message = string(row, "message"),
            location = string(row, "location"),
            photoPath = string(row, "photoPath"),
            avatarPath = row.getAttribute("avatarPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            createdAt = long(row, "createdAt"),
            authorLocation = string(row, "authorLocation"),
            authorIntro = string(row, "authorIntro"),
            musicName = row.getAttribute("musicName")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            musicPath = row.getAttribute("musicPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            quickReplies = stringList(row, "quickReplies"),
            commentCount = long(row, "commentCount"),
            previewComments = commentList(row, "previewComments"),
            extraPhotoPaths = stringList(row, "extraPhotoPaths"),
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
              LET track = DOCUMENT("$MUSIC", p.musicKey)
              RETURN {
                id: p._key,
                message: p.message,
                location: p.location,
                photoPath: p.photoPath,
                avatarPath: avatar.imagePath,
                createdAt: p.createdAt,
                views: p.views == null ? 0 : p.views,
                replyClicks: p.replyClicks == null ? 0 : p.replyClicks,
                shareClicks: p.shareClicks == null ? 0 : p.shareClicks,
                musicName: track.name
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
                musicName = row.getAttribute("musicName")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
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

    override fun updateQuickReplies(id: String, replies: List<String>) {
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH { quickReplies: @replies } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to id, "replies" to replies.take(Limits.QUICK_REPLY_COUNT)),
        )
    }

    override fun deletePost(id: String) {
        exec("FOR c IN $COMMENTS FILTER c.postKey == @id REMOVE c IN $COMMENTS RETURN OLD", mapOf("id" to id))
        exec("FOR w IN $COMMENT_WATCHES FILTER w.postKey == @id REMOVE w IN $COMMENT_WATCHES RETURN OLD", mapOf("id" to id))
        db.collection(POSTS).deleteDocument(id)
    }

    override fun insertComment(comment: CommentRecord): CommentRecord {
        val doc = BaseDocument()
        doc.addAttribute("postKey", comment.postId)
        doc.addAttribute("userKey", comment.userId)
        doc.addAttribute("text", comment.text)
        doc.addAttribute("replyToKey", comment.replyToId)
        doc.addAttribute("createdAt", comment.createdAt)
        val key = db.collection(COMMENTS).insertDocument(doc).key
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH { commentCount: (p.commentCount == null ? 0 : p.commentCount) + 1 } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to comment.postId),
        )
        return comment.copy(id = key)
    }

    override fun deleteComment(id: String) {
        val comment = findComment(id) ?: return
        db.collection(COMMENTS).deleteDocument(id)
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH { commentCount: (p.commentCount == null || p.commentCount < 1 ? 0 : p.commentCount - 1) } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to comment.postId),
        )
    }

    override fun findComment(id: String): CommentRecord? =
        db.collection(COMMENTS).getDocument(id, BaseDocument::class.java)?.let { doc ->
            CommentRecord(
                id = doc.key,
                postId = string(doc, "postKey"),
                userId = string(doc, "userKey"),
                text = string(doc, "text"),
                replyToId = string(doc, "replyToKey").takeIf { it.isNotBlank() && it != "null" },
                createdAt = long(doc, "createdAt"),
            )
        }

    override fun commentItem(id: String): CommentItem? {
        val row = first(
            """
            FOR c IN $COMMENTS
              FILTER c._key == @id
              LIMIT 1
              LET commenter = DOCUMENT("$USERS", c.userKey)
              LET parent = (c.replyToKey == null || c.replyToKey == "") ? null : DOCUMENT("$COMMENTS", c.replyToKey)
              LET parentAuthor = parent == null ? null : DOCUMENT("$USERS", parent.userKey)
              RETURN {
                id: c._key,
                postId: c.postKey,
                userId: c.userKey,
                authorName: commenter.name,
                authorPhotoPath: commenter.profilePhotoPath,
                authorLocation: commenter.location,
                authorIntro: commenter.intro,
                text: c.text,
                createdAt: c.createdAt,
                replyToId: parent == null ? null : parent._key,
                replyToAuthorName: parentAuthor == null ? null : parentAuthor.name,
                replyToText: parent == null ? null : parent.text
              }
            """.trimIndent(),
            mapOf("id" to id),
        ) ?: return null
        return commentFromAny(row)
    }

    override fun commentsForPost(postId: String, cursor: String?, limit: Int): CommentPage {
        val parsed = Cursors.parse(cursor)
        val bind = mapOf(
            "postKey" to postId,
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
        val rows = queryList(
            """
            FOR c IN $COMMENTS
              FILTER c.postKey == @postKey
              FILTER @hasCursor == false OR c.createdAt < @cursorAt OR (c.createdAt == @cursorAt AND c._key < @cursorKey)
              SORT c.createdAt DESC, c._key DESC
              LIMIT @limit
              LET commenter = DOCUMENT("$USERS", c.userKey)
              LET parent = (c.replyToKey == null || c.replyToKey == "") ? null : DOCUMENT("$COMMENTS", c.replyToKey)
              LET parentAuthor = parent == null ? null : DOCUMENT("$USERS", parent.userKey)
              RETURN {
                id: c._key,
                postId: c.postKey,
                userId: c.userKey,
                authorName: commenter.name,
                authorPhotoPath: commenter.profilePhotoPath,
                authorLocation: commenter.location,
                authorIntro: commenter.intro,
                text: c.text,
                createdAt: c.createdAt,
                replyToId: parent == null ? null : parent._key,
                replyToAuthorName: parentAuthor == null ? null : parentAuthor.name,
                replyToText: parent == null ? null : parent.text
              }
            """.trimIndent(),
            bind,
        )
        val items = rows.take(limit).mapNotNull(::commentFromAny)
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        val total = findPost(postId)?.commentCount ?: items.size.toLong()
        return CommentPage(items, next, total)
    }

    override fun latestComments(postId: String, limit: Int): List<CommentItem> =
        queryList(
            """
            FOR c IN $COMMENTS
              FILTER c.postKey == @postKey
              SORT c.createdAt DESC, c._key DESC
              LIMIT @limit
              LET commenter = DOCUMENT("$USERS", c.userKey)
              LET parent = (c.replyToKey == null || c.replyToKey == "") ? null : DOCUMENT("$COMMENTS", c.replyToKey)
              LET parentAuthor = parent == null ? null : DOCUMENT("$USERS", parent.userKey)
              RETURN {
                id: c._key,
                postId: c.postKey,
                userId: c.userKey,
                authorName: commenter.name,
                authorPhotoPath: commenter.profilePhotoPath,
                authorLocation: commenter.location,
                authorIntro: commenter.intro,
                text: c.text,
                createdAt: c.createdAt,
                replyToId: parent == null ? null : parent._key,
                replyToAuthorName: parentAuthor == null ? null : parentAuthor.name,
                replyToText: parent == null ? null : parent.text
              }
            """.trimIndent(),
            mapOf("postKey" to postId, "limit" to limit),
        ).mapNotNull(::commentFromAny)

    override fun armCommentWatch(postId: String, userId: String) {
        exec(
            """
            UPSERT { postKey: @postKey, userKey: @userKey }
            INSERT { postKey: @postKey, userKey: @userKey, armed: true }
            UPDATE { armed: true }
            IN $COMMENT_WATCHES
            RETURN NEW
            """.trimIndent(),
            mapOf("postKey" to postId, "userKey" to userId),
        )
    }

    override fun disarmCommentWatch(postId: String, userId: String) {
        exec(
            """
            FOR w IN $COMMENT_WATCHES
              FILTER w.postKey == @postKey AND w.userKey == @userKey
              UPDATE w WITH { armed: false } IN $COMMENT_WATCHES
              RETURN NEW
            """.trimIndent(),
            mapOf("postKey" to postId, "userKey" to userId),
        )
    }

    override fun armedWatchers(postId: String): List<String> =
        queryList(
            """
            FOR w IN $COMMENT_WATCHES
              FILTER w.postKey == @postKey AND w.armed == true
              RETURN w
            """.trimIndent(),
            mapOf("postKey" to postId),
        ).map { string(it, "userKey") }.filter { it.isNotBlank() }

    override fun insertNotification(notification: NotificationRecord): NotificationRecord {
        val doc = BaseDocument()
        doc.addAttribute("userKey", notification.userId)
        doc.addAttribute("type", notification.type)
        doc.addAttribute("postKey", notification.postId)
        doc.addAttribute("commentKey", notification.commentId)
        doc.addAttribute("actorKey", notification.actorId)
        doc.addAttribute("actorName", notification.actorName)
        doc.addAttribute("preview", notification.preview)
        doc.addAttribute("createdAt", notification.createdAt)
        doc.addAttribute("readAt", notification.readAt)
        val key = db.collection(NOTIFICATIONS).insertDocument(doc).key
        return notification.copy(id = key)
    }

    override fun notificationsFor(userId: String, cursor: String?, limit: Int): NotificationPage {
        val parsed = Cursors.parse(cursor)
        val bind = mapOf(
            "userKey" to userId,
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
        val rows = queryList(
            """
            FOR n IN $NOTIFICATIONS
              FILTER n.userKey == @userKey
              FILTER @hasCursor == false OR n.createdAt < @cursorAt OR (n.createdAt == @cursorAt AND n._key < @cursorKey)
              SORT n.createdAt DESC, n._key DESC
              LIMIT @limit
              RETURN n
            """.trimIndent(),
            bind,
        )
        val items = rows.take(limit).map(::notificationFrom)
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return NotificationPage(items, next, unreadNotificationCount(userId))
    }

    override fun findNotification(id: String): NotificationRecord? =
        db.collection(NOTIFICATIONS).getDocument(id, BaseDocument::class.java)?.let(::notificationFrom)

    override fun markAllNotificationsRead(userId: String, readAt: Long): Long {
        exec(
            """
            FOR n IN $NOTIFICATIONS
              FILTER n.userKey == @userKey AND n.readAt == null
              UPDATE n WITH { readAt: @readAt } IN $NOTIFICATIONS
              RETURN NEW
            """.trimIndent(),
            mapOf("userKey" to userId, "readAt" to readAt),
        )
        return unreadNotificationCount(userId)
    }

    override fun markNotificationRead(id: String, readAt: Long): NotificationRecord? {
        val current = findNotification(id) ?: return null
        if (current.readAt != null) return current
        val updated = current.copy(readAt = readAt)
        val doc = BaseDocument(id)
        doc.addAttribute("userKey", updated.userId)
        doc.addAttribute("type", updated.type)
        doc.addAttribute("postKey", updated.postId)
        doc.addAttribute("commentKey", updated.commentId)
        doc.addAttribute("actorKey", updated.actorId)
        doc.addAttribute("actorName", updated.actorName)
        doc.addAttribute("preview", updated.preview)
        doc.addAttribute("createdAt", updated.createdAt)
        doc.addAttribute("readAt", updated.readAt)
        db.collection(NOTIFICATIONS).replaceDocument(id, doc)
        return updated
    }

    override fun unreadNotificationCount(userId: String): Long {
        val row = first(
            """
            FOR n IN $NOTIFICATIONS
              FILTER n.userKey == @userKey AND n.readAt == null
              COLLECT WITH COUNT INTO total
              RETURN { total }
            """.trimIndent(),
            mapOf("userKey" to userId),
        )
        return long(row, "total")
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

    override fun allTimeStats() = AllTimeStats(
        totalUsers = db.collection(USERS).count().count,
        totalPosts = db.collection(POSTS).count().count,
    )

    override fun findAdmin(userId: String): AdminRecord? =
        db.collection(ADMINS).getDocument(userId, BaseDocument::class.java)?.let { doc ->
            AdminRecord(userId = doc.key, createdAt = long(doc, "createdAt"))
        }

    override fun claimFirstAdmin(userId: String, createdAt: Long): Boolean {
        if (findAdmin(userId) != null) return true
        if (db.collection(ADMINS).count().count > 0) return false
        val doc = BaseDocument(userId)
        doc.addAttribute("createdAt", createdAt)
        return try {
            db.collection(ADMINS).insertDocument(doc)
            true
        } catch (_: ArangoDBException) {
            findAdmin(userId) != null
        }
    }

    override fun usersPage(cursor: String?, limit: Int): AdminUserPage {
        val parsed = Cursors.parse(cursor)
        val bind = mapOf(
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
        val query = """
            FOR u IN $USERS
              FILTER @hasCursor == false OR u.createdAt < @cursorAt OR (u.createdAt == @cursorAt AND u._key < @cursorKey)
              SORT u.createdAt DESC, u._key DESC
              LIMIT @limit
              LET postCount = LENGTH(FOR p IN $POSTS FILTER p.userKey == u._key RETURN 1)
              RETURN {
                id: u._key,
                name: u.name,
                intro: u.intro,
                profilePhotoPath: u.profilePhotoPath,
                createdAt: u.createdAt,
                postCount: postCount,
                paused: u.paused == true
              }
        """.trimIndent()
        val rows = queryList(query, bind)
        val page = rows.take(limit)
        val items = page.map { row ->
            AdminUser(
                id = string(row, "id"),
                name = string(row, "name"),
                intro = string(row, "intro"),
                profilePhotoPath = row.getAttribute("profilePhotoPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                createdAt = long(row, "createdAt"),
                postCount = long(row, "postCount"),
                paused = boolean(row, "paused"),
            )
        }
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return AdminUserPage(items, next)
    }

    override fun setUserPaused(userId: String, paused: Boolean): UserRecord? {
        val user = findUser(userId) ?: return null
        val next = user.copy(paused = paused)
        updateUser(next)
        return findUser(userId) ?: next
    }

    override fun adminPosts(cursor: String?, limit: Int): AdminPostPage {
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
              LET track = DOCUMENT("$MUSIC", p.musicKey)
              RETURN {
                id: p._key,
                authorId: p.userKey,
                authorName: author.name,
                authorPhotoPath: author.profilePhotoPath,
                message: p.message,
                location: p.location,
                photoPath: p.photoPath,
                avatarPath: avatar.imagePath,
                createdAt: p.createdAt,
                views: p.views == null ? 0 : p.views,
                replyClicks: p.replyClicks == null ? 0 : p.replyClicks,
                shareClicks: p.shareClicks == null ? 0 : p.shareClicks,
                musicName: track.name
              }
        """.trimIndent()
        val rows = queryList(query, bind)
        val page = rows.take(limit)
        val items = page.map { row ->
            AdminPost(
                id = string(row, "id"),
                authorId = string(row, "authorId"),
                authorName = string(row, "authorName"),
                authorPhotoPath = row.getAttribute("authorPhotoPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                message = string(row, "message"),
                location = string(row, "location"),
                photoPath = string(row, "photoPath"),
                avatarPath = row.getAttribute("avatarPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                createdAt = long(row, "createdAt"),
                views = long(row, "views"),
                replyClicks = long(row, "replyClicks"),
                shareClicks = long(row, "shareClicks"),
                musicName = row.getAttribute("musicName")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            )
        }
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return AdminPostPage(items, next)
    }

    override fun adminComments(cursor: String?, limit: Int): CommentPage {
        val parsed = Cursors.parse(cursor)
        val bind = mapOf(
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
        val rows = queryList(
            """
            FOR c IN $COMMENTS
              FILTER @hasCursor == false OR c.createdAt < @cursorAt OR (c.createdAt == @cursorAt AND c._key < @cursorKey)
              SORT c.createdAt DESC, c._key DESC
              LIMIT @limit
              LET commenter = DOCUMENT("$USERS", c.userKey)
              LET parent = (c.replyToKey == null || c.replyToKey == "") ? null : DOCUMENT("$COMMENTS", c.replyToKey)
              LET parentAuthor = parent == null ? null : DOCUMENT("$USERS", parent.userKey)
              LET post = DOCUMENT("$POSTS", c.postKey)
              RETURN {
                id: c._key,
                postId: c.postKey,
                userId: c.userKey,
                authorName: commenter.name,
                authorPhotoPath: commenter.profilePhotoPath,
                authorLocation: commenter.location,
                authorIntro: commenter.intro,
                text: c.text,
                createdAt: c.createdAt,
                replyToId: parent == null ? null : parent._key,
                replyToAuthorName: parentAuthor == null ? null : parentAuthor.name,
                replyToText: parent == null ? null : parent.text,
                postText: post == null ? null : post.message
              }
            """.trimIndent(),
            bind,
        )
        val items = rows.take(limit).mapNotNull(::commentFromAny)
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return CommentPage(items, next, db.collection(COMMENTS).count().count)
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
        paused = boolean(doc, "paused"),
    )

    private fun musicFrom(doc: BaseDocument) = MusicRecord(
        id = doc.key,
        userId = string(doc, "userKey"),
        prompt = string(doc, "prompt"),
        name = string(doc, "name"),
        audioPath = doc.getAttribute("audioPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        status = string(doc, "status"),
        error = doc.getAttribute("error")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        gender = doc.getAttribute("gender")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        instrumental = when (val raw = doc.getAttribute("instrumental")) {
            is Boolean -> raw
            else -> raw?.toString().equals("true", true)
        },
        createdAt = long(doc, "createdAt"),
        lyrics = doc.getAttribute("lyrics")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
    )

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun avatarFrom(doc: BaseDocument) = AvatarRecord(
        id = doc.key,
        userId = string(doc, "userKey"),
        prompt = string(doc, "prompt"),
        imagePath = doc.getAttribute("imagePath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        status = string(doc, "status"),
        error = doc.getAttribute("error")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        createdAt = long(doc, "createdAt"),
    )

    private fun notificationFrom(doc: BaseDocument) = NotificationRecord(
        id = doc.key,
        userId = string(doc, "userKey"),
        type = string(doc, "type"),
        postId = string(doc, "postKey"),
        commentId = string(doc, "commentKey"),
        actorId = string(doc, "actorKey"),
        actorName = string(doc, "actorName"),
        preview = string(doc, "preview"),
        createdAt = long(doc, "createdAt"),
        readAt = doc.getAttribute("readAt")?.let { value -> (value as? Number)?.toLong() },
    )

    private fun stringList(doc: BaseDocument, name: String): List<String> {
        val raw = doc.getAttribute(name) ?: return emptyList()
        return when (raw) {
            is Collection<*> -> raw.mapNotNull { it?.toString()?.takeIf { value -> value.isNotBlank() && value != "null" } }
            is Array<*> -> raw.mapNotNull { it?.toString()?.takeIf { value -> value.isNotBlank() && value != "null" } }
            else -> emptyList()
        }
    }

    private fun commentList(doc: BaseDocument, name: String): List<CommentItem> {
        val raw = doc.getAttribute(name) ?: return emptyList()
        val rows = when (raw) {
            is Collection<*> -> raw
            is Array<*> -> raw.toList()
            else -> return emptyList()
        }
        return rows.mapNotNull(::commentFromAny)
    }

    private fun commentFromAny(row: Any?): CommentItem? {
        if (row == null) return null
        fun read(name: String): Any? = when (row) {
            is BaseDocument -> row.getAttribute(name)
            is Map<*, *> -> row[name]
            else -> null
        }
        fun str(name: String) = read(name)?.toString()?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        fun strOrNull(name: String) = read(name)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        fun lng(name: String) = (read(name) as? Number)?.toLong() ?: 0L
        val id = str("id")
        if (id.isBlank()) return null
        return CommentItem(
            id = id,
            postId = str("postId"),
            userId = str("userId"),
            authorName = str("authorName"),
            authorPhotoPath = strOrNull("authorPhotoPath"),
            authorLocation = str("authorLocation"),
            authorIntro = str("authorIntro"),
            text = str("text"),
            createdAt = lng("createdAt"),
            replyToId = strOrNull("replyToId"),
            replyToAuthorName = strOrNull("replyToAuthorName"),
            replyToText = strOrNull("replyToText"),
            postText = strOrNull("postText"),
        )
    }

    private fun string(doc: BaseDocument, name: String): String = doc.getAttribute(name)?.toString().orEmpty()

    private fun long(doc: BaseDocument?, name: String): Long =
        (doc?.getAttribute(name) as? Number)?.toLong() ?: 0L

    private fun boolean(doc: BaseDocument, name: String): Boolean = when (val raw = doc.getAttribute(name)) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", ignoreCase = true)
        else -> false
    }

    companion object {
        private const val USERS = "users"
        private const val SESSIONS = "sessions"
        private const val AVATARS = "avatars"
        private const val MUSIC = "music"
        private const val POSTS = "posts"
        private const val COMMENTS = "comments"
        private const val COMMENT_WATCHES = "commentWatches"
        private const val NOTIFICATIONS = "notifications"
        private const val ADMINS = "admins"
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
