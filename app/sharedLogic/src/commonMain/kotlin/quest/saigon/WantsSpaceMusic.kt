package quest.saigon

data class WantsSpaceNote(
    val startPulses: Int,
    val durationPulses: Int,
    val midiNote: Int,
    val velocity: Double = 1.0,
)

enum class WantsSpaceDrumKind {
    KICK,
    SNARE,
    HAT,
}

data class WantsSpaceDrumHit(
    val startPulses: Int,
    val kind: WantsSpaceDrumKind,
    val velocity: Double = 1.0,
)

data class WantsSpacePhraseEvents(
    val pad: List<WantsSpaceNote>,
    val crystal: List<WantsSpaceNote>,
    val drums: List<WantsSpaceDrumHit>,
)

object WantsSpaceMusic {
    const val BEATS_PER_MINUTE = 72
    const val PULSES_PER_QUARTER = 24
    const val PHRASE_PULSES = 7 * PULSES_PER_QUARTER
    const val COMPLEX_PHRASE_INTERVAL = 8

    private const val PULSES_PER_MINUTE = BEATS_PER_MINUTE * PULSES_PER_QUARTER
    private const val MOTION_SECTION_PHRASES = 4
    private const val SCALE_SECTION_PHRASES = 8
    private const val LOWEST_MIDI_NOTE = 38
    private const val HIGHEST_MIDI_NOTE = 86
    private val scaleIntervals = arrayOf(
        intArrayOf(0, 3, 5, 7, 10), // Minor pentatonic
        intArrayOf(0, 2, 4, 7, 9), // Major pentatonic
        intArrayOf(0, 2, 4, 6, 9), // Lydian pentatonic
        intArrayOf(0, 2, 3, 7, 9), // Kumoi
        intArrayOf(0, 2, 3, 7, 8), // Hirajoshi
        intArrayOf(0, 2, 4, 6, 8, 10), // Whole tone
        intArrayOf(0, 2, 4, 6, 7, 9, 11), // Lydian
        intArrayOf(0, 1, 5, 7, 10), // In sen
    )
    private val motif = intArrayOf(0, 2, 1, 3, 4, 2)
    private val crystalMelodyDegrees = arrayOf(
        intArrayOf(0, 2, 4, 3, 2, 1, 2),
        intArrayOf(1, 3, 5, 4, 3, 2, 1),
        intArrayOf(2, 4, 6, 5, 4, 3, 2),
        intArrayOf(2, 1, 0, 1, 2, 1, 0),
    )

    private data class MusicalScale(
        val root: Int,
        val intervals: IntArray,
        val pitches: IntArray,
    )

    private data class CrystalRhythm(
        val start: Int,
        val duration: Int,
        val beat: Int,
        val subdivision: Int,
    )

    fun phrase(phraseIndex: Long): List<WantsSpaceNote> = padRhythm(phraseIndex).mapIndexed { noteIndex, rhythm ->
        WantsSpaceNote(
            startPulses = rhythm.first,
            durationPulses = rhythm.second,
            midiNote = midiNote(phraseIndex, noteIndex),
            velocity = melodicVelocity(phraseIndex, rhythm.first, rhythm.second),
        )
    }

    fun crystalPhrase(phraseIndex: Long): List<WantsSpaceNote> {
        val role = floorMod(phraseIndex, 4L).toInt()
        val scale = musicalScale(phraseIndex)
        val degrees = crystalMelodyDegrees[role]
        return crystalRhythm(role).map { rhythm ->
            val degree = when (role) {
                0 -> degrees[rhythm.beat]
                1 -> if (rhythm.subdivision == 0 || rhythm.beat == 6) {
                    degrees[rhythm.beat]
                } else {
                    degrees[(rhythm.beat + 1) % degrees.size]
                }
                2 -> degrees[(rhythm.beat + rhythm.subdivision) % degrees.size]
                else -> if (rhythm.beat == 6 && rhythm.subdivision == 7) {
                    degrees[rhythm.beat]
                } else {
                    degrees[(rhythm.beat + rhythm.subdivision) % degrees.size]
                }
            }
            WantsSpaceNote(
                startPulses = rhythm.start,
                durationPulses = rhythm.duration,
                midiNote = melodyPitch(scale, degree),
                velocity = melodicVelocity(phraseIndex, rhythm.start, rhythm.duration),
            )
        }
    }

