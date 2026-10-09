package quest.saigon.town

import quest.saigon.Limits
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryTownRepository : TownRepository {
    private val users = ConcurrentHashMap<String, UserRecord>()
    private val phones = ConcurrentHashMap<String, String>()
    private val sessions = ConcurrentHashMap<String, SessionRecord>()
    private val avatars = ConcurrentHashMap<String, AvatarRecord>()
    private val music = ConcurrentHashMap<String, MusicRecord>()
    private val posts = ConcurrentHashMap<String, PostRecord>()
    private val comments = ConcurrentHashMap<String, CommentRecord>()
    private val watches = ConcurrentHashMap<String, Boolean>()
    private val notifications = ConcurrentHashMap<String, NotificationRecord>()
    private val admins = ConcurrentHashMap<String, AdminRecord>()
    private val daily = ConcurrentHashMap<String, DailyBucket>()

    override fun createUser(phone: String, passwordHash: String, name: String, createdAt: Long): UserRecord {
        val id = newId()
        val user = UserRecord(id, phone, passwordHash, name, "", null, "system", createdAt)
        synchronized(this) {
            if (phones.containsKey(phone)) throw TownException("phone_taken")
            users[id] = user
            phones[phone] = id
        }
        return user
    }

    override fun findUserByPhone(phone: String): UserRecord? = phones[phone]?.let { users[it] }

    override fun findUser(id: String): UserRecord? = users[id]

    override fun updateUser(user: UserRecord) {
        synchronized(this) {
            val current = users[user.id] ?: throw TownException("not_found")
            if (current.phone != user.phone) {
                val owner = phones[user.phone]
                if (owner != null && owner != user.id) throw TownException("phone_taken")
                phones.remove(current.phone)
                phones[user.phone] = user.id
            }
            users[user.id] = user
        }
    }

    override fun createSession(token: String, userId: String, expiresAt: Long) {
        sessions[token] = SessionRecord(token, userId, expiresAt)
    }

    override fun findSession(token: String): SessionRecord? = sessions[token]

    override fun deleteSession(token: String) {
        sessions.remove(token)
    }

    override fun insertAvatar(userId: String, prompt: String, createdAt: Long): AvatarRecord {
        val avatar = AvatarRecord(newId(), userId, prompt, null, AvatarStatus.GENERATING, null, createdAt)
        avatars[avatar.id] = avatar
        return avatar
    }

    override fun updateAvatar(id: String, status: String, imagePath: String?, error: String?) {
        val current = avatars[id] ?: return
        avatars[id] = current.copy(status = status, imagePath = imagePath ?: current.imagePath, error = error)
    }

    override fun findAvatar(id: String): AvatarRecord? = avatars[id]

    override fun avatarsFor(userId: String): List<AvatarRecord> =
        avatars.values.filter { it.userId == userId && it.status != AvatarStatus.REMOVED }
            .sortedByDescending { it.createdAt }

    override fun hasGeneratingAvatar(userId: String): Boolean =
        avatars.values.any { it.userId == userId && it.status == AvatarStatus.GENERATING }

    override fun deleteAvatar(id: String) {
        avatars.remove(id)
    }

    override fun avatarReferenced(id: String): Boolean = posts.values.any { it.avatarId == id }

    override fun insertMusic(
        userId: String,
        prompt: String,
        name: String,
        gender: String?,
        instrumental: Boolean,
        createdAt: Long,
    ): MusicRecord {
        val record = MusicRecord(
            id = newId(),
            userId = userId,
            prompt = prompt,
            name = name,
            audioPath = null,
            status = MusicStatus.GENERATING,
            error = null,
            gender = gender,
            instrumental = instrumental,
            createdAt = createdAt,
        )
        music[record.id] = record
        return record
    }

    override fun updateMusic(id: String, status: String, audioPath: String?, error: String?, name: String?, lyrics: String?) {
        val current = music[id] ?: return
        music[id] = current.copy(
            status = status,
            audioPath = audioPath ?: current.audioPath,
            error = error,
            name = name?.takeIf { it.isNotBlank() } ?: current.name,
            lyrics = lyrics ?: current.lyrics,
        )
    }

    override fun findMusic(id: String): MusicRecord? = music[id]

    override fun musicsFor(userId: String): List<MusicRecord> =
        music.values.filter { it.userId == userId && it.status != MusicStatus.REMOVED }
            .sortedByDescending { it.createdAt }

    override fun searchMusic(query: String, cursor: String?, limit: Int): MusicPage {
        val needle = query.trim().lowercase()
        val parsed = Cursors.parse(cursor)
        val sorted = music.values
            .filter { it.status == MusicStatus.READY && it.audioPath != null }
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.prompt.lowercase().contains(needle) }
            .sortedWith(compareByDescending<MusicRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit)
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return MusicPage(items, next)
    }

    override fun hasGeneratingMusic(userId: String): Boolean =
        music.values.any { it.userId == userId && it.status == MusicStatus.GENERATING }

    override fun deleteMusic(id: String) {
        music.remove(id)
    }

    override fun musicReferenced(id: String): Boolean = posts.values.any { it.musicId == id }

    override fun insertPost(post: PostRecord): PostRecord {
        val stored = if (post.id.isBlank()) post.copy(id = newId()) else post
        posts[stored.id] = stored
        return stored
    }

    override fun feed(cursor: String?, limit: Int): FeedPage {
        val parsed = Cursors.parse(cursor)
        val sorted = posts.values.sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map(::toFeedItem)
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return FeedPage(items, next)
    }

    override fun findPost(id: String): PostRecord? = posts[id]

    override fun feedItem(id: String): FeedItem? = posts[id]?.let(::toFeedItem)

    override fun postsByUser(userId: String, cursor: String?, limit: Int): OwnedPostPage {
        val parsed = Cursors.parse(cursor)
        val sorted = posts.values
            .filter { it.userId == userId }
            .sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map { post ->
            OwnedPost(
                id = post.id,
                message = post.message,
                location = post.location,
                photoPath = post.photoPath,
                avatarPath = post.avatarId?.let { avatars[it]?.imagePath },
                createdAt = post.createdAt,
                views = post.views,
                replyClicks = post.replyClicks,
                shareClicks = post.shareClicks,
                musicName = post.musicId?.let { music[it]?.name },
            )
        }
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return OwnedPostPage(items, next)
    }

    override fun recordView(id: String) {
        val post = posts[id] ?: return
        posts[id] = post.copy(views = post.views + 1)
    }

    override fun recordReplyClick(id: String) {
        val post = posts[id] ?: return
        posts[id] = post.copy(replyClicks = post.replyClicks + 1)
    }

    override fun recordShareClick(id: String) {
        val post = posts[id] ?: return
        posts[id] = post.copy(shareClicks = post.shareClicks + 1)
    }

    override fun updateQuickReplies(id: String, replies: List<String>) {
        val post = posts[id] ?: return
        posts[id] = post.copy(quickReplies = replies.take(Limits.QUICK_REPLY_COUNT))
    }

    override fun deletePost(id: String) {
        posts.remove(id)
        comments.values.filter { it.postId == id }.forEach { comments.remove(it.id) }
        watches.keys.filter { it.startsWith("$id:") }.forEach { watches.remove(it) }
    }

    override fun insertComment(comment: CommentRecord): CommentRecord {
        val stored = if (comment.id.isBlank()) comment.copy(id = newId()) else comment
        comments[stored.id] = stored
        val post = posts[stored.postId]
        if (post != null) posts[stored.postId] = post.copy(commentCount = post.commentCount + 1)
        return stored
    }

    override fun findComment(id: String): CommentRecord? = comments[id]

    override fun deleteComment(id: String) {
        val comment = comments.remove(id) ?: return
        val post = posts[comment.postId] ?: return
        posts[comment.postId] = post.copy(commentCount = (post.commentCount - 1).coerceAtLeast(0))
    }

    override fun commentItem(id: String): CommentItem? = comments[id]?.let(::toCommentItem)

    override fun commentsForPost(postId: String, cursor: String?, limit: Int): CommentPage {
        val parsed = Cursors.parse(cursor)
        val sorted = comments.values
            .filter { it.postId == postId }
            .sortedWith(compareByDescending<CommentRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map(::toCommentItem)
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        val total = posts[postId]?.commentCount ?: comments.values.count { it.postId == postId }.toLong()
        return CommentPage(items, next, total)
    }

    override fun latestComments(postId: String, limit: Int): List<CommentItem> =
        comments.values
            .filter { it.postId == postId }
            .sortedWith(compareByDescending<CommentRecord> { it.createdAt }.thenByDescending { it.id })
            .take(limit)
            .map(::toCommentItem)

    override fun armCommentWatch(postId: String, userId: String) {
        watches["$postId:$userId"] = true
    }

    override fun disarmCommentWatch(postId: String, userId: String) {
        watches["$postId:$userId"] = false
    }

    override fun armedWatchers(postId: String): List<String> =
        watches.entries
            .filter { it.value && it.key.startsWith("$postId:") }
            .map { it.key.substringAfter(':') }

    override fun insertNotification(notification: NotificationRecord): NotificationRecord {
        val stored = if (notification.id.isBlank()) notification.copy(id = newId()) else notification
        notifications[stored.id] = stored
        return stored
    }

    override fun notificationsFor(userId: String, cursor: String?, limit: Int): NotificationPage {
        val parsed = Cursors.parse(cursor)
        val sorted = notifications.values
            .filter { it.userId == userId }
            .sortedWith(compareByDescending<NotificationRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit)
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return NotificationPage(items, next, unreadNotificationCount(userId))
    }

    override fun findNotification(id: String): NotificationRecord? = notifications[id]

    override fun markAllNotificationsRead(userId: String, readAt: Long): Long {
        notifications.entries.forEach { (id, item) ->
            if (item.userId == userId && item.readAt == null) notifications[id] = item.copy(readAt = readAt)
        }
        return unreadNotificationCount(userId)
    }

    override fun markNotificationRead(id: String, readAt: Long): NotificationRecord? {
        val current = notifications[id] ?: return null
        if (current.readAt != null) return current
        val updated = current.copy(readAt = readAt)
        notifications[id] = updated
        return updated
    }

    override fun unreadNotificationCount(userId: String): Long =
        notifications.values.count { it.userId == userId && it.readAt == null }.toLong()

    private fun toFeedItem(post: PostRecord): FeedItem {
        val author = users[post.userId]
        val avatar = post.avatarId?.let { avatars[it] }
        val track = post.musicId?.let { music[it] }
        return FeedItem(
            id = post.id,
            authorId = post.userId,
            authorName = author?.name ?: "",
            authorPhotoPath = author?.profilePhotoPath,
            message = post.message,
            location = post.location,
            photoPath = post.photoPath,
            avatarPath = avatar?.imagePath,
            authorLocation = author?.location.orEmpty(),
            authorIntro = author?.intro.orEmpty(),
            createdAt = post.createdAt,
            musicName = track?.name,
            musicPath = track?.audioPath,
            quickReplies = post.quickReplies,
            commentCount = post.commentCount,
            previewComments = latestComments(post.id, Limits.COMMENT_PREVIEW),
            extraPhotoPaths = post.extraPhotoPaths,
        )
    }

    private fun toCommentItem(comment: CommentRecord): CommentItem {
        val author = users[comment.userId]
        val parent = comment.replyToId?.let { comments[it] }
        val parentAuthor = parent?.userId?.let { users[it] }
        return CommentItem(
            id = comment.id,
            postId = comment.postId,
            userId = comment.userId,
            authorName = author?.name ?: "",
            authorPhotoPath = author?.profilePhotoPath,
            authorLocation = author?.location.orEmpty(),
            authorIntro = author?.intro.orEmpty(),
            text = comment.text,
            createdAt = comment.createdAt,
            replyToId = parent?.id,
            replyToAuthorName = parentAuthor?.name,
            replyToText = parent?.text,
        )
    }

    override fun recordVisit(userId: String?, at: Long) {
        val bucket = daily.getOrPut(SaigonDays.dayKey(at)) { DailyBucket() }
        synchronized(bucket) {
            if (userId == null) {
                bucket.visitorVisits += 1
            } else {
                bucket.userVisits += 1
                bucket.uniqueUsers.add(userId)
            }
        }
    }

    override fun recordDailyEvent(field: String, at: Long) {
        val bucket = daily.getOrPut(SaigonDays.dayKey(at)) { DailyBucket() }
        synchronized(bucket) {
            when (field) {
                "postViews" -> bucket.postViews += 1
                "replyClicks" -> bucket.replyClicks += 1
                "shareClicks" -> bucket.shareClicks += 1
            }
        }
    }

    override fun statsByDay(now: Long): List<DayStats> {
        val postsByDay = posts.values.groupingBy { SaigonDays.dayKey(it.createdAt) }.eachCount()
        val usersByDay = users.values.groupingBy { SaigonDays.dayKey(it.createdAt) }.eachCount()
        return SaigonDays.range(now).map { day ->
            val bucket = daily[day]
            val snapshot = if (bucket == null) {
                DayStats(day)
            } else {
                synchronized(bucket) {
                    DayStats(
                        day = day,
                        visitorVisits = bucket.visitorVisits,
                        userVisits = bucket.userVisits,
                        uniqueUserVisits = bucket.uniqueUsers.size.toLong(),
                        replyClicks = bucket.replyClicks,
                        shareClicks = bucket.shareClicks,
                        postViews = bucket.postViews,
                    )
                }
            }
            snapshot.copy(
                posts = postsByDay[day]?.toLong() ?: 0L,
                newUsers = usersByDay[day]?.toLong() ?: 0L,
            )
        }
    }

    override fun allTimeStats() = AllTimeStats(
        totalUsers = users.size.toLong(),
        totalPosts = posts.size.toLong(),
    )

    override fun findAdmin(userId: String): AdminRecord? = admins[userId]

    override fun claimFirstAdmin(userId: String, createdAt: Long): Boolean {
        synchronized(this) {
            if (admins.containsKey(userId)) return true
            if (admins.isNotEmpty()) return false
            admins[userId] = AdminRecord(userId, createdAt)
            return true
        }
    }

    override fun usersPage(cursor: String?, limit: Int): AdminUserPage {
        val parsed = Cursors.parse(cursor)
        val sorted = users.values.sortedWith(compareByDescending<UserRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map { user ->
            AdminUser(
                id = user.id,
                name = user.name,
                intro = user.intro,
                profilePhotoPath = user.profilePhotoPath,
                createdAt = user.createdAt,
                postCount = posts.values.count { it.userId == user.id }.toLong(),
                paused = user.paused,
            )
        }
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return AdminUserPage(items, next)
    }

    override fun setUserPaused(userId: String, paused: Boolean): UserRecord? {
        synchronized(this) {
            val current = users[userId] ?: return null
            val next = current.copy(paused = paused)
            users[userId] = next
            return next
        }
    }

    override fun adminPosts(cursor: String?, limit: Int): AdminPostPage {
        val parsed = Cursors.parse(cursor)
        val sorted = posts.values.sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map { post ->
            val author = users[post.userId]
            AdminPost(
                id = post.id,
                authorId = post.userId,
                authorName = author?.name.orEmpty(),
                authorPhotoPath = author?.profilePhotoPath,
                message = post.message,
                location = post.location,
                photoPath = post.photoPath,
                avatarPath = post.avatarId?.let { avatars[it]?.imagePath },
                createdAt = post.createdAt,
                views = post.views,
                replyClicks = post.replyClicks,
                shareClicks = post.shareClicks,
                musicName = post.musicId?.let { music[it]?.name },
            )
        }
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return AdminPostPage(items, next)
    }

    override fun adminComments(cursor: String?, limit: Int): CommentPage {
        val parsed = Cursors.parse(cursor)
        val sorted = comments.values.sortedWith(compareByDescending<CommentRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map { comment ->
            toCommentItem(comment).copy(postText = posts[comment.postId]?.message)
        }
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return CommentPage(items, next, comments.size.toLong())
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    private class DailyBucket {
        var visitorVisits = 0L
        var userVisits = 0L
        val uniqueUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()
        var replyClicks = 0L
        var shareClicks = 0L
        var postViews = 0L
    }
}
