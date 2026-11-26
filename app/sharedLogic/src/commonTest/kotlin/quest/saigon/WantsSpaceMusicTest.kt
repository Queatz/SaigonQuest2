package quest.saigon

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WantsSpaceMusicTest {
    @Test
    fun phraseIsDeterministicAndPitchesStayWithinFourOctaves() {
        for (phraseIndex in listOf(0L, 1L, 7L, 8L, 123_456L)) {
            val first = WantsSpaceMusic.phrase(phraseIndex)
            assertEquals(first, WantsSpaceMusic.phrase(phraseIndex))
            assertTrue(first.all { it.midiNote in 38..86 })
        }
    }

    @Test
    fun melodySometimesWalksAndSometimesJumpsAcrossOctaves() {
        val notes = (0L until 80L).flatMap { phraseIndex ->
            WantsSpaceMusic.phrase(phraseIndex).map { it.midiNote }
        }
        val steps = notes.zipWithNext { first, second -> second - first }
        val gradualRuns = steps.windowed(3).filter { run ->
            run.all { it in -5..5 } && (run.all { it > 0 } || run.all { it < 0 })
        }

        assertTrue(notes.all { it in 38..86 })
        assertTrue((notes.maxOrNull() ?: 0) - (notes.minOrNull() ?: 0) >= 36)
        assertTrue(gradualRuns.isNotEmpty())
        assertTrue(steps.any { abs(it) >= 12 })
    }

    @Test
    fun melodyUsesDifferentScalesAcrossTimeLockedSections() {
        val scaleSignatures = (0L until 16L).map { sectionIndex ->
            (sectionIndex * 8 until sectionIndex * 8 + 8)
                .flatMap { WantsSpaceMusic.phrase(it).map { note -> note.midiNote % 12 } }
                .toSet()
        }

        assertTrue(scaleSignatures.distinct().size >= 4)
        val repeatedSignature = (16L until 24L)
            .flatMap { WantsSpaceMusic.phrase(it).map { note -> note.midiNote % 12 } }
            .toSet()
        assertEquals(scaleSignatures[2], repeatedSignature)
    }

    @Test
    fun regularPhraseUsesDottedHalfEighthEighthDottedHalf() {
        val notes = WantsSpaceMusic.phrase(0L)

        assertEquals(listOf(0, 72, 84, 96), notes.map { it.startPulses })
        assertEquals(listOf(72, 12, 12, 72), notes.map { it.durationPulses })
        assertEquals(WantsSpaceMusic.PHRASE_PULSES, notes.sumOf { it.durationPulses })
    }

    @Test
    fun padPhraseAddsSwingTripletSixteenthAndThirtySecondVariations() {
        val phrases = (0L until 8L).map { WantsSpaceMusic.phrase(it) }

        assertTrue(phrases.flatten().any { it.durationPulses == 16 })
        assertTrue(phrases.flatten().any { it.durationPulses == 8 })
        assertTrue(phrases.flatten().any { it.durationPulses == 6 })
        assertTrue(phrases.flatten().any { it.durationPulses == 3 })
        assertTrue(phrases.all { phrase -> phrase.sumOf { it.durationPulses } == WantsSpaceMusic.PHRASE_PULSES })
    }

    @Test
    fun occasionalComplexPhraseAddsSixteenthFillWithoutChangingPhraseLength() {
        val notes = WantsSpaceMusic.phrase(7L)

        assertEquals(listOf(0, 72, 78, 84, 90, 96), notes.map { it.startPulses })
        assertEquals(listOf(72, 6, 6, 6, 6, 72), notes.map { it.durationPulses })
        assertEquals(WantsSpaceMusic.PHRASE_PULSES, notes.sumOf { it.durationPulses })
    }

    @Test
    fun crystalPhraseRepeatsAndDevelopsItsQuestionAnswerMotif() {
        val firstStatement = WantsSpaceMusic.crystalPhrase(0L)
        val repeatedStatement = WantsSpaceMusic.crystalPhrase(4L)
        val answer = WantsSpaceMusic.crystalPhrase(3L)

        assertEquals(
            firstStatement.map { Triple(it.startPulses, it.durationPulses, it.midiNote) },
            repeatedStatement.map { Triple(it.startPulses, it.durationPulses, it.midiNote) },
        )
        assertEquals(answer.first { it.startPulses == 48 }.midiNote, answer.last().midiNote)
        assertTrue(firstStatement.map { it.startPulses } != WantsSpaceMusic.phrase(0L).map { it.startPulses })
        assertTrue(firstStatement.all { it.midiNote in 67..91 })
    }

    @Test
    fun crystalUsesTripletsSixteenthsThirtySecondsAndIndependentTiming() {
        val crystalPhrases = (0L until 4L).map { WantsSpaceMusic.crystalPhrase(it) }

        assertTrue(crystalPhrases.flatten().any { it.durationPulses == 16 || it.durationPulses == 8 })
        assertTrue(crystalPhrases.flatten().any { it.durationPulses == 6 })
        assertTrue(crystalPhrases.flatten().any { it.durationPulses == 3 })
        assertEquals(834L, WantsSpaceMusic.nextCrystalNoteStartMillis(0L))
        assertEquals(2_500L, WantsSpaceMusic.nextPadNoteStartMillis(0L))
        assertEquals(-1, WantsSpaceMusic.crystalPitchAt(2_500L))
    }

    @Test
    fun drumPatternIsDeterministicAndAddsComplexPhraseFill() {
        val regular = WantsSpaceMusic.drumPhrase(0L)
        val complex = WantsSpaceMusic.drumPhrase(7L)

        assertEquals(regular, WantsSpaceMusic.drumPhrase(0L))
        assertEquals(listOf(0, 24, 48, 72, 96, 120, 144), regular
            .filter { it.kind == WantsSpaceDrumKind.HAT }
            .filter { it.startPulses % 24 == 0 }
            .map { it.startPulses })
        assertTrue(regular.any { it.kind == WantsSpaceDrumKind.HAT && it.startPulses == 40 })
        assertEquals(listOf(72, 88, 144), regular
            .filter { it.kind == WantsSpaceDrumKind.SNARE }
            .map { it.startPulses })
        assertTrue(complex.any { it.kind == WantsSpaceDrumKind.SNARE && it.velocity < 0.5 })
        assertEquals(listOf(72, 144, 150, 156, 162), complex
            .filter { it.kind == WantsSpaceDrumKind.SNARE }
            .map { it.startPulses })
        assertTrue(WantsSpaceMusic.drumPhrase(1L).any {
            it.kind == WantsSpaceDrumKind.HAT && it.startPulses % 24 == 8
        })
        assertTrue(WantsSpaceMusic.drumPhrase(2L).any {
            it.kind == WantsSpaceDrumKind.HAT && it.startPulses % 24 == 6
        })
        assertTrue(WantsSpaceMusic.drumPhrase(3L).any {
            it.kind == WantsSpaceDrumKind.HAT && it.startPulses % 24 == 3
        })
    }

    @Test
    fun combinedEventsAreDeterministicAndStayWithinTheExpandedRange() {
        val events = WantsSpaceMusic.allEvents(7L)

        assertEquals(events, WantsSpaceMusic.allEvents(7L))
        assertTrue(events.pad.isNotEmpty())
        assertTrue(events.crystal.isNotEmpty())
        assertTrue(events.drums.isNotEmpty())
        assertTrue(events.pad.all { it.midiNote in 38..86 })
        assertTrue(events.crystal.all { it.midiNote in 67..91 })
    }

    @Test
    fun noteVelocitiesAreDeterministicLayeredAndVaryWithinTheIntendedRange() {
        for (phraseIndex in listOf(0L, 7L, 8L, 123_456L)) {
            val pad = WantsSpaceMusic.phrase(phraseIndex).indices.map {
                WantsSpaceMusic.padVelocity(phraseIndex, it)
            }
            val crystal = WantsSpaceMusic.crystalPhrase(phraseIndex).indices.map {
                WantsSpaceMusic.crystalVelocity(phraseIndex, it)
            }

            assertEquals(pad, WantsSpaceMusic.phrase(phraseIndex).indices.map {
                WantsSpaceMusic.padVelocity(phraseIndex, it)
            })
            assertTrue((pad + crystal).all { it in 0.55..1.0 })
            assertTrue(pad.distinct().size > 1)
            assertTrue(crystal.distinct().size > 1)
        }
    }

    @Test
    fun noteIndexAtTracksPhraseBoundariesAndComplexFills() {
        assertEquals(0, WantsSpaceMusic.padNoteIndexAt(0L))
        assertEquals(1, WantsSpaceMusic.padNoteIndexAt(2_500L))
        assertEquals(2, WantsSpaceMusic.padNoteIndexAt(WantsSpaceMusic.pulseStartMillis(7L * 168 + 78)))
        assertEquals(5, WantsSpaceMusic.padNoteIndexAt(WantsSpaceMusic.pulseStartMillis(7L * 168 + 96)))
        assertEquals(-1, WantsSpaceMusic.crystalNoteIndexAt(2_500L))
    }

    @Test
    fun absoluteTimeSelectsTheSamePitchAndSchedulesTheNextBoundary() {
        val timestamp = 1_800_000_000_000L
        val expectedPitch = WantsSpaceMusic.pitchAt(timestamp)

        WantsSpaceMusic.pitchAt(timestamp + 60_000L)
        assertEquals(expectedPitch, WantsSpaceMusic.pitchAt(timestamp))
        assertEquals(2_500L, WantsSpaceMusic.nextNoteStartMillis(0L))
        assertEquals(WantsSpaceMusic.phrase(0L)[0].midiNote, WantsSpaceMusic.pitchAt(2_499L))
        assertEquals(WantsSpaceMusic.phrase(0L)[1].midiNote, WantsSpaceMusic.pitchAt(2_500L))
        assertTrue(WantsSpaceMusic.nextNoteStartMillis(timestamp) > timestamp)
    }

    @Test
    fun phraseAndDrumBoundariesAreEpochAnchored() {
        assertEquals(0L, WantsSpaceMusic.phraseStartMillis(0L))
        assertEquals(2_500L, WantsSpaceMusic.pulseStartMillis(72L))
        assertEquals(1L, WantsSpaceMusic.phraseIndexAt(5_834L))
        assertEquals(167, WantsSpaceMusic.pulseInPhraseAt(5_833L))
        assertEquals(0, WantsSpaceMusic.pulseInPhraseAt(5_834L))
        assertEquals(834L, WantsSpaceMusic.nextDrumHitStartMillis(0L))
        assertEquals(listOf(WantsSpaceDrumKind.KICK, WantsSpaceDrumKind.HAT),
            WantsSpaceMusic.drumHitsAt(0L, 0).map { it.kind })
    }
}