    fun drumPhrase(phraseIndex: Long): List<WantsSpaceDrumHit> = buildList {
        add(WantsSpaceDrumHit(0, WantsSpaceDrumKind.KICK, 1.0))
        add(WantsSpaceDrumHit(4 * PULSES_PER_QUARTER, WantsSpaceDrumKind.KICK, 0.86))
        add(WantsSpaceDrumHit(3 * PULSES_PER_QUARTER, WantsSpaceDrumKind.SNARE, 0.9))
        add(WantsSpaceDrumHit(6 * PULSES_PER_QUARTER, WantsSpaceDrumKind.SNARE, 0.88))
        for (beat in 0..6) {
            add(WantsSpaceDrumHit(beat * PULSES_PER_QUARTER, WantsSpaceDrumKind.HAT, 0.68))
        }

        when (floorMod(phraseIndex, 4L).toInt()) {
            0 -> {
                for (beat in listOf(1, 3, 5)) {
                    add(WantsSpaceDrumHit(beat * PULSES_PER_QUARTER + 16, WantsSpaceDrumKind.HAT, 0.52))
                }
                add(WantsSpaceDrumHit(3 * PULSES_PER_QUARTER + 16, WantsSpaceDrumKind.SNARE, 0.34))
            }
            1 -> {
                for (beat in listOf(1, 4, 6)) {
                    add(WantsSpaceDrumHit(beat * PULSES_PER_QUARTER + 8, WantsSpaceDrumKind.HAT, 0.5))
                    add(WantsSpaceDrumHit(beat * PULSES_PER_QUARTER + 16, WantsSpaceDrumKind.HAT, 0.44))
                }
                add(WantsSpaceDrumHit(3 * PULSES_PER_QUARTER + 8, WantsSpaceDrumKind.SNARE, 0.3))
            }
            2 -> {
                for (beat in listOf(1, 3, 5)) {
                    for (offset in listOf(6, 12, 18)) {
                        add(WantsSpaceDrumHit(beat * PULSES_PER_QUARTER + offset, WantsSpaceDrumKind.HAT, 0.42))
                    }
                }
                add(WantsSpaceDrumHit(3 * PULSES_PER_QUARTER + 6, WantsSpaceDrumKind.SNARE, 0.3))
                add(WantsSpaceDrumHit(6 * PULSES_PER_QUARTER + 6, WantsSpaceDrumKind.SNARE, 0.28))
            }
            else -> {
                for (pulse in 6 * PULSES_PER_QUARTER..PHRASE_PULSES - 3 step 3) {
                    add(WantsSpaceDrumHit(pulse, WantsSpaceDrumKind.HAT, 0.38))
                }
                add(WantsSpaceDrumHit(6 * PULSES_PER_QUARTER + 6, WantsSpaceDrumKind.SNARE, 0.32))
                if (isComplexPhrase(phraseIndex)) {
                    add(WantsSpaceDrumHit(156, WantsSpaceDrumKind.SNARE, 0.26))
                    add(WantsSpaceDrumHit(162, WantsSpaceDrumKind.SNARE, 0.3))
                }
            }
        }
    }.sortedWith(compareBy<WantsSpaceDrumHit> { it.startPulses }.thenBy { it.kind.ordinal })

    fun allEvents(phraseIndex: Long): WantsSpacePhraseEvents = WantsSpacePhraseEvents(
        pad = phrase(phraseIndex),
        crystal = crystalPhrase(phraseIndex),
        drums = drumPhrase(phraseIndex),
    )

    fun phraseIndexAt(epochMillis: Long): Long =
        floorDiv(absolutePulseAt(epochMillis), PHRASE_PULSES.toLong())

    fun pulseInPhraseAt(epochMillis: Long): Int =
        floorMod(absolutePulseAt(epochMillis), PHRASE_PULSES.toLong()).toInt()

