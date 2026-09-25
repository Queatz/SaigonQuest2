package quest.saigon.town

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryTownRepository : TownRepository {
    private val users = ConcurrentHashMap<String, UserRecord>()
    private val phones = ConcurrentHashMap<String, String>()
    private val sessions = ConcurrentHashMap<String, SessionRecord>()
    private val avatars = ConcurrentHashMap<String, AvatarRecord>()
    private val posts = ConcurrentHashMap<String, PostRecord>()

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
        val items = page.take(limit).map { post ->
            val author = users[post.userId]
            val avatar = avatars[post.avatarId]
            FeedItem(
                id = post.id,
                authorName = author?.name ?: "",
                message = post.message,
                location = post.location,
                photoPath = post.photoPath,
                avatarPath = avatar?.imagePath,
                createdAt = post.createdAt,
            )
        }
        val next = if (page.size > limit) items.lastOrNull()?.let { Cursors.encode(it.createdAt, it.id) } else null
        return FeedPage(items, next)
    }

    override fun findPost(id: String): PostRecord? = posts[id]

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
                avatarPath = avatars[post.avatarId]?.imagePath,
                createdAt = post.createdAt,
                views = post.views,
                replyClicks = post.replyClicks,
                shareClicks = post.shareClicks,
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

    override fun deletePost(id: String) {
        posts.remove(id)
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")
}
