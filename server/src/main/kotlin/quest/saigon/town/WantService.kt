package quest.saigon.town

import org.slf4j.LoggerFactory
import quest.saigon.Limits
import quest.saigon.ai.UnsafeWantPromptException
import quest.saigon.ai.WantPhotoRenderer
import quest.saigon.http.Jsons
import quest.saigon.media.UploadStore
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    private val chats: ChatService,
    private val uploads: UploadStore? = null,
    private val renderer: WantPhotoRenderer? = null,
    private val configured: Boolean = false,
    private val executor: Executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "want-photo").apply { isDaemon = true }
    },
    private val retryIntervalMs: Long = 60 * 60_000L,
) {
    private val log = LoggerFactory.getLogger(WantService::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val started = AtomicBoolean(false)

    fun open(actor: UserRecord, rawName: String): OpenedWant {
        val name = WantNames.clean(rawName) ?: throw TownException("invalid_want")
        val slug = WantNames.slug(name)
        val existing = repo.findWantBySlug(slug)
        if (existing != null) {
            if (existing.photoPath.isNullOrBlank() && !existing.photoBlocked) {
                enqueuePhoto(existing)
            }
            return OpenedWant(join(actor, existing.id), created = false)
        }

        val created = try {
            repo.insertWant(WantRecord(id = "", name = name, slug = slug, createdAt = clock()))
        } catch (error: TownException) {
            if (error.code != "want_taken") throw error
            val raced = repo.findWantBySlug(slug) ?: throw error
            if (raced.photoPath.isNullOrBlank() && !raced.photoBlocked) {
                enqueuePhoto(raced)
            }
            return OpenedWant(join(actor, raced.id), created = false)
        }
        repo.joinWant(created.id, actor.id, clock())
        val saved = repo.findWant(created.id) ?: created
        enqueuePhoto(saved)
        return OpenedWant(saved, created = true)
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

    fun get(wantId: String, viewerId: String?): WantView {
        val want = repo.findWant(wantId) ?: throw TownException("not_found")
        return view(want, viewerId, Limits.WANT_SHEET_MEMBER_LIMIT)
    }

    fun listAll(viewerId: String?): List<WantView> =
        repo.allWants().shuffled().map { view(it, viewerId) }

    fun delete(wantId: String) {
        val removed = repo.deleteWant(wantId) ?: throw TownException("not_found")
        inFlight.remove(wantId)
        removed.photoPath?.takeIf { it.isNotBlank() }?.let { uploads?.delete(it) }
    }

    fun retryMissingPhotos() {
        if (!configured || renderer == null || uploads == null) return
        repo.wantsWithoutPhoto().forEach(::enqueuePhoto)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        Thread({
            while (true) {
                try {
                    retryMissingPhotos()
                } catch (_: Exception) {
                }
                try {
                    Thread.sleep(retryIntervalMs)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "want-photo-retry").apply { isDaemon = true }.start()
    }

    fun enqueuePhoto(want: WantRecord) {
        if (!configured || renderer == null || uploads == null) return
        if (!want.photoPath.isNullOrBlank() || want.photoBlocked) return
        if (!inFlight.add(want.id)) return
        try {
            executor.execute {
                try {
                    val latest = repo.findWant(want.id) ?: return@execute
                    if (!latest.photoPath.isNullOrBlank() || latest.photoBlocked) return@execute
                    val jpeg = renderer.render(latest.name)
                    val path = uploads.writeWant(jpeg)
                    repo.updateWantPhoto(latest.id, path, photoBlocked = false)
                } catch (unsafe: UnsafeWantPromptException) {
                    log.warn("Want {} photo blocked by safety check: {}", want.id, unsafe.message)
                    repo.updateWantPhoto(want.id, null, photoBlocked = true)
                } catch (error: Exception) {
                    log.warn("Want {} photo generation failed: {}", want.id, avatarFailureReason(error))
                } finally {
                    inFlight.remove(want.id)
                }
            }
        } catch (error: Exception) {
            inFlight.remove(want.id)
            log.warn("Want {} photo scheduling failed: {}", want.id, error.message)
        }
    }

    fun view(want: WantRecord, viewerId: String?, memberLimit: Int = Limits.WANT_CARD_MEMBER_LIMIT): WantView =
        WantView(
            want = want,
            joined = viewerId != null && repo.isWantMember(want.id, viewerId),
            members = repo.wantPeople(want.id, memberLimit),
        )

    fun startChat(actor: UserRecord, wantId: String, userIds: List<String>): ChatSummary {
        val want = repo.findWant(wantId) ?: throw TownException("not_found")
        val allowed = repo.wantMembers(want.id).toSet()
        val selected = userIds
            .map(String::trim)
            .filter { it.isNotBlank() && it != actor.id && it in allowed }
            .distinct()
        if (selected.isEmpty()) throw TownException("no_chat_members")
        val attachment = ChatAttachment(type = "want", wantId = want.id, wantName = want.name)
        return if (selected.size == 1) {
            chats.createDirect(actor, selected.first(), "", attachment)
        } else {
            val groupName = ChatService.cleanName(want.name.take(Limits.CHAT_NAME_MAX)) ?: want.name.take(Limits.CHAT_NAME_MAX)
            val group = chats.createGroup(actor, groupName)
            selected.forEach { memberId -> chats.addMember(actor, group.id, memberId) }
            chats.send(actor, group.id, "", emptyList(), attachment)
            chats.get(actor, group.id)
        }
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