    fun absolutePulseAt(epochMillis: Long): Long =
        floorDiv(epochMillis * PULSES_PER_MINUTE, 60_000L)

    fun phraseStartMillis(phraseIndex: Long): Long =
        pulseStartMillis(phraseIndex * PHRASE_PULSES)

    fun pulseStartMillis(absolutePulse: Long): Long =
        (absolutePulse * 60_000L + PULSES_PER_MINUTE - 1) / PULSES_PER_MINUTE

    fun noteStartMillis(phraseIndex: Long, note: WantsSpaceNote): Long =
        pulseStartMillis(phraseIndex * PHRASE_PULSES + note.startPulses)

    fun noteEndMillis(phraseIndex: Long, note: WantsSpaceNote): Long =
        pulseStartMillis(phraseIndex * PHRASE_PULSES + note.startPulses + note.durationPulses)

    fun padNoteAt(epochMillis: Long): WantsSpaceNote? =
        noteAt(phrase(phraseIndexAt(epochMillis)), pulseInPhraseAt(epochMillis))

    fun crystalNoteAt(epochMillis: Long): WantsSpaceNote? =
        noteAt(crystalPhrase(phraseIndexAt(epochMillis)), pulseInPhraseAt(epochMillis))

    fun padNoteIndexAt(epochMillis: Long): Int =
        noteIndexAt(phrase(phraseIndexAt(epochMillis)), pulseInPhraseAt(epochMillis))

    fun crystalNoteIndexAt(epochMillis: Long): Int =
        noteIndexAt(crystalPhrase(phraseIndexAt(epochMillis)), pulseInPhraseAt(epochMillis))

    fun nextPadNoteStartMillis(epochMillis: Long): Long =
        nextLayerNoteStartMillis(epochMillis, isCrystal = false)

    fun nextCrystalNoteStartMillis(epochMillis: Long): Long =
        nextLayerNoteStartMillis(epochMillis, isCrystal = true)

    fun padVelocity(phraseIndex: Long, noteIndex: Int): Double = phrase(phraseIndex)
        .getOrNull(noteIndex)?.velocity ?: 0.0

    fun crystalVelocity(phraseIndex: Long, noteIndex: Int): Double = crystalPhrase(phraseIndex)
        .getOrNull(noteIndex)?.velocity ?: 0.0

    fun drumHitsAt(phraseIndex: Long, pulseInPhrase: Int): List<WantsSpaceDrumHit> =
        drumPhrase(phraseIndex).filter { it.startPulses == pulseInPhrase }

    fun nextDrumHitStartMillis(epochMillis: Long): Long {
        var absolutePulse = absolutePulseAt(epochMillis) + 1
        while (true) {
            val phraseIndex = floorDiv(absolutePulse, PHRASE_PULSES.toLong())
            val pulseInPhrase = floorMod(absolutePulse, PHRASE_PULSES.toLong()).toInt()
            if (drumHitsAt(phraseIndex, pulseInPhrase).isNotEmpty()) return pulseStartMillis(absolutePulse)
            absolutePulse++
        }
    }

    fun pitchAt(epochMillis: Long): Int {
        return padNoteAt(epochMillis)?.midiNote ?: midiNote(phraseIndexAt(epochMillis), 0)
    }

    fun crystalPitchAt(epochMillis: Long): Int {
        return crystalNoteAt(epochMillis)?.midiNote ?: -1
    }

    fun nextNoteStartMillis(epochMillis: Long): Long = nextPadNoteStartMillis(epochMillis)

    private fun midiNote(phraseIndex: Long, noteIndex: Int): Int {
        val sectionIndex = floorDiv(phraseIndex, MOTION_SECTION_PHRASES.toLong())
        val seed = sectionSeed(sectionIndex)
        val scale = musicalScale(phraseIndex)
        return when (floorMod(seed.toLong(), 3L).toInt()) {
            0 -> climbingNote(phraseIndex, noteIndex, seed, scale, direction = 1)
            1 -> climbingNote(phraseIndex, noteIndex, seed, scale, direction = -1)
            else -> jumpingNote(phraseIndex, noteIndex, seed, scale)
        }
    }

