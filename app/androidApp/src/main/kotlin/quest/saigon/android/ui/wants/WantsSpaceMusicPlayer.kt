package quest.saigon.android.ui.wants

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import quest.saigon.WantsSpaceDrumKind
import quest.saigon.WantsSpaceMusic
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

internal class WantsSpaceMusicPlayer {
    private val sampleRate = 44_100
    private val track: AudioTrack
    @Volatile
    private var stopRequested = false

    init {
        val minimumBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minimumBufferBytes, sampleRate * 2 / 10))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun start() {
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return
        }
        try {
            track.play()
            Thread({ render() }, "wants-space-music").apply {
                isDaemon = true
                start()
            }
        } catch (_: Exception) {
            track.release()
        }
    }

    fun stop() {
        stopRequested = true
    }

    private fun render() {
        val buffer = ShortArray(1_024)
        val startEpochMillis = System.currentTimeMillis()
        val startElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        var sampleCursor = 0L
        var stopStartedAtSample: Long? = null
        var currentSixteenth = Long.MIN_VALUE
        var currentPhraseIndex = Long.MIN_VALUE
        var currentNoteIndex = -1
        var noteStartMs = 0L
        var pitch = -1
        var frequency = 0.0
        var padVelocity = 1.0
        var crystalVelocity = 1.0
        var padPhase = 0.0
        var crystalPhase = 0.0
        val activeDrums = mutableListOf<ActiveDrum>()

        try {
            while (true) {
                for (index in buffer.indices) {
                    val absoluteSample = sampleCursor + index
                    if (stopRequested && stopStartedAtSample == null) {
                        stopStartedAtSample = absoluteSample
                    }
                    val sampleTimeMs = startEpochMillis + absoluteSample * 1_000.0 / sampleRate
                    val sampleEpochMs = sampleTimeMs.toLong()
                    val absoluteSixteenth = WantsSpaceMusic.absoluteSixteenthAt(sampleEpochMs)
                    if (absoluteSixteenth != currentSixteenth) {
                        currentSixteenth = absoluteSixteenth
                        val phraseIndex = WantsSpaceMusic.phraseIndexAt(sampleEpochMs)
                        val pulseInPhrase = WantsSpaceMusic.pulseInPhraseAt(sampleEpochMs)
                        val noteIndex = WantsSpaceMusic.noteIndexAt(sampleEpochMs)
                        if (phraseIndex != currentPhraseIndex || noteIndex != currentNoteIndex) {
                            currentPhraseIndex = phraseIndex
                            currentNoteIndex = noteIndex
                            pitch = WantsSpaceMusic.pitchAt(sampleEpochMs)
                            frequency = 440.0 * 2.0.pow((pitch - 69) / 12.0)
                            noteStartMs = WantsSpaceMusic.currentNoteStartMillis(sampleEpochMs)
                            padVelocity = WantsSpaceMusic.padVelocity(phraseIndex, noteIndex)
                            crystalVelocity = WantsSpaceMusic.crystalVelocity(phraseIndex, noteIndex)
                            val noteAgeSeconds = (sampleTimeMs - noteStartMs) / 1_000.0
                            padPhase = (2.0 * PI * frequency * noteAgeSeconds) % (2.0 * PI)
                            crystalPhase = (4.0 * PI * frequency * noteAgeSeconds) % (2.0 * PI)
                        }
                        val drumStartMs = WantsSpaceMusic.sixteenthStartMillis(absoluteSixteenth)
                        if (sampleEpochMs - drumStartMs <= 2L) {
                            WantsSpaceMusic.drumHitsAt(phraseIndex, pulseInPhrase).forEach {
                                activeDrums.add(ActiveDrum(it.kind, drumStartMs, sampleRate))
                            }
                        }
                    }

                    val noteAgeSeconds = (sampleTimeMs - noteStartMs) / 1_000.0
                    val padAttack = (noteAgeSeconds / 0.06).coerceIn(0.0, 1.0)
                    val pad = sin(padPhase) * 0.12 * padVelocity * padAttack
                    val crystalAttack = (noteAgeSeconds / 0.008).coerceIn(0.0, 1.0)
                    val crystalEnvelope = exp(-noteAgeSeconds / 0.65) * crystalAttack
                    val crystal = (sin(crystalPhase) + 0.2 * sin(crystalPhase * 2.0)) *
                        0.07 * crystalEnvelope * crystalVelocity
                    padPhase += 2.0 * PI * frequency / sampleRate
                    crystalPhase += 4.0 * PI * frequency / sampleRate
                    if (padPhase >= 2.0 * PI) padPhase -= 2.0 * PI
                    if (crystalPhase >= 2.0 * PI) crystalPhase -= 2.0 * PI

                    var drums = 0.0
                    for (drumIndex in activeDrums.indices.reversed()) {
                        val drum = activeDrums[drumIndex]
                        val ageSeconds = (sampleTimeMs - drum.startedAtMs) / 1_000.0
                        val noise = if (drum.kind == WantsSpaceDrumKind.KICK) 0.0 else drum.nextFilteredNoise()
                        val sound = when (drum.kind) {
                            WantsSpaceDrumKind.KICK -> {
                                if (ageSeconds >= 0.24) 0.0 else {
                                    val kickPhase = 2.0 * PI * (
                                        45.0 * ageSeconds + 105.0 * (1.0 - exp(-ageSeconds * 25.0)) / 25.0
                                    )
                                    sin(kickPhase) * exp(-ageSeconds * 16.0) * 0.12
                                }
                            }
                            WantsSpaceDrumKind.SNARE ->
                                if (ageSeconds >= 0.14) 0.0 else noise * exp(-ageSeconds * 24.0) * 0.035
                            WantsSpaceDrumKind.HAT ->
                                if (ageSeconds >= 0.07) 0.0 else noise * exp(-ageSeconds * 58.0) * 0.015
                        }
                        if (ageSeconds >= drum.durationSeconds) {
                            activeDrums.removeAt(drumIndex)
                        } else {
                            drums += sound
                        }
                    }

                    val sampleElapsedMs = startElapsedRealtimeMillis + absoluteSample * 1_000.0 / sampleRate
                    val fadeIn = ((sampleElapsedMs - startElapsedRealtimeMillis) / 2_000.0).coerceIn(0.0, 1.0)
                    val fadeOut = stopStartedAtSample?.let {
                        (1.0 - (absoluteSample - it) * 1_000.0 / sampleRate / 180.0).coerceIn(0.0, 1.0)
                    } ?: 1.0
                    buffer[index] = ((pad + crystal + drums).coerceIn(-0.95, 0.95) *
                        0.5 * fadeIn * fadeOut * Short.MAX_VALUE)
                        .toInt()
                        .toShort()
                }

                var writtenOffset = 0
                while (writtenOffset < buffer.size) {
                    val written = track.write(
                        buffer,
                        writtenOffset,
                        buffer.size - writtenOffset,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    if (written <= 0) break
                    writtenOffset += written
                }
                if (writtenOffset < buffer.size) break
                sampleCursor += buffer.size
                val stopAtSample = stopStartedAtSample
                if (stopAtSample != null && sampleCursor - stopAtSample >= 180L * sampleRate / 1_000L) break
            }
        } finally {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }
    }

    private class ActiveDrum(
        val kind: WantsSpaceDrumKind,
        val startedAtMs: Long,
        sampleRate: Int,
    ) {
        private var noiseState = startedAtMs.toInt() xor (kind.ordinal * 0x45D9F3B)
        private val highPassAlpha = exp(-2.0 * PI * (if (kind == WantsSpaceDrumKind.SNARE) 1_500.0 else 7_000.0) / sampleRate)
        private val lowPassAlpha = 1.0 - exp(-2.0 * PI * (if (kind == WantsSpaceDrumKind.SNARE) 6_000.0 else 12_000.0) / sampleRate)
        private var previousNoise1 = 0.0
        private var highPassedNoise1 = 0.0
        private var previousNoise2 = 0.0
        private var highPassedNoise2 = 0.0
        private var lowPassedNoise = 0.0

        init {
            if (noiseState == 0) noiseState = 0x13579BDF
        }

        fun nextFilteredNoise(): Double {
            noiseState = noiseState * 1_664_525 + 1_013_904_223
            val noise = noiseState.toDouble() / 2_147_483_648.0
            highPassedNoise1 = highPassAlpha * (highPassedNoise1 + noise - previousNoise1)
            previousNoise1 = noise
            highPassedNoise2 = highPassAlpha * (highPassedNoise2 + highPassedNoise1 - previousNoise2)
            previousNoise2 = highPassedNoise1
            lowPassedNoise += lowPassAlpha * (highPassedNoise2 - lowPassedNoise)
            return lowPassedNoise
        }

        val durationSeconds: Double
            get() = when (kind) {
                WantsSpaceDrumKind.KICK -> 0.24
                WantsSpaceDrumKind.SNARE -> 0.14
                WantsSpaceDrumKind.HAT -> 0.07
            }
    }
}