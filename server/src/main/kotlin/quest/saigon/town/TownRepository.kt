package quest.saigon.town

interface TownRepository {
    fun createUser(phone: String, passwordHash: String, name: String, createdAt: Long): UserRecord
    fun findUserByPhone(phone: String): UserRecord?
    fun findUser(id: String): UserRecord?
    fun updateUser(user: UserRecord)
    fun createSession(token: String, userId: String, expiresAt: Long)
    fun findSession(token: String): SessionRecord?
    fun deleteSession(token: String)
    fun insertAvatar(userId: String, prompt: String, createdAt: Long): AvatarRecord
    fun updateAvatar(id: String, status: String, imagePath: String?, error: String?)
    fun findAvatar(id: String): AvatarRecord?
    fun avatarsFor(userId: String): List<AvatarRecord>
    fun hasGeneratingAvatar(userId: String): Boolean
    fun deleteAvatar(id: String)
    fun avatarReferenced(id: String): Boolean
    fun insertMusic(
        userId: String,
        prompt: String,
        name: String,
        gender: String?,
        instrumental: Boolean,
        createdAt: Long,
    ): MusicRecord
    fun updateMusic(
        id: String,
        status: String,
        audioPath: String?,
        error: String?,
        name: String? = null,
        lyrics: String? = null,
    )
    fun findMusic(id: String): MusicRecord?
    fun musicsFor(userId: String): List<MusicRecord>
    fun searchMusic(query: String, cursor: String?, limit: Int): MusicPage
    fun hasGeneratingMusic(userId: String): Boolean
    fun deleteMusic(id: String)
    fun musicReferenced(id: String): Boolean
    fun insertPost(post: PostRecord): PostRecord
    fun feed(cursor: String?, limit: Int): FeedPage
    fun findPost(id: String): PostRecord?
    fun feedItem(id: String): FeedItem?
    fun postsByUser(userId: String, cursor: String?, limit: Int): OwnedPostPage
    fun recordView(id: String)
    fun recordReplyClick(id: String)
    fun recordShareClick(id: String)
    fun updateQuickReplies(id: String, replies: List<String>)
    fun deletePost(id: String)
    fun insertComment(comment: CommentRecord): CommentRecord
    fun findComment(id: String): CommentRecord?
    fun deleteComment(id: String)
    fun commentItem(id: String): CommentItem?
    fun commentsForPost(postId: String, cursor: String?, limit: Int): CommentPage
    fun latestComments(postId: String, limit: Int): List<CommentItem>
    fun armCommentWatch(postId: String, userId: String)
    fun disarmCommentWatch(postId: String, userId: String)
    fun armedWatchers(postId: String): List<String>
    fun insertNotification(notification: NotificationRecord): NotificationRecord
    fun notificationsFor(userId: String, cursor: String?, limit: Int): NotificationPage
    fun findNotification(id: String): NotificationRecord?
    fun markNotificationRead(id: String, readAt: Long): NotificationRecord?
    fun markAllNotificationsRead(userId: String, readAt: Long): Long
    fun unreadNotificationCount(userId: String): Long
    fun recordVisit(userId: String?, at: Long)
    fun recordDailyEvent(field: String, at: Long)
    fun statsByDay(now: Long): List<DayStats>
    fun allTimeStats(): AllTimeStats
    fun findAdmin(userId: String): AdminRecord?
    fun claimFirstAdmin(userId: String, createdAt: Long): Boolean
    fun usersPage(cursor: String?, limit: Int): AdminUserPage
    fun setUserPaused(userId: String, paused: Boolean): UserRecord?
    fun adminPosts(cursor: String?, limit: Int): AdminPostPage
}
