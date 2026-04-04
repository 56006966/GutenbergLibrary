package com.kdhuf.projectgutenberglibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsLogicTest {

    @Test
    fun `buildWordBoundaries tracks each visible word`() {
        val boundaries = ReaderTtsLogic.buildWordBoundaries("One fish two fish")

        assertEquals(4, boundaries.size)
        assertEquals(TtsWordBoundary(index = 0, start = 0, endExclusive = 3), boundaries[0])
        assertEquals(TtsWordBoundary(index = 1, start = 4, endExclusive = 8), boundaries[1])
        assertEquals(TtsWordBoundary(index = 2, start = 9, endExclusive = 12), boundaries[2])
        assertEquals(TtsWordBoundary(index = 3, start = 13, endExclusive = 17), boundaries[3])
    }

    @Test
    fun `speakableSuffix starts reading from selected word`() {
        val text = "Call me Ishmael some years ago"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)

        val suffix = ReaderTtsLogic.speakableSuffix(
            text = text,
            boundaries = boundaries,
            startWordIndex = 2
        )

        assertEquals("Ishmael some years ago", suffix)
    }

    @Test
    fun `speakableSuffix trims full text when there are no word boundaries`() {
        val suffix = ReaderTtsLogic.speakableSuffix(
            text = "   ",
            boundaries = emptyList(),
            startWordIndex = 0
        )

        assertEquals("", suffix)
    }

    @Test
    fun `tapped word can become the next read aloud starting point`() {
        val text = "Once upon a midnight dreary"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)
        val tappedWordIndex = ReaderTtsLogic.clampWordIndex(wordIndex = 3, wordCount = boundaries.size)

        val suffix = ReaderTtsLogic.speakableSuffix(
            text = text,
            boundaries = boundaries,
            startWordIndex = tappedWordIndex
        )

        assertEquals("midnight dreary", suffix)
    }

    @Test
    fun `tapped word index is clamped when it overshoots the page word count`() {
        val text = "Call me Ishmael"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)

        val tappedWordIndex = ReaderTtsLogic.clampWordIndex(wordIndex = 99, wordCount = boundaries.size)
        val suffix = ReaderTtsLogic.speakableSuffix(
            text = text,
            boundaries = boundaries,
            startWordIndex = tappedWordIndex
        )

        assertEquals(2, tappedWordIndex)
        assertEquals("Ishmael", suffix)
    }

    @Test
    fun `utteranceChunk limits spoken text length while keeping whole words`() {
        val text = "alpha beta gamma delta epsilon zeta"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)

        val chunk = ReaderTtsLogic.utteranceChunk(
            text = text,
            boundaries = boundaries,
            startWordIndex = 0,
            maxCharacters = 16
        )

        assertEquals("alpha beta gamma", chunk.text)
        assertEquals(0, chunk.startWordIndex)
        assertEquals(2, chunk.endWordIndex)
    }

    @Test
    fun `utteranceChunk starts from selected word and continues to the page end when short enough`() {
        val text = "alpha beta gamma delta"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)

        val chunk = ReaderTtsLogic.utteranceChunk(
            text = text,
            boundaries = boundaries,
            startWordIndex = 2,
            maxCharacters = 100
        )

        assertEquals("gamma delta", chunk.text)
        assertEquals(2, chunk.startWordIndex)
        assertEquals(3, chunk.endWordIndex)
    }

    @Test
    fun `utteranceChunk still returns one word when max length is tiny`() {
        val text = "encyclopedia beta"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)

        val chunk = ReaderTtsLogic.utteranceChunk(
            text = text,
            boundaries = boundaries,
            startWordIndex = 0,
            maxCharacters = 3
        )

        assertEquals("encyclopedia", chunk.text)
        assertEquals(0, chunk.endWordIndex)
    }

    @Test
    fun `resolveWordIndexForRange maps utterance offsets back to absolute word index`() {
        val text = "alpha beta gamma delta"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)

        val activeWordIndex = ReaderTtsLogic.resolveWordIndexForRange(
            boundaries = boundaries,
            startWordIndex = 1,
            utteranceStart = 5,
            utteranceEnd = 10
        )

        assertEquals(2, activeWordIndex)
    }

    @Test
    fun `resolveWordIndexForRange clamps to last known word when offsets overshoot`() {
        val text = "alpha beta"
        val boundaries = ReaderTtsLogic.buildWordBoundaries(text)

        val activeWordIndex = ReaderTtsLogic.resolveWordIndexForRange(
            boundaries = boundaries,
            startWordIndex = 0,
            utteranceStart = 200,
            utteranceEnd = 210
        )

        assertEquals(1, activeWordIndex)
    }

    @Test
    fun `clampWordIndex returns zero for empty word list`() {
        assertEquals(0, ReaderTtsLogic.clampWordIndex(wordIndex = 8, wordCount = 0))
    }

    @Test
    fun `nextContinuationStep advances when another page exists`() {
        val step = ReaderTtsLogic.nextContinuationStep(
            currentPageIndex = 2,
            lastPageIndex = 5
        )

        assertEquals(TtsContinuationAction.ADVANCE_TO_NEXT_PAGE, step.action)
        assertEquals(3, step.targetPageIndex)
    }

    @Test
    fun `nextContinuationStep stops when current page is the last page`() {
        val step = ReaderTtsLogic.nextContinuationStep(
            currentPageIndex = 5,
            lastPageIndex = 5
        )

        assertEquals(TtsContinuationAction.STOP_READING, step.action)
        assertEquals(5, step.targetPageIndex)
    }
}
