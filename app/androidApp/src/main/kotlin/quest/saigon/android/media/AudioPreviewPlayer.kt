package quest.saigon.android.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import quest.saigon.android.diagnostics.CrashReporter

data class AudioPlaybackState(
    val currentUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
)

class AudioPreviewPlayer(context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _state.update {
                        it.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    CrashReporter.recordException(error, "ExoPlayer error: ${_state.value.currentUrl}")
                    _state.update { it.copy(isPlaying = false, isBuffering = false) }
                }
            },
        )
    }

    fun play(url: String?) {
        val clean = url?.takeIf { it.isNotBlank() } ?: return
        if (_state.value.currentUrl == clean) {
            if (!player.isPlaying) player.play()
            return
        }
        _state.value = AudioPlaybackState(currentUrl = clean, isPlaying = false, isBuffering = true)
        player.setMediaItem(MediaItem.fromUri(clean))
        player.prepare()
        player.play()
    }

    fun toggle(url: String?) {
        val clean = url?.takeIf { it.isNotBlank() } ?: return
        if (_state.value.currentUrl == clean) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            return
        }
        _state.value = AudioPlaybackState(currentUrl = clean, isPlaying = false, isBuffering = true)
        player.setMediaItem(MediaItem.fromUri(clean))
        player.prepare()
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
        _state.value = AudioPlaybackState()
    }

    fun release() {
        player.release()
    }
}
