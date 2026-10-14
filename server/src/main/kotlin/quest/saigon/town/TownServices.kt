package quest.saigon.town

import org.slf4j.LoggerFactory
import quest.saigon.AppConfig
import quest.saigon.ai.AvatarPipeline
import quest.saigon.ai.AvatarRenderer
import quest.saigon.ai.CommunityPrompts
import quest.saigon.ai.CommunitySuggester
import quest.saigon.ai.MusicPipeline
import quest.saigon.ai.QuickReplyPipeline
import quest.saigon.ai.QwenClient
import quest.saigon.auth.RateLimiter
import quest.saigon.auth.SessionTokens
import quest.saigon.maps.GooglePlaces
import quest.saigon.maps.NoPlaces
import quest.saigon.maps.PlaceLookup
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class TownServices(
    val repo: TownRepository,
    val uploads: UploadStore,
    val avatars: AvatarService,
    val music: MusicService,
    val config: AppConfig,
    val clock: () -> Long = System::currentTimeMillis,
    val tokens: () -> String = SessionTokens::new,
    val contactLimiter: RateLimiter = RateLimiter(30, 10 * 60_000, clock),
    val viewLimiter: RateLimiter = RateLimiter(1, 30_000, clock),
    val visitLimiter: RateLimiter = RateLimiter(60, 60_000, clock),
    val images: ImageOps = ImageOps(),
    val hub: NotificationHub = NotificationHub(),
    val pusher: NotificationPusher = NoNotificationPush,
    val replies: QuickReplyService = QuickReplyService(repo),
    val communities: CommunityService = CommunityService(repo, clock),
    val places: PlaceLookup = NoPlaces,
    val events: EventService = EventService(repo, hub, clock, pusher),
    val polls: PollService = PollService(repo, hub, clock, pusher),
    val chatHub: NotificationHub = NotificationHub(),
    val chats: ChatService = ChatService(repo, chatHub, clock, pusher),
) {
    val comments: CommentService = CommentService(repo, hub, clock, pusher)

    companion object {
        fun open(config: AppConfig): TownServices {
            val repo = ArangoTownRepository.open(config)
            val uploads = UploadStore(config.uploadDir).also { it.ensure() }
            val qwen = QwenClient.fromConfig(config)
            val clock: () -> Long = System::currentTimeMillis
            val hub = NotificationHub()
            val chatHub = NotificationHub()
            val pusher = WebPushService.fromConfig(config, repo, clock)
            val events = EventService(repo, hub, clock, pusher).also { it.start() }
            return TownServices(
                repo = repo,
                uploads = uploads,
                avatars = AvatarService(repo, uploads, AvatarPipeline(qwen), config.qwenConfigured),
                music = MusicService(repo, uploads, MusicPipeline(qwen), config.qwenConfigured),
                config = config,
                clock = clock,
                hub = hub,
                pusher = pusher,
                replies = QuickReplyService(repo, QuickReplyPipeline(qwen), config.qwenConfigured),
                communities = CommunityService(repo, clock, communitySuggester(qwen, config.qwenConfigured)),
                places = config.googleMapsApiKey?.takeIf { it.isNotBlank() }?.let(::GooglePlaces) ?: NoPlaces,
                events = events,
                chatHub = chatHub,
                chats = ChatService(repo, chatHub, clock, pusher),
            )
        }
    }
}

class AvatarService(
    private val repo: TownRepository,
    private val uploads: UploadStore,
    private val renderer: AvatarRenderer,
    private val configured: Boolean,
    private val executor: Executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "avatar").apply { isDaemon = true }
    },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(AvatarService::class.java)

    fun enqueue(user: UserRecord, prompt: String): AvatarRecord {
        if (!configured) throw TownException("qwen_not_configured")
        val photoPath = user.profilePhotoPath ?: throw TownException("profile_photo_required")
        if (repo.hasGeneratingAvatar(user.id)) throw TownException("avatar_busy")
        val avatar = repo.insertAvatar(user.id, prompt, clock())
        start(avatar.id, photoPath, prompt)
        return avatar
    }

    fun retry(user: UserRecord, avatarId: String): AvatarRecord {
        if (!configured) throw TownException("qwen_not_configured")
        val photoPath = user.profilePhotoPath ?: throw TownException("profile_photo_required")
        val avatar = owned(user, avatarId)
        if (avatar.status != AvatarStatus.FAILED) throw TownException("avatar_not_failed")
        if (repo.hasGeneratingAvatar(user.id)) throw TownException("avatar_busy")
        repo.updateAvatar(avatar.id, AvatarStatus.GENERATING, avatar.imagePath, null)
        start(avatar.id, photoPath, avatar.prompt)
        return avatar.copy(status = AvatarStatus.GENERATING, error = null)
    }

    fun delete(user: UserRecord, avatarId: String) {
        val avatar = owned(user, avatarId)
        if (avatar.status == AvatarStatus.GENERATING) throw TownException("avatar_busy")
        if (repo.avatarReferenced(avatar.id)) {
            repo.updateAvatar(avatar.id, AvatarStatus.REMOVED, avatar.imagePath, null)
        } else {
            avatar.imagePath?.let { uploads.delete(it) }
            repo.deleteAvatar(avatar.id)
        }
    }

    private fun owned(user: UserRecord, avatarId: String): AvatarRecord {
        val avatar = repo.findAvatar(avatarId) ?: throw TownException("not_found")
        if (avatar.userId != user.id) throw TownException("not_found")
        return avatar
    }

    private fun start(id: String, photoPath: String, prompt: String) {
        val photo = uploads.read(photoPath)
        executor.execute {
            try {
                val png = renderer.render(photo, prompt)
                val path = uploads.writeAvatar(png)
                repo.updateAvatar(id, AvatarStatus.READY, path, null)
            } catch (error: Exception) {
                val reason = avatarFailureReason(error)
                log.warn("Avatar {} failed: {}", id, reason)
                repo.updateAvatar(id, AvatarStatus.FAILED, null, reason)
            }
        }
    }
}

internal fun communitySuggester(qwen: QwenClient, configured: Boolean): CommunitySuggester =
    CommunitySuggester { message, existing ->
        if (!configured) {
            CommunityPrompts.fallback(message, existing)
        } else {
            try {
                CommunityPrompts.parse(
                    qwen.chat(
                        CommunityPrompts.SYSTEM,
                        CommunityPrompts.user(message, existing),
                        temperature = 0.2,
                    ),
                ) ?: CommunityPrompts.fallback(message, existing)
            } catch (_: Exception) {
                CommunityPrompts.fallback(message, existing)
            }
        }
    }

internal fun avatarFailureReason(error: Throwable): String {
    val raw = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf { message -> message.isNotEmpty() } }
        .firstOrNull()
        ?: "generation_failed"
    return raw
        .replace(Regex("sk-[A-Za-z0-9._-]+"), "sk-redacted")
        .replace(Regex("\\s+"), " ")
        .take(180)
}
