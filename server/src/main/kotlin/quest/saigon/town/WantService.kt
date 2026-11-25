package quest.saigon.town

import quest.saigon.Limits
import quest.saigon.http.Jsons
import java.text.Normalizer

data class OpenedWant(val want: WantRecord, val created: Boolean)

internal fun wantMembershipKey(wantId: String, userId: String) = "${wantId}_$userId"

object WantNames {
    fun clean(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.length !in 1..Limits.WANT_NAME_MAX) return null
        if (name.any { it.isISOControl() }) return null
        return name
    }

    fun slug(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
}

class WantService(
    private val repo: TownRepository,
    private val hub: NotificationHub,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pusher: NotificationPusher = NoNotificationPush,
) {
    fun open(actor: UserRecord, rawName: String): OpenedWant {
        val name = WantNames.clean(rawName) ?: throw TownException("invalid_want")
        val slug = WantNames.slug(name)
        val existing = repo.findWantBySlug(slug)
        if (existing != null) return OpenedWant(join(actor, existing.id), created = false)

        val created = try {
            repo.insertWant(WantRecord(id = "", name = name, slug = slug, createdAt = clock()))
        } catch (error: TownException) {
            if (error.code != "want_taken") throw error
            val raced = repo.findWantBySlug(slug) ?: throw error
            return OpenedWant(join(actor, raced.id), created = false)
        }
        repo.joinWant(created.id, actor.id, clock())
        return OpenedWant(repo.findWant(created.id) ?: created, created = true)
    }

    fun join(actor: UserRecord, wantId: String): WantRecord {
        val want = repo.findWant(wantId) ?: throw TownException("not_found")
        val existingMembers = repo.wantMembers(want.id)
        if (repo.joinWant(want.id, actor.id, clock())) {
            existingMembers.filter { it != actor.id }.forEach { userId ->
                notifyMember(userId, actor, want)
            }
        }
        return repo.findWant(want.id) ?: want
    }

    fun leave(actor: UserRecord, wantId: String): WantRecord {
        val want = repo.findWant(wantId) ?: throw TownException("not_found")
        repo.leaveWant(want.id, actor.id)
        return repo.findWant(want.id) ?: want
    }

    private fun notifyMember(userId: String, actor: UserRecord, want: WantRecord) {
        val notification = repo.insertNotification(
            NotificationRecord(
                id = "",
                userId = userId,
                type = NotificationType.WANT_JOINED,
                postId = "",
                commentId = "",
                actorId = actor.id,
                actorName = actor.name,
                preview = want.name,
                createdAt = clock(),
            ),
        )
        hub.publish(userId, Jsons.write(CommentService.notificationPayload(notification)))
        pusher.notify(userId, notification)
    }
}