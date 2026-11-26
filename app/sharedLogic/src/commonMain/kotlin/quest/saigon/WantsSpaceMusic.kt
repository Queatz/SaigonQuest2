package quest.saigon

data class WantsSpaceNote(
    val startSixteenths: Int,
    val durationSixteenths: Int,
    val midiNote: Int,
)

enum class WantsSpaceDrumKind {
    KICK,
    SNARE,
    HAT,
}

data class WantsSpaceDrumHit(
    val startSixteenths: Int,
    val kind: WantsSpaceDrumKind,
)

data class WantsSpacePhraseEvents(
    val pad: List<WantsSpaceNote>,
    val crystal: List<WantsSpaceNote>,
    val drums: List<WantsSpaceDrumHit>,
)

object WantsSpaceMusic {
    const val BEATS_PER_MINUTE = 72
    const val PHRASE_SIXTEENTHS = 28
    const val COMPLEX_PHRASE_INTERVAL = 8

    private const val PULSES_PER_MINUTE = BEATS_PER_MINUTE * 4
    private val pentatonicNotes = intArrayOf(50, 53, 55, 57, 60, 62)
    private val motif = intArrayOf(0, 2, 1, 3, 4, 2)
    private val regularNoteBoundaries = intArrayOf(0, 12, 14, 16)
    private val complexNoteBoundaries = intArrayOf(0, 12, 13, 14, 15, 16)

    fun phrase(phraseIndex: Long): List<WantsSpaceNote> {
        val startsAndLengths = if (isComplexPhrase(phraseIndex)) {
            intArrayOf(0, 12, 12, 1, 13, 1, 14, 1, 15, 1, 16, 12)
        } else {
            intArrayOf(0, 12, 12, 2, 14, 2, 16, 12)
        }
        val count = startsAndLengths.size / 2
        return List(count) { index ->
            val start = startsAndLengths[index * 2]
            val duration = startsAndLengths[index * 2 + 1]
            WantsSpaceNote(start, duration, midiNote(phraseIndex, index))
        }
    }

    fun crystalPhrase(phraseIndex: Long): List<WantsSpaceNote> =
        phrase(phraseIndex).map { it.copy(midiNote = it.midiNote + 12) }

    fun drumPhrase(phraseIndex: Long): List<WantsSpaceDrumHit> = buildList {
        add(WantsSpaceDrumHit(0, WantsSpaceDrumKind.KICK))
        add(WantsSpaceDrumHit(16, WantsSpaceDrumKind.KICK))
        for (start in 0..24 step 4) {
            add(WantsSpaceDrumHit(start, WantsSpaceDrumKind.HAT))
        }
        add(WantsSpaceDrumHit(12, WantsSpaceDrumKind.SNARE))
        add(WantsSpaceDrumHit(14, WantsSpaceDrumKind.SNARE))
        if (isComplexPhrase(phraseIndex)) {
            add(WantsSpaceDrumHit(13, WantsSpaceDrumKind.SNARE))
            add(WantsSpaceDrumHit(15, WantsSpaceDrumKind.SNARE))
        }
    }.sortedWith(compareBy<WantsSpaceDrumHit> { it.startSixteenths }.thenBy { it.kind.ordinal })

    fun allEvents(phraseIndex: Long): WantsSpacePhraseEvents = WantsSpacePhraseEvents(
        pad = phrase(phraseIndex),
        crystal = crystalPhrase(phraseIndex),
        drums = drumPhrase(phraseIndex),
    )

    fun phraseIndexAt(epochMillis: Long): Long =
        floorDiv(absoluteSixteenthAt(epochMillis), PHRASE_SIXTEENTHS.toLong())

    fun pulseInPhraseAt(epochMillis: Long): Int =
        floorMod(absoluteSixteenthAt(epochMillis), PHRASE_SIXTEENTHS.toLong()).toInt()

    fun absoluteSixteenthAt(epochMillis: Long): Long =
        floorDiv(epochMillis * PULSES_PER_MINUTE, 60_000L)

    fun phraseStartMillis(phraseIndex: Long): Long =
        sixteenthStartMillis(phraseIndex * PHRASE_SIXTEENTHS)

    fun sixteenthStartMillis(absoluteSixteenth: Long): Long =
        pulseStartMillis(absoluteSixteenth)

    fun currentNoteStartMillis(epochMillis: Long): Long {
        val absoluteSixteenth = absoluteSixteenthAt(epochMillis)
        val phraseIndex = floorDiv(absoluteSixteenth, PHRASE_SIXTEENTHS.toLong())
        val pulseInPhrase = floorMod(absoluteSixteenth, PHRASE_SIXTEENTHS.toLong()).toInt()
        val boundaries = noteBoundaries(phraseIndex)
        val noteStart = boundaries.last { it <= pulseInPhrase }
        return pulseStartMillis(phraseIndex * PHRASE_SIXTEENTHS + noteStart)
    }

