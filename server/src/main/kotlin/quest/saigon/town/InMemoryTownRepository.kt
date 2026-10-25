package quest.saigon.town

import quest.saigon.CallToAction
import quest.saigon.Limits
import quest.saigon.PhoneNumbers
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
    private val pushSubscriptions = ConcurrentHashMap<String, PushSubscriptionRecord>()
    private val admins = ConcurrentHashMap<String, AdminRecord>()
    private val communities = ConcurrentHashMap<String, CommunityRecord>()
    private val memberships = ConcurrentHashMap<String, Membership>()
    private val attendances = ConcurrentHashMap<String, Attendance>()
    private val pollVotes = ConcurrentHashMap<String, PollVote>()
    private val questionAnswers = ConcurrentHashMap<String, QuestionAnswerRecord>()
    private val features = ConcurrentHashMap<String, FeatureSuggestionRecord>()
    private val featureVotes = ConcurrentHashMap<String, FeatureVote>()
    private val chats = ConcurrentHashMap<String, ChatRecord>()
    private val chatMembers = ConcurrentHashMap<String, ChatMemberRecord>()
    private val chatMessages = ConcurrentHashMap<String, ChatMessageRecord>()
    private val chatReactions = ConcurrentHashMap<String, ChatReactionRecord>()
    private val daily = ConcurrentHashMap<String, DailyBucket>()

    override fun createUser(phone: String, passwordHash: String, name: String, createdAt: Long): UserRecord {
        val id = newId()
        val user = UserRecord(id, phone, passwordHash, name, "", null, "system", createdAt, lastActiveAt = createdAt)
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

    override fun feed(cursor: String?, limit: Int, viewerId: String?): FeedPage = pagePosts(
        posts.values.filter { !it.communityOnly },
        cursor,
        limit,
        viewerId,
    )

    override fun searchPosts(query: String, cursor: String?, limit: Int, viewerId: String?): FeedPage {
        val needle = query.trim().lowercase()
        val matching = posts.values.filter { post ->
            if (!placePostVisible(post, viewerId)) return@filter false
            if (needle.isBlank()) return@filter true
            val authorName = users[post.userId]?.name.orEmpty()
            val communityName = post.communityId?.let { communities[it]?.name }.orEmpty()
            val eventDetails = listOfNotNull(
                post.eventStartAt?.toString(),
                post.eventEndAt?.toString(),
                post.eventPrice?.toString(),
                if (post.eventStartAt != null && (post.eventPrice == null || post.eventPrice == 0L)) "free" else null,
            )
            val searchable = listOf(
                authorName,
                post.message,
                post.location,
                communityName,
                post.questionTitle,
                post.ctaTitle,
                post.ctaText,
                post.ctaDestination,
                post.ctaUrl,
            ) + post.pollOptions + eventDetails
            searchable.any { it.lowercase().contains(needle) }
        }
        return pagePosts(matching, cursor, limit, viewerId)
    }

    override fun communityFeed(communityId: String, cursor: String?, limit: Int, viewerId: String?): FeedPage = pagePosts(
        posts.values.filter { it.communityId == communityId },
        cursor,
        limit,
        viewerId,
    )

    private fun pagePosts(source: Collection<PostRecord>, cursor: String?, limit: Int, viewerId: String?): FeedPage {
        val parsed = Cursors.parse(cursor)
        val sorted = source.sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map { toFeedItem(it, viewerId) }
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return FeedPage(items, next)
    }

    override fun findPost(id: String): PostRecord? = posts[id]

    override fun feedItem(id: String, viewerId: String?): FeedItem? = posts[id]?.let { toFeedItem(it, viewerId) }

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
            val community = post.communityId?.let { communities[it] }
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
                communityId = community?.id,
                communityName = community?.name,
                communityEmoji = community?.emoji,
                communityOnly = post.communityOnly,
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

    override fun updatePostMessage(id: String, message: String) {
        val post = posts[id] ?: return
        posts[id] = post.copy(message = message)
    }

    override fun updateQuickReplies(id: String, replies: List<String>) {
        val post = posts[id] ?: return
        posts[id] = post.copy(quickReplies = replies.take(Limits.QUICK_REPLY_COUNT))
    }

    override fun deletePost(id: String) {
        posts.remove(id)
        comments.values.filter { it.postId == id }.forEach { comments.remove(it.id) }
        watches.keys.filter { it.startsWith("$id:") }.forEach { watches.remove(it) }
        attendances.keys.filter { it.startsWith("${id}_") || attendances[it]?.postId == id }.forEach { attendances.remove(it) }
        pollVotes.keys.filter { it.startsWith("${id}_") || pollVotes[it]?.postId == id }.forEach { pollVotes.remove(it) }
        questionAnswers.keys.filter { it.startsWith("${id}_") || questionAnswers[it]?.postId == id }.forEach { questionAnswers.remove(it) }
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
            .filter { it.userId == userId && !NotificationType.isChat(it.type) }
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
            if (item.userId == userId && item.readAt == null && !NotificationType.isChat(item.type)) {
                notifications[id] = item.copy(readAt = readAt)
            }
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
        notifications.values.count { it.userId == userId && it.readAt == null && !NotificationType.isChat(it.type) }.toLong()

    private fun toFeedItem(post: PostRecord, viewerId: String? = null): FeedItem {
        val author = users[post.userId]
        val avatar = post.avatarId?.let { avatars[it] }
        val track = post.musicId?.let { music[it] }
        val community = post.communityId?.let { communities[it] }
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
            communityId = community?.id,
            communityName = community?.name,
            communityEmoji = community?.emoji,
            communityOnly = post.communityOnly,
            placeId = post.placeId,
            lat = post.lat,
            lng = post.lng,
            eventStartAt = post.eventStartAt,
            eventEndAt = post.eventEndAt,
            eventPrice = post.eventPrice,
            attendeeCount = post.attendeeCount,
            joined = viewerId != null && isAttending(post.id, viewerId),
            attendees = previewAttendees(post.id),
            pollOptions = pollViews(post, viewerId),
            hasQuestion = post.hasQuestion,
            questionTitle = post.questionTitle,
            answerCount = if (viewerId == post.userId) {
                questionAnswers.values.count { it.postId == post.id }.toLong()
            } else {
                0L
            },
            answered = viewerId != null && questionAnswers.containsKey(membershipKey(post.id, viewerId)),
            ctaTitle = post.ctaTitle,
            ctaText = post.ctaText,
            ctaDestination = post.ctaDestination,
            ctaUrl = post.ctaUrl,
            authorZaloUrl = if (post.ctaDestination == CallToAction.ZALO) {
                author?.phone?.takeIf { it.isNotBlank() }?.let(PhoneNumbers::zaloUrl)
            } else {
                null
            },
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
                users[userId]?.let { users[userId] = it.copy(lastActiveAt = at) }
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

    override fun insertCommunity(community: CommunityRecord): CommunityRecord {
        if (communities.values.any { it.slug == community.slug }) throw TownException("community_taken")
        val stored = if (community.id.isBlank()) community.copy(id = newId()) else community
        communities[stored.id] = stored
        return stored
    }

    override fun findCommunity(id: String): CommunityRecord? = communities[id]

    override fun findCommunityBySlug(slug: String): CommunityRecord? =
        communities.values.firstOrNull { it.slug == slug }

    override fun updateCommunityDescription(id: String, description: String): CommunityRecord? {
        val current = communities[id] ?: return null
        val updated = current.copy(description = description)
        communities[id] = updated
        return updated
    }

    override fun searchCommunities(query: String, userId: String?, limit: Int): List<CommunityView> {
        val needle = query.trim().lowercase()
        return communities.values
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.slug.contains(needle) }
            .sortedWith(compareByDescending<CommunityRecord> { it.memberCount }.thenBy { it.name.lowercase() })
            .take(limit)
            .map { CommunityView(it, userId != null && isMember(it.id, userId)) }
    }

    override fun communitiesForUser(userId: String): List<CommunityRecord> =
        memberships.values
            .filter { it.userId == userId }
            .sortedByDescending { it.joinedAt }
            .mapNotNull { communities[it.communityId] }

    override fun joinCommunity(communityId: String, userId: String, joinedAt: Long): Boolean {
        val community = communities[communityId] ?: return false
        val key = membershipKey(communityId, userId)
        if (memberships.containsKey(key)) return false
        memberships[key] = Membership(communityId, userId, joinedAt)
        communities[communityId] = community.copy(memberCount = community.memberCount + 1)
        return true
    }

    override fun leaveCommunity(communityId: String, userId: String): Boolean {
        val community = communities[communityId] ?: return false
        if (memberships.remove(membershipKey(communityId, userId)) == null) return false
        communities[communityId] = community.copy(memberCount = (community.memberCount - 1).coerceAtLeast(0))
        return true
    }

    override fun isMember(communityId: String, userId: String): Boolean =
        memberships.containsKey(membershipKey(communityId, userId))

    override fun members(communityId: String, cursor: String?, limit: Int): CommunityMemberPage {
        val parsed = Cursors.parse(cursor)
        val sorted = memberships.values
            .filter { it.communityId == communityId }
            .sortedWith(compareByDescending<Membership> { it.joinedAt }.thenByDescending { membershipKey(it.communityId, it.userId) })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (joinedAt, key) = parsed
            sorted.filter { it.joinedAt < joinedAt || (it.joinedAt == joinedAt && membershipKey(it.communityId, it.userId) < key) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).mapNotNull { membership ->
            val user = users[membership.userId] ?: return@mapNotNull null
            CommunityMember(
                id = user.id,
                name = user.name,
                profilePhotoPath = user.profilePhotoPath,
                location = user.location,
                intro = user.intro,
                joinedAt = membership.joinedAt,
            )
        }
        val next = if (page.size > limit) {
            page.getOrNull(limit - 1)?.let { Cursors.encode(it.joinedAt, membershipKey(it.communityId, it.userId)) }
        } else {
            null
        }
        return CommunityMemberPage(items, next)
    }

    override fun joinEvent(postId: String, userId: String, joinedAt: Long): Boolean {
        val post = posts[postId] ?: return false
        if (post.eventStartAt == null) return false
        val key = membershipKey(postId, userId)
        if (attendances.containsKey(key)) return false
        attendances[key] = Attendance(postId, userId, joinedAt)
        posts[postId] = post.copy(attendeeCount = post.attendeeCount + 1)
        return true
    }

    override fun leaveEvent(postId: String, userId: String): Boolean {
        val post = posts[postId] ?: return false
        if (attendances.remove(membershipKey(postId, userId)) == null) return false
        posts[postId] = post.copy(attendeeCount = (post.attendeeCount - 1).coerceAtLeast(0))
        return true
    }

    override fun isAttending(postId: String, userId: String): Boolean =
        attendances.containsKey(membershipKey(postId, userId))

    override fun attendees(postId: String, cursor: String?, limit: Int): EventAttendeePage {
        val parsed = Cursors.parse(cursor)
        val sorted = attendances.values
            .filter { it.postId == postId }
            .sortedWith(compareByDescending<Attendance> { it.joinedAt }.thenByDescending { membershipKey(it.postId, it.userId) })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (joinedAt, key) = parsed
            sorted.filter { it.joinedAt < joinedAt || (it.joinedAt == joinedAt && membershipKey(it.postId, it.userId) < key) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).mapNotNull { attendance ->
            val user = users[attendance.userId] ?: return@mapNotNull null
            EventAttendee(
                id = user.id,
                name = user.name,
                profilePhotoPath = user.profilePhotoPath,
                location = user.location,
                intro = user.intro,
                phone = user.phone,
                joinedAt = attendance.joinedAt,
            )
        }
        val next = if (page.size > limit) {
            page.getOrNull(limit - 1)?.let { Cursors.encode(it.joinedAt, membershipKey(it.postId, it.userId)) }
        } else {
            null
        }
        return EventAttendeePage(items, next)
    }

    override fun attendeeIds(postId: String): List<String> =
        attendances.values.filter { it.postId == postId }.map { it.userId }

    override fun upcomingEvents(fromMillis: Long, viewerId: String?, limit: Int): List<FeedItem> =
        posts.values
            .filter { start ->
                val at = start.eventStartAt ?: return@filter false
                at >= fromMillis && !start.communityOnly
            }
            .sortedWith(compareBy<PostRecord> { it.eventStartAt }.thenBy { it.id })
            .take(limit)
            .map { toFeedItem(it, viewerId) }

    override fun eventCounts(todayStart: Long, tomorrowStart: Long, tomorrowEnd: Long, weekEnd: Long): EventSummary {
        val upcoming = posts.values.filter { post ->
            val start = post.eventStartAt ?: return@filter false
            !post.communityOnly && start >= todayStart && start < weekEnd
        }
        return EventSummary(
            today = upcoming.count { it.eventStartAt!! in todayStart until tomorrowStart }.toLong(),
            tomorrow = upcoming.count { it.eventStartAt!! in tomorrowStart until tomorrowEnd }.toLong(),
            week = upcoming.size.toLong(),
        )
    }

    override fun recentPlaces(communityId: String?, limit: Int): List<PlaceSuggestion> {
        val seen = linkedSetOf<String>()
        val places = mutableListOf<PlaceSuggestion>()
        posts.values
            .filter { post ->
                post.location.isNotBlank() &&
                    (communityId.isNullOrBlank() || post.communityId == communityId)
            }
            .sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
            .forEach { post ->
                val key = post.placeId?.takeIf { it.isNotBlank() } ?: post.location.lowercase()
                if (seen.add(key)) {
                    places += PlaceSuggestion(
                        name = post.location,
                        placeId = post.placeId,
                        lat = post.lat,
                        lng = post.lng,
                        recent = true,
                    )
                }
            }
        return places.take(limit)
    }

    override fun postsAtPlace(
        location: String,
        placeId: String?,
        cursor: String?,
        limit: Int,
        viewerId: String?,
    ): FeedPage = pagePosts(placePosts(location, placeId, viewerId), cursor, limit, viewerId)

    override fun countPostsAtPlace(location: String, placeId: String?, viewerId: String?): Long =
        placePosts(location, placeId, viewerId).size.toLong()

    override fun postsByAuthor(userId: String, cursor: String?, limit: Int, viewerId: String?): FeedPage =
        pagePosts(authorPosts(userId, viewerId), cursor, limit, viewerId)

    override fun countPostsByAuthor(userId: String, viewerId: String?): Long =
        authorPosts(userId, viewerId).size.toLong()

    private fun placePosts(location: String, placeId: String?, viewerId: String?): List<PostRecord> {
        val pid = placeId?.takeIf { it.isNotBlank() }
        return posts.values.filter { post ->
            post.location == location &&
                post.placeId?.takeIf { it.isNotBlank() } == pid &&
                placePostVisible(post, viewerId)
        }
    }

    private fun authorPosts(userId: String, viewerId: String?): List<PostRecord> =
        posts.values.filter { post -> post.userId == userId && authorPostVisible(post, viewerId) }

    private fun authorPostVisible(post: PostRecord, viewerId: String?): Boolean =
        viewerId == post.userId || placePostVisible(post, viewerId)

    private fun placePostVisible(post: PostRecord, viewerId: String?): Boolean {
        if (!post.communityOnly) return true
        val communityId = post.communityId?.takeIf { it.isNotBlank() } ?: return false
        val viewer = viewerId?.takeIf { it.isNotBlank() } ?: return false
        return isMember(communityId, viewer)
    }

    override fun people(cursor: String?, limit: Int): CommunityMemberPage {
        val parsed = Cursors.parse(cursor)
        val sorted = users.values.sortedWith(
            compareByDescending<UserRecord> { maxOf(it.lastActiveAt, it.createdAt) }.thenByDescending { it.id },
        )
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (activeAt, id) = parsed
            sorted.filter {
                val stamp = maxOf(it.lastActiveAt, it.createdAt)
                stamp < activeAt || (stamp == activeAt && it.id < id)
            }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).map { user ->
            CommunityMember(
                id = user.id,
                name = user.name,
                profilePhotoPath = user.profilePhotoPath,
                location = user.location,
                intro = user.intro,
                joinedAt = maxOf(user.lastActiveAt, user.createdAt),
            )
        }
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.joinedAt, it.id) } else null
        return CommunityMemberPage(items, next)
    }

    override fun eventPostsBetween(from: Long, to: Long): List<PostRecord> =
        posts.values
            .filter { start ->
                val at = start.eventStartAt ?: return@filter false
                at in from..to
            }
            .sortedBy { it.eventStartAt }

    override fun markEventReminder(postId: String, hour: Boolean, nowFlag: Boolean) {
        val post = posts[postId] ?: return
        posts[postId] = post.copy(
            eventHourNotified = post.eventHourNotified || hour,
            eventNowNotified = post.eventNowNotified || nowFlag,
        )
    }

    override fun findPollVote(postId: String, userId: String): PollVote? =
        pollVotes[membershipKey(postId, userId)]

    override fun castPollVote(postId: String, userId: String, optionIndex: Int, votedAt: Long): Boolean {
        val post = posts[postId] ?: return false
        if (optionIndex !in post.pollOptions.indices) return false
        pollVotes[membershipKey(postId, userId)] = PollVote(postId, optionIndex, userId, votedAt)
        return true
    }

    override fun removePollVote(postId: String, userId: String): Boolean =
        pollVotes.remove(membershipKey(postId, userId)) != null

    override fun pollOptions(postId: String, viewerId: String?, voterLimit: Int): List<PollOptionView> {
        val post = posts[postId] ?: return emptyList()
        return pollViews(post, viewerId, voterLimit)
    }

    override fun insertQuestionAnswer(answer: QuestionAnswerRecord): QuestionAnswerRecord {
        val key = membershipKey(answer.postId, answer.userId)
        val stored = answer.copy(id = if (answer.id.isBlank()) key else answer.id)
        questionAnswers[key] = stored
        return stored
    }

    override fun findQuestionAnswer(postId: String, userId: String): QuestionAnswerRecord? =
        questionAnswers[membershipKey(postId, userId)]

    override fun questionAnswers(postId: String): List<QuestionAnswerItem> =
        questionAnswers.values
            .filter { it.postId == postId }
            .sortedWith(compareByDescending<QuestionAnswerRecord> { it.createdAt }.thenByDescending { it.id })
            .map { answer ->
                val author = users[answer.userId]
                QuestionAnswerItem(
                    id = answer.id,
                    postId = answer.postId,
                    userId = answer.userId,
                    authorName = author?.name.orEmpty(),
                    authorPhotoPath = author?.profilePhotoPath,
                    authorLocation = author?.location.orEmpty(),
                    authorIntro = author?.intro.orEmpty(),
                    text = answer.text,
                    createdAt = answer.createdAt,
                )
            }

    override fun insertFeatureSuggestion(userId: String, text: String, createdAt: Long): FeatureSuggestionRecord {
        val stored = FeatureSuggestionRecord(newId(), userId, text, createdAt)
        features[stored.id] = stored
        return stored
    }

    override fun findFeatureSuggestion(id: String): FeatureSuggestionRecord? = features[id]

    override fun deleteFeatureSuggestion(id: String): Boolean {
        if (features.remove(id) == null) return false
        featureVotes.entries.removeIf { it.value.suggestionId == id }
        return true
    }

    override fun resolveFeatureSuggestion(id: String, resolvedAt: Long): FeatureSuggestionView? {
        val current = features[id] ?: return null
        val updated = current.copy(resolvedAt = current.resolvedAt ?: resolvedAt)
        features[id] = updated
        return toFeatureView(updated, null)
    }

    override fun toggleFeatureVote(id: String, userId: String, votedAt: Long): FeatureSuggestionView? {
        val current = features[id] ?: return null
        val key = membershipKey(id, userId)
        if (featureVotes.containsKey(key)) {
            featureVotes.remove(key)
        } else {
            featureVotes[key] = FeatureVote(id, userId, votedAt)
        }
        return toFeatureView(current, userId)
    }

    override fun featureSuggestions(viewerId: String?, limit: Int, openOnly: Boolean): List<FeatureSuggestionView> =
        features.values
            .filter { !openOnly || it.resolvedAt == null }
            .map { toFeatureView(it, viewerId) }
            .sortedWith(
                compareByDescending<FeatureSuggestionView> { !it.resolved }
                    .thenByDescending { it.voteCount }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id },
            )
            .take(limit)

    override fun featureSuggestionView(id: String, viewerId: String?): FeatureSuggestionView? =
        features[id]?.let { toFeatureView(it, viewerId) }

    private fun toFeatureView(record: FeatureSuggestionRecord, viewerId: String?): FeatureSuggestionView {
        val votes = featureVotes.values.filter { it.suggestionId == record.id }
        return FeatureSuggestionView(
            id = record.id,
            text = record.text,
            voteCount = votes.size.toLong(),
            voted = viewerId != null && votes.any { it.userId == viewerId },
            resolved = record.resolvedAt != null,
            createdAt = record.createdAt,
        )
    }

    override fun upsertPushSubscription(subscription: PushSubscriptionRecord) {
        pushSubscriptions[subscription.endpoint] = subscription
    }

    override fun deletePushSubscription(userId: String, endpoint: String) {
        val current = pushSubscriptions[endpoint] ?: return
        if (current.userId == userId) pushSubscriptions.remove(endpoint)
    }

    override fun deletePushSubscriptionByEndpoint(endpoint: String) {
        pushSubscriptions.remove(endpoint)
    }

    override fun pushSubscriptionsFor(userId: String): List<PushSubscriptionRecord> =
        pushSubscriptions.values.filter { it.userId == userId }

    override fun insertChat(chat: ChatRecord): ChatRecord {
        val stored = if (chat.id.isBlank()) chat.copy(id = newId()) else chat
        chats[stored.id] = stored
        return stored
    }

    override fun updateChat(chat: ChatRecord) {
        chats[chat.id] = chat
    }

    override fun findChat(id: String): ChatRecord? = chats[id]

    override fun findDirectChat(userA: String, userB: String): ChatRecord? {
        val key = ChatService.pairKey(userA, userB)
        return chats.values.firstOrNull { it.type == ChatType.DIRECT && it.pairKey == key }
    }

    override fun deleteChat(id: String) {
        chats.remove(id)
        chatMembers.keys.filter { it.startsWith("$id:") }.forEach { chatMembers.remove(it) }
        chatMessages.values.filter { it.chatId == id }.forEach { message ->
            chatMessages.remove(message.id)
            chatReactions.values.filter { it.messageId == message.id }.forEach { chatReactions.remove(it.id) }
        }
    }

    override fun chatsFor(userId: String, cursor: String?, limit: Int): ChatPage {
        val parsed = Cursors.parse(cursor)
        val sorted = chatMembers.values
            .filter { it.userId == userId }
            .mapNotNull { chats[it.chatId] }
            .sortedWith(compareByDescending<ChatRecord> { it.lastMessageAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (at, id) = parsed
            sorted.filter { it.lastMessageAt < at || (it.lastMessageAt == at && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).mapNotNull { chatSummary(it.id, userId) }
        val next = if (page.size > limit) page.take(limit).lastOrNull()?.let { Cursors.encode(it.lastMessageAt, it.id) } else null
        return ChatPage(items, next, unreadChatNotificationCount(userId))
    }

    override fun chatSummary(chatId: String, viewerId: String): ChatSummary? {
        val chat = chats[chatId] ?: return null
        val member = chatMembers[chatKey(chatId, viewerId)] ?: return null
        val members = chatMembers.values.filter { it.chatId == chatId }
        val faces = chatFaces(chat, members, viewerId)
        val peer = if (chat.type == ChatType.DIRECT) {
            members.firstOrNull { it.userId != viewerId }?.userId
        } else {
            null
        }
        val displayName = when {
            chat.type == ChatType.DIRECT -> peer?.let { users[it]?.name }.orEmpty().ifBlank { chat.name }
            chat.name.isNotBlank() -> chat.name
            else -> members.mapNotNull { users[it.userId]?.name }.filter { it.isNotBlank() }.take(3).joinToString(", ")
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
        chatMembers[chatKey(member.chatId, member.userId)] = member
    }

    override fun findChatMember(chatId: String, userId: String): ChatMemberRecord? =
        chatMembers[chatKey(chatId, userId)]

    override fun deleteChatMember(chatId: String, userId: String) {
        chatMembers.remove(chatKey(chatId, userId))
    }

    override fun chatMemberIds(chatId: String): List<String> =
        chatMembers.values.filter { it.chatId == chatId }.map { it.userId }

    override fun chatMembers(chatId: String): List<ChatMemberView> =
        chatMembers.values
            .filter { it.chatId == chatId }
            .sortedBy { it.joinedAt }
            .mapNotNull { member ->
                val user = users[member.userId] ?: return@mapNotNull null
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

    override fun insertChatMessage(message: ChatMessageRecord): ChatMessageRecord {
        val stored = if (message.id.isBlank()) message.copy(id = newId()) else message
        chatMessages[stored.id] = stored
        return stored
    }

    override fun findChatMessage(id: String): ChatMessageRecord? = chatMessages[id]

    override fun deleteChatMessage(id: String) {
        chatMessages.remove(id)
        chatReactions.values.filter { it.messageId == id }.forEach { chatReactions.remove(it.id) }
    }

    override fun chatMessages(chatId: String, cursor: String?, limit: Int, viewerId: String): ChatMessagePage {
        val parsed = Cursors.parse(cursor)
        val sorted = chatMessages.values
            .filter { it.chatId == chatId }
            .sortedWith(compareByDescending<ChatMessageRecord> { it.createdAt }.thenByDescending { it.id })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, id) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.id < id) }
        }
        val page = filtered.take(limit + 1)
        val newestFirst = page.take(limit)
        val items = newestFirst.asReversed().map { toChatMessageItem(it, viewerId) }
        val next = if (page.size > limit) newestFirst.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return ChatMessagePage(items, next)
    }

    override fun chatMessageItem(id: String, viewerId: String): ChatMessageItem? =
        chatMessages[id]?.let { toChatMessageItem(it, viewerId) }

    override fun chatPhotoPaths(chatId: String): List<String> =
        chatMessages.values.filter { it.chatId == chatId }.flatMap { it.photoPaths }

    override fun incrementChatUnread(chatId: String, exceptUserId: String) {
        chatMembers.values.filter { it.chatId == chatId && it.userId != exceptUserId }.forEach { member ->
            chatMembers[chatKey(member.chatId, member.userId)] = member.copy(unreadCount = member.unreadCount + 1)
        }
    }

    override fun markChatRead(chatId: String, userId: String, readAt: Long) {
        val member = chatMembers[chatKey(chatId, userId)] ?: return
        chatMembers[chatKey(chatId, userId)] = member.copy(unreadCount = 0, lastReadAt = readAt)
    }

    override fun insertChatReaction(reaction: ChatReactionRecord): ChatReactionRecord {
        val stored = if (reaction.id.isBlank()) reaction.copy(id = newId()) else reaction
        chatReactions[stored.id] = stored
        return stored
    }

    override fun findChatReaction(messageId: String, userId: String, emoji: String): ChatReactionRecord? =
        chatReactions.values.firstOrNull { it.messageId == messageId && it.userId == userId && it.emoji == emoji }

    override fun deleteChatReaction(id: String) {
        chatReactions.remove(id)
    }

    override fun chatReactionDetails(messageId: String, viewerId: String): List<ReactionDetail> =
        chatReactions.values
            .filter { it.messageId == messageId }
            .sortedBy { it.createdAt }
            .mapNotNull { reaction ->
                val user = users[reaction.userId] ?: return@mapNotNull null
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
        chatReactions.values
            .filter { it.messageId == messageId }
            .groupBy { it.emoji }
            .map { (emoji, group) ->
                ReactionCount(
                    emoji = emoji,
                    count = group.size,
                    mine = group.any { it.userId == viewerId },
                )
            }
            .sortedWith(compareByDescending<ReactionCount> { it.count }.thenBy { it.emoji })

    override fun topChatReactions(chatId: String, limit: Int): List<String> =
        chatReactions.values
            .filter { it.chatId == chatId }
            .groupBy { it.emoji }
            .map { it.key to it.value.size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { it.first }

    override fun searchPeople(query: String, excludeIds: Set<String>, limit: Int): List<EventAttendee> {
        val needle = query.trim().lowercase()
        return users.values
            .filter { it.id !in excludeIds }
            .filter { needle.isBlank() || it.name.lowercase().contains(needle) }
            .sortedWith(compareByDescending<UserRecord> { maxOf(it.lastActiveAt, it.createdAt) }.thenByDescending { it.id })
            .take(limit)
            .map { user ->
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
        notifications.entries.forEach { (id, item) ->
            if (item.userId == userId && item.chatId == chatId && item.readAt == null && NotificationType.isChat(item.type)) {
                notifications[id] = item.copy(readAt = readAt)
            }
        }
    }

    override fun markChatNoticeTypesRead(userId: String, types: Collection<String>, readAt: Long) {
        notifications.entries.forEach { (id, item) ->
            if (item.userId == userId && item.readAt == null && item.type in types) {
                notifications[id] = item.copy(readAt = readAt)
            }
        }
    }

    override fun unreadChatNotificationCount(userId: String): Long =
        notifications.values.count { it.userId == userId && it.readAt == null && NotificationType.isChat(it.type) }.toLong()

    private fun pollViews(post: PostRecord, viewerId: String?, voterLimit: Int = 5): List<PollOptionView> {
        if (post.pollOptions.isEmpty()) return emptyList()
        val votes = pollVotes.values.filter { it.postId == post.id }
        return post.pollOptions.mapIndexed { index, text ->
            val optionVotes = votes.filter { it.optionIndex == index }.sortedByDescending { it.votedAt }
            PollOptionView(
                index = index,
                text = text,
                voteCount = optionVotes.size.toLong(),
                voted = viewerId != null && optionVotes.any { it.userId == viewerId },
                voters = optionVotes.take(voterLimit.coerceAtLeast(0)).mapNotNull { vote ->
                    val user = users[vote.userId] ?: return@mapNotNull null
                    EventAttendee(
                        id = user.id,
                        name = user.name,
                        profilePhotoPath = user.profilePhotoPath,
                        location = user.location,
                        intro = user.intro,
                        joinedAt = vote.votedAt,
                    )
                },
            )
        }
    }

    private fun previewAttendees(postId: String): List<EventAttendee> =
        attendances.values
            .filter { it.postId == postId }
            .sortedByDescending { it.joinedAt }
            .take(5)
            .mapNotNull { attendance ->
                val user = users[attendance.userId] ?: return@mapNotNull null
                EventAttendee(
                    id = user.id,
                    name = user.name,
                    profilePhotoPath = user.profilePhotoPath,
                    location = user.location,
                    joinedAt = attendance.joinedAt,
                )
            }

    private fun chatKey(chatId: String, userId: String): String = "$chatId:$userId"

    private fun chatFaces(chat: ChatRecord, members: List<ChatMemberRecord>, viewerId: String): List<ChatFace> {
        if (chat.type == ChatType.DIRECT) {
            val peer = members.firstOrNull { it.userId != viewerId } ?: members.firstOrNull()
            val user = peer?.let { users[it.userId] } ?: return emptyList()
            return listOf(ChatFace(user.id, user.name, user.profilePhotoPath))
        }
        val recentSenders = chatMessages.values
            .filter { it.chatId == chat.id }
            .sortedWith(compareByDescending<ChatMessageRecord> { it.createdAt }.thenByDescending { it.id })
            .map { it.userId }
            .distinct()
        val ordered = (recentSenders + members.sortedByDescending { it.joinedAt }.map { it.userId }).distinct()
        return ordered.take(2).mapNotNull { userId ->
            val user = users[userId] ?: return@mapNotNull null
            ChatFace(user.id, user.name, user.profilePhotoPath)
        }
    }

    private fun toChatMessageItem(message: ChatMessageRecord, viewerId: String): ChatMessageItem {
        val author = users[message.userId]
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
        )
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    private class Membership(val communityId: String, val userId: String, val joinedAt: Long)

    private class Attendance(val postId: String, val userId: String, val joinedAt: Long)

    private class DailyBucket {
        var visitorVisits = 0L
        var userVisits = 0L
        val uniqueUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()
        var replyClicks = 0L
        var shareClicks = 0L
        var postViews = 0L
    }
}