    private fun climbingNote(
        phraseIndex: Long,
        noteIndex: Int,
        seed: Int,
        scale: MusicalScale,
        direction: Int,
    ): Int {
        val sectionIndex = floorDiv(phraseIndex, MOTION_SECTION_PHRASES.toLong())
        val sectionStartPhrase = sectionIndex * MOTION_SECTION_PHRASES
        var noteOrdinal = noteIndex
        repeat(floorMod(phraseIndex, MOTION_SECTION_PHRASES.toLong()).toInt()) { offset ->
            noteOrdinal += noteCount(sectionStartPhrase + offset)
        }

        val lastScaleIndex = scale.pitches.lastIndex
        val initialDegree = floorMod((seed ushr 2).toLong(), scale.pitches.size.toLong())
        val walkPeriod = lastScaleIndex * 2
        val walkPosition = floorMod(
            initialDegree + direction * noteOrdinal.toLong(),
            walkPeriod.toLong(),
        ).toInt()
        val scaleIndex = if (walkPosition <= lastScaleIndex) walkPosition else walkPeriod - walkPosition
        return scale.pitches[scaleIndex]
    }

    private fun jumpingNote(phraseIndex: Long, noteIndex: Int, seed: Int, scale: MusicalScale): Int {
        val degree = (motif[noteIndex % motif.size] +
            floorMod(seed.toLong(), scale.intervals.size.toLong()).toInt()) % scale.intervals.size
        val pitchClass = scale.intervals[degree]
        val octavePitches = scale.pitches.filter {
            floorMod((it - scale.root).toLong(), 12L).toInt() == pitchClass
        }
        var octaveSeed = seed xor (phraseIndex.toInt() * 1_013_904_223) xor (noteIndex * 0x45D9F3B)
        octaveSeed = octaveSeed xor (octaveSeed ushr 16)
        val octave = floorMod((octaveSeed ushr 1).toLong(), octavePitches.size.toLong()).toInt()
        return octavePitches[octave]
    }

    private fun padRhythm(phraseIndex: Long): List<Pair<Int, Int>> {
        if (isComplexPhrase(phraseIndex)) {
            return buildList {
                add(0 to 72)
                for (pulse in 72..90 step 6) add(pulse to 6)
                add(96 to 72)
            }
        }
        return when (floorMod(phraseIndex, 4L).toInt()) {
            0 -> listOf(0 to 72, 72 to 12, 84 to 12, 96 to 72)
            1 -> listOf(0 to 72, 72 to 16, 88 to 8, 96 to 72)
            2 -> listOf(0 to 72, 72 to 8, 80 to 8, 88 to 8, 96 to 72)
            else -> buildList {
                add(0 to 72)
                for (pulse in 72..93 step 3) add(pulse to 3)
                add(96 to 72)
            }
        }
    }

    private fun crystalRhythm(role: Int): List<CrystalRhythm> = buildList {
        when (role) {
            0 -> for (beat in 0..6) {
                if (beat != 3) add(CrystalRhythm(beat * PULSES_PER_QUARTER, PULSES_PER_QUARTER, beat, 0))
            }
            1 -> for (beat in 0..6) {
                if (beat != 3) {
                    add(CrystalRhythm(beat * PULSES_PER_QUARTER, 16, beat, 0))
                    add(CrystalRhythm(beat * PULSES_PER_QUARTER + 16, 8, beat, 1))
                }
            }
            2 -> for (beat in 0..6) {
                if (beat == 3) continue
                if (beat == 2 || beat == 5) {
                    for (subdivision in 0..2) {
                        add(CrystalRhythm(
                            beat * PULSES_PER_QUARTER + subdivision * 8,
                            8,
                            beat,
                            subdivision,
                        ))
                    }
                } else {
                    add(CrystalRhythm(beat * PULSES_PER_QUARTER, PULSES_PER_QUARTER, beat, 0))
                }
            }
            else -> {
                add(CrystalRhythm(0, 24, 0, 0))
                for (subdivision in 0..3) add(CrystalRhythm(24 + subdivision * 6, 6, 1, subdivision))
                add(CrystalRhythm(48, 24, 2, 0))
                add(CrystalRhythm(96, 24, 4, 0))
                add(CrystalRhythm(120, 12, 5, 0))
                add(CrystalRhythm(132, 12, 5, 1))
                for (subdivision in 0..7) add(CrystalRhythm(144 + subdivision * 3, 3, 6, subdivision))
            }
        }
    }

