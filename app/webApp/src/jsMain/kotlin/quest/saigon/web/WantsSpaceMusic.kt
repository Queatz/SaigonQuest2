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
    private var nextPadNoteAtMs: Long? = null
    private var nextCrystalNoteAtMs: Long? = null
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
                    nextPadNoteAtMs = null
                    nextCrystalNoteAtMs = null
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
        var nextPadNoteAt = nextPadNoteAtMs
        var nextCrystalNoteAt = nextCrystalNoteAtMs

        if (nextPadNoteAt == null || nextCrystalNoteAt == null) {
            masterGain.gain.setValueAtTime(0.0001, nowAudioTime)
            masterGain.gain.linearRampToValueAtTime(0.07, nowAudioTime + 2.0)
            scheduleCurrentLayer(isCrystal = false, nowEpochMs, nowAudioTime)
            scheduleCurrentLayer(isCrystal = true, nowEpochMs, nowAudioTime)
            nextPadNoteAt = WantsSpaceSequencer.nextPadNoteStartMillis(nowEpochMs)
            nextCrystalNoteAt = WantsSpaceSequencer.nextCrystalNoteStartMillis(nowEpochMs)
        }

        var padCursor = checkNotNull(nextPadNoteAt)
        var crystalCursor = checkNotNull(nextCrystalNoteAt)

        if (padCursor < nowEpochMs - 100L) {
            scheduleCurrentLayer(isCrystal = false, nowEpochMs, nowAudioTime)
            padCursor = WantsSpaceSequencer.nextPadNoteStartMillis(nowEpochMs)
        }
        if (crystalCursor < nowEpochMs - 100L) {
            scheduleCurrentLayer(isCrystal = true, nowEpochMs, nowAudioTime)
            crystalCursor = WantsSpaceSequencer.nextCrystalNoteStartMillis(nowEpochMs)
        }

        while (padCursor <= nowEpochMs + 250L) {
            scheduleLayerNote(padCursor, isCrystal = false, nowEpochMs, nowAudioTime)
            padCursor = WantsSpaceSequencer.nextPadNoteStartMillis(padCursor)
        }
        while (crystalCursor <= nowEpochMs + 250L) {
            scheduleLayerNote(crystalCursor, isCrystal = true, nowEpochMs, nowAudioTime)
            crystalCursor = WantsSpaceSequencer.nextCrystalNoteStartMillis(crystalCursor)
        }
        nextPadNoteAtMs = padCursor
        nextCrystalNoteAtMs = crystalCursor

        var nextDrumHit: Long = nextDrumHitAtMs
            ?: WantsSpaceSequencer.nextDrumHitStartMillis(nowEpochMs - 1L)
        if (nextDrumHit < nowEpochMs - 100L) {
            nextDrumHit = WantsSpaceSequencer.nextDrumHitStartMillis(nowEpochMs - 1L)
        }
        while (nextDrumHit <= nowEpochMs + 250L) {
            val phraseIndex = WantsSpaceSequencer.phraseIndexAt(nextDrumHit)
            val pulseInPhrase = WantsSpaceSequencer.pulseInPhraseAt(nextDrumHit)
            for (hit in WantsSpaceSequencer.drumHitsAt(phraseIndex, pulseInPhrase)) {
                scheduleDrumHit(hit.kind, hit.velocity, nextDrumHit, nowEpochMs, nowAudioTime)
            }
            nextDrumHit = WantsSpaceSequencer.nextDrumHitStartMillis(nextDrumHit)
        }
        nextDrumHitAtMs = nextDrumHit
    }

    private fun scheduleCurrentLayer(
        isCrystal: Boolean,
        nowEpochMs: Long,
        nowAudioTime: Double,
    ) {
        val note = if (isCrystal) WantsSpaceSequencer.crystalNoteAt(nowEpochMs)
        else WantsSpaceSequencer.padNoteAt(nowEpochMs)
        if (note != null) {
            val phraseIndex = WantsSpaceSequencer.phraseIndexAt(nowEpochMs)
            val noteEndMs = WantsSpaceSequencer.noteEndMillis(phraseIndex, note)
            scheduleNote(
                maxOf(WantsSpaceSequencer.noteStartMillis(phraseIndex, note), nowEpochMs),
                noteEndMs,
                note.midiNote,
                nowEpochMs,
                nowAudioTime,
                isCrystal,
                note.velocity,
            )
        }
    }

    private fun scheduleLayerNote(
        startEpochMs: Long,
        isCrystal: Boolean,
        nowEpochMs: Long,
        nowAudioTime: Double,
    ) {
        val phraseIndex = WantsSpaceSequencer.phraseIndexAt(startEpochMs)
        val note = if (isCrystal) {
            WantsSpaceSequencer.crystalPhrase(phraseIndex).firstOrNull {
                it.startPulses == WantsSpaceSequencer.pulseInPhraseAt(startEpochMs)
            }
        } else {
            WantsSpaceSequencer.phrase(phraseIndex).firstOrNull {
                it.startPulses == WantsSpaceSequencer.pulseInPhraseAt(startEpochMs)
            }
        } ?: return
        scheduleNote(
            startEpochMs,
            WantsSpaceSequencer.noteEndMillis(phraseIndex, note),
            note.midiNote,
            nowEpochMs,
            nowAudioTime,
            isCrystal,
            note.velocity,
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
        val frequency = 440.0 * 2.0.pow((pitch - 69) / 12.0)
        oscillator.type = "sine"
        oscillator.frequency.setValueAtTime(frequency, startAudioTime)
        voiceGain.gain.setValueAtTime(0.0001, startAudioTime)
        val peakGain = (if (isCrystal) 0.58 else 0.8) * velocity
        val sustainGain = if (isCrystal) peakGain * 0.08 else peakGain
        voiceGain.gain.linearRampToValueAtTime(peakGain, startAudioTime + attackSeconds)
        if (isCrystal) {
            val partialOscillator = context.createOscillator()
            val partialGain = context.createGain()
            partialOscillator.type = "sine"
            partialOscillator.frequency.setValueAtTime(frequency * 2.76, startAudioTime)
            partialGain.gain.setValueAtTime(0.22, startAudioTime)
            partialOscillator.connect(partialGain)
            partialGain.connect(voiceGain)
            partialOscillator.start(startAudioTime)
            partialOscillator.stop(endAudioTime + 0.02)
            val decayEnd = min(endAudioTime - releaseSeconds, startAudioTime + 0.32)
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
        velocity: Double,
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
            gain.gain.linearRampToValueAtTime(0.8 * velocity, startAudioTime + 0.004)
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
        val peakGain = (if (kind == WantsSpaceDrumKind.SNARE) 0.24 else 0.11) * velocity
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