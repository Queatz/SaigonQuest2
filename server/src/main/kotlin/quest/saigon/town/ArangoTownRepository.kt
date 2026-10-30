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
import quest.saigon.decodeLocationToken
import java.time.LocalDate

class ArangoTownRepository(private val db: ArangoDatabase) : TownRepository {
    fun ensureSchema() {
        listOf(
            USERS, SESSIONS, AVATARS, MUSIC, POSTS, COMMENTS, COMMENT_WATCHES,
            NOTIFICATIONS, PUSH_SUBSCRIPTIONS, ADMINS, DAY_STATS, COMMUNITIES, MEMBERSHIPS, EVENT_ATTENDANCES,
            POLL_VOTES, QUESTION_ANSWERS, FEATURE_SUGGESTIONS, FEATURE_VOTES, PRODUCTS, PRODUCT_ORDERS,
            CHATS, CHAT_MEMBERS, CHAT_MESSAGES, CHAT_REACTIONS,
        ).forEach { name ->
            val collection = db.collection(name)
            if (!collection.exists()) collection.create()
        }
        db.collection(USERS).ensurePersistentIndex(listOf("phone"), PersistentIndexOptions().unique(true))
        db.collection(USERS).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(USERS).ensurePersistentIndex(listOf("lastActiveAt"), PersistentIndexOptions())
        db.collection(POSTS).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(POSTS).ensurePersistentIndex(listOf("eventStartAt"), PersistentIndexOptions())
        db.collection(POSTS).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
        db.collection(POSTS).ensurePersistentIndex(listOf("communityKey", "createdAt"), PersistentIndexOptions())
        db.collection(COMMUNITIES).ensurePersistentIndex(listOf("slug"), PersistentIndexOptions().unique(true))
        db.collection(COMMUNITIES).ensurePersistentIndex(listOf("memberCount"), PersistentIndexOptions())
        db.collection(MEMBERSHIPS).ensurePersistentIndex(listOf("userKey", "joinedAt"), PersistentIndexOptions())
        db.collection(MEMBERSHIPS).ensurePersistentIndex(listOf("communityKey", "joinedAt"), PersistentIndexOptions())
        db.collection(EVENT_ATTENDANCES).ensurePersistentIndex(listOf("postKey", "userKey"), PersistentIndexOptions().unique(true))
        db.collection(EVENT_ATTENDANCES).ensurePersistentIndex(listOf("postKey", "joinedAt"), PersistentIndexOptions())
        db.collection(POLL_VOTES).ensurePersistentIndex(listOf("postKey", "userKey"), PersistentIndexOptions().unique(true))
        db.collection(POLL_VOTES).ensurePersistentIndex(listOf("postKey", "optionIndex", "votedAt"), PersistentIndexOptions())
        db.collection(QUESTION_ANSWERS).ensurePersistentIndex(listOf("postKey", "userKey"), PersistentIndexOptions().unique(true))
        db.collection(QUESTION_ANSWERS).ensurePersistentIndex(listOf("postKey", "createdAt"), PersistentIndexOptions())
        db.collection(FEATURE_SUGGESTIONS).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(FEATURE_VOTES).ensurePersistentIndex(listOf("suggestionKey", "userKey"), PersistentIndexOptions().unique(true))
        db.collection(FEATURE_VOTES).ensurePersistentIndex(listOf("suggestionKey"), PersistentIndexOptions())
        db.collection(PRODUCTS).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
        db.collection(PRODUCT_ORDERS).ensurePersistentIndex(listOf("sellerKey", "createdAt"), PersistentIndexOptions())
        db.collection(MUSIC).ensurePersistentIndex(listOf("createdAt"), PersistentIndexOptions())
        db.collection(MUSIC).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
        db.collection(COMMENTS).ensurePersistentIndex(listOf("postKey", "createdAt"), PersistentIndexOptions())
        db.collection(COMMENT_WATCHES).ensurePersistentIndex(listOf("postKey", "userKey"), PersistentIndexOptions().unique(true))
        db.collection(NOTIFICATIONS).ensurePersistentIndex(listOf("userKey", "createdAt"), PersistentIndexOptions())
        db.collection(NOTIFICATIONS).ensurePersistentIndex(listOf("userKey", "readAt"), PersistentIndexOptions())
        db.collection(PUSH_SUBSCRIPTIONS).ensurePersistentIndex(listOf("endpoint"), PersistentIndexOptions().unique(true))
        db.collection(PUSH_SUBSCRIPTIONS).ensurePersistentIndex(listOf("userKey"), PersistentIndexOptions())
        db.collection(CHATS).ensurePersistentIndex(listOf("pairKey"), PersistentIndexOptions().unique(true).sparse(true))
        db.collection(CHATS).ensurePersistentIndex(listOf("lastMessageAt"), PersistentIndexOptions())
        db.collection(CHAT_MEMBERS).ensurePersistentIndex(listOf("chatKey", "userKey"), PersistentIndexOptions().unique(true))
        db.collection(CHAT_MEMBERS).ensurePersistentIndex(listOf("userKey"), PersistentIndexOptions())
        db.collection(CHAT_MESSAGES).ensurePersistentIndex(listOf("chatKey", "createdAt"), PersistentIndexOptions())
        db.collection(CHAT_REACTIONS).ensurePersistentIndex(listOf("messageKey", "userKey", "emoji"), PersistentIndexOptions().unique(true))
        db.collection(CHAT_REACTIONS).ensurePersistentIndex(listOf("chatKey"), PersistentIndexOptions())
        db.collection(CHAT_REACTIONS).ensurePersistentIndex(listOf("messageKey"), PersistentIndexOptions())
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
        doc.addAttribute("lastActiveAt", createdAt)
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
        doc.addAttribute("placeId", user.placeId)
        doc.addAttribute("lat", user.lat)
        doc.addAttribute("lng", user.lng)
        doc.addAttribute("intro", user.intro)
        doc.addAttribute("profilePhotoPath", user.profilePhotoPath)
        doc.addAttribute("language", user.language)
        doc.addAttribute("paused", user.paused)
        doc.addAttribute("createdAt", user.createdAt)
        doc.addAttribute("lastActiveAt", user.lastActiveAt)
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
        doc.addAttribute("communityKey", post.communityId)
        doc.addAttribute("communityOnly", post.communityOnly)
        doc.addAttribute("placeId", post.placeId)
        doc.addAttribute("lat", post.lat)
        doc.addAttribute("lng", post.lng)
        doc.addAttribute("eventStartAt", post.eventStartAt)
        doc.addAttribute("eventEndAt", post.eventEndAt)
        doc.addAttribute("eventPrice", post.eventPrice)
        doc.addAttribute("attendeeCount", post.attendeeCount)
        doc.addAttribute("eventHourNotified", post.eventHourNotified)
        doc.addAttribute("eventNowNotified", post.eventNowNotified)
        doc.addAttribute("pollOptions", post.pollOptions)
        doc.addAttribute("hasQuestion", post.hasQuestion)
        doc.addAttribute("questionTitle", post.questionTitle)
        doc.addAttribute("ctaTitle", post.ctaTitle)
        doc.addAttribute("ctaText", post.ctaText)
        doc.addAttribute("ctaDestination", post.ctaDestination)
        doc.addAttribute("ctaUrl", post.ctaUrl)
        val key = db.collection(POSTS).insertDocument(doc).key
        return post.copy(id = key)
    }

    override fun feed(cursor: String?, limit: Int, viewerId: String?): FeedPage = pageFeed(
        "${communityVisibleFilter()} AND (@hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey))",
        cursorBind(cursor, limit) + mapOf("viewerId" to viewerId.orEmpty()),
        limit,
    )

    override fun searchPosts(query: String, cursor: String?, limit: Int, viewerId: String?): FeedPage {
        val where = """
            ${communityVisibleFilter()}
            AND (
              @query == ""
              OR CONTAINS(LOWER(TO_STRING(p.message == null ? "" : p.message)), @query)
              OR CONTAINS(LOWER(TO_STRING(p.location == null ? "" : p.location)), @query)
              OR (
                DOCUMENT("$USERS", p.userKey) != null
                AND CONTAINS(
                  LOWER(TO_STRING(DOCUMENT("$USERS", p.userKey).name)),
                  @query
                )
              )
              OR (
                DOCUMENT("$COMMUNITIES", p.communityKey) != null
                AND CONTAINS(
                  LOWER(TO_STRING(DOCUMENT("$COMMUNITIES", p.communityKey).name)),
                  @query
                )
              )
              OR (p.eventStartAt != null AND CONTAINS(LOWER(TO_STRING(p.eventStartAt)), @query))
              OR (p.eventEndAt != null AND CONTAINS(LOWER(TO_STRING(p.eventEndAt)), @query))
              OR (
                p.eventStartAt != null
                AND CONTAINS(
                  LOWER((p.eventPrice == null OR p.eventPrice == 0) ? "free" : TO_STRING(p.eventPrice)),
                  @query
                )
              )
              OR LENGTH(
                FOR option IN (p.pollOptions == null ? [] : p.pollOptions)
                  FILTER CONTAINS(LOWER(TO_STRING(option == null ? "" : option)), @query)
                  RETURN 1
              ) > 0
              OR CONTAINS(LOWER(TO_STRING(p.questionTitle == null ? "" : p.questionTitle)), @query)
              OR CONTAINS(LOWER(TO_STRING(p.ctaTitle == null ? "" : p.ctaTitle)), @query)
              OR CONTAINS(LOWER(TO_STRING(p.ctaText == null ? "" : p.ctaText)), @query)
              OR CONTAINS(LOWER(TO_STRING(p.ctaDestination == null ? "" : p.ctaDestination)), @query)
              OR CONTAINS(LOWER(TO_STRING(p.ctaUrl == null ? "" : p.ctaUrl)), @query)
            )
            AND (@hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey))
        """.trimIndent()
        return pageFeed(
            where,
            cursorBind(cursor, limit) + mapOf("query" to query.trim().lowercase(), "viewerId" to viewerId.orEmpty()),
            limit,
        )
    }

    override fun communityFeed(communityId: String, cursor: String?, limit: Int, viewerId: String?): FeedPage = pageFeed(
        "p.communityKey == @communityKey AND (@hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey))",
        cursorBind(cursor, limit) + mapOf("communityKey" to communityId, "viewerId" to viewerId.orEmpty()),
        limit,
    )

