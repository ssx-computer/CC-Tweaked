// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class NetStackTest {
    private static final IpAddress NETMASK = IpAddress.parse("255.255.255.0");

    private static NetStack stack(String mac, NetEventSink sink) {
        return new NetStack(Mac.parse(mac), sink);
    }

    /**
     * Build two stacks on the same subnet, connected by a wire. Returns the two stacks and their sinks.
     */
    private record Pair(NetStack a, NetStack b, TestNetwork.RecordingSink sinkA, TestNetwork.RecordingSink sinkB) {
    }

    private static Pair pair() {
        var sinkA = new TestNetwork.RecordingSink();
        var sinkB = new TestNetwork.RecordingSink();
        var a = stack("cc:00:00:01", sinkA);
        var b = stack("cc:00:00:02", sinkB);

        var aIface = TestNetwork.iface("cc:00:00:01", "eth0");
        var bIface = TestNetwork.iface("cc:00:00:02", "eth0");
        a.addInterface(aIface);
        b.addInterface(bIface);
        TestNetwork.connect(a, aIface, b, bIface);

        a.setAddress(IpAddress.parse("10.0.0.1"), null, NETMASK);
        b.setAddress(IpAddress.parse("10.0.0.2"), null, NETMASK);
        return new Pair(a, b, sinkA, sinkB);
    }

    @Test
    void testSendAndReceive() {
        var pair = pair();

        var aSocket = pair.a().listen(1234);
        var bSocket = pair.b().listen(1234);

        assertNotNull(pair.b().socket(bSocket));
        assertTrue(pair.a().send(IpAddress.parse("10.0.0.2"), 1234, 1234, NetPacket.PROTOCOL_UDP, "hello".getBytes(StandardCharsets.UTF_8)));

        var socket = pair.b().socket(bSocket);
        assertNotNull(socket);
        assertEquals(1, socket.pending());

        var message = socket.poll();
        assertNotNull(message);
        assertEquals(IpAddress.parse("10.0.0.1"), message.from());
        assertEquals(1234, message.port());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), message.payload());
        assertNull(socket.poll(), "Polling an empty socket returns null");

        var events = pair.sinkB().events("net_message");
        assertEquals(1, events.size());
        assertArrayEquals(new Object[]{ "10.0.0.1", 1234, "hello" }, events.get(0).args());
        assertNull(pair.sinkA().last(), "The sender gets no event");
    }

    @Test
    void testPacketsForOtherAddressesAreIgnored() {
        var pair = pair();
        var socket = pair.b().listen(1234);

        var outsider = stack("cc:00:00:03", new TestNetwork.RecordingSink());
        var iface = TestNetwork.iface("cc:00:00:03", "eth0");
        outsider.addInterface(iface);
        outsider.setAddress(IpAddress.parse("10.0.0.3"), null, NETMASK);

        var packet = new NetPacket(
            IpAddress.parse("10.0.0.3"), IpAddress.parse("10.0.0.99"), null, null,
            NetPacket.PROTOCOL_UDP, 100, 1234, 64, 0, new byte[]{ 0, 'x' }
        );
        pair.b().receive(iface, packet);

        var bSocket = pair.b().socket(socket);
        assertNotNull(bSocket);
        assertEquals(0, bSocket.pending());
        assertTrue(pair.sinkB().events().isEmpty());
    }

    @Test
    void testBroadcastIsReceived() {
        var pair = pair();
        var socket = pair.b().listen(DhcpServer.CLIENT_PORT);

        var iface = TestNetwork.iface("cc:00:00:01", "eth0");
        var packet = new NetPacket(
            IpAddress.parse("10.0.0.1"), IpAddress.BROADCAST, null, null,
            NetPacket.PROTOCOL_UDP, 100, DhcpServer.CLIENT_PORT, 64, 0, new byte[]{ 0, 'x' }
        );
        pair.b().receive(iface, packet);

        var bSocket = pair.b().socket(socket);
        assertNotNull(bSocket);
        assertEquals(1, bSocket.pending(), "Broadcasts are delivered to listening sockets");
    }

    @Test
    void testConnectHandshake() {
        var pair = pair();
        var server = pair.b().listen(4321);
        assertNotNull(pair.b().socket(server));

        var client = pair.a().listen(1234);
        assertTrue(pair.a().connect(client, IpAddress.parse("10.0.0.2"), 4321));

        var clientSocket = pair.a().socket(client);
        assertNotNull(clientSocket);
        assertTrue(clientSocket.isConnected(), "The handshake completes synchronously");
        assertEquals(IpAddress.parse("10.0.0.2"), clientSocket.remoteAddress());
        assertEquals(4321, clientSocket.remotePort());

        var events = pair.sinkA().events("net_connect");
        assertEquals(1, events.size());
        assertArrayEquals(new Object[]{ client, "10.0.0.2", 4321 }, events.get(0).args());
    }

    @Test
    void testConnectTimesOut() {
        var pair = pair();
        // Nobody is listening on 9999, so the handshake is never answered.
        var client = pair.a().listen(1234);
        assertTrue(pair.a().connect(client, IpAddress.parse("10.0.0.2"), 9999));

        var socket = pair.a().socket(client);
        assertNotNull(socket);
        assertFalse(socket.isConnected());

        pair.a().tick(System.currentTimeMillis() + NetStack.DEFAULT_CONNECT_TIMEOUT + 1);

        assertFalse(socket.isConnected());
        var events = pair.sinkA().events("net_disconnect");
        assertEquals(1, events.size());
        assertEquals("timeout", events.get(0).args()[3]);
    }

    @Test
    void testCloseDisconnects() {
        var pair = pair();
        pair.b().listen(4321);
        var client = pair.a().listen(1234);
        assertTrue(pair.a().connect(client, IpAddress.parse("10.0.0.2"), 4321));

        pair.a().close(client);
        assertNull(pair.a().socket(client), "Closed sockets are removed");

        var events = pair.sinkA().events("net_disconnect");
        assertEquals(1, events.size());
        assertEquals("closed", events.get(0).args()[3]);
    }

    @Test
    void testListenValidation() {
        var stack = stack("cc:00:00:01", new TestNetwork.RecordingSink());
        assertThrows(IllegalArgumentException.class, () -> stack.listen(0));
        assertThrows(IllegalArgumentException.class, () -> stack.listen(65536));

        var id = stack.listen(80);
        assertThrows(IllegalArgumentException.class, () -> stack.listen(80), "Ports cannot be shared");
        stack.close(id);
        assertDoesNotThrow(() -> stack.listen(80), "Closing a socket releases its port");
    }

    @Test
    void testSocketIdsAreReused() {
        var stack = stack("cc:00:00:01", new TestNetwork.RecordingSink());
        var first = stack.listen(80);
        var second = stack.listen(81);
        assertNotEquals(first, second);

        stack.close(first);
        assertEquals(first, stack.listen(82), "The lowest free id is reused");
    }

    @Test
    void testSendWithoutAddressFails() {
        var stack = stack("cc:00:00:01", new TestNetwork.RecordingSink());
        stack.addInterface(TestNetwork.iface("cc:00:00:01", "eth0"));
        assertFalse(stack.send(IpAddress.parse("10.0.0.2"), 80, 80, NetPacket.PROTOCOL_UDP, new byte[0]), "A stack with no address cannot send");
    }

    @Test
    void testSendWithoutInterfaceFails() {
        var stack = stack("cc:00:00:01", new TestNetwork.RecordingSink());
        stack.setAddress(IpAddress.parse("10.0.0.1"), null, NETMASK);
        assertFalse(stack.send(IpAddress.parse("10.0.0.2"), 80, 80, NetPacket.PROTOCOL_UDP, new byte[0]));
    }

    @Test
    void testInterfaces() {
        var stack = stack("cc:00:00:01", new TestNetwork.RecordingSink());
        var iface = TestNetwork.iface("cc:00:00:01", "eth0");
        stack.addInterface(iface);
        assertEquals(1, stack.interfaces().size());
        assertEquals("eth0", stack.interfaces().get(0).name());

        assertThrows(IllegalArgumentException.class, () -> stack.addInterface(iface), "An interface cannot be added twice");
        stack.removeInterface(iface);
        assertTrue(stack.interfaces().isEmpty());
    }

    @Test
    void testTtlIsRespected() {
        var pair = pair();
        var socket = pair.b().listen(1234);

        var iface = TestNetwork.iface("cc:00:00:01", "eth0");
        var packet = new NetPacket(
            IpAddress.parse("10.0.0.1"), IpAddress.parse("10.0.0.2"), null, null,
            NetPacket.PROTOCOL_UDP, 100, 1234, 0, 0, new byte[]{ 0, 'x' }
        );
        pair.b().receive(iface, packet);

        var bSocket = pair.b().socket(socket);
        assertNotNull(bSocket);
        assertEquals(0, bSocket.pending(), "Packets with an expired TTL are dropped");
    }

    @Test
    void testInboxIsBounded() {
        var stack = stack("cc:00:00:01", new TestNetwork.RecordingSink());
        var iface = TestNetwork.iface("cc:00:00:02", "eth0");
        stack.addInterface(iface);
        stack.setAddress(IpAddress.parse("10.0.0.2"), null, NETMASK);

        var id = stack.listen(1234);
        var socket = stack.socket(id);
        assertNotNull(socket);
        socket.setInboxSize(2);

        for (var i = 0; i < 5; i++) {
            var packet = new NetPacket(
                IpAddress.parse("10.0.0.1"), IpAddress.parse("10.0.0.2"), null, null,
                NetPacket.PROTOCOL_UDP, 100, 1234, 64, 0, new byte[]{ 0, (byte) i }
            );
            stack.receive(iface, packet);
        }

        assertEquals(2, socket.pending());
        var messages = socket.drain();
        assertEquals(2, messages.size());
        assertArrayEquals(new byte[]{ 3 }, messages.get(0).payload(), "The oldest messages are dropped");
        assertArrayEquals(new byte[]{ 4 }, messages.get(1).payload());
    }

    @Test
    void testDhcpRoundTrip() {
        var sink = new TestNetwork.RecordingSink();
        var client = stack("cc:00:00:01", sink);
        var clientIface = TestNetwork.iface("cc:00:00:01", "eth0");
        client.addInterface(clientIface);

        var server = new DhcpServer(IpAddress.parse("10.0.0.1"), NETMASK);
        server.setPool(IpAddress.parse("10.0.0.100"), IpAddress.parse("10.0.0.105"));
        server.setGateway(IpAddress.parse("10.0.0.1"));

        // Wire the client's interface directly to the DHCP server.
        clientIface.connect((from, packet) -> {
            var reply = server.handle(packet, 0);
            if (reply != null) client.receive(clientIface, reply);
        });

        assertTrue(client.requestDhcp());
        assertEquals(IpAddress.parse("10.0.0.100"), client.address(), "The stack applies its new address");
        assertEquals(IpAddress.parse("10.0.0.1"), client.gateway());
        assertEquals(NETMASK, client.netmask());

        var events = sink.events("net_dhcp");
        assertEquals(1, events.size());
        assertArrayEquals(new Object[]{ "10.0.0.100", "255.255.255.0", "10.0.0.1" }, events.get(0).args());

        assertEquals(1, server.leases().size());
        assertEquals(Mac.parse("cc:00:00:01"), server.leases().get(0).mac());
    }

    @Test
    void testDhcpRefused() {
        var sink = new TestNetwork.RecordingSink();
        var client = stack("cc:00:00:01", sink);
        var clientIface = TestNetwork.iface("cc:00:00:01", "eth0");
        client.addInterface(clientIface);

        var server = new DhcpServer(IpAddress.parse("10.0.0.1"), NETMASK);
        server.setPool(IpAddress.parse("10.0.0.100"), IpAddress.parse("10.0.0.105"));
        server.deny(Mac.parse("cc:00:00:01"));

        clientIface.connect((from, packet) -> {
            var reply = server.handle(packet, 0);
            if (reply != null) client.receive(clientIface, reply);
        });

        assertTrue(client.requestDhcp());
        assertNull(client.address(), "A refused client keeps no address");

        var events = sink.events("net_dhcp");
        assertEquals(1, events.size());
        assertArrayEquals(new Object[]{ "", "", "" }, events.get(0).args(), "A refusal is reported with empty strings");
    }

    @Test
    void testStackWithoutEventSink() {
        var stack = new NetStack(Mac.parse("cc:00:00:01"), null);
        var iface = TestNetwork.iface("cc:00:00:02", "eth0");
        stack.addInterface(iface);
        stack.setAddress(IpAddress.parse("10.0.0.2"), null, NETMASK);

        var socket = stack.listen(1234);
        var packet = new NetPacket(
            IpAddress.parse("10.0.0.1"), IpAddress.parse("10.0.0.2"), null, null,
            NetPacket.PROTOCOL_UDP, 100, 1234, 64, 0, new byte[]{ 0, 'x' }
        );

        // A stack with no sink still works, it just has nobody to tell.
        assertDoesNotThrow(() -> stack.receive(iface, packet));
        var s = stack.socket(socket);
        assertNotNull(s);
        assertEquals(1, s.pending());
    }

    @Test
    void testReset() {
        var pair = pair();
        pair.a().listen(80);
        pair.a().reset();

        assertNull(pair.a().address());
        assertTrue(pair.a().sockets().isEmpty());
    }
}
