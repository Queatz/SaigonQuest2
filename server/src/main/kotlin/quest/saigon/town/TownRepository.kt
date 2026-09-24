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
    fun insertPost(post: PostRecord): PostRecord
    fun feed(cursor: String?, limit: Int): FeedPage
    fun findPost(id: String): PostRecord?
}