    private fun pageFeed(where: String, bind: Map<String, Any?>, limit: Int): FeedPage {
        val query = """
            FOR p IN $POSTS
              FILTER $where
              SORT p.createdAt DESC, p._key DESC
              LIMIT @limit
              LET author = DOCUMENT("$USERS", p.userKey)
              LET avatar = DOCUMENT("$AVATARS", p.avatarKey)
              LET track = DOCUMENT("$MUSIC", p.musicKey)
              LET community = DOCUMENT("$COMMUNITIES", p.communityKey)
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
                previewComments: preview,
                communityId: community == null ? null : community._key,
                communityName: community == null ? null : community.name,
                communityEmoji: community == null ? null : community.emoji,
                communityOnly: p.communityOnly == true,
                placeId: p.placeId,
                lat: p.lat,
                lng: p.lng,
                eventStartAt: p.eventStartAt,
                eventEndAt: p.eventEndAt,
                eventPrice: p.eventPrice,
                attendeeCount: p.attendeeCount == null ? 0 : p.attendeeCount,
                joined: @viewerId != "" && LENGTH(
                  FOR a IN $EVENT_ATTENDANCES
                    FILTER a.postKey == p._key AND a.userKey == @viewerId
                    LIMIT 1
                    RETURN 1
                ) > 0,
                attendees: (
                  FOR a IN $EVENT_ATTENDANCES
                    FILTER a.postKey == p._key
                    SORT a.joinedAt DESC
                    LIMIT 5
                    LET person = DOCUMENT("$USERS", a.userKey)
                    FILTER person != null
                    RETURN {
                      id: person._key,
                      name: person.name,
                      profilePhotoPath: person.profilePhotoPath,
                      location: person.location,
                      intro: person.intro,
                      joinedAt: a.joinedAt
                    }
                ),
                ${pollOptionsAql()},
                ${questionAql()},
                ${ctaAql()}
              }
        """.trimIndent()
        val rows = queryList(query, bind)
        val page = rows.take(limit)
        val items = page.map(::feedItemFrom)
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return FeedPage(items, next)
    }

    override fun findPost(id: String): PostRecord? =
        db.collection(POSTS).getDocument(id, BaseDocument::class.java)?.let { doc ->
            PostRecord(
                id = doc.key,
                userId = string(doc, "userKey"),
                avatarId = string(doc, "avatarKey").takeIf { it.isNotBlank() && it != "null" },
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
                communityId = string(doc, "communityKey").takeIf { it.isNotBlank() && it != "null" },
                communityOnly = boolean(doc, "communityOnly"),
                placeId = optional(doc, "placeId"),
                lat = doubleOrNull(doc, "lat"),
                lng = doubleOrNull(doc, "lng"),
                eventStartAt = longOrNull(doc, "eventStartAt"),
                eventEndAt = longOrNull(doc, "eventEndAt"),
                eventPrice = longOrNull(doc, "eventPrice"),
                attendeeCount = long(doc, "attendeeCount"),
                eventHourNotified = boolean(doc, "eventHourNotified"),
                eventNowNotified = boolean(doc, "eventNowNotified"),
                pollOptions = stringList(doc, "pollOptions"),
                hasQuestion = boolean(doc, "hasQuestion"),
                questionTitle = string(doc, "questionTitle"),
                ctaTitle = string(doc, "ctaTitle"),
                ctaText = string(doc, "ctaText"),
                ctaDestination = string(doc, "ctaDestination"),
                ctaUrl = string(doc, "ctaUrl"),
            )
        }

