// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NetPacketTest {
    private static final IpAddress SRC = IpAddress.parse("10.0.0.1");
    private static final IpAddress DST = IpAddress.parse("10.0.0.2");

    private static NetPacket packet(byte[] payload) {
        return new NetPacket(
            SRC, DST, Mac.parse("cc:00:00:01"), Mac.parse("cc:00:00:02"),
            NetPacket.PROTOCOL_UDP, 1234, 4321, 64, 7, payload
        );
    }

    @Test
    void testRoundTrip() {
        var payload = "hello world".getBytes(StandardCharsets.UTF_8);
        var decoded = NetPacket.decode(packet(payload).encode());

        assertEquals(SRC, decoded.src());
        assertEquals(DST, decoded.dst());
        assertEquals(Mac.parse("cc:00:00:01"), decoded.srcMac());
        assertEquals(Mac.parse("cc:00:00:02"), decoded.dstMac());
        assertEquals(NetPacket.PROTOCOL_UDP, decoded.protocol());
        assertEquals(1234, decoded.srcPort());
        assertEquals(4321, decoded.dstPort());
        assertEquals(64, decoded.ttl());
        assertEquals(7, decoded.id());
        assertArrayEquals(payload, decoded.payload());
    }

    @Test
    void testRoundTripWithoutMacs() {
        var packet = new NetPacket(SRC, IpAddress.BROADCAST, null, null, NetPacket.PROTOCOL_ICMP, 0, 0, 1, 0, new byte[0]);
        var decoded = NetPacket.decode(packet.encode());

        assertNull(decoded.srcMac());
        assertNull(decoded.dstMac());
        assertArrayEquals(new byte[0], decoded.payload());
        assertEquals(IpAddress.BROADCAST, decoded.dst());
    }

    @Test
    void testEmptyPayloadAndExtremes() {
        var packet = new NetPacket(IpAddress.ANY, IpAddress.BROADCAST, null, null, 0, 0, 0, 0, 0, new byte[0]);
        var decoded = NetPacket.decode(packet.encode());
        assertEquals(0, decoded.ttl());
        assertEquals(0, decoded.protocol());

        var max = new NetPacket(SRC, DST, null, null, 255, 65535, 65535, 255, 65535, new byte[]{ 1, 2, 3 });
        var decodedMax = NetPacket.decode(max.encode());
        assertEquals(255, decodedMax.protocol());
        assertEquals(65535, decodedMax.srcPort());
        assertEquals(65535, decodedMax.dstPort());
        assertEquals(255, decodedMax.ttl());
        assertEquals(65535, decodedMax.id());
    }

    @Test
    void testEncodedVersionByte() {
        var bytes = packet(new byte[0]).encode();
        assertEquals(1, bytes[0], "The first byte of an encoded packet is the format version");
    }

    @Test
    void testPayloadIsDefensive() {
        var payload = new byte[]{ 1, 2, 3 };
        var packet = packet(payload);
        payload[0] = 42;
        assertArrayEquals(new byte[]{ 1, 2, 3 }, packet.payload(), "Constructing a packet copies the payload");

        var got = packet.payload();
        got[0] = 42;
        assertArrayEquals(new byte[]{ 1, 2, 3 }, packet.payload(), "Reading the payload copies it");
    }

    @Test
    void testWithTtl() {
        var packet = packet(new byte[0]);
        var shorter = packet.withTtl(packet.ttl() - 1);
        assertEquals(63, shorter.ttl());
        assertEquals(64, packet.ttl(), "The original packet is not modified");
        assertArrayEquals(packet.payload(), shorter.payload());
    }

    @Test
    void testDecodeRejectsBadVersion() {
        var bytes = packet(new byte[0]).encode();
        bytes[0] = 2;
        var error = assertThrows(IllegalArgumentException.class, () -> NetPacket.decode(bytes));
        assertTrue(error.getMessage().contains("version"), "Error should mention the version, was: " + error.getMessage());
    }

    @Test
    void testDecodeRejectsTruncated() {
        var bytes = packet(new byte[0]).encode();
        for (var length = 0; length < 18; length++) {
            var truncated = Arrays.copyOf(bytes, length);
            assertThrows(IllegalArgumentException.class, () -> NetPacket.decode(truncated), "Should reject " + length + " byte packets");
        }
    }

    @Test
    void testDecodeRejectsTruncatedMac() {
        // Keep the header, but claim a MAC which is not present.
        var bytes = packet(new byte[0]).encode();
        var truncated = Arrays.copyOf(bytes, 18 + 1);
        truncated[18] = (byte) 99;
        assertThrows(IllegalArgumentException.class, () -> NetPacket.decode(truncated));
    }

    @Test
    void testDecodeRejectsUnknownFlags() {
        var bytes = packet(new byte[0]).encode();
        bytes[1] = (byte) 0b1000_0000;
        assertThrows(IllegalArgumentException.class, () -> NetPacket.decode(bytes));
    }

    @Test
    void testDecodeRejectsInvalidMac() {
        var bytes = packet(new byte[0]).encode();
        // The first MAC starts at offset 19; replace it with something which is not a valid MAC.
        Arrays.fill(bytes, 19, 19 + 8, (byte) 'z');
        assertThrows(IllegalArgumentException.class, () -> NetPacket.decode(bytes));
    }

    @Test
    void testDecodeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> NetPacket.decode(null));
    }

    @Test
    void testConstructorRangeChecks() {
        assertThrows(IllegalArgumentException.class, () -> new NetPacket(SRC, DST, null, null, 256, 0, 0, 0, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new NetPacket(SRC, DST, null, null, 0, -1, 0, 0, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new NetPacket(SRC, DST, null, null, 0, 0, 65536, 0, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new NetPacket(SRC, DST, null, null, 0, 0, 0, 256, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new NetPacket(SRC, DST, null, null, 0, 0, 0, 0, 65536, new byte[0]));
        assertThrows(NullPointerException.class, () -> new NetPacket(null, DST, null, null, 0, 0, 0, 0, 0, new byte[0]));
    }
}