    fun noteIndexAt(epochMillis: Long): Int {
        val absoluteSixteenth = absoluteSixteenthAt(epochMillis)
        val phraseIndex = floorDiv(absoluteSixteenth, PHRASE_SIXTEENTHS.toLong())
        val pulseInPhrase = floorMod(absoluteSixteenth, PHRASE_SIXTEENTHS.toLong()).toInt()
        return noteBoundaries(phraseIndex).indexOfLast { it <= pulseInPhrase }
    }

    fun padVelocity(phraseIndex: Long, noteIndex: Int): Double = noteVelocity(
        phraseIndex,
        noteIndex,
        isCrystal = false,
    )

    fun crystalVelocity(phraseIndex: Long, noteIndex: Int): Double = noteVelocity(
        phraseIndex,
        noteIndex,
        isCrystal = true,
    )

    fun drumHitsAt(phraseIndex: Long, pulseInPhrase: Int): List<WantsSpaceDrumHit> =
        drumPhrase(phraseIndex).filter { it.startSixteenths == pulseInPhrase }

    fun nextDrumHitStartMillis(epochMillis: Long): Long {
        var absoluteSixteenth = absoluteSixteenthAt(epochMillis)
        if (pulseStartMillis(absoluteSixteenth) <= epochMillis) absoluteSixteenth++

        while (true) {
            val phraseIndex = floorDiv(absoluteSixteenth, PHRASE_SIXTEENTHS.toLong())
            val pulseInPhrase = floorMod(absoluteSixteenth, PHRASE_SIXTEENTHS.toLong()).toInt()
            if (drumHitsAt(phraseIndex, pulseInPhrase).isNotEmpty()) {
                return pulseStartMillis(absoluteSixteenth)
            }
            absoluteSixteenth++
        }
    }

    fun pitchAt(epochMillis: Long): Int {
        val absolutePulse = absoluteSixteenthAt(epochMillis)
        val phraseIndex = floorDiv(absolutePulse, PHRASE_SIXTEENTHS.toLong())
        val pulseInPhrase = floorMod(absolutePulse, PHRASE_SIXTEENTHS.toLong()).toInt()
        val noteIndex = when {
            pulseInPhrase < 12 -> 0
            isComplexPhrase(phraseIndex) && pulseInPhrase < 16 -> pulseInPhrase - 11
            pulseInPhrase < 14 -> 1
            pulseInPhrase < 16 -> 2
            else -> if (isComplexPhrase(phraseIndex)) 5 else 3
        }
        return midiNote(phraseIndex, noteIndex)
    }

    fun nextNoteStartMillis(epochMillis: Long): Long {
        val absolutePulse = absoluteSixteenthAt(epochMillis)
        val phraseIndex = floorDiv(absolutePulse, PHRASE_SIXTEENTHS.toLong())
        val pulseInPhrase = floorMod(absolutePulse, PHRASE_SIXTEENTHS.toLong()).toInt()
        val boundaries = noteBoundaries(phraseIndex)
        val nextBoundary = boundaries.firstOrNull { it > pulseInPhrase } ?: PHRASE_SIXTEENTHS
        return pulseStartMillis(phraseIndex * PHRASE_SIXTEENTHS + nextBoundary)
    }

    private fun midiNote(phraseIndex: Long, noteIndex: Int): Int {
        var seed = phraseIndex.toInt() * 1_664_525 + 1_013_904_223
        seed = seed xor (seed ushr 16)
        val root = (seed ushr 1) % pentatonicNotes.size
        val degree = (root + motif[noteIndex]) % pentatonicNotes.size
        return pentatonicNotes[degree]
    }

    private fun noteVelocity(phraseIndex: Long, noteIndex: Int, isCrystal: Boolean): Double {
        val note = phrase(phraseIndex)[noteIndex]
        var seed = phraseIndex.toInt() * 1_664_525 + noteIndex * 1_013_904_223
        seed = seed xor (seed ushr 16)
        val variation = (seed ushr 1 and 0x0F) / 15.0 * 0.18
        val accent = when {
            note.durationSixteenths >= 8 -> if (isCrystal) 0.70 else 0.82
            note.durationSixteenths >= 2 -> if (isCrystal) 0.82 else 0.70
            else -> if (isCrystal) 0.76 else 0.68
        }
        return accent + if (isCrystal) 0.18 - variation else variation
    }

    private fun isComplexPhrase(phraseIndex: Long): Boolean =
        floorMod(phraseIndex, COMPLEX_PHRASE_INTERVAL.toLong()) == COMPLEX_PHRASE_INTERVAL - 1L

    private fun noteBoundaries(phraseIndex: Long): IntArray =
        if (isComplexPhrase(phraseIndex)) complexNoteBoundaries else regularNoteBoundaries

    private fun pulseStartMillis(absolutePulse: Long): Long =
        (absolutePulse * 60_000L + PULSES_PER_MINUTE - 1) / PULSES_PER_MINUTE

    private fun floorDiv(value: Long, divisor: Long): Long =
        if (value >= 0) value / divisor else (value - divisor + 1) / divisor

    private fun floorMod(value: Long, divisor: Long): Long =
        ((value % divisor) + divisor) % divisor
}