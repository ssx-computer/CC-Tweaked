// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DhcpServerTest {
    private static final IpAddress SERVER_IP = IpAddress.parse("10.0.0.1");
    private static final IpAddress NETMASK = IpAddress.parse("255.255.255.0");
    private static final long NOW = 1_000_000L;

    private static Mac mac(int id) {
        return Mac.parse(String.format("cc:00:00:%02x", id));
    }

    private static NetPacket discover(Mac mac) {
        return packet(mac, new DhcpMessage(DhcpMessage.DISCOVER, mac, null, null, null, null, "host-" + mac.value(), null));
    }

    private static NetPacket request(Mac mac, IpAddress address) {
        return packet(mac, new DhcpMessage(DhcpMessage.REQUEST, mac, address, null, null, null, "host-" + mac.value(), null));
    }

    private static NetPacket packet(Mac mac, DhcpMessage message) {
        return new NetPacket(
            IpAddress.ANY, IpAddress.BROADCAST, mac, null,
            NetPacket.PROTOCOL_UDP, DhcpServer.CLIENT_PORT, DhcpServer.SERVER_PORT, 64, 0, message.encode()
        );
    }

    private static DhcpServer server() {
        var server = new DhcpServer(SERVER_IP, NETMASK);
        server.setPool(IpAddress.parse("10.0.0.100"), IpAddress.parse("10.0.0.110"));
        return server;
    }

    private static DhcpMessage reply(NetPacket packet) {
        assertEquals(SERVER_IP, packet.src(), "Replies come from the DHCP server");
        assertEquals(DhcpServer.SERVER_PORT, packet.srcPort());
        assertEquals(DhcpServer.CLIENT_PORT, packet.dstPort());
        return DhcpMessage.decode(packet.payload());
    }

    /**
     * Perform a full DISCOVER/OFFER/REQUEST/ACK exchange, returning the address which was assigned.
     *
     * @param server The server to talk to.
     * @param mac    The MAC address to lease an address for.
     * @param now    The current time, in milliseconds since the epoch.
     * @return The address which was assigned.
     */
    private static IpAddress lease(DhcpServer server, Mac mac, long now) {
        var offer = server.handle(discover(mac), now);
        assertNotNull(offer, "Server should offer an address to " + mac);
        var offered = reply(offer);
        assertEquals(DhcpMessage.OFFER, offered.operation());
        assertNotNull(offered.address());

        var ack = server.handle(request(mac, offered.address()), now);
        assertNotNull(ack, "Server should acknowledge " + mac);
        var acknowledged = reply(ack);
        assertEquals(DhcpMessage.ACK, acknowledged.operation());
        assertEquals(offered.address(), acknowledged.address());
        return acknowledged.address();
    }

    @Test
    void testPoolAllocation() {
        var server = server();
        assertEquals(IpAddress.parse("10.0.0.100"), lease(server, mac(1), NOW));
        assertEquals(IpAddress.parse("10.0.0.101"), lease(server, mac(2), NOW));
        assertEquals(IpAddress.parse("10.0.0.102"), lease(server, mac(3), NOW));

        var leases = server.leases();
        assertEquals(3, leases.size());
        var first = leases.get(0);
        assertEquals(mac(1), first.mac());
        assertEquals(IpAddress.parse("10.0.0.100"), first.address());
        assertEquals("host-cc:00:00:01", first.hostname());
        assertFalse(first.reserved());
        assertEquals(NOW + DhcpServer.DEFAULT_LEASE_TIME * 1000L, first.expiresAtMillis());
    }

    @Test
    void testOfferIsNotCommitted() {
        var server = server();
        var offer = server.handle(discover(mac(1)), NOW);
        assertNotNull(offer);
        reply(offer);
        assertTrue(server.leases().isEmpty(), "A DISCOVER should not create a lease");
    }

    @Test
    void testPoolExhaustion() {
        var server = new DhcpServer(SERVER_IP, NETMASK);
        server.setPool(IpAddress.parse("10.0.0.100"), IpAddress.parse("10.0.0.101"));

        lease(server, mac(1), NOW);
        lease(server, mac(2), NOW);

        var response = server.handle(discover(mac(3)), NOW);
        assertNotNull(response, "An exhausted server should still reply");
        assertEquals(DhcpMessage.NAK, reply(response).operation());
    }

    @Test
    void testSameMacGetsSameAddress() {
        var server = server();
        var first = lease(server, mac(1), NOW);
        var second = lease(server, mac(1), NOW);
        assertEquals(first, second);
        assertEquals(1, server.leases().size());
    }

    @Test
    void testReservedAddress() {
        var server = server();
        var reserved = IpAddress.parse("10.0.0.105");
        server.reserve(mac(1), reserved);

        assertEquals(reserved, lease(server, mac(1), NOW));
        var lease = server.leases().get(0);
        assertTrue(lease.reserved(), "Reserved leases are marked as such");
        assertEquals(Long.MAX_VALUE, lease.expiresAtMillis(), "Reserved leases never expire");

        // Another device must not be given the reserved address.
        var other = lease(server, mac(2), NOW);
        assertNotEquals(reserved, other);
    }

    @Test
    void testReservedAddressIsHonouredEvenAfterExpiry() {
        var server = server();
        server.setLeaseTime(1);
        var reserved = IpAddress.parse("10.0.0.105");
        server.reserve(mac(1), reserved);

        lease(server, mac(1), NOW);
        server.tick(NOW + 10_000);
        assertEquals(1, server.leases().size(), "Reserved leases are never expired");

        assertEquals(reserved, lease(server, mac(1), NOW + 20_000));
    }

    @Test
    void testLeaseExpiry() {
        var server = server();
        server.setLeaseTime(10);

        assertEquals(IpAddress.parse("10.0.0.100"), lease(server, mac(1), NOW));
        assertEquals(1, server.leases().size());

        server.tick(NOW + 5_000);
        assertEquals(1, server.leases().size(), "The lease is still valid");

        server.tick(NOW + 10_000);
        assertTrue(server.leases().isEmpty(), "The lease has expired");
    }

    @Test
    void testExpiredAddressIsReused() {
        var server = new DhcpServer(SERVER_IP, NETMASK);
        server.setPool(IpAddress.parse("10.0.0.100"), IpAddress.parse("10.0.0.100"));
        server.setLeaseTime(10);

        assertEquals(IpAddress.parse("10.0.0.100"), lease(server, mac(1), NOW));
        var refused = server.handle(discover(mac(2)), NOW + 1_000);
        assertNotNull(refused);
        assertEquals(DhcpMessage.NAK, reply(refused).operation(), "The only address is in use");

        // Once the lease expires, the address can be handed to somebody else.
        assertEquals(IpAddress.parse("10.0.0.100"), lease(server, mac(2), NOW + 20_000));
    }

    @Test
    void testDeniedDeviceGetsNak() {
        var server = server();
        server.deny(mac(1));

        var response = server.handle(discover(mac(1)), NOW);
        assertNotNull(response, "A denied device should be told why it was refused");
        var decoded = reply(response);
        assertEquals(DhcpMessage.NAK, decoded.operation());
        assertNull(decoded.address());
        assertNotNull(decoded.reason());
        assertTrue(server.leases().isEmpty(), "A denied device is never given a lease");
    }

    @Test
    void testDeniedDeviceIsNakedOnRequest() {
        var server = server();
        server.deny(mac(1));
        var response = server.handle(request(mac(1), IpAddress.parse("10.0.0.100")), NOW);
        assertNotNull(response);
        assertEquals(DhcpMessage.NAK, reply(response).operation());
    }

    @Test
    void testDefaultPolicyDeny() {
        var server = server();
        server.setDefaultPolicy(false);

        assertEquals(DhcpMessage.NAK, reply(server.handle(discover(mac(1)), NOW)).operation());

        server.allow(mac(1));
        assertEquals(DhcpMessage.OFFER, reply(server.handle(discover(mac(1)), NOW)).operation());
    }

    @Test
    void testAllowOverridesDeny() {
        var server = server();
        server.deny(mac(1));
        server.allow(mac(1));
        assertEquals(DhcpMessage.OFFER, reply(server.handle(discover(mac(1)), NOW)).operation());

        server.deny(mac(1));
        assertEquals(DhcpMessage.NAK, reply(server.handle(discover(mac(1)), NOW)).operation());
    }

    @Test
    void testRequestForWrongAddressGetsNak() {
        var server = server();
        lease(server, mac(1), NOW);

        var response = server.handle(request(mac(2), IpAddress.parse("10.0.0.100")), NOW);
        assertNotNull(response);
        assertEquals(DhcpMessage.NAK, reply(response).operation(), "Address is taken, so the request must be refused");
    }

    @Test
    void testServerIgnoresGarbage() {
        var server = server();
        var garbage = new NetPacket(
            IpAddress.ANY, IpAddress.BROADCAST, mac(1), null,
            NetPacket.PROTOCOL_UDP, DhcpServer.CLIENT_PORT, DhcpServer.SERVER_PORT, 64, 0,
            "not a dhcp message".getBytes(StandardCharsets.UTF_8)
        );
        assertNull(server.handle(garbage, NOW));
        assertTrue(server.leases().isEmpty());
    }

    @Test
    void testServerIgnoresAckAndOffer() {
        var server = server();
        var ack = packet(mac(1), new DhcpMessage(DhcpMessage.ACK, mac(1), IpAddress.parse("10.0.0.100"), NETMASK, null, SERVER_IP, null, null));
        assertNull(server.handle(ack, NOW), "A server ignores replies from other servers");
    }

    @Test
    void testReplyDestination() {
        var server = server();
        var response = server.handle(discover(mac(1)), NOW);
        assertNotNull(response);
        assertEquals(IpAddress.BROADCAST, response.dst(), "Replies to a broadcast request are broadcast");
        assertEquals(NETMASK, reply(response).netmask());
        assertEquals(SERVER_IP, reply(response).server());
    }

    @Test
    void testInvalidConfiguration() {
        var server = server();
        assertThrows(IllegalArgumentException.class, () -> server.setPool(IpAddress.parse("10.0.0.110"), IpAddress.parse("10.0.0.100")));
        assertThrows(IllegalArgumentException.class, () -> server.setLeaseTime(0));
        assertThrows(IllegalArgumentException.class, () -> server.setLeaseTime(-1));
    }
}
