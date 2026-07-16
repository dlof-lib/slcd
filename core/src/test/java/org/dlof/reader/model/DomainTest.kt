package org.dlof.reader.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainTest {

    @Test
    fun fromXml_knownValue_returnsMatchingDomain() {
        assertEquals(Domain.COMIC, Domain.fromXml("comic"))
        assertEquals(Domain.RECIPE, Domain.fromXml("recipe"))
        assertEquals(Domain.MANGA, Domain.fromXml("manga"))
    }

    @Test
    fun fromXml_unknownValue_fallsBackToCustom() {
        assertEquals(Domain.CUSTOM, Domain.fromXml("this-does-not-exist"))
    }

    @Test
    fun fromXml_isCaseSensitive_unknownCaseFallsBackToCustom() {
        // القيم في xmlValue كلها lowercase — أي اختلاف في الحالة يُعامَل كقيمة غير معروفة.
        assertEquals(Domain.CUSTOM, Domain.fromXml("COMIC"))
    }
}
