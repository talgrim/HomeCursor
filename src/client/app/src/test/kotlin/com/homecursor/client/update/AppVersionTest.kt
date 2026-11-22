package com.homecursor.client.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parse_readsThreeNumbers() {
        assertEquals(AppVersion(1, 3, 0), AppVersion.parse("1.3.0"))
    }

    @Test
    fun parse_rejectsAnythingElse() {
        listOf(null, "", "1.3", "1.3.0.0", "1.x.0", "1.3.0-beta", "-1.3.0", "1..0", " 1.3.0").forEach {
            assertNull("'$it'", AppVersion.parse(it))
        }
    }

    @Test
    fun comparison_isNumeric_notTextual() {
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 9))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertTrue(AppVersion(1, 3, 1) > AppVersion(1, 3, 0))
        assertEquals(0, AppVersion(1, 3, 0).compareTo(AppVersion(1, 3, 0)))
    }

    @Test
    fun toString_isTheDottedVersion() {
        assertEquals("1.3.0", AppVersion(1, 3, 0).toString())
    }
}
