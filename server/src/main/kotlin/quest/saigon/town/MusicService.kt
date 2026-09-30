package quest.saigon.town

import org.slf4j.LoggerFactory
import quest.saigon.Limits
import quest.saigon.MusicNames
import quest.saigon.ai.MusicRenderer
import quest.saigon.media.UploadStore
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MusicService(
    private val repo: TownRepository,
    private val uploads: UploadStore,
    private val renderer: MusicRenderer,
    private val configured: Boolean,
    private val executor: Executor = Executors.newFixedThreadPool(1) { runnable ->
        Thread(runnable, "music").apply { isDaemon = true }
    },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(MusicService::class.java)

    fun enqueue(user: UserRecord, prompt: String, gender: String?, instrumental: Boolean): MusicRecord {
        if (!configured) throw TownException("qwen_not_configured")
        if (repo.hasGeneratingMusic(user.id)) throw TownException("music_busy")
        val music = repo.insertMusic(
            userId = user.id,
            prompt = prompt,
            name = MusicNames.fromPrompt(prompt),
            gender = if (instrumental) null else gender,
            instrumental = instrumental,
            createdAt = clock(),
        )
        start(music)
        return repo.findMusic(music.id) ?: music
    }

    fun retry(user: UserRecord, musicId: String): MusicRecord {
        if (!configured) throw TownException("qwen_not_configured")
        val music = owned(user, musicId)
        if (music.status != MusicStatus.FAILED) throw TownException("music_not_failed")
        if (repo.hasGeneratingMusic(user.id)) throw TownException("music_busy")
        repo.updateMusic(music.id, MusicStatus.GENERATING, music.audioPath, null)
        val next = music.copy(status = MusicStatus.GENERATING, error = null)
        start(next)
        return repo.findMusic(music.id) ?: next
    }

    fun rename(user: UserRecord, musicId: String, name: String): MusicRecord {
        val music = owned(user, musicId)
        if (music.status == MusicStatus.GENERATING) throw TownException("music_busy")
        repo.updateMusic(music.id, music.status, music.audioPath, music.error, name)
        return repo.findMusic(music.id) ?: music.copy(name = name)
    }

    fun delete(user: UserRecord, musicId: String) {
        val music = owned(user, musicId)
        if (music.status == MusicStatus.GENERATING) throw TownException("music_busy")
        if (repo.musicReferenced(music.id)) {
            repo.updateMusic(music.id, MusicStatus.REMOVED, music.audioPath, null)
        } else {
            music.audioPath?.let { uploads.delete(it) }
            repo.deleteMusic(music.id)
        }
    }

    private fun owned(user: UserRecord, musicId: String): MusicRecord {
        val music = repo.findMusic(musicId) ?: throw TownException("not_found")
        if (music.userId != user.id) throw TownException("not_found")
        return music
    }

    private fun start(music: MusicRecord) {
        executor.execute {
            try {
                val rendered = try {
                    renderer.render(music.prompt, music.gender, music.instrumental)
                } catch (first: Exception) {
                    log.warn("Music {} retrying: {}", music.id, first.message)
                    renderer.render(music.prompt, music.gender, music.instrumental)
                }
                val path = uploads.writeMusic(rendered.bytes)
                repo.updateMusic(music.id, MusicStatus.READY, path, null, rendered.name, clipLyrics(rendered.lyrics))
            } catch (error: Exception) {
                val reason = avatarFailureReason(error)
                log.warn("Music {} failed: {}", music.id, reason)
                repo.updateMusic(music.id, MusicStatus.FAILED, null, reason)
            }
        }
    }

    private fun clipLyrics(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return if (text.length <= Limits.MUSIC_LYRICS_MAX) text else text.take(Limits.MUSIC_LYRICS_MAX)
    }
}
