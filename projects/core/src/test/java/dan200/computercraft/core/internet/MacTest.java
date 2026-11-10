// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacTest {
    @Test
    void testParse() {
        assertEquals("cc:12:34:56", Mac.parse("cc:12:34:56").toString());
        assertEquals("cc:12:34:56", new Mac("CC:12:34:56").toString(), "MACs are normalised to lower case");
        assertEquals(Mac.parse("cc:12:34:56"), Mac.parse("CC:12:34:56"));
        assertEquals("aa", Mac.parse("aa").toString(), "A single group is allowed");
    }

    @Test
    void testParseRejectsInvalid() {
        for (var bad : new String[]{ "", "cc:12:34:5", "cc:1:34", "cc:zz:34", "cc:12:34:", ":" }) {
            assertThrows(IllegalArgumentException.class, () -> Mac.parse(bad), "Should reject '" + bad + "'");
        }
    }

    @Test
    void testEquality() {
        assertEquals(Mac.parse("cc:01:02:03"), Mac.parse("cc:01:02:03"));
        assertNotEquals(Mac.parse("cc:01:02:03"), Mac.parse("cc:01:02:04"));
    }
}
