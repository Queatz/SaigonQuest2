package quest.saigon.town

interface TownRepository {
    fun createUser(phone: String, passwordHash: String, name: String, createdAt: Long): UserRecord
    fun findUserByPhone(phone: String): UserRecord?
    fun findUser(id: String): UserRecord?
    fun updateUser(user: UserRecord)
    fun markAndroidLaunch(userId: String, at: Long)
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
    fun feed(cursor: String?, limit: Int, viewerId: String? = null): FeedPage
    fun activeCheckIns(now: Long, viewerId: String? = null): List<CheckInView>
    fun searchPosts(query: String, cursor: String?, limit: Int, viewerId: String? = null): FeedPage
    fun findPost(id: String): PostRecord?
    fun feedItem(id: String, viewerId: String? = null): FeedItem?
    fun postsByUser(userId: String, cursor: String?, limit: Int): OwnedPostPage
    fun postsByAuthor(userId: String, cursor: String?, limit: Int, viewerId: String? = null): FeedPage
    fun countPostsByAuthor(userId: String, viewerId: String? = null): Long
    fun insertProduct(product: ProductRecord): ProductRecord
    fun findProduct(id: String): ProductRecord?
    fun updateProduct(product: ProductRecord): ProductRecord?
    fun deleteProduct(id: String): ProductRecord?
    fun productsByUser(userId: String, cursor: String?, limit: Int): ProductPage
    fun searchProducts(query: String, cursor: String?, limit: Int): ProductPage
    fun insertProductOrder(order: ProductOrderRecord): ProductOrderRecord
    fun productOrdersForSeller(sellerId: String, cursor: String?, limit: Int): ProductOrderPage
    fun updateProductOrderResolved(id: String, sellerId: String, resolved: Boolean): ProductOrderView?
    fun recordView(id: String)
    fun recordReplyClick(id: String)
    fun recordShareClick(id: String)
    fun updatePostMessage(id: String, message: String)
    fun updatePostCheckInUntil(id: String, untilAt: Long?)
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
    fun adminComments(cursor: String?, limit: Int): CommentPage
    fun insertCommunity(community: CommunityRecord): CommunityRecord
    fun findCommunity(id: String): CommunityRecord?
    fun findCommunityBySlug(slug: String): CommunityRecord?
    fun updateCommunityDescription(id: String, description: String): CommunityRecord?
    fun searchCommunities(query: String, userId: String?, limit: Int): List<CommunityView>
    fun communitiesForUser(userId: String): List<CommunityRecord>
    fun joinCommunity(communityId: String, userId: String, joinedAt: Long): Boolean
    fun leaveCommunity(communityId: String, userId: String): Boolean
    fun isMember(communityId: String, userId: String): Boolean
    fun members(communityId: String, cursor: String?, limit: Int): CommunityMemberPage
    fun communityFeed(communityId: String, cursor: String?, limit: Int, viewerId: String? = null): FeedPage
    fun insertWant(want: WantRecord): WantRecord
    fun findWant(id: String): WantRecord?
    fun findWantBySlug(slug: String): WantRecord?
    fun wantsForUser(userId: String): List<WantRecord>
    fun wantMembers(wantId: String): List<String>
    fun joinWant(wantId: String, userId: String, joinedAt: Long): Boolean
    fun leaveWant(wantId: String, userId: String): Boolean
    fun isWantMember(wantId: String, userId: String): Boolean
    fun joinEvent(postId: String, userId: String, joinedAt: Long): Boolean
    fun leaveEvent(postId: String, userId: String): Boolean
    fun isAttending(postId: String, userId: String): Boolean
    fun consumeOpenChatSlot(postId: String): Boolean
    fun attendees(postId: String, cursor: String?, limit: Int): EventAttendeePage
    fun attendeeIds(postId: String): List<String>
    fun upcomingEvents(fromMillis: Long, viewerId: String?, limit: Int, query: String = ""): List<FeedItem>
    fun eventCounts(todayStart: Long, tomorrowStart: Long, tomorrowEnd: Long, weekEnd: Long): EventSummary
    fun insertActivity(activity: ActivityRecord): ActivityRecord
    fun findActivity(id: String): ActivityRecord?
    fun updateActivity(activity: ActivityRecord): ActivityRecord?
    fun deleteActivity(id: String): ActivityRecord?
    fun activitiesByUser(userId: String, cursor: String?, limit: Int): ActivityPage
    fun activitiesInterestedByUser(userId: String, cursor: String?, limit: Int): ActivityPage
    fun activitiesForLocation(
        locationName: String,
        placeId: String?,
        locationId: String?,
        cursor: String?,
        limit: Int,
    ): ActivityPage
    fun searchActivities(cursor: String?, limit: Int, query: String = ""): ActivityPage
    fun addActivityInterest(activityId: String, userId: String, createdAt: Long): Boolean
    fun removeActivityInterest(activityId: String, userId: String): Boolean
    fun isActivityInterested(activityId: String, userId: String): Boolean
    fun activityInterested(activityId: String, cursor: String?, limit: Int): EventAttendeePage
    fun activityInterestedIds(activityId: String): List<String>
    fun activityLocationFlags(): ActivityLocationFlags
    fun insertUserPhoto(photo: UserPhotoRecord): UserPhotoRecord
    fun findUserPhoto(id: String): UserPhotoRecord?
    fun deleteUserPhoto(id: String): UserPhotoRecord?
    fun userPhotosForActivity(activityId: String, limit: Int): List<UserPhotoRecord>
    fun userPhotosAtLocation(locationName: String, placeId: String?, locationId: String?, limit: Int): List<UserPhotoRecord>
    fun deleteActivityPhotos(activityId: String): List<UserPhotoRecord>
    fun locationDescriptionAtLocation(locationName: String, placeId: String?, locationId: String?): LocationDescriptionRecord?
    fun upsertLocationDescription(description: LocationDescriptionRecord): LocationDescriptionRecord
    fun insertUserLocation(name: String, lat: Double, lng: Double, createdBy: String, createdAt: Long): UserLocationRecord
    fun searchUserLocations(query: String, limit: Int): List<UserLocationRecord>
    fun userLocations(): List<UserLocationRecord>
    fun postedGooglePlaces(viewerId: String?): List<PlaceSuggestion>
    fun findUserLocation(id: String): UserLocationRecord?
    fun recentPlaces(communityId: String?, limit: Int): List<PlaceSuggestion>
    fun latestPostPhotoAtPlace(location: String, placeId: String?, viewerId: String?, locationId: String? = null): String?
    fun findPlace(placeId: String, viewerId: String? = null): PlaceSuggestion?
    fun postsAtPlace(
        location: String,
        placeId: String?,
        cursor: String?,
        limit: Int,
        viewerId: String? = null,
        locationId: String? = null,
    ): FeedPage
    fun countPostsAtPlace(location: String, placeId: String?, viewerId: String? = null, locationId: String? = null): Long
    fun people(cursor: String?, limit: Int, viewerId: String? = null, query: String = ""): CommunityMemberPage
    fun eventPostsBetween(from: Long, to: Long): List<PostRecord>
    fun markEventReminder(postId: String, hour: Boolean, nowFlag: Boolean)
    fun findPollVote(postId: String, userId: String): PollVote?
    fun castPollVote(postId: String, userId: String, optionIndex: Int, votedAt: Long): Boolean
    fun removePollVote(postId: String, userId: String): Boolean
    fun pollOptions(postId: String, viewerId: String?, voterLimit: Int = 5): List<PollOptionView>
    fun insertQuestionAnswer(answer: QuestionAnswerRecord): QuestionAnswerRecord
    fun findQuestionAnswer(postId: String, userId: String): QuestionAnswerRecord?
    fun questionAnswers(postId: String): List<QuestionAnswerItem>
    fun insertFeatureSuggestion(userId: String, text: String, createdAt: Long): FeatureSuggestionRecord
    fun findFeatureSuggestion(id: String): FeatureSuggestionRecord?
    fun deleteFeatureSuggestion(id: String): Boolean
    fun resolveFeatureSuggestion(id: String, resolvedAt: Long): FeatureSuggestionView?
    fun toggleFeatureVote(id: String, userId: String, votedAt: Long): FeatureSuggestionView?
    fun featureSuggestions(viewerId: String?, limit: Int, openOnly: Boolean): List<FeatureSuggestionView>
    fun featureSuggestionView(id: String, viewerId: String?): FeatureSuggestionView?
    fun upsertPushSubscription(subscription: PushSubscriptionRecord)
    fun deletePushSubscription(userId: String, endpoint: String)
    fun deletePushSubscriptionByEndpoint(endpoint: String)
    fun pushSubscriptionsFor(userId: String): List<PushSubscriptionRecord>
    fun upsertFcmToken(record: FcmTokenRecord)
    fun deleteFcmToken(userId: String, token: String)
    fun deleteFcmTokenByValue(token: String)
    fun fcmTokensFor(userId: String): List<FcmTokenRecord>
    fun insertChat(chat: ChatRecord): ChatRecord
    fun updateChat(chat: ChatRecord)
    fun findChat(id: String): ChatRecord?
    fun findDirectChat(userA: String, userB: String): ChatRecord?
    fun deleteChat(id: String)
    fun chatsFor(userId: String, cursor: String?, limit: Int, type: String? = null): ChatPage
    fun chatSummary(chatId: String, viewerId: String): ChatSummary?
    fun upsertChatMember(member: ChatMemberRecord)
    fun findChatMember(chatId: String, userId: String): ChatMemberRecord?
    fun deleteChatMember(chatId: String, userId: String)
    fun chatMemberIds(chatId: String): List<String>
    fun chatMembers(chatId: String): List<ChatMemberView>
    fun insertChatMessage(message: ChatMessageRecord): ChatMessageRecord
    fun findChatMessage(id: String): ChatMessageRecord?
    fun deleteChatMessage(id: String)
    fun chatMessages(chatId: String, cursor: String?, limit: Int, viewerId: String): ChatMessagePage
    fun chatMessageItem(id: String, viewerId: String): ChatMessageItem?
    fun chatPhotoPaths(chatId: String): List<String>
    fun incrementChatUnread(chatId: String, exceptUserId: String)
    fun markChatRead(chatId: String, userId: String, readAt: Long)
    fun insertChatReaction(reaction: ChatReactionRecord): ChatReactionRecord
    fun findChatReaction(messageId: String, userId: String, emoji: String): ChatReactionRecord?
    fun deleteChatReaction(id: String)
    fun chatReactionDetails(messageId: String, viewerId: String): List<ReactionDetail>
    fun chatReactionCounts(messageId: String, viewerId: String): List<ReactionCount>
    fun topChatReactions(chatId: String, limit: Int): List<String>
    fun searchPeople(query: String, excludeIds: Set<String>, limit: Int): List<EventAttendee>
    fun markChatNotificationsRead(userId: String, chatId: String, readAt: Long)
    fun markChatNoticeTypesRead(userId: String, types: Collection<String>, readAt: Long)
    fun unreadChatNotificationCount(userId: String): Long
}
