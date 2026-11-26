package quest.saigon.town

import quest.saigon.CallToAction
import quest.saigon.Limits
import quest.saigon.PhoneNumbers
import quest.saigon.decodeLocationToken
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
    private val fcmTokens = ConcurrentHashMap<String, FcmTokenRecord>()
    private val admins = ConcurrentHashMap<String, AdminRecord>()
    private val communities = ConcurrentHashMap<String, CommunityRecord>()
    private val memberships = ConcurrentHashMap<String, Membership>()
    private val wants = ConcurrentHashMap<String, WantRecord>()
    private val wantMemberships = ConcurrentHashMap<String, WantMember>()
    private val attendances = ConcurrentHashMap<String, Attendance>()
    private val pollVotes = ConcurrentHashMap<String, PollVote>()
    private val questionAnswers = ConcurrentHashMap<String, QuestionAnswerRecord>()
    private val features = ConcurrentHashMap<String, FeatureSuggestionRecord>()
    private val featureVotes = ConcurrentHashMap<String, FeatureVote>()
    private val products = ConcurrentHashMap<String, ProductRecord>()
    private val productOrders = ConcurrentHashMap<String, ProductOrderRecord>()
    private val activities = ConcurrentHashMap<String, ActivityRecord>()
    private val activityInterests = ConcurrentHashMap<String, ActivityInterest>()
    private val userPhotos = ConcurrentHashMap<String, UserPhotoRecord>()
    private val locationDescriptions = ConcurrentHashMap<String, LocationDescriptionRecord>()
    private val chats = ConcurrentHashMap<String, ChatRecord>()
    private val chatMembers = ConcurrentHashMap<String, ChatMemberRecord>()
    private val chatMessages = ConcurrentHashMap<String, ChatMessageRecord>()
    private val chatReactions = ConcurrentHashMap<String, ChatReactionRecord>()
    private val locations = ConcurrentHashMap<String, UserLocationRecord>()
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

    override fun markAndroidLaunch(userId: String, at: Long) {
        synchronized(this) {
            val current = users[userId] ?: return
            if (current.androidLaunchedAt <= 0) users[userId] = current.copy(androidLaunchedAt = at)
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
        posts.values.filter { placePostVisible(it, viewerId) },
        cursor,
        limit,
        viewerId,
    )

    override fun activeCheckIns(now: Long, viewerId: String?): List<CheckInView> = posts.values
        .asSequence()
        .filter { post ->
            (post.checkInUntilAt ?: 0L) > now && post.lat != null && post.lng != null && placePostVisible(post, viewerId)
        }
        .sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
        .distinctBy { it.userId }
        .mapNotNull { post ->
            val author = users[post.userId] ?: return@mapNotNull null
            CheckInView(
                postId = post.id,
                message = post.message,
                authorId = author.id,
                authorName = author.name,
                authorPhotoPath = author.profilePhotoPath,
                location = post.location,
                placeId = post.placeId,
                locationId = post.locationId,
                lat = post.lat ?: return@mapNotNull null,
                lng = post.lng ?: return@mapNotNull null,
                untilAt = post.checkInUntilAt ?: return@mapNotNull null,
            )
        }
        .toList()

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
                post.openChatName,
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

    override fun updatePostCheckInUntil(id: String, untilAt: Long?) {
        val post = posts[id] ?: return
        posts[id] = post.copy(checkInUntilAt = untilAt)
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
            locationId = post.locationId,
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
            openChatId = post.openChatId,
            openChatName = post.openChatName,
            openChatExpiresAt = post.openChatExpiresAt,
            openChatRemaining = post.openChatRemaining,
            openChatJoined = viewerId != null && post.openChatId?.let { chatId ->
                findChatMember(chatId, viewerId) != null
            } == true,
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
            .map { community ->
                val latestPosts = posts.values
                    .asSequence()
                    .filter { it.communityId == community.id && it.photoPath.isNotBlank() && placePostVisible(it, userId) }
                    .sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
                    .take(Limits.COMMUNITY_CARD_PHOTO_LIMIT)
                    .map { CommunityPostPhoto(it.id, it.photoPath) }
                    .toList()
                val members = memberships.values
                    .asSequence()
                    .filter { it.communityId == community.id }
                    .sortedWith(compareByDescending<Membership> { it.joinedAt }.thenByDescending { membershipKey(it.communityId, it.userId) })
                    .take(5)
                    .mapNotNull { membership ->
                        users[membership.userId]?.let { user ->
                            CommunityMember(
                                id = user.id,
                                name = user.name,
                                profilePhotoPath = user.profilePhotoPath,
                                location = user.location,
                                intro = user.intro,
                                joinedAt = membership.joinedAt,
                            )
                        }
                    }
                    .toList()
                CommunityView(community, userId != null && isMember(community.id, userId), latestPosts, members)
            }
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

    override fun insertWant(want: WantRecord): WantRecord = synchronized(this) {
        if (wants.values.any { it.slug == want.slug }) throw TownException("want_taken")
        val stored = if (want.id.isBlank()) want.copy(id = newId()) else want
        wants[stored.id] = stored
        stored
    }

    override fun findWant(id: String): WantRecord? = wants[id]

    override fun findWantBySlug(slug: String): WantRecord? = wants.values.firstOrNull { it.slug == slug }

    override fun allWants(): List<WantRecord> = synchronized(this) {
        wants.values.sortedWith(compareByDescending<WantRecord> { it.memberCount }.thenByDescending { it.createdAt })
    }

    override fun wantsWithoutPhoto(): List<WantRecord> = synchronized(this) {
        wants.values
            .filter { it.photoPath.isNullOrBlank() && !it.photoBlocked }
            .sortedBy { it.createdAt }
    }

    override fun updateWantPhoto(wantId: String, photoPath: String?, photoBlocked: Boolean): WantRecord? = synchronized(this) {
        val existing = wants[wantId] ?: return@synchronized null
        val updated = existing.copy(photoPath = photoPath, photoBlocked = photoBlocked)
        wants[wantId] = updated
        updated
    }

    override fun wantsForUser(userId: String): List<WantRecord> =
        wantMemberships.values
            .filter { it.userId == userId }
            .sortedByDescending { it.joinedAt }
            .mapNotNull { wants[it.wantId] }

    override fun wantMembers(wantId: String): List<String> =
        wantMemberships.values.filter { it.wantId == wantId }.map { it.userId }

    override fun wantPeople(wantId: String, limit: Int): List<WantMemberView> =
        wantMemberships.values
            .filter { it.wantId == wantId }
            .sortedWith(compareByDescending<WantMember> { it.joinedAt }.thenByDescending { it.userId })
            .take(limit.coerceAtLeast(0))
            .mapNotNull { membership ->
                val user = users[membership.userId] ?: return@mapNotNull null
                WantMemberView(
                    id = user.id,
                    name = user.name,
                    profilePhotoPath = user.profilePhotoPath,
                    location = user.location,
                    intro = user.intro,
                    joinedAt = membership.joinedAt,
                )
            }

    override fun joinWant(wantId: String, userId: String, joinedAt: Long): Boolean = synchronized(this) {
        val want = wants[wantId] ?: return@synchronized false
        val key = wantMembershipKey(wantId, userId)
        if (wantMemberships.containsKey(key)) return@synchronized false
        wantMemberships[key] = WantMember(wantId, userId, joinedAt)
        wants[wantId] = want.copy(memberCount = want.memberCount + 1)
        true
    }

    override fun leaveWant(wantId: String, userId: String): Boolean = synchronized(this) {
        val want = wants[wantId] ?: return@synchronized false
        if (wantMemberships.remove(wantMembershipKey(wantId, userId)) == null) return@synchronized false
        wants[wantId] = want.copy(memberCount = (want.memberCount - 1).coerceAtLeast(0))
        true
    }

    override fun isWantMember(wantId: String, userId: String): Boolean =
        wantMemberships.containsKey(wantMembershipKey(wantId, userId))

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

    override fun consumeOpenChatSlot(postId: String): Boolean = synchronized(this) {
        val post = posts[postId] ?: return@synchronized false
        val remaining = post.openChatRemaining ?: return@synchronized true
        if (remaining <= 0) return@synchronized false
        posts[postId] = post.copy(openChatRemaining = remaining - 1)
        true
    }

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

    override fun upcomingEvents(fromMillis: Long, viewerId: String?, limit: Int, query: String): List<FeedItem> =
        posts.values
            .filter { start ->
                val at = start.eventStartAt ?: return@filter false
                at >= fromMillis && !start.communityOnly &&
                    (query.isBlank() || start.message.contains(query, ignoreCase = true) || start.location.contains(query, ignoreCase = true))
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

    override fun insertUserLocation(
        name: String,
        lat: Double,
        lng: Double,
        createdBy: String,
        createdAt: Long,
    ): UserLocationRecord {
        val location = UserLocationRecord(newId(), name, lat, lng, createdBy, createdAt)
        locations[location.id] = location
        return location
    }

    override fun searchUserLocations(query: String, limit: Int): List<UserLocationRecord> =
        locations.values
            .filter { it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<UserLocationRecord> { it.createdAt }.thenBy { it.name.lowercase() })
            .take(limit)

    override fun userLocations(): List<UserLocationRecord> =
        locations.values.sortedWith(compareByDescending<UserLocationRecord> { it.createdAt }.thenBy { it.id })

    override fun postedGooglePlaces(viewerId: String?): List<PlaceSuggestion> {
        val seenPlaceIds = mutableSetOf<String>()
        return posts.values
            .asSequence()
            .filter { post ->
                !post.placeId.isNullOrBlank() && post.locationId.isNullOrBlank() && post.location.isNotBlank() &&
                    post.lat != null && post.lng != null && placePostVisible(post, viewerId)
            }
            .sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
            .mapNotNull { post ->
                val placeId = post.placeId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (!seenPlaceIds.add(placeId)) return@mapNotNull null
                PlaceSuggestion(
                    name = post.location,
                    placeId = placeId,
                    lat = post.lat,
                    lng = post.lng,
                    photoPath = post.photoPath,
                )
            }
            .toList()
    }

    override fun findUserLocation(id: String): UserLocationRecord? = locations[id]

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
                val key = post.locationId?.takeIf { it.isNotBlank() }
                    ?: post.placeId?.takeIf { it.isNotBlank() }
                    ?: post.location.lowercase()
                if (seen.add(key)) {
                    places += PlaceSuggestion(
                        name = post.location,
                        placeId = post.placeId,
                        lat = post.lat,
                        lng = post.lng,
                        recent = true,
                        photoPath = post.photoPath,
                        id = post.locationId,
                        custom = !post.locationId.isNullOrBlank(),
                    )
                }
            }
        return places.take(limit)
    }

    override fun latestPostPhotoAtPlace(
        location: String,
        placeId: String?,
        viewerId: String?,
        locationId: String?,
    ): String? {
        val pid = placeId?.takeIf { it.isNotBlank() }
        val customId = locationId?.takeIf { it.isNotBlank() }
        return posts.values
            .filter { post ->
                val matchesPlace = when {
                    customId != null -> post.locationId == customId
                    pid != null -> post.locationId.isNullOrBlank() && post.placeId == pid
                    else -> post.locationId.isNullOrBlank() && post.placeId.isNullOrBlank() && post.location == location
                }
                matchesPlace && placePostVisible(post, viewerId)
            }
            .sortedWith(compareByDescending<PostRecord> { it.createdAt }.thenByDescending { it.id })
            .firstOrNull()
            ?.photoPath
    }

    override fun findPlace(placeId: String, viewerId: String?): PlaceSuggestion? {
        findUserLocation(placeId)?.let { location ->
            return PlaceSuggestion(
                name = location.name,
                lat = location.lat,
                lng = location.lng,
                id = location.id,
                custom = true,
            )
        }
        val location = decodeLocationToken(placeId)
        if (location != null) {
            val nameOnlyPost = posts.values
                .filter {
                    it.location == location && it.placeId.isNullOrBlank() && it.locationId.isNullOrBlank() &&
                        placePostVisible(it, viewerId)
                }
                .maxWithOrNull(compareBy<PostRecord> { it.createdAt }.thenBy { it.id })
            if (nameOnlyPost != null) {
                return PlaceSuggestion(
                    name = nameOnlyPost.location,
                    placeId = null,
                    lat = nameOnlyPost.lat,
                    lng = nameOnlyPost.lng,
                )
            }
        }
        return posts.values
            .filter { it.placeId == placeId && it.location.isNotBlank() && placePostVisible(it, viewerId) }
            .maxWithOrNull(compareBy<PostRecord> { it.createdAt }.thenBy { it.id })
            ?.let { post ->
                PlaceSuggestion(name = post.location, placeId = post.placeId, lat = post.lat, lng = post.lng)
            }
    }

    override fun postsAtPlace(
        location: String,
        placeId: String?,
        cursor: String?,
        limit: Int,
        viewerId: String?,
        locationId: String?,
    ): FeedPage = pagePosts(placePosts(location, placeId, locationId, viewerId), cursor, limit, viewerId)

    override fun countPostsAtPlace(location: String, placeId: String?, viewerId: String?, locationId: String?): Long =
        placePosts(location, placeId, locationId, viewerId).size.toLong()

    override fun postsByAuthor(userId: String, cursor: String?, limit: Int, viewerId: String?): FeedPage =
        pagePosts(authorPosts(userId, viewerId), cursor, limit, viewerId)

    override fun countPostsByAuthor(userId: String, viewerId: String?): Long =
        authorPosts(userId, viewerId).size.toLong()

    override fun insertProduct(product: ProductRecord): ProductRecord {
        val stored = product.copy(id = product.id.ifBlank { newId() })
        products[stored.id] = stored
        return stored
    }

    override fun findProduct(id: String): ProductRecord? = products[id]

    override fun updateProduct(product: ProductRecord): ProductRecord? {
        if (!products.containsKey(product.id)) return null
        products[product.id] = product
        return product
    }

    override fun deleteProduct(id: String): ProductRecord? = products.remove(id)

    override fun productsByUser(userId: String, cursor: String?, limit: Int): ProductPage {
        val sorted = products.values.filter { it.userId == userId }
            .sortedWith(compareByDescending<ProductRecord> { it.createdAt }.thenByDescending { it.id })
        val parsed = Cursors.parse(cursor)
        val filtered = if (parsed == null) sorted else sorted.filter {
            it.createdAt < parsed.first || (it.createdAt == parsed.first && it.id < parsed.second)
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit)
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return ProductPage(items, sorted.size.toLong(), next)
    }

    override fun searchProducts(query: String, cursor: String?, limit: Int): ProductPage {
        val needle = query.trim().lowercase()
        val matching = products.values.filter { product ->
            needle.isBlank() || listOf(product.name, product.description, product.category)
                .any { it.lowercase().contains(needle) }
        }.sortedWith(compareByDescending<ProductRecord> { it.createdAt }.thenByDescending { it.id })
        val parsed = Cursors.parse(cursor)
        val filtered = if (parsed == null) matching else matching.filter {
            it.createdAt < parsed.first || (it.createdAt == parsed.first && it.id < parsed.second)
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit)
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return ProductPage(items, matching.size.toLong(), next)
    }

    override fun insertProductOrder(order: ProductOrderRecord): ProductOrderRecord {
        val stored = order.copy(id = order.id.ifBlank { newId() })
        productOrders[stored.id] = stored
        return stored
    }

    override fun productOrdersForSeller(sellerId: String, cursor: String?, limit: Int): ProductOrderPage {
        val sorted = productOrders.values.filter { it.sellerId == sellerId }
            .sortedWith(compareBy<ProductOrderRecord> { it.resolved }.thenByDescending { it.createdAt }.thenByDescending { it.id })
        val parsed = Cursors.parseProductOrder(cursor)
        val filtered = if (parsed == null) sorted else sorted.filter {
            (it.resolved && !parsed.resolved) ||
                (it.resolved == parsed.resolved &&
                    (it.createdAt < parsed.createdAt || (it.createdAt == parsed.createdAt && it.id < parsed.id)))
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).mapNotNull { order ->
            val buyer = users[order.buyerId] ?: return@mapNotNull null
            ProductOrderView(order, buyer.name, buyer.phone, buyer.profilePhotoPath, buyer.location, buyer.intro)
        }
        val next = if (page.size > limit) {
            page.getOrNull(limit - 1)?.let { Cursors.encodeProductOrder(it.resolved, it.createdAt, it.id) }
        } else {
            null
        }
        val openCount = productOrders.values.count { it.sellerId == sellerId && !it.resolved }.toLong()
        return ProductOrderPage(items, next, openCount)
    }

    override fun updateProductOrderResolved(id: String, sellerId: String, resolved: Boolean): ProductOrderView? {
        val current = productOrders[id]?.takeIf { it.sellerId == sellerId } ?: return null
        val updated = current.copy(resolved = resolved)
        productOrders[id] = updated
        val buyer = users[updated.buyerId] ?: return null
        return ProductOrderView(updated, buyer.name, buyer.phone, buyer.profilePhotoPath, buyer.location, buyer.intro)
    }

    override fun insertActivity(activity: ActivityRecord): ActivityRecord {
        val stored = activity.copy(id = activity.id.ifBlank { newId() })
        activities[stored.id] = stored
        return stored
    }

    override fun findActivity(id: String): ActivityRecord? = activities[id]

    override fun updateActivity(activity: ActivityRecord): ActivityRecord? {
        if (!activities.containsKey(activity.id)) return null
        activities[activity.id] = activity
        return activity
    }

    override fun deleteActivity(id: String): ActivityRecord? {
        val removed = activities.remove(id) ?: return null
        activityInterests.keys.filter { it.startsWith("${id}_") }.forEach { activityInterests.remove(it) }
        return removed
    }

    override fun activitiesByUser(userId: String, cursor: String?, limit: Int): ActivityPage {
        val sorted = activities.values.filter { it.createdBy == userId }
            .sortedWith(compareByDescending<ActivityRecord> { it.createdAt }.thenByDescending { it.id })
        val parsed = Cursors.parse(cursor)
        val filtered = if (parsed == null) sorted else sorted.filter {
            it.createdAt < parsed.first || (it.createdAt == parsed.first && it.id < parsed.second)
        }
        return pageActivities(filtered, limit) { Cursors.encode(it.createdAt, it.id) }
    }

    override fun activitiesInterestedByUser(userId: String, cursor: String?, limit: Int): ActivityPage {
        val parsed = Cursors.parse(cursor)
        val sorted = activityInterests.values
            .filter { it.userId == userId && activities.containsKey(it.activityId) }
            .sortedWith(compareByDescending<ActivityInterest> { it.createdAt }.thenByDescending { it.activityId })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, activityId) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && it.activityId < activityId) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).mapNotNull { activities[it.activityId] }
        val next = if (page.size > limit) {
            page.getOrNull(limit - 1)?.let { Cursors.encode(it.createdAt, it.activityId) }
        } else {
            null
        }
        return ActivityPage(items, next)
    }

    override fun activitiesForLocation(
        locationName: String,
        placeId: String?,
        locationId: String?,
        cursor: String?,
        limit: Int,
    ): ActivityPage {
        val matching = activities.values.filter { activityMatchesLocation(it, locationName, placeId, locationId) }
            .sortedWith(
                compareByDescending<ActivityRecord> { it.interestedCount }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id },
            )
        val parsed = Cursors.parseActivity(cursor)
        val filtered = if (parsed == null) matching else matching.filter { afterActivityCursor(it, parsed) }
        return pageActivities(filtered, limit) { Cursors.encodeActivity(it.interestedCount, it.createdAt, it.id) }
    }

    override fun searchActivities(cursor: String?, limit: Int, query: String): ActivityPage {
        val matching = activities.values.filter { activity ->
            query.isBlank() || listOf(activity.name, activity.description, activity.locationName).any { it.contains(query, ignoreCase = true) } ||
                activity.translations?.locales?.values.orEmpty().any { translated ->
                    listOf("name", "description").any { field -> translated[field]?.contains(query, ignoreCase = true) == true }
                }
        }.sortedWith(
            compareByDescending<ActivityRecord> { it.interestedCount }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )
        val parsed = Cursors.parseActivity(cursor)
        val filtered = if (parsed == null) matching else matching.filter { afterActivityCursor(it, parsed) }
        return pageActivities(filtered, limit) { Cursors.encodeActivity(it.interestedCount, it.createdAt, it.id) }
    }

    override fun addActivityInterest(activityId: String, userId: String, createdAt: Long): Boolean {
        val activity = activities[activityId] ?: return false
        val key = membershipKey(activityId, userId)
        if (activityInterests.containsKey(key)) return false
        activityInterests[key] = ActivityInterest(activityId, userId, createdAt)
        activities[activityId] = activity.copy(interestedCount = activity.interestedCount + 1)
        return true
    }

    override fun removeActivityInterest(activityId: String, userId: String): Boolean {
        val activity = activities[activityId] ?: return false
        if (activityInterests.remove(membershipKey(activityId, userId)) == null) return false
        activities[activityId] = activity.copy(interestedCount = (activity.interestedCount - 1).coerceAtLeast(0))
        return true
    }

    override fun isActivityInterested(activityId: String, userId: String): Boolean =
        activityInterests.containsKey(membershipKey(activityId, userId))

    override fun activityInterested(activityId: String, cursor: String?, limit: Int): EventAttendeePage {
        val parsed = Cursors.parse(cursor)
        val sorted = activityInterests.values
            .filter { it.activityId == activityId }
            .sortedWith(compareByDescending<ActivityInterest> { it.createdAt }.thenByDescending { membershipKey(it.activityId, it.userId) })
        val filtered = if (parsed == null) {
            sorted
        } else {
            val (createdAt, key) = parsed
            sorted.filter { it.createdAt < createdAt || (it.createdAt == createdAt && membershipKey(it.activityId, it.userId) < key) }
        }
        val page = filtered.take(limit + 1)
        val items = page.take(limit).mapNotNull { interest ->
            val user = users[interest.userId] ?: return@mapNotNull null
            EventAttendee(
                id = user.id,
                name = user.name,
                profilePhotoPath = user.profilePhotoPath,
                location = user.location,
                intro = user.intro,
                phone = user.phone,
                joinedAt = interest.createdAt,
            )
        }
        val next = if (page.size > limit) {
            page.getOrNull(limit - 1)?.let { Cursors.encode(it.createdAt, membershipKey(it.activityId, it.userId)) }
        } else {
            null
        }
        return EventAttendeePage(items, next)
    }

    override fun activityInterestedIds(activityId: String): List<String> =
        activityInterests.values.filter { it.activityId == activityId }.map { it.userId }

    override fun activityLocationFlags(): ActivityLocationFlags {
        val ordered = activities.values.sortedWith(
            compareByDescending<ActivityRecord> { it.interestedCount }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )
        val locationActivities = ordered
            .filter { !it.locationId.isNullOrBlank() }
            .groupBy { it.locationId!! }
            .mapValues { (_, matching) ->
                ActivityLocationSummary(
                    count = matching.size.toLong(),
                    emojis = matching.map { it.emoji }.filter { it.isNotBlank() }.distinct(),
                )
            }
        val placeActivities = ordered
            .filter { it.locationId.isNullOrBlank() }
            .filter { !it.placeId.isNullOrBlank() }
            .groupBy { it.placeId!! }
            .mapValues { (_, matching) ->
                ActivityLocationSummary(
                    count = matching.size.toLong(),
                    emojis = matching.map { it.emoji }.filter { it.isNotBlank() }.distinct(),
                )
            }
        return ActivityLocationFlags(
            locationIds = locationActivities.keys,
            placeIds = placeActivities.keys,
            locationActivities = locationActivities,
            placeActivities = placeActivities,
        )
    }

    override fun insertUserPhoto(photo: UserPhotoRecord): UserPhotoRecord {
        val stored = photo.copy(id = photo.id.ifBlank { newId() })
        userPhotos[stored.id] = stored
        return stored
    }

    override fun findUserPhoto(id: String): UserPhotoRecord? = userPhotos[id]

    override fun deleteUserPhoto(id: String): UserPhotoRecord? = userPhotos.remove(id)

    override fun userPhotosForActivity(activityId: String, limit: Int): List<UserPhotoRecord> =
        userPhotos.values.filter { it.activityId == activityId }
            .sortedWith(compareByDescending<UserPhotoRecord> { it.createdAt }.thenByDescending { it.id })
            .take(limit.coerceAtLeast(0))

    override fun userPhotosAtLocation(
        locationName: String,
        placeId: String?,
        locationId: String?,
        limit: Int,
    ): List<UserPhotoRecord> = userPhotos.values.filter { photo ->
        photo.activityId == null && userPhotoMatchesLocation(photo, locationName, placeId, locationId)
    }.sortedWith(compareByDescending<UserPhotoRecord> { it.createdAt }.thenByDescending { it.id })
        .take(limit.coerceAtLeast(0))

    override fun deleteActivityPhotos(activityId: String): List<UserPhotoRecord> =
        userPhotos.values.filter { it.activityId == activityId }.mapNotNull { photo ->
            userPhotos.remove(photo.id)
        }

    override fun locationDescriptionAtLocation(
        locationName: String,
        placeId: String?,
        locationId: String?,
    ): LocationDescriptionRecord? = locationDescriptions[locationIdentityKey(locationName, placeId, locationId)]

    override fun upsertLocationDescription(description: LocationDescriptionRecord): LocationDescriptionRecord {
        val key = locationIdentityKey(description.locationName, description.placeId, description.locationId)
        return locationDescriptions.compute(key) { _, current ->
            description.copy(id = current?.id ?: description.id.ifBlank { newId() })
        } ?: error("Location description upsert returned no record")
    }

    private fun afterActivityCursor(activity: ActivityRecord, cursor: ActivityCursor): Boolean =
        activity.interestedCount < cursor.interestedCount ||
            (activity.interestedCount == cursor.interestedCount && activity.createdAt < cursor.createdAt) ||
            (activity.interestedCount == cursor.interestedCount && activity.createdAt == cursor.createdAt && activity.id < cursor.id)

    private fun pageActivities(filtered: List<ActivityRecord>, limit: Int, encode: (ActivityRecord) -> String): ActivityPage {
        val page = filtered.take(limit + 1)
        val items = page.take(limit)
        val next = if (page.size > limit) items.lastOrNull()?.let(encode) else null
        return ActivityPage(items, next)
    }

    private fun placePosts(location: String, placeId: String?, locationId: String?, viewerId: String?): List<PostRecord> {
        val pid = placeId?.takeIf { it.isNotBlank() }
        val customId = locationId?.takeIf { it.isNotBlank() }
        return posts.values.filter { post ->
            post.location == location &&
                when {
                    customId != null -> post.locationId == customId
                    pid != null -> post.locationId.isNullOrBlank() && post.placeId == pid
                    else -> post.locationId.isNullOrBlank() && post.placeId.isNullOrBlank()
                } &&
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

    override fun people(cursor: String?, limit: Int, viewerId: String?, query: String): CommunityMemberPage {
        val parsed = Cursors.parse(cursor)
        val sorted = users.values.sortedWith(
            compareByDescending<UserRecord> { maxOf(it.lastActiveAt, it.createdAt) }.thenByDescending { it.id },
        ).filter { user ->
            query.isBlank() || user.name.contains(query, ignoreCase = true) ||
                user.location.contains(query, ignoreCase = true) || user.intro.contains(query, ignoreCase = true)
        }
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
            val latestPost = authorPosts(user.id, viewerId)
                .maxWithOrNull(compareBy<PostRecord> { it.createdAt }.thenBy { it.id })
            CommunityMember(
                id = user.id,
                name = user.name,
                profilePhotoPath = user.profilePhotoPath,
                location = user.location,
                intro = user.intro,
                joinedAt = maxOf(user.lastActiveAt, user.createdAt),
                latestPostId = latestPost?.id,
                latestPostPhotoPath = latestPost?.photoPath,
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

    override fun upsertFcmToken(record: FcmTokenRecord) {
        fcmTokens[record.token] = record
    }

    override fun deleteFcmToken(userId: String, token: String) {
        val current = fcmTokens[token] ?: return
        if (current.userId == userId) fcmTokens.remove(token)
    }

    override fun deleteFcmTokenByValue(token: String) {
        fcmTokens.remove(token)
    }

    override fun fcmTokensFor(userId: String): List<FcmTokenRecord> =
        fcmTokens.values.filter { it.userId == userId }

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

    override fun chatsFor(userId: String, cursor: String?, limit: Int, type: String?): ChatPage {
        val parsed = Cursors.parse(cursor)
        val sorted = chatMembers.values
            .filter { it.userId == userId }
            .mapNotNull { chats[it.chatId] }
            .filter { type == null || it.type == type }
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
            attachment = message.attachment,
        )
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    private class Membership(val communityId: String, val userId: String, val joinedAt: Long)

    private class Attendance(val postId: String, val userId: String, val joinedAt: Long)

    private class ActivityInterest(val activityId: String, val userId: String, val createdAt: Long)

    private class DailyBucket {
        var visitorVisits = 0L
        var userVisits = 0L
        val uniqueUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()
        var replyClicks = 0L
        var shareClicks = 0L
        var postViews = 0L
    }
}