    private fun melodyPitch(scale: MusicalScale, degree: Int): Int {
        val octave = degree / scale.intervals.size
        val interval = scale.intervals[degree % scale.intervals.size]
        var pitch = scale.root + 36 + octave * 12 + interval
        while (pitch < 67) pitch += 12
        while (pitch > 91) pitch -= 12
        return pitch
    }

    private fun melodicVelocity(phraseIndex: Long, startPulse: Int, duration: Int): Double {
        var seed = phraseIndex.toInt() * 1_664_525 + startPulse * 1_013_904_223
        seed = seed xor (seed ushr 16)
        val variation = (seed ushr 1 and 0x07) / 100.0
        val accent = when {
            duration <= 3 -> 0.58
            duration <= 8 -> 0.66
            duration <= 16 -> 0.74
            else -> 0.82
        }
        val downbeat = if (startPulse % PULSES_PER_QUARTER == 0) 0.12 else 0.0
        return (accent + downbeat + variation).coerceIn(0.55, 1.0)
    }

    private fun noteAt(notes: List<WantsSpaceNote>, pulseInPhrase: Int): WantsSpaceNote? =
        notes.firstOrNull { pulseInPhrase in it.startPulses until it.startPulses + it.durationPulses }

    private fun noteIndexAt(notes: List<WantsSpaceNote>, pulseInPhrase: Int): Int =
        notes.indexOfFirst { pulseInPhrase in it.startPulses until it.startPulses + it.durationPulses }

    private fun nextLayerNoteStartMillis(epochMillis: Long, isCrystal: Boolean): Long {
        var absolutePulse = absolutePulseAt(epochMillis) + 1
        while (true) {
            val phraseIndex = floorDiv(absolutePulse, PHRASE_PULSES.toLong())
            val pulseInPhrase = floorMod(absolutePulse, PHRASE_PULSES.toLong()).toInt()
            val notes = if (isCrystal) crystalPhrase(phraseIndex) else phrase(phraseIndex)
            if (notes.any { it.startPulses == pulseInPhrase }) return pulseStartMillis(absolutePulse)
            absolutePulse++
        }
    }

    private fun musicalScale(phraseIndex: Long): MusicalScale {
        val sectionIndex = floorDiv(phraseIndex, SCALE_SECTION_PHRASES.toLong())
        val seed = sectionSeed(sectionIndex)
        val intervals = scaleIntervals[floorMod(seed.toLong(), scaleIntervals.size.toLong()).toInt()]
        val root = 36 + floorMod((seed ushr 3).toLong(), 12L).toInt()
        val pitches = buildList {
            for (octave in -1..5) {
                for (interval in intervals) {
                    val pitch = root + octave * 12 + interval
                    if (pitch in LOWEST_MIDI_NOTE..HIGHEST_MIDI_NOTE) add(pitch)
                }
            }
        }.distinct().sorted().toIntArray()
        return MusicalScale(root, intervals, pitches)
    }

    private fun sectionSeed(sectionIndex: Long): Int {
        var seed = (sectionIndex xor (sectionIndex ushr 32)).toInt() * 1_664_525 + 1_013_904_223
        seed = seed xor (seed ushr 16)
        return seed
    }

    private fun noteCount(phraseIndex: Long): Int = padRhythm(phraseIndex).size

    private fun isComplexPhrase(phraseIndex: Long): Boolean =
        floorMod(phraseIndex, COMPLEX_PHRASE_INTERVAL.toLong()) == COMPLEX_PHRASE_INTERVAL - 1L


    private fun floorDiv(value: Long, divisor: Long): Long =
        if (value >= 0) value / divisor else (value - divisor + 1) / divisor

    private fun floorMod(value: Long, divisor: Long): Long =
        ((value % divisor) + divisor) % divisor
}