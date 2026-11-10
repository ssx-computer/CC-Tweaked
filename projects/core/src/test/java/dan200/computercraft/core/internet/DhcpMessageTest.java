// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DhcpMessageTest {
    @Test
    void testRoundTrip() {
        var message = new DhcpMessage(
            DhcpMessage.ACK, Mac.parse("cc:00:00:01"), IpAddress.parse("10.0.0.5"),
            IpAddress.parse("255.255.255.0"), IpAddress.parse("10.0.0.1"), IpAddress.parse("10.0.0.254"),
            "my-computer", null
        );

        var decoded = DhcpMessage.decode(message.encode());
        assertEquals(message, decoded);
    }

    @Test
    void testDiscoverRoundTrip() {
        var message = new DhcpMessage(DhcpMessage.DISCOVER, Mac.parse("cc:00:00:01"), null, null, null, null, null, null);
        assertEquals(message, DhcpMessage.decode(message.encode()));
    }

    @Test
    void testRejectReasons() {
        var nak = new DhcpMessage(DhcpMessage.NAK, Mac.parse("cc:00:00:01"), null, null, null, null, null, "nope");
        assertEquals("nope", DhcpMessage.decode(nak.encode()).reason());
    }

    @Test
    void testUnknownKeysAreIgnored() {
        var decoded = DhcpMessage.decode("OFFER\nmac=cc:00:00:01\nfuture=1\nip=10.0.0.5\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(DhcpMessage.OFFER, decoded.operation());
        assertEquals(IpAddress.parse("10.0.0.5"), decoded.address());
    }

    @Test
    void testDecodeRejectsInvalid() {
        for (var bad : new String[]{ "", "HELLO\n", "offer\n", "OFFER\nnot-a-field\n", "OFFER\nip=not-an-ip\n", "OFFER\nmac=nope\n" }) {
            assertThrows(IllegalArgumentException.class,
                () -> DhcpMessage.decode(bad.getBytes(StandardCharsets.UTF_8)), "Should reject '" + bad + "'");
        }
    }
}
