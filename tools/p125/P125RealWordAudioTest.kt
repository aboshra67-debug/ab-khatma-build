package com.ab.khatma.ui.screens

import org.junit.Assert.*
import org.junit.Test

class P125RealWordAudioTest {
    @Test fun exactBismillahWordsOnly() {
        assertEquals(listOf("001_001_001","001_001_002","001_001_003","001_001_004"),
            P125WordAudio.bismillah.map { it.key })
        assertEquals(listOf("بِسْمِ","اللَّهِ","الرَّحْمَٰنِ","الرَّحِيمِ"),
            P125WordAudio.bismillah.map { it.label })
        assertTrue(P123TeachingAudio.hasPlayback("R01"))
        assertEquals("اسمع بِسْمِ",P123TeachingAudio.playbackLabel("R01"))
    }
    @Test fun noFakeAudioForShadda() {
        assertEquals("شاهد شرح الشدة",P123TeachingAudio.playbackLabel("N13"))
        assertTrue(P123TeachingAudio.notice("N13").contains("ليس تسجيلًا منفصلًا"))
    }
    @Test fun previousContentIntact() {
        assertEquals(58,P121CurriculumBank.lessons.size)
        assertEquals(58,P121CurriculumBank.lessons.map{it.id}.toSet().size)
    }
}
