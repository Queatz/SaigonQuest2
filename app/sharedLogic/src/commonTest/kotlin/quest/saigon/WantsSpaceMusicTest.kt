package quest.saigon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WantsSpaceMusicTest {
    @Test
    fun phraseIsDeterministicAndPitchesStayWithinTwoOctaves() {
        for (phraseIndex in listOf(0L, 1L, 7L, 8L, 123_456L)) {
            val first = WantsSpaceMusic.phrase(phraseIndex)
            assertEquals(first, WantsSpaceMusic.phrase(phraseIndex))
            assertTrue(first.all { it.midiNote in 50..74 })
        }
    }

    @Test
    fun regularPhraseUsesDottedHalfEighthEighthDottedHalf() {
        val notes = WantsSpaceMusic.phrase(0L)

        assertEquals(listOf(0, 12, 14, 16), notes.map { it.startSixteenths })
        assertEquals(listOf(12, 2, 2, 12), notes.map { it.durationSixteenths })
        assertEquals(WantsSpaceMusic.PHRASE_SIXTEENTHS, notes.sumOf { it.durationSixteenths })
    }

    @Test
    fun occasionalComplexPhraseAddsSixteenthFillWithoutChangingPhraseLength() {
        val notes = WantsSpaceMusic.phrase(7L)

        assertEquals(listOf(0, 12, 13, 14, 15, 16), notes.map { it.startSixteenths })
        assertEquals(listOf(12, 1, 1, 1, 1, 12), notes.map { it.durationSixteenths })
        assertEquals(WantsSpaceMusic.PHRASE_SIXTEENTHS, notes.sumOf { it.durationSixteenths })
    }

    @Test
    fun crystalPhraseIsAnOctaveAboveThePadAndPreservesItsRhythm() {
        for (phraseIndex in listOf(0L, 7L, 8L, 123_456L)) {
            val pad = WantsSpaceMusic.phrase(phraseIndex)
            val crystal = WantsSpaceMusic.crystalPhrase(phraseIndex)

            assertEquals(pad.map { it.startSixteenths }, crystal.map { it.startSixteenths })
            assertEquals(pad.map { it.durationSixteenths }, crystal.map { it.durationSixteenths })
            assertEquals(pad.map { it.midiNote + 12 }, crystal.map { it.midiNote })
            assertTrue(crystal.all { it.midiNote in 62..74 })
        }
    }

    @Test
    fun drumPatternIsDeterministicAndAddsComplexPhraseFill() {
        val regular = WantsSpaceMusic.drumPhrase(0L)
        val complex = WantsSpaceMusic.drumPhrase(7L)

        assertEquals(regular, WantsSpaceMusic.drumPhrase(0L))
        assertEquals(listOf(0, 4, 8, 12, 16, 20, 24), regular
            .filter { it.kind == WantsSpaceDrumKind.HAT }
            .map { it.startSixteenths })
        assertEquals(listOf(12, 14), regular
            .filter { it.kind == WantsSpaceDrumKind.SNARE }
            .map { it.startSixteenths })
        assertEquals(listOf(12, 13, 14, 15), complex
            .filter { it.kind == WantsSpaceDrumKind.SNARE }
            .map { it.startSixteenths })
        assertEquals(regular.toSet() + setOf(
            WantsSpaceDrumHit(13, WantsSpaceDrumKind.SNARE),
            WantsSpaceDrumHit(15, WantsSpaceDrumKind.SNARE),
        ), complex.toSet())
    }

    @Test
    fun combinedEventsAreDeterministicAndStayWithinTwoOctaves() {
        val events = WantsSpaceMusic.allEvents(7L)

        assertEquals(events, WantsSpaceMusic.allEvents(7L))
        assertTrue(events.pad.isNotEmpty())
        assertTrue(events.crystal.isNotEmpty())
        assertTrue(events.drums.isNotEmpty())
        assertTrue(events.pad.all { it.midiNote in 50..74 })
        assertTrue(events.crystal.all { it.midiNote in 50..74 })
    }

    @Test
    fun noteVelocitiesAreDeterministicLayeredAndVaryWithinTheIntendedRange() {
        for (phraseIndex in listOf(0L, 7L, 8L, 123_456L)) {
            val pad = WantsSpaceMusic.phrase(phraseIndex).indices.map {
                WantsSpaceMusic.padVelocity(phraseIndex, it)
            }
            val crystal = WantsSpaceMusic.phrase(phraseIndex).indices.map {
                WantsSpaceMusic.crystalVelocity(phraseIndex, it)
            }

            assertEquals(pad, WantsSpaceMusic.phrase(phraseIndex).indices.map {
                WantsSpaceMusic.padVelocity(phraseIndex, it)
            })
            assertTrue((pad + crystal).all { it in 0.68..1.0 })
            assertTrue(pad.distinct().size > 1)
            assertTrue(crystal.distinct().size > 1)
        }
    }

    @Test
    fun noteIndexAtTracksPhraseBoundariesAndComplexFills() {
        assertEquals(0, WantsSpaceMusic.noteIndexAt(0L))
        assertEquals(1, WantsSpaceMusic.noteIndexAt(2_500L))
        assertEquals(2, WantsSpaceMusic.noteIndexAt(WantsSpaceMusic.sixteenthStartMillis(7L * 28 + 13)))
        assertEquals(5, WantsSpaceMusic.noteIndexAt(WantsSpaceMusic.phraseStartMillis(7L) + 3_334L))
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
        assertEquals(2_500L, WantsSpaceMusic.sixteenthStartMillis(12L))
        assertEquals(1L, WantsSpaceMusic.phraseIndexAt(5_834L))
        assertEquals(27, WantsSpaceMusic.pulseInPhraseAt(5_833L))
        assertEquals(0, WantsSpaceMusic.pulseInPhraseAt(5_834L))
        assertEquals(834L, WantsSpaceMusic.nextDrumHitStartMillis(0L))
        assertEquals(listOf(WantsSpaceDrumKind.KICK, WantsSpaceDrumKind.HAT),
            WantsSpaceMusic.drumHitsAt(0L, 0).map { it.kind })
    }
}