    override fun feedItem(id: String, viewerId: String?): FeedItem? {
        val bind = mapOf("id" to id, "preview" to Limits.COMMENT_PREVIEW, "viewerId" to viewerId.orEmpty())
        val query = """
            FOR p IN $POSTS
              FILTER p._key == @id
              LIMIT 1
              LET author = DOCUMENT("$USERS", p.userKey)
              LET avatar = DOCUMENT("$AVATARS", p.avatarKey)
              LET track = DOCUMENT("$MUSIC", p.musicKey)
              LET community = DOCUMENT("$COMMUNITIES", p.communityKey)
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
                previewComments: preview,
                communityId: community == null ? null : community._key,
                communityName: community == null ? null : community.name,
                communityEmoji: community == null ? null : community.emoji,
                communityOnly: p.communityOnly == true,
                placeId: p.placeId,
                lat: p.lat,
                lng: p.lng,
                eventStartAt: p.eventStartAt,
                eventEndAt: p.eventEndAt,
                eventPrice: p.eventPrice,
                attendeeCount: p.attendeeCount == null ? 0 : p.attendeeCount,
                joined: @viewerId != "" && LENGTH(
                  FOR a IN $EVENT_ATTENDANCES
                    FILTER a.postKey == p._key AND a.userKey == @viewerId
                    LIMIT 1
                    RETURN 1
                ) > 0,
                attendees: (
                  FOR a IN $EVENT_ATTENDANCES
                    FILTER a.postKey == p._key
                    SORT a.joinedAt DESC
                    LIMIT 5
                    LET person = DOCUMENT("$USERS", a.userKey)
                    FILTER person != null
                    RETURN {
                      id: person._key,
                      name: person.name,
                      profilePhotoPath: person.profilePhotoPath,
                      location: person.location,
                      intro: person.intro,
                      joinedAt: a.joinedAt
                    }
                ),
                ${pollOptionsAql()},
                ${questionAql()},
                ${ctaAql()}
              }
        """.trimIndent()
        val row = first(query, bind) ?: return null
        return feedItemFrom(row)
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
              LET community = DOCUMENT("$COMMUNITIES", p.communityKey)
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
                musicName: track.name,
                communityId: community == null ? null : community._key,
                communityName: community == null ? null : community.name,
                communityEmoji: community == null ? null : community.emoji,
                communityOnly: p.communityOnly == true
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
                communityId = optional(row, "communityId"),
                communityName = optional(row, "communityName"),
                communityEmoji = optional(row, "communityEmoji"),
                communityOnly = boolean(row, "communityOnly"),
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

    override fun updatePostMessage(id: String, message: String) {
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH { message: @message } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to id, "message" to message),
        )
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
        exec("FOR a IN $EVENT_ATTENDANCES FILTER a.postKey == @id REMOVE a IN $EVENT_ATTENDANCES RETURN OLD", mapOf("id" to id))
        exec("FOR v IN $POLL_VOTES FILTER v.postKey == @id REMOVE v IN $POLL_VOTES RETURN OLD", mapOf("id" to id))
        exec("FOR a IN $QUESTION_ANSWERS FILTER a.postKey == @id REMOVE a IN $QUESTION_ANSWERS RETURN OLD", mapOf("id" to id))
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
        doc.addAttribute("chatKey", notification.chatId)
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
              FILTER n.type != "chat_message" AND n.type != "chat_added" AND n.type != "chat_removed" AND n.type != "chat_deleted"
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
              FILTER n.type != "chat_message" AND n.type != "chat_added" AND n.type != "chat_removed" AND n.type != "chat_deleted"
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
        doc.addAttribute("chatKey", updated.chatId)
        db.collection(NOTIFICATIONS).replaceDocument(id, doc)
        return updated
    }

    override fun unreadNotificationCount(userId: String): Long {
        val row = first(
            """
            FOR n IN $NOTIFICATIONS
              FILTER n.userKey == @userKey AND n.readAt == null
              FILTER n.type != "chat_message" AND n.type != "chat_added" AND n.type != "chat_removed" AND n.type != "chat_deleted"
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
        exec(
            """
            FOR u IN $USERS
              FILTER u._key == @userId
              UPDATE u WITH { lastActiveAt: @at } IN $USERS
              RETURN NEW
            """.trimIndent(),
            mapOf("userId" to userId, "at" to at),
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

    private fun cursorBind(cursor: String?, limit: Int): Map<String, Any?> {
        val parsed = Cursors.parse(cursor)
        return mapOf(
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
    }

    private fun optional(doc: BaseDocument, name: String): String? =
        doc.getAttribute(name)?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    override fun insertCommunity(community: CommunityRecord): CommunityRecord {
        val doc = BaseDocument()
        doc.addAttribute("name", community.name)
        doc.addAttribute("emoji", community.emoji)
        doc.addAttribute("slug", community.slug)
        doc.addAttribute("createdBy", community.createdBy)
        doc.addAttribute("createdAt", community.createdAt)
        doc.addAttribute("memberCount", community.memberCount)
        doc.addAttribute("description", community.description)
        val key = try {
            db.collection(COMMUNITIES).insertDocument(doc).key
        } catch (error: ArangoDBException) {
            if (error.errorNum == 1210) throw TownException("community_taken")
            throw error
        }
        return community.copy(id = key)
    }

    override fun findCommunity(id: String): CommunityRecord? =
        db.collection(COMMUNITIES).getDocument(id, BaseDocument::class.java)?.let(::communityFrom)

    override fun findCommunityBySlug(slug: String): CommunityRecord? = first(
        "FOR c IN $COMMUNITIES FILTER c.slug == @slug LIMIT 1 RETURN c",
        mapOf("slug" to slug),
    )?.let(::communityFrom)

    override fun updateCommunityDescription(id: String, description: String): CommunityRecord? = first(
        """
        FOR c IN $COMMUNITIES
          FILTER c._key == @id
          UPDATE c WITH { description: @description } IN $COMMUNITIES
          RETURN NEW
        """.trimIndent(),
        mapOf("id" to id, "description" to description),
    )?.let(::communityFrom)

    override fun searchCommunities(query: String, userId: String?, limit: Int): List<CommunityView> {
        val needle = query.trim().lowercase()
        val like = if (needle.isEmpty()) "%" else "%${escapeLike(needle)}%"
        val rows = queryList(
            """
            FOR c IN $COMMUNITIES
              FILTER @hasQuery == false OR LOWER(c.name) LIKE @like OR LOWER(c.slug) LIKE @like
              SORT c.memberCount DESC, c.name ASC
              LIMIT @limit
              RETURN c
            """.trimIndent(),
            mapOf("hasQuery" to needle.isNotEmpty(), "like" to like, "limit" to limit),
        )
        val joined = joinedIds(userId)
        return rows.map(::communityFrom).map { CommunityView(it, it.id in joined) }
    }

    override fun communitiesForUser(userId: String): List<CommunityRecord> = queryList(
        """
        FOR m IN $MEMBERSHIPS
          FILTER m.userKey == @userKey
          SORT m.joinedAt DESC
          LET c = DOCUMENT("$COMMUNITIES", m.communityKey)
          FILTER c != null
          RETURN c
        """.trimIndent(),
        mapOf("userKey" to userId),
    ).map(::communityFrom)

    override fun joinCommunity(communityId: String, userId: String, joinedAt: Long): Boolean {
        val community = findCommunity(communityId) ?: return false
        val key = membershipKey(communityId, userId)
        val collection = db.collection(MEMBERSHIPS)
        if (collection.documentExists(key)) return false
        val doc = BaseDocument(key)
        doc.addAttribute("communityKey", communityId)
        doc.addAttribute("userKey", userId)
        doc.addAttribute("joinedAt", joinedAt)
        try {
            collection.insertDocument(doc)
        } catch (error: ArangoDBException) {
            if (error.errorNum == 1210) return false
            throw error
        }
        val patch = BaseDocument(community.id)
        patch.addAttribute("memberCount", community.memberCount + 1)
        db.collection(COMMUNITIES).updateDocument(community.id, patch)
        return true
    }

    override fun leaveCommunity(communityId: String, userId: String): Boolean {
        val community = findCommunity(communityId) ?: return false
        val key = membershipKey(communityId, userId)
        val collection = db.collection(MEMBERSHIPS)
        if (!collection.documentExists(key)) return false
        collection.deleteDocument(key)
        val patch = BaseDocument(community.id)
        patch.addAttribute("memberCount", (community.memberCount - 1).coerceAtLeast(0))
        db.collection(COMMUNITIES).updateDocument(community.id, patch)
        return true
    }

    override fun isMember(communityId: String, userId: String): Boolean =
        db.collection(MEMBERSHIPS).documentExists(membershipKey(communityId, userId))

    override fun members(communityId: String, cursor: String?, limit: Int): CommunityMemberPage {
        val parsed = Cursors.parse(cursor)
        val rows = queryList(
            """
            FOR m IN $MEMBERSHIPS
              FILTER m.communityKey == @communityKey
              FILTER @hasCursor == false OR m.joinedAt < @cursorAt OR (m.joinedAt == @cursorAt AND m._key < @cursorKey)
              SORT m.joinedAt DESC, m._key DESC
              LIMIT @limit
              LET u = DOCUMENT("$USERS", m.userKey)
              FILTER u != null
              RETURN {
                id: u._key,
                name: u.name,
                profilePhotoPath: u.profilePhotoPath,
                location: u.location,
                intro: u.intro,
                joinedAt: m.joinedAt,
                memberKey: m._key
              }
            """.trimIndent(),
            mapOf(
                "communityKey" to communityId,
                "limit" to limit + 1,
                "hasCursor" to (parsed != null),
                "cursorAt" to (parsed?.first ?: 0L),
                "cursorKey" to (parsed?.second ?: ""),
            ),
        )
        val page = rows.take(limit)
        val items = page.map { row ->
            CommunityMember(
                id = string(row, "id"),
                name = string(row, "name"),
                profilePhotoPath = optional(row, "profilePhotoPath"),
                location = string(row, "location"),
                intro = string(row, "intro"),
                joinedAt = long(row, "joinedAt"),
            )
        }
        val next = if (rows.size > limit) {
            page.lastOrNull()?.let { Cursors.encode(long(it, "joinedAt"), string(it, "memberKey")) }
        } else {
            null
        }
        return CommunityMemberPage(items, next)
    }

    override fun joinEvent(postId: String, userId: String, joinedAt: Long): Boolean {
        val post = findPost(postId) ?: return false
        if (post.eventStartAt == null) return false
        val key = membershipKey(postId, userId)
        if (db.collection(EVENT_ATTENDANCES).documentExists(key)) return false
        val doc = BaseDocument(key)
        doc.addAttribute("postKey", postId)
        doc.addAttribute("userKey", userId)
        doc.addAttribute("joinedAt", joinedAt)
        try {
            db.collection(EVENT_ATTENDANCES).insertDocument(doc)
        } catch (_: ArangoDBException) {
            return false
        }
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH { attendeeCount: (p.attendeeCount == null ? 0 : p.attendeeCount) + 1 } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to postId),
        )
        return true
    }

    override fun leaveEvent(postId: String, userId: String): Boolean {
        val key = membershipKey(postId, userId)
        if (!db.collection(EVENT_ATTENDANCES).documentExists(key)) return false
        db.collection(EVENT_ATTENDANCES).deleteDocument(key)
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH { attendeeCount: (p.attendeeCount == null || p.attendeeCount < 1 ? 0 : p.attendeeCount - 1) } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to postId),
        )
        return true
    }

    override fun isAttending(postId: String, userId: String): Boolean =
        db.collection(EVENT_ATTENDANCES).documentExists(membershipKey(postId, userId))

    override fun attendees(postId: String, cursor: String?, limit: Int): EventAttendeePage {
        val parsed = Cursors.parse(cursor)
        val rows = queryList(
            """
            FOR a IN $EVENT_ATTENDANCES
              FILTER a.postKey == @postKey
              FILTER @hasCursor == false OR a.joinedAt < @cursorAt OR (a.joinedAt == @cursorAt AND a._key < @cursorKey)
              SORT a.joinedAt DESC, a._key DESC
              LIMIT @limit
              LET u = DOCUMENT("$USERS", a.userKey)
              FILTER u != null
              RETURN {
                id: u._key,
                name: u.name,
                profilePhotoPath: u.profilePhotoPath,
                location: u.location,
                intro: u.intro,
                phone: u.phone,
                joinedAt: a.joinedAt,
                memberKey: a._key
              }
            """.trimIndent(),
            mapOf(
                "postKey" to postId,
                "limit" to limit + 1,
                "hasCursor" to (parsed != null),
                "cursorAt" to (parsed?.first ?: 0L),
                "cursorKey" to (parsed?.second ?: ""),
            ),
        )
        val page = rows.take(limit)
        val items = page.map { row ->
            EventAttendee(
                id = string(row, "id"),
                name = string(row, "name"),
                profilePhotoPath = optional(row, "profilePhotoPath"),
                location = string(row, "location"),
                intro = string(row, "intro"),
                phone = optional(row, "phone"),
                joinedAt = long(row, "joinedAt"),
            )
        }
        val next = if (rows.size > limit) {
            page.lastOrNull()?.let { Cursors.encode(long(it, "joinedAt"), string(it, "memberKey")) }
        } else {
            null
        }
        return EventAttendeePage(items, next)
    }

    override fun attendeeIds(postId: String): List<String> = queryList(
        "FOR a IN $EVENT_ATTENDANCES FILTER a.postKey == @postKey RETURN a",
        mapOf("postKey" to postId),
    ).map { string(it, "userKey") }

    override fun upcomingEvents(fromMillis: Long, viewerId: String?, limit: Int): List<FeedItem> {
        val bind = mapOf(
            "from" to fromMillis,
            "limit" to limit,
            "viewerId" to viewerId.orEmpty(),
        )
        val query = """
            FOR p IN $POSTS
              FILTER p.eventStartAt != null AND p.eventStartAt >= @from AND p.communityOnly != true
              SORT p.eventStartAt ASC, p._key ASC
              LIMIT @limit
              LET author = DOCUMENT("$USERS", p.userKey)
              LET avatar = DOCUMENT("$AVATARS", p.avatarKey)
              LET track = DOCUMENT("$MUSIC", p.musicKey)
              LET community = DOCUMENT("$COMMUNITIES", p.communityKey)
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
                previewComments: [],
                communityId: community == null ? null : community._key,
                communityName: community == null ? null : community.name,
                communityEmoji: community == null ? null : community.emoji,
                communityOnly: p.communityOnly == true,
                placeId: p.placeId,
                lat: p.lat,
                lng: p.lng,
                eventStartAt: p.eventStartAt,
                eventEndAt: p.eventEndAt,
                eventPrice: p.eventPrice,
                attendeeCount: p.attendeeCount == null ? 0 : p.attendeeCount,
                joined: @viewerId != "" && LENGTH(
                  FOR a IN $EVENT_ATTENDANCES
                    FILTER a.postKey == p._key AND a.userKey == @viewerId
                    LIMIT 1
                    RETURN 1
                ) > 0,
                attendees: (
                  FOR a IN $EVENT_ATTENDANCES
                    FILTER a.postKey == p._key
                    SORT a.joinedAt DESC
                    LIMIT 5
                    LET person = DOCUMENT("$USERS", a.userKey)
                    FILTER person != null
                    RETURN {
                      id: person._key,
                      name: person.name,
                      profilePhotoPath: person.profilePhotoPath,
                      location: person.location,
                      intro: person.intro,
                      joinedAt: a.joinedAt
                    }
                ),
                ${pollOptionsAql()},
                ${questionAql()},
                ${ctaAql()}
              }
        """.trimIndent()
        return queryList(query, bind).map(::feedItemFrom)
    }

    override fun eventCounts(todayStart: Long, tomorrowStart: Long, tomorrowEnd: Long, weekEnd: Long): EventSummary {
        val row = first(
            """
            LET upcoming = (
              FOR p IN $POSTS
                FILTER p.eventStartAt != null AND p.eventStartAt >= @todayStart AND p.eventStartAt < @weekEnd AND p.communityOnly != true
                RETURN p.eventStartAt
            )
            RETURN {
              today: LENGTH(FOR t IN upcoming FILTER t >= @todayStart AND t < @tomorrowStart RETURN 1),
              tomorrow: LENGTH(FOR t IN upcoming FILTER t >= @tomorrowStart AND t < @tomorrowEnd RETURN 1),
              week: LENGTH(upcoming)
            }
            """.trimIndent(),
            mapOf(
                "todayStart" to todayStart,
                "tomorrowStart" to tomorrowStart,
                "tomorrowEnd" to tomorrowEnd,
                "weekEnd" to weekEnd,
            ),
        )
        return EventSummary(long(row, "today"), long(row, "tomorrow"), long(row, "week"))
    }

    override fun recentPlaces(communityId: String?, limit: Int): List<PlaceSuggestion> {
        val rows = queryList(
            """
            FOR p IN $POSTS
              FILTER p.location != null AND p.location != ""
              FILTER @communityKey == "" OR p.communityKey == @communityKey
              SORT p.createdAt DESC, p._key DESC
              LIMIT 80
              RETURN {
                name: p.location,
                placeId: p.placeId,
                lat: p.lat,
                lng: p.lng
              }
            """.trimIndent(),
            mapOf("communityKey" to communityId.orEmpty()),
        )
        val seen = linkedSetOf<String>()
        val places = mutableListOf<PlaceSuggestion>()
        for (row in rows) {
            val name = string(row, "name")
            val placeId = optional(row, "placeId")
            val key = placeId ?: name.lowercase()
            if (!seen.add(key)) continue
            places += PlaceSuggestion(
                name = name,
                placeId = placeId,
                lat = doubleOrNull(row, "lat"),
                lng = doubleOrNull(row, "lng"),
                recent = true,
            )
            if (places.size >= limit) break
        }
        return places
    }

    override fun findPlace(placeId: String, viewerId: String?): PlaceSuggestion? {
        val location = decodeLocationToken(placeId)
        val nameOnlyPlace = location?.let {
            first(
                """
                FOR p IN $POSTS
                  FILTER p.location == @location AND (p.placeId == null OR p.placeId == "")
                  FILTER ${communityVisibleFilter()}
                  SORT p.createdAt DESC, p._key DESC
                  LIMIT 1
                  RETURN {name: p.location, lat: p.lat, lng: p.lng}
                """.trimIndent(),
                mapOf("location" to it, "viewerId" to viewerId.orEmpty()),
            )?.let { row ->
                PlaceSuggestion(
                    name = string(row, "name"),
                    placeId = null,
                    lat = doubleOrNull(row, "lat"),
                    lng = doubleOrNull(row, "lng"),
                )
            }
        }
        if (nameOnlyPlace != null) return nameOnlyPlace

        return first(
            """
            FOR p IN $POSTS
              FILTER p.placeId == @placeId AND p.location != null AND p.location != ""
              FILTER ${communityVisibleFilter()}
              SORT p.createdAt DESC, p._key DESC
              LIMIT 1
              RETURN {name: p.location, placeId: p.placeId, lat: p.lat, lng: p.lng}
            """.trimIndent(),
            mapOf("placeId" to placeId, "viewerId" to viewerId.orEmpty()),
        )?.let { row ->
            PlaceSuggestion(
                name = string(row, "name"),
                placeId = optional(row, "placeId"),
                lat = doubleOrNull(row, "lat"),
                lng = doubleOrNull(row, "lng"),
            )
        }
    }

    override fun postsAtPlace(
        location: String,
        placeId: String?,
        cursor: String?,
        limit: Int,
        viewerId: String?,
    ): FeedPage = pageFeed(
        "${placePostFilter()} AND (@hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey))",
        cursorBind(cursor, limit) + placePostBind(location, placeId, viewerId),
        limit,
    )

    override fun postsByAuthor(userId: String, cursor: String?, limit: Int, viewerId: String?): FeedPage = pageFeed(
        "${authorPostFilter()} AND (@hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey))",
        cursorBind(cursor, limit) + authorPostBind(userId, viewerId),
        limit,
    )

    override fun countPostsByAuthor(userId: String, viewerId: String?): Long {
        val row = first(
            """
            RETURN { count: LENGTH(
              FOR p IN $POSTS
                FILTER ${authorPostFilter()}
                RETURN 1
            )}
            """.trimIndent(),
            authorPostBind(userId, viewerId),
        )
        return long(row, "count")
    }

    override fun insertProduct(product: ProductRecord): ProductRecord {
        val doc = productDocument(product)
        val key = db.collection(PRODUCTS).insertDocument(doc).key
        return product.copy(id = key)
    }

    override fun findProduct(id: String): ProductRecord? =
        db.collection(PRODUCTS).getDocument(id, BaseDocument::class.java)?.let(::productFrom)

    override fun updateProduct(product: ProductRecord): ProductRecord? {
        if (!db.collection(PRODUCTS).documentExists(product.id)) return null
        db.collection(PRODUCTS).replaceDocument(product.id, productDocument(product.id, product))
        return product
    }

    override fun deleteProduct(id: String): ProductRecord? {
        val product = findProduct(id) ?: return null
        db.collection(PRODUCTS).deleteDocument(id)
        return product
    }

    override fun productsByUser(userId: String, cursor: String?, limit: Int): ProductPage {
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
            FOR p IN $PRODUCTS
              FILTER p.userKey == @userKey
              FILTER @hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey)
              SORT p.createdAt DESC, p._key DESC
              LIMIT @limit
              RETURN p
            """.trimIndent(),
            bind,
        )
        val count = long(first("FOR p IN $PRODUCTS FILTER p.userKey == @userKey COLLECT WITH COUNT INTO total RETURN { total }", mapOf("userKey" to userId)), "total")
        val page = rows.take(limit)
        val next = if (rows.size > limit) page.lastOrNull()?.let { Cursors.encode(long(it, "createdAt"), it.key) } else null
        return ProductPage(page.map(::productFrom), count, next)
    }

    override fun searchProducts(query: String, cursor: String?, limit: Int): ProductPage {
        val needle = query.trim().lowercase()
        val parsed = Cursors.parse(cursor)
        val bind = mapOf(
            "query" to needle,
            "limit" to limit + 1,
            "hasCursor" to (parsed != null),
            "cursorAt" to (parsed?.first ?: 0L),
            "cursorKey" to (parsed?.second ?: ""),
        )
        val searchFilter = """
            @query == ""
            OR CONTAINS(LOWER(TO_STRING(p.name == null ? "" : p.name)), @query)
            OR CONTAINS(LOWER(TO_STRING(p.description == null ? "" : p.description)), @query)
            OR CONTAINS(LOWER(TO_STRING(p.category == null ? "" : p.category)), @query)
        """.trimIndent()
        val rows = queryList(
            """
            FOR p IN $PRODUCTS
              FILTER ($searchFilter)
              FILTER @hasCursor == false OR p.createdAt < @cursorAt OR (p.createdAt == @cursorAt AND p._key < @cursorKey)
              SORT p.createdAt DESC, p._key DESC
              LIMIT @limit
              RETURN p
            """.trimIndent(),
            bind,
        )
        val count = long(
            first(
                """
                FOR p IN $PRODUCTS
                  FILTER ($searchFilter)
                  COLLECT WITH COUNT INTO total
                  RETURN { total }
                """.trimIndent(),
                mapOf("query" to needle),
            ),
            "total",
        )
        val page = rows.take(limit)
        val next = if (rows.size > limit) page.lastOrNull()?.let { Cursors.encode(long(it, "createdAt"), it.key) } else null
        return ProductPage(page.map(::productFrom), count, next)
    }

    override fun insertProductOrder(order: ProductOrderRecord): ProductOrderRecord {
        val doc = productOrderDocument(order)
        val key = db.collection(PRODUCT_ORDERS).insertDocument(doc).key
        return order.copy(id = key)
    }

    override fun productOrdersForSeller(sellerId: String, cursor: String?, limit: Int): ProductOrderPage {
        val parsed = Cursors.parseProductOrder(cursor)
        val rows = queryList(
            """
            FOR o IN $PRODUCT_ORDERS
              FILTER o.sellerKey == @sellerKey
              LET resolved = o.resolved == true
              FILTER @hasCursor == false
                OR (@cursorResolved == false AND resolved == true)
                OR (resolved == @cursorResolved AND (
                  o.createdAt < @cursorAt OR (o.createdAt == @cursorAt AND o._key < @cursorKey)
                ))
              SORT resolved ASC, o.createdAt DESC, o._key DESC
              LIMIT @limit
              LET buyer = DOCUMENT("$USERS", o.buyerKey)
              FILTER buyer != null
              RETURN MERGE(o, {
                resolved: resolved,
                buyerName: buyer.name,
                buyerPhone: buyer.phone,
                buyerPhotoPath: buyer.profilePhotoPath,
                buyerLocation: buyer.location,
                buyerIntro: buyer.intro
              })
            """.trimIndent(),
            mapOf(
                "sellerKey" to sellerId,
                "limit" to limit + 1,
                "hasCursor" to (parsed != null),
                "cursorResolved" to (parsed?.resolved ?: false),
                "cursorAt" to (parsed?.createdAt ?: 0L),
                "cursorKey" to (parsed?.id ?: ""),
            ),
        )
        val page = rows.take(limit)
        val next = if (rows.size > limit) {
            page.lastOrNull()?.let {
                Cursors.encodeProductOrder(boolean(it, "resolved"), long(it, "createdAt"), it.key)
            }
        } else {
            null
        }
        val openCount = long(
            first(
                """
                RETURN { count: LENGTH(
                  FOR o IN $PRODUCT_ORDERS
                    FILTER o.sellerKey == @sellerKey AND o.resolved == false
                    RETURN 1
                ) }
                """.trimIndent(),
                mapOf("sellerKey" to sellerId),
            ),
            "count",
        )
        return ProductOrderPage(page.map {
            ProductOrderView(
                productOrderFrom(it),
                string(it, "buyerName"),
                string(it, "buyerPhone"),
                string(it, "buyerPhotoPath").takeIf { value -> value.isNotBlank() && value != "null" },
                string(it, "buyerLocation"),
                string(it, "buyerIntro"),
            )
        }, next, openCount)
    }

    override fun updateProductOrderResolved(id: String, sellerId: String, resolved: Boolean): ProductOrderView? {
        val row = first(
            """
            FOR o IN $PRODUCT_ORDERS
              FILTER o._key == @id AND o.sellerKey == @sellerKey
              LET buyer = DOCUMENT("$USERS", o.buyerKey)
              FILTER buyer != null
              UPDATE o WITH { resolved: @resolved } IN $PRODUCT_ORDERS
              RETURN MERGE(NEW, {
                buyerName: buyer.name,
                buyerPhone: buyer.phone,
                buyerPhotoPath: buyer.profilePhotoPath,
                buyerLocation: buyer.location,
                buyerIntro: buyer.intro
              })
            """.trimIndent(),
            mapOf("id" to id, "sellerKey" to sellerId, "resolved" to resolved),
        ) ?: return null
        return ProductOrderView(
            productOrderFrom(row),
            string(row, "buyerName"),
            string(row, "buyerPhone"),
            string(row, "buyerPhotoPath").takeIf { it.isNotBlank() && it != "null" },
            string(row, "buyerLocation"),
            string(row, "buyerIntro"),
        )
    }

    override fun countPostsAtPlace(location: String, placeId: String?, viewerId: String?): Long {
        val row = first(
            """
            RETURN { count: LENGTH(
              FOR p IN $POSTS
                FILTER ${placePostFilter()}
                RETURN 1
            )}
            """.trimIndent(),
            placePostBind(location, placeId, viewerId),
        )
        return long(row, "count")
    }

    private fun authorPostBind(userId: String, viewerId: String?): Map<String, Any?> = mapOf(
        "userKey" to userId,
        "viewerId" to viewerId.orEmpty(),
    )

    private fun authorPostFilter(): String = """
        p.userKey == @userKey
        AND (
          p.userKey == @viewerId
          OR ${communityVisibleFilter()}
        )
    """.trimIndent()

    private fun placePostBind(location: String, placeId: String?, viewerId: String?): Map<String, Any?> = mapOf(
        "location" to location,
        "placeId" to placeId.orEmpty(),
        "viewerId" to viewerId.orEmpty(),
    )

    private fun placePostFilter(): String = """
        p.location == @location
        AND (
          (@placeId == "" AND (p.placeId == null OR p.placeId == ""))
          OR (@placeId != "" AND p.placeId == @placeId)
        )
        AND ${communityVisibleFilter()}
    """.trimIndent()

    private fun communityVisibleFilter(): String = """
        (
          p.communityOnly != true
          OR (
            @viewerId != ""
            AND p.communityKey != null
            AND p.communityKey != ""
            AND LENGTH(
              FOR m IN $MEMBERSHIPS
                FILTER m.communityKey == p.communityKey AND m.userKey == @viewerId
                LIMIT 1
                RETURN 1
            ) > 0
          )
        )
    """.trimIndent()

    override fun people(cursor: String?, limit: Int): CommunityMemberPage {
        val parsed = Cursors.parse(cursor)
        val rows = queryList(
            """
            FOR u IN $USERS
              LET activeAt = u.lastActiveAt == null || u.lastActiveAt == 0 ? u.createdAt : u.lastActiveAt
              FILTER @hasCursor == false OR activeAt < @cursorAt OR (activeAt == @cursorAt AND u._key < @cursorKey)
              SORT activeAt DESC, u._key DESC
              LIMIT @limit
              RETURN {
                id: u._key,
                name: u.name,
                profilePhotoPath: u.profilePhotoPath,
                location: u.location,
                intro: u.intro,
                joinedAt: activeAt
              }
            """.trimIndent(),
            mapOf(
                "limit" to limit + 1,
                "hasCursor" to (parsed != null),
                "cursorAt" to (parsed?.first ?: 0L),
                "cursorKey" to (parsed?.second ?: ""),
            ),
        )
        val page = rows.take(limit)
        val items = page.map { row ->
            CommunityMember(
                id = string(row, "id"),
                name = string(row, "name"),
                profilePhotoPath = optional(row, "profilePhotoPath"),
                location = string(row, "location"),
                intro = string(row, "intro"),
                joinedAt = long(row, "joinedAt"),
            )
        }
        val next = if (rows.size > limit) items.lastOrNull()?.let { Cursors.encode(it.joinedAt, it.id) } else null
        return CommunityMemberPage(items, next)
    }

    override fun eventPostsBetween(from: Long, to: Long): List<PostRecord> = queryList(
        """
        FOR p IN $POSTS
          FILTER p.eventStartAt != null AND p.eventStartAt >= @from AND p.eventStartAt <= @to
          SORT p.eventStartAt ASC
          RETURN p
        """.trimIndent(),
        mapOf("from" to from, "to" to to),
    ).mapNotNull { doc -> findPost(doc.key) }

    override fun findPollVote(postId: String, userId: String): PollVote? {
        val doc = db.collection(POLL_VOTES).getDocument(membershipKey(postId, userId), BaseDocument::class.java)
            ?: return null
        return PollVote(
            postId = string(doc, "postKey").ifBlank { postId },
            optionIndex = long(doc, "optionIndex").toInt(),
            userId = string(doc, "userKey").ifBlank { userId },
            votedAt = long(doc, "votedAt"),
        )
    }

    override fun castPollVote(postId: String, userId: String, optionIndex: Int, votedAt: Long): Boolean {
        val post = findPost(postId) ?: return false
        if (optionIndex !in post.pollOptions.indices) return false
        val key = membershipKey(postId, userId)
        val doc = BaseDocument(key)
        doc.addAttribute("postKey", postId)
        doc.addAttribute("userKey", userId)
        doc.addAttribute("optionIndex", optionIndex)
        doc.addAttribute("votedAt", votedAt)
        if (db.collection(POLL_VOTES).documentExists(key)) {
            db.collection(POLL_VOTES).replaceDocument(key, doc)
        } else {
            try {
                db.collection(POLL_VOTES).insertDocument(doc)
            } catch (_: ArangoDBException) {
                return false
            }
        }
        return true
    }

    override fun removePollVote(postId: String, userId: String): Boolean {
        val key = membershipKey(postId, userId)
        if (!db.collection(POLL_VOTES).documentExists(key)) return false
        db.collection(POLL_VOTES).deleteDocument(key)
        return true
    }

    override fun pollOptions(postId: String, viewerId: String?, voterLimit: Int): List<PollOptionView> {
        val post = findPost(postId) ?: return emptyList()
        if (post.pollOptions.isEmpty()) return emptyList()
        val rows = queryList(
            """
            LET options = @options
            FOR optionIndex IN 0..${Limits.POLL_OPTIONS_MAX - 1}
              FILTER optionIndex < LENGTH(options)
              LET votes = (
                FOR v IN $POLL_VOTES
                  FILTER v.postKey == @postKey AND v.optionIndex == optionIndex
                  SORT v.votedAt DESC
                  LET person = DOCUMENT("$USERS", v.userKey)
                  FILTER person != null
                  LIMIT @voterLimit
                  RETURN {
                    id: person._key,
                    name: person.name,
                    profilePhotoPath: person.profilePhotoPath,
                    location: person.location,
                    intro: person.intro,
                    joinedAt: v.votedAt
                  }
              )
              LET voteCount = LENGTH(
                FOR v IN $POLL_VOTES
                  FILTER v.postKey == @postKey AND v.optionIndex == optionIndex
                  RETURN 1
              )
              RETURN {
                index: optionIndex,
                text: options[optionIndex],
                voteCount: voteCount,
                voted: @viewerId != "" && LENGTH(
                  FOR v IN $POLL_VOTES
                    FILTER v.postKey == @postKey AND v.optionIndex == optionIndex AND v.userKey == @viewerId
                    LIMIT 1
                    RETURN 1
                ) > 0,
                voters: votes
              }
            """.trimIndent(),
            mapOf(
                "postKey" to postId,
                "options" to post.pollOptions,
                "viewerId" to viewerId.orEmpty(),
                "voterLimit" to voterLimit.coerceAtLeast(0),
            ),
        )
        return rows.mapNotNull(::pollOptionFrom)
    }

    override fun insertQuestionAnswer(answer: QuestionAnswerRecord): QuestionAnswerRecord {
        val key = membershipKey(answer.postId, answer.userId)
        val doc = BaseDocument(key)
        doc.addAttribute("postKey", answer.postId)
        doc.addAttribute("userKey", answer.userId)
        doc.addAttribute("text", answer.text)
        doc.addAttribute("createdAt", answer.createdAt)
        db.collection(QUESTION_ANSWERS).insertDocument(doc)
        return answer.copy(id = key)
    }

    override fun findQuestionAnswer(postId: String, userId: String): QuestionAnswerRecord? {
        val doc = db.collection(QUESTION_ANSWERS).getDocument(membershipKey(postId, userId), BaseDocument::class.java)
            ?: return null
        return QuestionAnswerRecord(
            id = doc.key,
            postId = string(doc, "postKey").ifBlank { postId },
            userId = string(doc, "userKey").ifBlank { userId },
            text = string(doc, "text"),
            createdAt = long(doc, "createdAt"),
        )
    }

    override fun questionAnswers(postId: String): List<QuestionAnswerItem> =
        queryList(
            """
            FOR a IN $QUESTION_ANSWERS
              FILTER a.postKey == @postId
              SORT a.createdAt DESC, a._key DESC
              LET person = DOCUMENT("$USERS", a.userKey)
              FILTER person != null
              RETURN {
                id: a._key,
                postId: a.postKey,
                userId: a.userKey,
                authorName: person.name,
                authorPhotoPath: person.profilePhotoPath,
                authorLocation: person.location,
                authorIntro: person.intro,
                text: a.text,
                createdAt: a.createdAt
              }
            """.trimIndent(),
            mapOf("postId" to postId),
        ).map(::questionAnswerItemFrom)

    override fun insertFeatureSuggestion(userId: String, text: String, createdAt: Long): FeatureSuggestionRecord {
        val doc = BaseDocument()
        doc.addAttribute("userKey", userId)
        doc.addAttribute("text", text)
        doc.addAttribute("createdAt", createdAt)
        doc.addAttribute("resolvedAt", null)
        val key = db.collection(FEATURE_SUGGESTIONS).insertDocument(doc).key
        return FeatureSuggestionRecord(key, userId, text, createdAt)
    }

    override fun findFeatureSuggestion(id: String): FeatureSuggestionRecord? =
        db.collection(FEATURE_SUGGESTIONS).getDocument(id, BaseDocument::class.java)?.let(::featureFrom)

    override fun deleteFeatureSuggestion(id: String): Boolean {
        if (!db.collection(FEATURE_SUGGESTIONS).documentExists(id)) return false
        db.collection(FEATURE_SUGGESTIONS).deleteDocument(id)
        exec(
            "FOR v IN $FEATURE_VOTES FILTER v.suggestionKey == @id REMOVE v IN $FEATURE_VOTES",
            mapOf("id" to id),
        )
        return true
    }

    override fun resolveFeatureSuggestion(id: String, resolvedAt: Long): FeatureSuggestionView? {
        first(
            """
            FOR s IN $FEATURE_SUGGESTIONS
              FILTER s._key == @id
              UPDATE s WITH { resolvedAt: @resolvedAt } IN $FEATURE_SUGGESTIONS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to id, "resolvedAt" to resolvedAt),
        ) ?: return null
        return featureSuggestionView(id, null)
    }

    override fun toggleFeatureVote(id: String, userId: String, votedAt: Long): FeatureSuggestionView? {
        findFeatureSuggestion(id) ?: return null
        val key = membershipKey(id, userId)
        if (db.collection(FEATURE_VOTES).documentExists(key)) {
            db.collection(FEATURE_VOTES).deleteDocument(key)
        } else {
            val doc = BaseDocument(key)
            doc.addAttribute("suggestionKey", id)
            doc.addAttribute("userKey", userId)
            doc.addAttribute("votedAt", votedAt)
            try {
                db.collection(FEATURE_VOTES).insertDocument(doc)
            } catch (_: ArangoDBException) {
                return featureSuggestionView(id, userId)
            }
        }
        return featureSuggestionView(id, userId)
    }

    override fun featureSuggestions(viewerId: String?, limit: Int, openOnly: Boolean): List<FeatureSuggestionView> =
        queryList(
            """
            FOR s IN $FEATURE_SUGGESTIONS
              FILTER @openOnly == false || s.resolvedAt == null
              LET voteCount = LENGTH(FOR v IN $FEATURE_VOTES FILTER v.suggestionKey == s._key RETURN 1)
              LET voted = @viewerId != "" && LENGTH(
                FOR v IN $FEATURE_VOTES
                  FILTER v.suggestionKey == s._key AND v.userKey == @viewerId
                  LIMIT 1
                  RETURN 1
              ) > 0
              SORT s.resolvedAt == null DESC, voteCount DESC, s.createdAt DESC, s._key
              LIMIT @limit
              RETURN MERGE(s, { voteCount: voteCount, voted: voted })
            """.trimIndent(),
            mapOf(
                "viewerId" to viewerId.orEmpty(),
                "openOnly" to openOnly,
                "limit" to limit,
            ),
        ).map(::featureViewFrom)

    override fun featureSuggestionView(id: String, viewerId: String?): FeatureSuggestionView? = first(
        """
        FOR s IN $FEATURE_SUGGESTIONS
          FILTER s._key == @id
          LET voteCount = LENGTH(FOR v IN $FEATURE_VOTES FILTER v.suggestionKey == s._key RETURN 1)
          LET voted = @viewerId != "" && LENGTH(
            FOR v IN $FEATURE_VOTES
              FILTER v.suggestionKey == s._key AND v.userKey == @viewerId
              LIMIT 1
              RETURN 1
          ) > 0
          RETURN MERGE(s, { voteCount: voteCount, voted: voted })
        """.trimIndent(),
        mapOf("id" to id, "viewerId" to viewerId.orEmpty()),
    )?.let(::featureViewFrom)

    private fun featureFrom(doc: BaseDocument) = FeatureSuggestionRecord(
        id = doc.key,
        userId = string(doc, "userKey"),
        text = string(doc, "text"),
        createdAt = long(doc, "createdAt"),
        resolvedAt = longOrNull(doc, "resolvedAt"),
    )

    private fun featureViewFrom(doc: BaseDocument) = FeatureSuggestionView(
        id = doc.key,
        text = string(doc, "text"),
        voteCount = long(doc, "voteCount"),
        voted = boolean(doc, "voted"),
        resolved = longOrNull(doc, "resolvedAt") != null,
        createdAt = long(doc, "createdAt"),
    )

    override fun markEventReminder(postId: String, hour: Boolean, nowFlag: Boolean) {
        exec(
            """
            FOR p IN $POSTS
              FILTER p._key == @id
              UPDATE p WITH {
                eventHourNotified: p.eventHourNotified == true || @hour,
                eventNowNotified: p.eventNowNotified == true || @nowFlag
              } IN $POSTS
              RETURN NEW
            """.trimIndent(),
            mapOf("id" to postId, "hour" to hour, "nowFlag" to nowFlag),
        )
    }

    override fun upsertPushSubscription(subscription: PushSubscriptionRecord) {
        val existing = first(
            "FOR s IN $PUSH_SUBSCRIPTIONS FILTER s.endpoint == @endpoint LIMIT 1 RETURN s",
            mapOf("endpoint" to subscription.endpoint),
        )
        if (existing != null) {
            writePushSubscription(existing.key, subscription)
            return
        }
        val doc = pushDocument(null, subscription)
        try {
            db.collection(PUSH_SUBSCRIPTIONS).insertDocument(doc)
        } catch (error: ArangoDBException) {
            if (error.errorNum != 1210) throw error
            val again = first(
                "FOR s IN $PUSH_SUBSCRIPTIONS FILTER s.endpoint == @endpoint LIMIT 1 RETURN s",
                mapOf("endpoint" to subscription.endpoint),
            ) ?: throw error
            writePushSubscription(again.key, subscription)
        }
    }

    override fun deletePushSubscription(userId: String, endpoint: String) {
        exec(
            """
            FOR s IN $PUSH_SUBSCRIPTIONS
              FILTER s.userKey == @userKey AND s.endpoint == @endpoint
              REMOVE s IN $PUSH_SUBSCRIPTIONS
              RETURN OLD
            """.trimIndent(),
            mapOf("userKey" to userId, "endpoint" to endpoint),
        )
    }

    override fun deletePushSubscriptionByEndpoint(endpoint: String) {
        exec(
            """
            FOR s IN $PUSH_SUBSCRIPTIONS
              FILTER s.endpoint == @endpoint
              REMOVE s IN $PUSH_SUBSCRIPTIONS
              RETURN OLD
            """.trimIndent(),
            mapOf("endpoint" to endpoint),
        )
    }

    override fun pushSubscriptionsFor(userId: String): List<PushSubscriptionRecord> = queryList(
        "FOR s IN $PUSH_SUBSCRIPTIONS FILTER s.userKey == @userKey RETURN s",
        mapOf("userKey" to userId),
    ).map(::pushFrom)

    override fun insertChat(chat: ChatRecord): ChatRecord {
        val doc = chatDocument(null, chat)
        val key = db.collection(CHATS).insertDocument(doc).key
        return chat.copy(id = key)
    }

    override fun updateChat(chat: ChatRecord) {
        db.collection(CHATS).replaceDocument(chat.id, chatDocument(chat.id, chat))
    }

    override fun findChat(id: String): ChatRecord? =
        db.collection(CHATS).getDocument(id, BaseDocument::class.java)?.let(::chatFrom)

    override fun findDirectChat(userA: String, userB: String): ChatRecord? = first(
        "FOR c IN $CHATS FILTER c.pairKey == @pair LIMIT 1 RETURN c",
        mapOf("pair" to ChatService.pairKey(userA, userB)),
    )?.let(::chatFrom)

    override fun deleteChat(id: String) {
        exec(
            """
            FOR r IN $CHAT_REACTIONS FILTER r.chatKey == @chatKey REMOVE r IN $CHAT_REACTIONS RETURN OLD
            """.trimIndent(),
            mapOf("chatKey" to id),
        )
        exec(
            """
            FOR m IN $CHAT_MESSAGES FILTER m.chatKey == @chatKey REMOVE m IN $CHAT_MESSAGES RETURN OLD
            """.trimIndent(),
            mapOf("chatKey" to id),
        )
        exec(
            """
            FOR m IN $CHAT_MEMBERS FILTER m.chatKey == @chatKey REMOVE m IN $CHAT_MEMBERS RETURN OLD
            """.trimIndent(),
            mapOf("chatKey" to id),
        )
        db.collection(CHATS).deleteDocument(id)
    }

    override fun chatsFor(userId: String, cursor: String?, limit: Int): ChatPage {
        val parsed = Cursors.parse(cursor)
        val rows = queryList(
            """
            FOR m IN $CHAT_MEMBERS
              FILTER m.userKey == @userKey
              FOR c IN $CHATS
                FILTER c._key == m.chatKey
                FILTER @hasCursor == false OR c.lastMessageAt < @cursorAt OR (c.lastMessageAt == @cursorAt AND c._key < @cursorKey)
                SORT c.lastMessageAt DESC, c._key DESC
                LIMIT @limit
                RETURN c
            """.trimIndent(),
            mapOf(
                "userKey" to userId,
                "hasCursor" to (parsed != null),
                "cursorAt" to (parsed?.first ?: 0L),
                "cursorKey" to (parsed?.second ?: ""),
                "limit" to limit + 1,
            ),
        )
        val page = rows.take(limit).map(::chatFrom)
        val items = page.mapNotNull { chatSummary(it.id, userId) }
        val next = if (rows.size > limit) page.lastOrNull()?.let { Cursors.encode(it.lastMessageAt, it.id) } else null
        return ChatPage(items, next, unreadChatNotificationCount(userId))
    }

    override fun chatSummary(chatId: String, viewerId: String): ChatSummary? {
        val chat = findChat(chatId) ?: return null
        val member = findChatMember(chatId, viewerId) ?: return null
        val members = queryList(
            "FOR m IN $CHAT_MEMBERS FILTER m.chatKey == @chatKey RETURN m",
            mapOf("chatKey" to chatId),
        ).map(::chatMemberFrom)
        val faces = arangoChatFaces(chat, members, viewerId)
        val peer = if (chat.type == ChatType.DIRECT) members.firstOrNull { it.userId != viewerId }?.userId else null
        val displayName = when {
            chat.type == ChatType.DIRECT -> peer?.let { findUser(it)?.name }.orEmpty().ifBlank { chat.name }
            chat.name.isNotBlank() -> chat.name
            else -> members.mapNotNull { findUser(it.userId)?.name }.filter { it.isNotBlank() }.take(3).joinToString(", ")
        }
        return ChatSummary(
            id = chat.id,
            type = chat.type,
            name = displayName,
            photos = faces,
            lastMessageSenderName = chat.lastMessageSenderName,
            lastMessageText = chat.lastMessageText,
            lastMessageAt = chat.lastMessageAt,
            unreadCount = member.unreadCount,
            peerId = peer,
            owner = member.owner,
            memberCount = members.size,
        )
    }

    override fun upsertChatMember(member: ChatMemberRecord) {
        val existing = first(
            "FOR m IN $CHAT_MEMBERS FILTER m.chatKey == @chatKey AND m.userKey == @userKey LIMIT 1 RETURN m",
            mapOf("chatKey" to member.chatId, "userKey" to member.userId),
        )
        val doc = BaseDocument()
        if (existing != null) doc.key = existing.key
        doc.addAttribute("chatKey", member.chatId)
        doc.addAttribute("userKey", member.userId)
        doc.addAttribute("owner", member.owner)
        doc.addAttribute("joinedAt", member.joinedAt)
        doc.addAttribute("lastReadAt", member.lastReadAt)
        doc.addAttribute("unreadCount", member.unreadCount)
        if (existing != null) {
            db.collection(CHAT_MEMBERS).replaceDocument(existing.key, doc)
        } else {
            db.collection(CHAT_MEMBERS).insertDocument(doc)
        }
    }

    override fun findChatMember(chatId: String, userId: String): ChatMemberRecord? = first(
        "FOR m IN $CHAT_MEMBERS FILTER m.chatKey == @chatKey AND m.userKey == @userKey LIMIT 1 RETURN m",
        mapOf("chatKey" to chatId, "userKey" to userId),
    )?.let(::chatMemberFrom)

    override fun deleteChatMember(chatId: String, userId: String) {
        exec(
            """
            FOR m IN $CHAT_MEMBERS
              FILTER m.chatKey == @chatKey AND m.userKey == @userKey
              REMOVE m IN $CHAT_MEMBERS
              RETURN OLD
            """.trimIndent(),
            mapOf("chatKey" to chatId, "userKey" to userId),
        )
    }

    override fun chatMemberIds(chatId: String): List<String> = queryList(
        "FOR m IN $CHAT_MEMBERS FILTER m.chatKey == @chatKey RETURN m",
        mapOf("chatKey" to chatId),
    ).map { string(it, "userKey") }.filter { it.isNotBlank() }

    override fun chatMembers(chatId: String): List<ChatMemberView> {
        val members = queryList(
            "FOR m IN $CHAT_MEMBERS FILTER m.chatKey == @chatKey SORT m.joinedAt ASC RETURN m",
            mapOf("chatKey" to chatId),
        ).map(::chatMemberFrom)
        return members.mapNotNull { member ->
            val user = findUser(member.userId) ?: return@mapNotNull null
            ChatMemberView(
                id = user.id,
                name = user.name,
                profilePhotoPath = user.profilePhotoPath,
                location = user.location,
                intro = user.intro,
                phone = user.phone,
                owner = member.owner,
                joinedAt = member.joinedAt,
            )
        }
    }

    override fun insertChatMessage(message: ChatMessageRecord): ChatMessageRecord {
        val doc = BaseDocument()
        doc.addAttribute("chatKey", message.chatId)
        doc.addAttribute("userKey", message.userId)
        doc.addAttribute("text", message.text)
        doc.addAttribute("photoPaths", message.photoPaths)
        doc.addAttribute("createdAt", message.createdAt)
        message.attachment?.let { attachment ->
            doc.addAttribute("attachmentType", attachment.type)
            doc.addAttribute("attachmentProductId", attachment.productId)
            doc.addAttribute("attachmentProductName", attachment.productName)
            doc.addAttribute("attachmentPrice", attachment.price)
            attachment.photoPath?.let { doc.addAttribute("attachmentPhotoPath", it) }
        }
        val key = db.collection(CHAT_MESSAGES).insertDocument(doc).key
        return message.copy(id = key)
    }

    override fun findChatMessage(id: String): ChatMessageRecord? =
        db.collection(CHAT_MESSAGES).getDocument(id, BaseDocument::class.java)?.let(::chatMessageFrom)

    override fun deleteChatMessage(id: String) {
        exec(
            """
            FOR r IN $CHAT_REACTIONS FILTER r.messageKey == @id REMOVE r IN $CHAT_REACTIONS RETURN OLD
            """.trimIndent(),
            mapOf("id" to id),
        )
        db.collection(CHAT_MESSAGES).deleteDocument(id)
    }

    override fun chatMessages(chatId: String, cursor: String?, limit: Int, viewerId: String): ChatMessagePage {
        val parsed = Cursors.parse(cursor)
        val rows = queryList(
            """
            FOR m IN $CHAT_MESSAGES
              FILTER m.chatKey == @chatKey
              FILTER @hasCursor == false OR m.createdAt < @cursorAt OR (m.createdAt == @cursorAt AND m._key < @cursorKey)
              SORT m.createdAt DESC, m._key DESC
              LIMIT @limit
              RETURN m
            """.trimIndent(),
            mapOf(
                "chatKey" to chatId,
                "hasCursor" to (parsed != null),
                "cursorAt" to (parsed?.first ?: 0L),
                "cursorKey" to (parsed?.second ?: ""),
                "limit" to limit + 1,
            ),
        )
        val newestFirst = rows.take(limit).map(::chatMessageFrom)
        val items = newestFirst.asReversed().map { toChatMessageItem(it, viewerId) }
        val next = if (rows.size > limit) newestFirst.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return ChatMessagePage(items, next)
    }

    override fun chatMessageItem(id: String, viewerId: String): ChatMessageItem? =
        findChatMessage(id)?.let { toChatMessageItem(it, viewerId) }

    override fun chatPhotoPaths(chatId: String): List<String> = queryList(
        "FOR m IN $CHAT_MESSAGES FILTER m.chatKey == @chatKey RETURN m",
        mapOf("chatKey" to chatId),
    ).flatMap { stringList(it, "photoPaths") }

    override fun incrementChatUnread(chatId: String, exceptUserId: String) {
        exec(
            """
            FOR m IN $CHAT_MEMBERS
              FILTER m.chatKey == @chatKey AND m.userKey != @except
              UPDATE m WITH { unreadCount: m.unreadCount + 1 } IN $CHAT_MEMBERS
              RETURN NEW
            """.trimIndent(),
            mapOf("chatKey" to chatId, "except" to exceptUserId),
        )
    }

    override fun markChatRead(chatId: String, userId: String, readAt: Long) {
        exec(
            """
            FOR m IN $CHAT_MEMBERS
              FILTER m.chatKey == @chatKey AND m.userKey == @userKey
              UPDATE m WITH { unreadCount: 0, lastReadAt: @readAt } IN $CHAT_MEMBERS
              RETURN NEW
            """.trimIndent(),
            mapOf("chatKey" to chatId, "userKey" to userId, "readAt" to readAt),
        )
    }

    override fun insertChatReaction(reaction: ChatReactionRecord): ChatReactionRecord {
        val doc = BaseDocument()
        doc.addAttribute("chatKey", reaction.chatId)
        doc.addAttribute("messageKey", reaction.messageId)
        doc.addAttribute("userKey", reaction.userId)
        doc.addAttribute("emoji", reaction.emoji)
        doc.addAttribute("createdAt", reaction.createdAt)
        val key = db.collection(CHAT_REACTIONS).insertDocument(doc).key
        return reaction.copy(id = key)
    }

    override fun findChatReaction(messageId: String, userId: String, emoji: String): ChatReactionRecord? = first(
        """
        FOR r IN $CHAT_REACTIONS
          FILTER r.messageKey == @messageKey AND r.userKey == @userKey AND r.emoji == @emoji
          LIMIT 1
          RETURN r
        """.trimIndent(),
        mapOf("messageKey" to messageId, "userKey" to userId, "emoji" to emoji),
    )?.let(::chatReactionFrom)

    override fun deleteChatReaction(id: String) {
        db.collection(CHAT_REACTIONS).deleteDocument(id)
    }

    override fun chatReactionDetails(messageId: String, viewerId: String): List<ReactionDetail> =
        queryList(
            "FOR r IN $CHAT_REACTIONS FILTER r.messageKey == @messageKey SORT r.createdAt ASC RETURN r",
            mapOf("messageKey" to messageId),
        ).map(::chatReactionFrom).mapNotNull { reaction ->
            val user = findUser(reaction.userId) ?: return@mapNotNull null
            ReactionDetail(
                id = reaction.id,
                emoji = reaction.emoji,
                userId = user.id,
                name = user.name,
                photoPath = user.profilePhotoPath,
                mine = user.id == viewerId,
            )
        }

    override fun chatReactionCounts(messageId: String, viewerId: String): List<ReactionCount> =
        chatReactionDetails(messageId, viewerId)
            .groupBy { it.emoji }
            .map { (emoji, group) ->
                ReactionCount(emoji = emoji, count = group.size, mine = group.any { it.mine })
            }
            .sortedWith(compareByDescending<ReactionCount> { it.count }.thenBy { it.emoji })

    override fun topChatReactions(chatId: String, limit: Int): List<String> {
        val rows = queryList(
            """
            FOR r IN $CHAT_REACTIONS
              FILTER r.chatKey == @chatKey
              COLLECT emoji = r.emoji WITH COUNT INTO total
              SORT total DESC, emoji ASC
              LIMIT @limit
              RETURN { emoji, total }
            """.trimIndent(),
            mapOf("chatKey" to chatId, "limit" to limit),
        )
        return rows.map { string(it, "emoji") }.filter { it.isNotBlank() }
    }

    override fun searchPeople(query: String, excludeIds: Set<String>, limit: Int): List<EventAttendee> {
        val needle = query.trim().lowercase()
        val rows = queryList(
            """
            FOR u IN $USERS
              FILTER u._key NOT IN @exclude
              FILTER @needle == "" OR CONTAINS(LOWER(u.name), @needle)
              SORT u.lastActiveAt DESC, u._key DESC
              LIMIT @limit
              RETURN u
            """.trimIndent(),
            mapOf(
                "exclude" to excludeIds.toList(),
                "needle" to needle,
                "limit" to limit,
            ),
        )
        return rows.map(::userFrom).map { user ->
            EventAttendee(
                id = user.id,
                name = user.name,
                profilePhotoPath = user.profilePhotoPath,
                location = user.location,
                intro = user.intro,
            )
        }
    }

    override fun markChatNotificationsRead(userId: String, chatId: String, readAt: Long) {
        exec(
            """
            FOR n IN $NOTIFICATIONS
              FILTER n.userKey == @userKey AND n.chatKey == @chatKey AND n.readAt == null
              UPDATE n WITH { readAt: @readAt } IN $NOTIFICATIONS
              RETURN NEW
            """.trimIndent(),
            mapOf("userKey" to userId, "chatKey" to chatId, "readAt" to readAt),
        )
    }

    override fun markChatNoticeTypesRead(userId: String, types: Collection<String>, readAt: Long) {
        exec(
            """
            FOR n IN $NOTIFICATIONS
              FILTER n.userKey == @userKey AND n.readAt == null AND n.type IN @types
              UPDATE n WITH { readAt: @readAt } IN $NOTIFICATIONS
              RETURN NEW
            """.trimIndent(),
            mapOf("userKey" to userId, "types" to types.toList(), "readAt" to readAt),
        )
    }

    override fun unreadChatNotificationCount(userId: String): Long {
        val row = first(
            """
            FOR n IN $NOTIFICATIONS
              FILTER n.userKey == @userKey AND n.readAt == null
              FILTER n.type == "chat_message" OR n.type == "chat_added" OR n.type == "chat_removed" OR n.type == "chat_deleted"
              COLLECT WITH COUNT INTO total
              RETURN { total }
            """.trimIndent(),
            mapOf("userKey" to userId),
        )
        return long(row, "total")
    }

    private fun writePushSubscription(key: String, subscription: PushSubscriptionRecord) {
        db.collection(PUSH_SUBSCRIPTIONS).replaceDocument(key, pushDocument(key, subscription))
    }

    private fun pushDocument(key: String?, subscription: PushSubscriptionRecord): BaseDocument {
        val doc = if (key == null) BaseDocument() else BaseDocument(key)
        doc.addAttribute("userKey", subscription.userId)
        doc.addAttribute("endpoint", subscription.endpoint)
        doc.addAttribute("p256dh", subscription.p256dh)
        doc.addAttribute("auth", subscription.auth)
        doc.addAttribute("language", subscription.language)
        doc.addAttribute("createdAt", subscription.createdAt)
        return doc
    }

    private fun pushFrom(doc: BaseDocument) = PushSubscriptionRecord(
        endpoint = string(doc, "endpoint"),
        userId = string(doc, "userKey"),
        p256dh = string(doc, "p256dh"),
        auth = string(doc, "auth"),
        language = string(doc, "language").ifBlank { "en" },
        createdAt = long(doc, "createdAt"),
    )

    private fun joinedIds(userId: String?): Set<String> {
        if (userId.isNullOrBlank()) return emptySet()
        return queryList(
            "FOR m IN $MEMBERSHIPS FILTER m.userKey == @userKey RETURN m",
            mapOf("userKey" to userId),
        ).map { string(it, "communityKey") }.toSet()
    }

    private fun communityFrom(doc: BaseDocument) = CommunityRecord(
        id = doc.key,
        name = string(doc, "name"),
        emoji = string(doc, "emoji").ifBlank { "✨" },
        slug = string(doc, "slug"),
        createdBy = string(doc, "createdBy"),
        createdAt = long(doc, "createdAt"),
        memberCount = long(doc, "memberCount"),
        description = string(doc, "description"),
    )

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
        lastActiveAt = long(doc, "lastActiveAt").takeIf { it > 0 } ?: long(doc, "createdAt"),
        placeId = doc.getAttribute("placeId")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        lat = doc.getAttribute("lat")?.toString()?.toDoubleOrNull(),
        lng = doc.getAttribute("lng")?.toString()?.toDoubleOrNull(),
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

    private fun productDocument(product: ProductRecord): BaseDocument =
        BaseDocument().apply {
            addAttribute("userKey", product.userId)
            addAttribute("name", product.name)
            addAttribute("price", product.price)
            addAttribute("category", product.category)
            addAttribute("description", product.description)
            addAttribute("photoPaths", product.photoPaths)
            addAttribute("createdAt", product.createdAt)
        }

    private fun productDocument(key: String, product: ProductRecord): BaseDocument =
        BaseDocument(key).apply {
            addAttribute("userKey", product.userId)
            addAttribute("name", product.name)
            addAttribute("price", product.price)
            addAttribute("category", product.category)
            addAttribute("description", product.description)
            addAttribute("photoPaths", product.photoPaths)
            addAttribute("createdAt", product.createdAt)
        }

    private fun productFrom(doc: BaseDocument) = ProductRecord(
        id = doc.key,
        userId = string(doc, "userKey"),
        name = string(doc, "name"),
        price = long(doc, "price"),
        category = string(doc, "category"),
        description = string(doc, "description"),
        photoPaths = stringList(doc, "photoPaths"),
        createdAt = long(doc, "createdAt"),
    )

    private fun productOrderDocument(order: ProductOrderRecord): BaseDocument =
        BaseDocument().apply {
            addAttribute("buyerKey", order.buyerId)
            addAttribute("sellerKey", order.sellerId)
            addAttribute("productKey", order.productId)
            addAttribute("productName", order.productName)
            addAttribute("price", order.price)
            addAttribute("contactMethod", order.contactMethod)
            addAttribute("message", order.message)
            addAttribute("contactChatId", order.contactChatId)
            addAttribute("resolved", order.resolved)
            addAttribute("createdAt", order.createdAt)
        }

    private fun productOrderFrom(doc: BaseDocument) = ProductOrderRecord(
        id = doc.key,
        buyerId = string(doc, "buyerKey"),
        sellerId = string(doc, "sellerKey"),
        productId = string(doc, "productKey"),
        productName = string(doc, "productName"),
        price = long(doc, "price"),
        contactMethod = string(doc, "contactMethod"),
        message = string(doc, "message"),
        contactChatId = doc.getAttribute("contactChatId")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        resolved = boolean(doc, "resolved"),
        createdAt = long(doc, "createdAt"),
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

    private fun feedItemFrom(row: BaseDocument) = FeedItem(
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
        communityId = optional(row, "communityId"),
        communityName = optional(row, "communityName"),
        communityEmoji = optional(row, "communityEmoji"),
        communityOnly = boolean(row, "communityOnly"),
        placeId = optional(row, "placeId"),
        lat = doubleOrNull(row, "lat"),
        lng = doubleOrNull(row, "lng"),
        eventStartAt = longOrNull(row, "eventStartAt"),
        eventEndAt = longOrNull(row, "eventEndAt"),
        eventPrice = longOrNull(row, "eventPrice"),
        attendeeCount = long(row, "attendeeCount"),
        joined = boolean(row, "joined"),
        attendees = attendeeList(row, "attendees"),
        pollOptions = pollOptionList(row, "pollOptions"),
        hasQuestion = boolean(row, "hasQuestion"),
        questionTitle = string(row, "questionTitle"),
        answerCount = long(row, "answerCount"),
        answered = boolean(row, "answered"),
        ctaTitle = string(row, "ctaTitle"),
        ctaText = string(row, "ctaText"),
        ctaDestination = string(row, "ctaDestination"),
        ctaUrl = string(row, "ctaUrl"),
        authorZaloUrl = optional(row, "authorZaloUrl"),
    )

    private fun attendeeList(doc: BaseDocument, name: String): List<EventAttendee> =
        attendeeListFrom(doc.getAttribute(name))

    private fun pollOptionList(doc: BaseDocument, name: String): List<PollOptionView> {
        val raw = doc.getAttribute(name) ?: return emptyList()
        val rows = when (raw) {
            is Collection<*> -> raw
            is Array<*> -> raw.toList()
            else -> return emptyList()
        }
        return rows.mapNotNull(::pollOptionFromAny)
    }

    private fun pollOptionFrom(doc: BaseDocument): PollOptionView? = pollOptionFromAny(doc)

    private fun pollOptionFromAny(row: Any?): PollOptionView? {
        if (row == null) return null
        fun read(field: String): Any? = when (row) {
            is BaseDocument -> row.getAttribute(field)
            is Map<*, *> -> row[field]
            else -> null
        }
        val text = read("text")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val index = (read("index") as? Number)?.toInt() ?: return null
        return PollOptionView(
            index = index,
            text = text,
            voteCount = (read("voteCount") as? Number)?.toLong() ?: 0L,
            voted = when (val raw = read("voted")) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw.equals("true", true)
                else -> false
            },
            voters = attendeeListFrom(read("voters")),
        )
    }

    private fun attendeeListFrom(raw: Any?): List<EventAttendee> {
        val rows = when (raw) {
            is Collection<*> -> raw
            is Array<*> -> raw.toList()
            else -> return emptyList()
        }
        return rows.mapNotNull { row ->
            fun read(field: String): Any? = when (row) {
                is BaseDocument -> row.getAttribute(field)
                is Map<*, *> -> row[field]
                else -> null
            }
            fun str(field: String) = read(field)?.toString()?.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            fun strOrNull(field: String) = read(field)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            val id = str("id")
            if (id.isBlank()) null else EventAttendee(
                id = id,
                name = str("name"),
                profilePhotoPath = strOrNull("profilePhotoPath"),
                location = str("location"),
                intro = str("intro"),
                joinedAt = (read("joinedAt") as? Number)?.toLong() ?: 0L,
            )
        }
    }

    private fun pollOptionsAql(): String = """
        pollOptions: (
          LET options = p.pollOptions == null ? [] : p.pollOptions
          FOR optionIndex IN 0..${Limits.POLL_OPTIONS_MAX - 1}
            FILTER optionIndex < LENGTH(options)
            LET votes = (
              FOR v IN $POLL_VOTES
                FILTER v.postKey == p._key AND v.optionIndex == optionIndex
                SORT v.votedAt DESC
                LET person = DOCUMENT("$USERS", v.userKey)
                FILTER person != null
                RETURN {
                  id: person._key,
                  name: person.name,
                  profilePhotoPath: person.profilePhotoPath,
                  location: person.location,
                  intro: person.intro,
                  joinedAt: v.votedAt
                }
            )
            RETURN {
              index: optionIndex,
              text: options[optionIndex],
              voteCount: LENGTH(votes),
              voted: @viewerId != "" && LENGTH(
                FOR v IN $POLL_VOTES
                  FILTER v.postKey == p._key AND v.optionIndex == optionIndex AND v.userKey == @viewerId
                  LIMIT 1
                  RETURN 1
              ) > 0,
              voters: SLICE(votes, 0, 5)
            }
        )
    """.trimIndent()

   private fun questionAql(): String = """
       hasQuestion: p.hasQuestion == true,
       questionTitle: p.questionTitle == null ? "" : p.questionTitle,
       answerCount: @viewerId != "" && @viewerId == p.userKey ? LENGTH(
         FOR a IN $QUESTION_ANSWERS
           FILTER a.postKey == p._key
           RETURN 1
       ) : 0,
       answered: @viewerId != "" && LENGTH(
         FOR a IN $QUESTION_ANSWERS
           FILTER a.postKey == p._key AND a.userKey == @viewerId
           LIMIT 1
           RETURN 1
       ) > 0
   """.trimIndent()

   private fun ctaAql(): String = """
       ctaTitle: p.ctaTitle == null ? "" : p.ctaTitle,
       ctaText: p.ctaText == null ? "" : p.ctaText,
       ctaDestination: p.ctaDestination == null ? "" : p.ctaDestination,
       ctaUrl: p.ctaUrl == null ? "" : p.ctaUrl,
       authorZaloUrl: p.ctaDestination == "zalo" && author != null && author.phone != null && author.phone != ""
         ? CONCAT("https://zalo.me/", author.phone)
         : null
   """.trimIndent()

   private fun questionAnswerItemFrom(row: BaseDocument) = QuestionAnswerItem(
       id = string(row, "id"),
       postId = string(row, "postId"),
       userId = string(row, "userId"),
       authorName = string(row, "authorName"),
       authorPhotoPath = row.getAttribute("authorPhotoPath")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
       authorLocation = string(row, "authorLocation"),
       authorIntro = string(row, "authorIntro"),
       text = string(row, "text"),
       createdAt = long(row, "createdAt"),
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
        chatId = string(doc, "chatKey"),
    )

    private fun chatDocument(key: String?, chat: ChatRecord): BaseDocument {
        val doc = if (key == null) BaseDocument() else BaseDocument(key)
        doc.addAttribute("type", chat.type)
        doc.addAttribute("name", chat.name)
        doc.addAttribute("createdBy", chat.createdBy)
        doc.addAttribute("createdAt", chat.createdAt)
        doc.addAttribute("lastMessageAt", chat.lastMessageAt)
        doc.addAttribute("lastMessageId", chat.lastMessageId)
        doc.addAttribute("lastMessageText", chat.lastMessageText)
        doc.addAttribute("lastMessageSenderId", chat.lastMessageSenderId)
        doc.addAttribute("lastMessageSenderName", chat.lastMessageSenderName)
        if (chat.pairKey.isNotBlank()) doc.addAttribute("pairKey", chat.pairKey)
        return doc
    }

    private fun chatFrom(doc: BaseDocument) = ChatRecord(
        id = doc.key,
        type = string(doc, "type"),
        name = string(doc, "name"),
        createdBy = string(doc, "createdBy"),
        createdAt = long(doc, "createdAt"),
        lastMessageAt = long(doc, "lastMessageAt"),
        lastMessageId = string(doc, "lastMessageId"),
        lastMessageText = string(doc, "lastMessageText"),
        lastMessageSenderId = string(doc, "lastMessageSenderId"),
        lastMessageSenderName = string(doc, "lastMessageSenderName"),
        pairKey = string(doc, "pairKey"),
    )

    private fun chatMemberFrom(doc: BaseDocument) = ChatMemberRecord(
        chatId = string(doc, "chatKey"),
        userId = string(doc, "userKey"),
        owner = boolean(doc, "owner"),
        joinedAt = long(doc, "joinedAt"),
        lastReadAt = long(doc, "lastReadAt"),
        unreadCount = long(doc, "unreadCount"),
    )

    private fun chatMessageFrom(doc: BaseDocument) = ChatMessageRecord(
        id = doc.key,
        chatId = string(doc, "chatKey"),
        userId = string(doc, "userKey"),
        text = string(doc, "text"),
        photoPaths = stringList(doc, "photoPaths"),
        createdAt = long(doc, "createdAt"),
        attachment = string(doc, "attachmentType").takeIf { it.isNotBlank() }?.let { type ->
            ChatAttachment(
                type = type,
                productId = string(doc, "attachmentProductId"),
                productName = string(doc, "attachmentProductName"),
                price = long(doc, "attachmentPrice"),
                photoPath = string(doc, "attachmentPhotoPath").takeIf { it.isNotBlank() && it != "null" },
            )
        },
    )

    private fun chatReactionFrom(doc: BaseDocument) = ChatReactionRecord(
        id = doc.key,
        chatId = string(doc, "chatKey"),
        messageId = string(doc, "messageKey"),
        userId = string(doc, "userKey"),
        emoji = string(doc, "emoji"),
        createdAt = long(doc, "createdAt"),
    )

    private fun arangoChatFaces(chat: ChatRecord, members: List<ChatMemberRecord>, viewerId: String): List<ChatFace> {
        if (chat.type == ChatType.DIRECT) {
            val peer = members.firstOrNull { it.userId != viewerId } ?: members.firstOrNull()
            val user = peer?.let { findUser(it.userId) } ?: return emptyList()
            return listOf(ChatFace(user.id, user.name, user.profilePhotoPath))
        }
        val recent = queryList(
            """
            FOR m IN $CHAT_MESSAGES
              FILTER m.chatKey == @chatKey
              SORT m.createdAt DESC, m._key DESC
              LIMIT 20
              RETURN m
            """.trimIndent(),
            mapOf("chatKey" to chat.id),
        ).map { string(it, "userKey") }.filter { it.isNotBlank() }.distinct()
        val ordered = (recent + members.sortedByDescending { it.joinedAt }.map { it.userId }).distinct()
        return ordered.take(2).mapNotNull { userId ->
            val user = findUser(userId) ?: return@mapNotNull null
            ChatFace(user.id, user.name, user.profilePhotoPath)
        }
    }

    private fun toChatMessageItem(message: ChatMessageRecord, viewerId: String): ChatMessageItem {
        val author = findUser(message.userId)
        return ChatMessageItem(
            id = message.id,
            chatId = message.chatId,
            userId = message.userId,
            authorName = author?.name.orEmpty(),
            authorPhotoPath = author?.profilePhotoPath,
            text = message.text,
            photoPaths = message.photoPaths,
            createdAt = message.createdAt,
            reactions = chatReactionCounts(message.id, viewerId),
            attachment = message.attachment,
        )
    }

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

    private fun longOrNull(doc: BaseDocument, name: String): Long? =
        (doc.getAttribute(name) as? Number)?.toLong()

    private fun doubleOrNull(doc: BaseDocument, name: String): Double? =
        (doc.getAttribute(name) as? Number)?.toDouble()

    companion object {
        private const val USERS = "users"
        private const val SESSIONS = "sessions"
        private const val AVATARS = "avatars"
        private const val MUSIC = "music"
        private const val POSTS = "posts"
        private const val COMMENTS = "comments"
        private const val COMMENT_WATCHES = "commentWatches"
        private const val NOTIFICATIONS = "notifications"
        private const val PUSH_SUBSCRIPTIONS = "pushSubscriptions"
        private const val ADMINS = "admins"
        private const val DAY_STATS = "dayStats"
        private const val COMMUNITIES = "communities"
        private const val MEMBERSHIPS = "memberships"
        private const val EVENT_ATTENDANCES = "eventAttendances"
        private const val POLL_VOTES = "pollVotes"
        private const val QUESTION_ANSWERS = "questionAnswers"
        private const val FEATURE_SUGGESTIONS = "featureSuggestions"
        private const val FEATURE_VOTES = "featureVotes"
        private const val PRODUCTS = "products"
        private const val PRODUCT_ORDERS = "productOrders"
        private const val CHATS = "chats"
        private const val CHAT_MEMBERS = "chatMembers"
        private const val CHAT_MESSAGES = "chatMessages"
        private const val CHAT_REACTIONS = "chatReactions"
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
