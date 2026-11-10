// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpAddressTest {
    @Test
    void testOfAndToString() {
        assertEquals("10.0.0.5", IpAddress.of(10, 0, 0, 5).toString());
        assertEquals("255.255.255.255", IpAddress.of(255, 255, 255, 255).toString());
        assertEquals("0.0.0.0", IpAddress.of(0, 0, 0, 0).toString());
        assertEquals("192.168.1.200", IpAddress.of(192, 168, 1, 200).toString());
    }

    @Test
    void testRawValue() {
        assertEquals(0x0A000005, IpAddress.of(10, 0, 0, 5).value());
        assertEquals(0, IpAddress.ANY.value());
        assertEquals(-1, IpAddress.BROADCAST.value());
    }

    @Test
    void testParse() {
        assertEquals(IpAddress.of(10, 0, 0, 5), IpAddress.parse("10.0.0.5"));
        assertEquals(IpAddress.parse("010.0.0.5"), IpAddress.parse("10.0.0.5"), "Leading zeroes are allowed");
        assertEquals(IpAddress.BROADCAST, IpAddress.parse("255.255.255.255"));
        assertEquals(IpAddress.ANY, IpAddress.parse("0.0.0.0"));
    }

    @Test
    void testParseRejectsInvalid() {
        for (var bad : new String[]{ "", "10.0.0", "10.0.0.5.6", "10.0.0.256", "10.0.0.-1", "10.0.0.x", "10.0.0.0005", "10.0.0.", " 10.0.0.1" }) {
            assertThrows(IllegalArgumentException.class, () -> IpAddress.parse(bad), "Should reject '" + bad + "'");
        }
        assertThrows(IllegalArgumentException.class, () -> IpAddress.parse("10.0.0.1 "));
    }

    @Test
    void testOfRejectsInvalidOctets() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of(256, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> IpAddress.of(0, -1, 0, 0));
    }

    @Test
    void testBroadcastAndAny() {
        assertTrue(IpAddress.BROADCAST.isBroadcast());
        assertFalse(IpAddress.ANY.isBroadcast());
        assertFalse(IpAddress.of(10, 0, 0, 1).isBroadcast());

        assertTrue(IpAddress.ANY.isAny());
        assertFalse(IpAddress.BROADCAST.isAny());
    }

    @Test
    void testInSubnet() {
        var network = IpAddress.parse("10.0.0.0");
        assertTrue(IpAddress.parse("10.0.0.5").inSubnet(network, 24));
        assertTrue(IpAddress.parse("10.0.0.255").inSubnet(network, 24));
        assertFalse(IpAddress.parse("10.0.1.1").inSubnet(network, 24));
        assertTrue(IpAddress.parse("10.0.1.1").inSubnet(network, 16));
        assertTrue(IpAddress.parse("10.255.255.255").inSubnet(network, 8));
        assertTrue(IpAddress.parse("1.2.3.4").inSubnet(IpAddress.ANY, 0), "A /0 network contains everything");
        assertTrue(IpAddress.parse("10.0.0.5").inSubnet(IpAddress.parse("10.0.0.5"), 32));
        assertFalse(IpAddress.parse("10.0.0.6").inSubnet(IpAddress.parse("10.0.0.5"), 32));
    }

    @Test
    void testInSubnetRejectsBadPrefix() {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.ANY.inSubnet(IpAddress.ANY, -1));
        assertThrows(IllegalArgumentException.class, () -> IpAddress.ANY.inSubnet(IpAddress.ANY, 33));
    }

    @Test
    void testPrefixLength() {
        assertEquals(0, IpAddress.prefixLength(IpAddress.parse("0.0.0.0")));
        assertEquals(8, IpAddress.prefixLength(IpAddress.parse("255.0.0.0")));
        assertEquals(16, IpAddress.prefixLength(IpAddress.parse("255.255.0.0")));
        assertEquals(24, IpAddress.prefixLength(IpAddress.parse("255.255.255.0")));
        assertEquals(32, IpAddress.prefixLength(IpAddress.parse("255.255.255.255")));
    }
}
