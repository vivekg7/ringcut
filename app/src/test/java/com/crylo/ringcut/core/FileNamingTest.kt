package com.crylo.ringcut.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamingTest {
    @Test fun stripsIllegalCharactersAndWhitespace() {
        assertEquals("AC DC Back in Black", FileNaming.sanitize("AC/DC:  Back\tin \"Black\""))
        assertEquals("name", FileNaming.sanitize("  ..name.. "))
    }

    @Test fun capsLength() {
        assertEquals(FileNaming.MAX_LENGTH, FileNaming.sanitize("a".repeat(200)).length)
    }

    @Test fun titleFromSourceDropsExtension() {
        assertEquals("My Song", FileNaming.titleFromSource("My Song.mp3"))
        assertEquals("video.final", FileNaming.titleFromSource("video.final.mp4"))
        assertEquals("hidden", FileNaming.titleFromSource(".hidden"))
        assertEquals(FileNaming.FALLBACK, FileNaming.titleFromSource(null))
        assertEquals(FileNaming.FALLBACK, FileNaming.titleFromSource("???.mp3"))
    }

    @Test fun fileNameAlwaysEndsInM4aOnce() {
        assertEquals("Alarm.m4a", FileNaming.fileName("Alarm"))
        assertEquals("Alarm.m4a", FileNaming.fileName("Alarm.M4A"))
        assertEquals("Tone.m4a", FileNaming.fileName("  /// "))
        assertEquals("a b.m4a", FileNaming.fileName("a|b"))
    }
}
