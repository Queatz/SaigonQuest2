package quest.saigon.web

import quest.saigon.WantsSpaceMusic as WantsSpaceSequencer
import quest.saigon.WantsSpaceDrumKind
import web.dom.document
import web.window.window
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal class WantsSpaceMusicPlayer {
    private var context: dynamic = null
    private var masterGain: dynamic = null
    private var intervalId: dynamic = null
    private var resumeHandler: ((dynamic) -> Unit)? = null
    private var nextNoteAtMs: Long? = null
    private var nextDrumHitAtMs: Long? = null
    private var closed = false

    init {
        try {
            context = js("new (window.AudioContext || window.webkitAudioContext)()")
            masterGain = context.createGain()
            masterGain.connect(context.destination)
            if (context.state != "running") {
                resumeHandler = { _: dynamic -> resumeFromGesture() }
                document.asDynamic().addEventListener("pointerdown", resumeHandler)
            }
            intervalId = window.asDynamic().setInterval({ update() }, 40)
        } catch (_: Throwable) {
            context = null
        }
    }

    fun stop() {
        if (closed) return
        closed = true
        if (intervalId != null && intervalId != undefined) {
            window.asDynamic().clearInterval(intervalId)
            intervalId = null
        }
        resumeHandler?.let {
            document.asDynamic().removeEventListener("pointerdown", it)
            resumeHandler = null
        }
        if (context == null || context.state == "closed") return

        try {
            if (context.state == "running") {
                val now = context.currentTime as Double
                val gain = (masterGain.gain.value as Number).toDouble()
                masterGain.gain.cancelScheduledValues(now)
                masterGain.gain.setValueAtTime(gain, now)
                masterGain.gain.linearRampToValueAtTime(0.0001, now + 0.18)
                window.asDynamic().setTimeout({ context.close() }, 220)
            } else {
                context.close()
            }
        } catch (_: Throwable) {
            try {
                context.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun resumeFromGesture() {
        if (closed || context == null) return
        try {
            context.resume().then({
                if (context.state == "running") {
                    resumeHandler?.let {
                        document.asDynamic().removeEventListener("pointerdown", it)
                        resumeHandler = null
                    }
                    nextNoteAtMs = null
                    update()
                }
            }, { _: dynamic -> Unit })
        } catch (_: Throwable) {
        }
    }

    private fun update() {
        if (closed || context == null || context.state != "running") return

        val nowEpochMs = (js("Date.now()") as Double).toLong()
        val nowAudioTime = context.currentTime as Double
        var scheduledThroughMs = nextNoteAtMs

        if (scheduledThroughMs == null) {
            masterGain.gain.setValueAtTime(0.0001, nowAudioTime)
            masterGain.gain.linearRampToValueAtTime(0.07, nowAudioTime + 2.0)
            val noteEndMs = WantsSpaceSequencer.nextNoteStartMillis(nowEpochMs)
            scheduleNotes(
                startEpochMs = nowEpochMs,
                endEpochMs = noteEndMs,
                pitch = WantsSpaceSequencer.pitchAt(nowEpochMs),
                nowEpochMs = nowEpochMs,
                nowAudioTime = nowAudioTime,
            )
            scheduledThroughMs = noteEndMs
        }

        var nextNoteAt: Long = scheduledThroughMs
        if (nextNoteAt < nowEpochMs - 100L) {
            val noteEndMs = WantsSpaceSequencer.nextNoteStartMillis(nowEpochMs)
            scheduleNotes(
                startEpochMs = nowEpochMs,
                endEpochMs = noteEndMs,
                pitch = WantsSpaceSequencer.pitchAt(nowEpochMs),
                nowEpochMs = nowEpochMs,
                nowAudioTime = nowAudioTime,
            )
            nextNoteAt = noteEndMs
        }

        while (nextNoteAt <= nowEpochMs + 250L) {
            val noteEndMs = WantsSpaceSequencer.nextNoteStartMillis(nextNoteAt)
            scheduleNotes(
                startEpochMs = nextNoteAt,
                endEpochMs = noteEndMs,
                pitch = WantsSpaceSequencer.pitchAt(nextNoteAt),
                nowEpochMs = nowEpochMs,
                nowAudioTime = nowAudioTime,
            )
            nextNoteAt = noteEndMs
        }
        nextNoteAtMs = nextNoteAt

        var nextDrumHit: Long = nextDrumHitAtMs
            ?: WantsSpaceSequencer.nextDrumHitStartMillis(nowEpochMs - 1L)
        if (nextDrumHit < nowEpochMs - 100L) {
            nextDrumHit = WantsSpaceSequencer.nextDrumHitStartMillis(nowEpochMs - 1L)
        }
        while (nextDrumHit <= nowEpochMs + 250L) {
            val phraseIndex = WantsSpaceSequencer.phraseIndexAt(nextDrumHit)
            val pulseInPhrase = WantsSpaceSequencer.pulseInPhraseAt(nextDrumHit)
            for (hit in WantsSpaceSequencer.drumHitsAt(phraseIndex, pulseInPhrase)) {
                scheduleDrumHit(hit.kind, nextDrumHit, nowEpochMs, nowAudioTime)
            }
            nextDrumHit = WantsSpaceSequencer.nextDrumHitStartMillis(nextDrumHit)
        }
        nextDrumHitAtMs = nextDrumHit
    }

    private fun scheduleNotes(
        startEpochMs: Long,
        endEpochMs: Long,
        pitch: Int,
        nowEpochMs: Long,
        nowAudioTime: Double,
    ) {
        val phraseIndex = WantsSpaceSequencer.phraseIndexAt(startEpochMs)
        val noteIndex = WantsSpaceSequencer.noteIndexAt(startEpochMs)
        scheduleNote(
            startEpochMs,
            endEpochMs,
            pitch,
            nowEpochMs,
            nowAudioTime,
            isCrystal = false,
            velocity = WantsSpaceSequencer.padVelocity(phraseIndex, noteIndex),
        )
        scheduleNote(
            startEpochMs,
            endEpochMs,
            pitch + 12,
            nowEpochMs,
            nowAudioTime,
            isCrystal = true,
            velocity = WantsSpaceSequencer.crystalVelocity(phraseIndex, noteIndex),
        )
    }

    private fun scheduleNote(
        startEpochMs: Long,
        endEpochMs: Long,
        pitch: Int,
        nowEpochMs: Long,
        nowAudioTime: Double,
        isCrystal: Boolean,
        velocity: Double,
    ) {
        val startAudioTime = nowAudioTime + (startEpochMs - nowEpochMs) / 1_000.0 + SCHEDULING_LEAD_SECONDS
        val endAudioTime = nowAudioTime + (endEpochMs - nowEpochMs) / 1_000.0 + SCHEDULING_LEAD_SECONDS
        if (endAudioTime <= startAudioTime) return

        val oscillator = context.createOscillator()
        val voiceGain = context.createGain()
        val durationSeconds = endAudioTime - startAudioTime
        val attackSeconds = min(0.07, durationSeconds / 3.0)
        val releaseSeconds = min(0.10, durationSeconds / 3.0)
        oscillator.type = if (isCrystal) "triangle" else "sine"
        oscillator.frequency.setValueAtTime(440.0 * 2.0.pow((pitch - 69) / 12.0), startAudioTime)
        voiceGain.gain.setValueAtTime(0.0001, startAudioTime)
        val peakGain = (if (isCrystal) 0.28 else 0.8) * velocity
        val sustainGain = if (isCrystal) peakGain * 0.28 else peakGain
        voiceGain.gain.linearRampToValueAtTime(peakGain, startAudioTime + attackSeconds)
        if (isCrystal) {
            val decayEnd = min(endAudioTime - releaseSeconds, startAudioTime + 0.55)
            if (decayEnd > startAudioTime + attackSeconds) {
                voiceGain.gain.exponentialRampToValueAtTime(sustainGain, decayEnd)
            }
        }
        voiceGain.gain.setValueAtTime(sustainGain, endAudioTime - releaseSeconds)
        voiceGain.gain.linearRampToValueAtTime(0.0001, endAudioTime)
        oscillator.connect(voiceGain)
        voiceGain.connect(masterGain)
        oscillator.start(startAudioTime)
        oscillator.stop(endAudioTime + 0.02)
    }

    private fun scheduleDrumHit(
        kind: WantsSpaceDrumKind,
        startEpochMs: Long,
        nowEpochMs: Long,
        nowAudioTime: Double,
    ) {
        val startAudioTime = max(
            nowAudioTime + SCHEDULING_LEAD_SECONDS,
            nowAudioTime + (startEpochMs - nowEpochMs) / 1_000.0 + SCHEDULING_LEAD_SECONDS,
        )
        if (kind == WantsSpaceDrumKind.KICK) {
            val oscillator = context.createOscillator()
            val gain = context.createGain()
            oscillator.type = "sine"
            oscillator.frequency.setValueAtTime(155.0, startAudioTime)
            oscillator.frequency.exponentialRampToValueAtTime(48.0, startAudioTime + 0.13)
            gain.gain.setValueAtTime(0.0001, startAudioTime)
            gain.gain.linearRampToValueAtTime(0.8, startAudioTime + 0.004)
            gain.gain.exponentialRampToValueAtTime(0.0001, startAudioTime + 0.24)
            oscillator.connect(gain)
            gain.connect(masterGain)
            oscillator.start(startAudioTime)
            oscillator.stop(startAudioTime + 0.25)
            return
        }

        val durationSeconds = if (kind == WantsSpaceDrumKind.SNARE) 0.14 else 0.07
        val sampleCount = (context.sampleRate as Double * durationSeconds).toInt()
        val noiseBuffer = context.createBuffer(1, sampleCount, context.sampleRate)
        val channelData = noiseBuffer.getChannelData(0)
        var seed = startEpochMs.toInt() xor (kind.ordinal * 0x45D9F3B)
        if (seed == 0) seed = 0x13579BDF
        for (index in 0 until sampleCount) {
            seed = seed * 1_664_525 + 1_013_904_223
            channelData[index] = seed.toDouble() / Int.MAX_VALUE
        }

        val source = context.createBufferSource()
        val filter = context.createBiquadFilter()
        val gain = context.createGain()
        source.buffer = noiseBuffer
        filter.type = "highpass"
        filter.frequency.setValueAtTime(if (kind == WantsSpaceDrumKind.SNARE) 1_500.0 else 7_000.0, startAudioTime)
        val peakGain = if (kind == WantsSpaceDrumKind.SNARE) 0.24 else 0.11
        gain.gain.setValueAtTime(0.0001, startAudioTime)
        gain.gain.linearRampToValueAtTime(peakGain, startAudioTime + 0.003)
        gain.gain.exponentialRampToValueAtTime(0.0001, startAudioTime + durationSeconds)
        source.connect(filter)
        filter.connect(gain)
        gain.connect(masterGain)
        source.start(startAudioTime)
        source.stop(startAudioTime + durationSeconds)
    }

    private companion object {
        const val SCHEDULING_LEAD_SECONDS = 0.05
    }
}