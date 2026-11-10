// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A virtual IP stack, belonging to a single device: a terminal computer, the network core, or a switch port.
 * <p>
 * The stack owns an {@linkplain #address() IP address} (or none, if it has not been configured), a set of
 * {@link NetInterface}s, and a set of {@link Socket}s. Packets are sent with {@link NetInterface#send(NetPacket)}, and
 * packets received by the mod layer are fed back in with {@link #receive(NetInterface, NetPacket)}.
 * <h2>Framing</h2>
 * Every payload carried by the stack's own sockets is prefixed with a single framing byte, so the stack can tell user
 * data apart from control messages:
 * <ul>
 *     <li>{@code 0} - data. The rest of the payload is handed to the socket.</li>
 *     <li>{@code 1} - connect. Requests a connection with the peer.</li>
 *     <li>{@code 2} - accept. Accepts a connection request.</li>
 *     <li>{@code 3} - close. The peer is closing the connection.</li>
 *     <li>{@code 4} - reject. The peer refused the connection.</li>
 * </ul>
 * The framing is an implementation detail of the stack: callers of {@link #send} and {@link Socket#poll()} only ever
 * see their own payloads. DHCP messages are the one exception - they are handled before the socket layer, and so are
 * carried without a framing byte.
 * <h2>DHCP</h2>
 * A stack with no address can acquire one with {@link #requestDhcp()}. It broadcasts a {@code DISCOVER} to port
 * {@value DhcpServer#SERVER_PORT}, and applies the resulting {@code ACK} automatically, emitting a {@code net_dhcp}
 * event. If the request is refused, the event is emitted with three empty strings.
 * <h2>Thread safety</h2>
 * All methods synchronise on the stack, so it is safe to use from both the server thread and a block entity tick.
 * {@link NetEventSink} implementations must therefore be prepared to be called from whichever thread drives the stack.
 */
public final class NetStack {
    /** The maximum number of sockets a stack may have open at once. */
    public static final int MAX_SOCKETS = 128;

    /** The maximum payload size, in bytes, that {@link #send} will accept. */
    public static final int MAX_PAYLOAD = 65535;

    /** How long a {@link #connect(int, IpAddress, int) connection attempt} may take before it is abandoned, in milliseconds. */
    public static final long DEFAULT_CONNECT_TIMEOUT = 5000;

    private static final int FRAME_DATA = 0;
    private static final int FRAME_CONNECT = 1;
    private static final int FRAME_ACCEPT = 2;
    private static final int FRAME_CLOSE = 3;
    private static final int FRAME_REJECT = 4;
    /** Payloads with no framing byte. Used by DHCP, which is handled before the socket layer. */
    private static final int FRAME_NONE = -1;

    private final Mac mac;
    private final @Nullable NetEventSink sink;

    private final List<NetInterface> interfaces = new ArrayList<>();
    private final Map<Integer, Socket> sockets = new LinkedHashMap<>();

    private @Nullable IpAddress address;
    private @Nullable IpAddress gateway;
    private @Nullable IpAddress netmask;
    private @Nullable IpAddress dhcpOffered;

    private int nextPacketId = 1;
    private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /**
     * Create a new stack.
     *
     * @param mac  This device's MAC.
     * @param sink The sink to push events to, or {@code null} if this device has no Lua runtime.
     */
    public NetStack(Mac mac, @Nullable NetEventSink sink) {
        this.mac = mac;
        this.sink = sink;
    }

    /**
     * This device's MAC.
     *
     * @return This device's MAC.
     */
    public Mac mac() {
        return mac;
    }

    /**
     * The current address of this device.
     *
     * @return This device's address, or {@code null} if it has not been configured.
     */
    public synchronized @Nullable IpAddress address() {
        return address;
    }

    /**
     * The current gateway of this device.
     *
     * @return This device's gateway, or {@code null}.
     */
    public synchronized @Nullable IpAddress gateway() {
        return gateway;
    }

    /**
     * The current netmask of this device.
     *
     * @return This device's netmask, or {@code null}.
     */
    public synchronized @Nullable IpAddress netmask() {
        return netmask;
    }

    /**
     * Set this device's address.
     *
     * @param address This device's new address, or {@code null} to clear it.
     * @param gateway The gateway to use, or {@code null} for none.
     * @param netmask The netmask for this device's subnet, or {@code null} if unknown.
     */
    public synchronized void setAddress(@Nullable IpAddress address, @Nullable IpAddress gateway, @Nullable IpAddress netmask) {
        this.address = address;
        this.gateway = gateway;
        this.netmask = netmask;
    }

    /**
     * The address most recently offered to this device by a DHCP server, if any.
     *
     * @return The offered address, or {@code null} if no offer is outstanding.
     */
    public synchronized @Nullable IpAddress offeredAddress() {
        return dhcpOffered;
    }

    /**
     * Attach a network interface to this stack.
     *
     * @param iface The interface to add.
     * @throws IllegalArgumentException If this interface is already attached.
     */
    public synchronized void addInterface(NetInterface iface) {
        if (interfaces.contains(iface)) throw new IllegalArgumentException("Interface " + iface.name() + " is already attached.");
        interfaces.add(iface);
    }

    /**
     * Detach a network interface from this stack.
     *
     * @param iface The interface to remove.
     */
    public synchronized void removeInterface(NetInterface iface) {
        interfaces.remove(iface);
    }

    /**
     * Get the interfaces attached to this stack.
     *
     * @return A snapshot of this stack's interfaces.
     */
    public synchronized List<NetInterface> interfaces() {
        return List.copyOf(interfaces);
    }

    /**
     * Set how long a connection attempt may take before it is abandoned.
     *
     * @param timeoutMillis The timeout, in milliseconds. Must be positive.
     * @throws IllegalArgumentException If the timeout is not positive.
     */
    public synchronized void setConnectTimeout(long timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Connect timeout must be positive, got " + timeoutMillis + ".");
        connectTimeout = timeoutMillis;
    }

    /**
     * Get the sockets currently open on this stack.
     *
     * @return The open sockets.
     */
    public synchronized List<Socket> sockets() {
        return List.copyOf(sockets.values());
    }

    /**
     * Open a socket, listening on a local port.
     *
     * @param port The local port to listen on, in the range [1, 65535]. Port {@code 0} is not a valid port to listen on.
     * @return The id of the new socket.
     * @throws IllegalArgumentException If the port is out of range, or is already in use.
     * @throws IllegalStateException    If this stack already has {@value #MAX_SOCKETS} sockets open.
     */
    public synchronized int listen(int port) {
        return listen(port, NetPacket.PROTOCOL_UDP);
    }

    /**
     * Open a socket, listening on a local port for a given protocol.
     *
     * @param port     The local port to listen on, in the range [1, 65535].
     * @param protocol The protocol to listen for, e.g. {@link NetPacket#PROTOCOL_UDP}.
     * @return The id of the new socket.
     * @throws IllegalArgumentException If the port or protocol is out of range, or the port is already in use.
     * @throws IllegalStateException    If this stack already has {@value #MAX_SOCKETS} sockets open.
     */
    public synchronized int listen(int port, int protocol) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be in the range [1, 65535], got " + port + ".");
        if (protocol < 0 || protocol > 255) throw new IllegalArgumentException("Protocol must be in the range [0, 255], got " + protocol + ".");
        if (sockets.size() >= MAX_SOCKETS) throw new IllegalStateException("Too many open sockets (max " + MAX_SOCKETS + ").");

        for (var socket : sockets.values()) {
            if (socket.localPort() == port && socket.protocol() == protocol) {
                throw new IllegalArgumentException("Port " + port + " is already in use.");
            }
        }

        // Reuse the lowest free id, so that ids stay small and predictable for Lua code.
        var id = 1;
        while (sockets.containsKey(id)) id++;

        sockets.put(id, new Socket(id, port, protocol));
        return id;
    }

    /**
     * Close a socket, releasing its port.
     *
     * @param id The id of the socket to close.
     */
    public synchronized void close(int id) {
        var socket = sockets.remove(id);
        if (socket == null) return;

        var remote = socket.remoteAddress();
        if (remote != null) {
            sendFrame(remote, socket.remotePort(), socket.localPort(), socket.protocol(), FRAME_CLOSE);
            emit("net_disconnect", id, remote.toString(), socket.remotePort(), "closed");
        }
        socket.close();
    }

    /**
     * Close all sockets.
     */
    public synchronized void closeAll() {
        for (var id : List.copyOf(sockets.keySet())) {
            close(id);
        }
    }

    /**
     * Get a socket by its id.
     *
     * @param id The socket id.
     * @return The socket, or {@code null} if there is no socket with that id.
     */
    public synchronized @Nullable Socket socket(int id) {
        return sockets.get(id);
    }

    /**
     * Send a message to an address and port, without needing a bound socket.
     *
     * @param dst      The destination address.
     * @param dstPort  The destination port.
     * @param srcPort  The source port to send from.
     * @param protocol The protocol to use, e.g. {@link NetPacket#PROTOCOL_UDP}.
     * @param payload  The message payload.
     * @return Whether the message was given to at least one interface. This does not guarantee delivery.
     * @throws IllegalArgumentException If any port or protocol is out of range, or the payload is too large.
     */
    public synchronized boolean send(IpAddress dst, int dstPort, int srcPort, int protocol, byte[] payload) {
        if (payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Payload is too large (" + payload.length + " bytes, max " + MAX_PAYLOAD + ").");
        }
        if (address == null) return false;
        return sendFrame(dst, dstPort, srcPort, protocol, FRAME_DATA, payload);
    }

    /**
     * Connect a socket to a remote peer. This performs a simple handshake: a connect message is sent to the peer, and
     * the socket becomes {@linkplain Socket#isConnected() connected} once it is accepted. On success a
     * {@code net_connect} event is emitted; on rejection or timeout, {@code net_disconnect}.
     *
     * @param id      The id of the socket to connect.
     * @param dst     The address to connect to.
     * @param dstPort The port to connect to.
     * @return Whether the connection attempt was started.
     */
    public synchronized boolean connect(int id, IpAddress dst, int dstPort) {
        var socket = sockets.get(id);
        if (socket == null || socket.isClosed() || socket.isConnected()) return false;
        if (dstPort < 1 || dstPort > 65535) return false;
        if (address == null) return false;

        socket.connectPending(dst, dstPort);
        return sendFrame(dst, dstPort, socket.localPort(), socket.protocol(), FRAME_CONNECT);
    }

    /**
     * Send a DHCP {@code DISCOVER} to find an address for this device.
     *
     * @return Whether the request was given to an interface.
     */
    public synchronized boolean requestDhcp() {
        dhcpOffered = null;
        var message = new DhcpMessage(DhcpMessage.DISCOVER, mac, null, null, null, null, null, null);
        return sendFrame(IpAddress.BROADCAST, DhcpServer.SERVER_PORT, DhcpServer.CLIENT_PORT, NetPacket.PROTOCOL_UDP, FRAME_NONE, message.encode());
    }

    /**
     * Feed a received packet into the stack. This is called by the mod layer when a packet is received on an interface.
     *
     * @param from   The interface the packet arrived on.
     * @param packet The packet which was received.
     */
    public synchronized void receive(NetInterface from, NetPacket packet) {
        if (packet.ttl() <= 0) return;

        var self = address;
        var dst = packet.dst();
        if (!dst.isBroadcast() && (self == null || !dst.equals(self))) return;
        // Ignore anything we sent ourselves, in case an interface loops packets back to us.
        if (self != null && packet.src().equals(self)) return;

        if (packet.protocol() == NetPacket.PROTOCOL_UDP && packet.dstPort() == DhcpServer.CLIENT_PORT && handleDhcp(packet)) return;

        for (var socket : sockets.values()) {
            if (socket.localPort() != packet.dstPort() || socket.protocol() != packet.protocol()) continue;
            handleSocket(socket, packet);
            return;
        }
    }

    /**
     * Drive periodic work, such as timing out connection attempts.
     *
     * @param nowMillis The current time, in milliseconds since the epoch.
     */
    public synchronized void tick(long nowMillis) {
        for (var socket : List.copyOf(sockets.values())) {
            var pending = socket.pendingAddress();
            if (pending == null) continue;
            if (nowMillis - socket.connectStart() < connectTimeout) continue;

            var pendingPort = socket.pendingPort();
            socket.setDisconnected();
            emit("net_disconnect", socket.id(), pending.toString(), pendingPort, "timeout");
        }
    }

    /**
     * Reset this stack, closing all sockets and clearing its address.
     */
    public synchronized void reset() {
        closeAll();
        setAddress(null, null, null);
        dhcpOffered = null;
    }

    private void handleSocket(Socket socket, NetPacket packet) {
        var payload = packet.payload();
        if (payload.length == 0) return;

        var frame = payload[0];
        var body = new byte[payload.length - 1];
        System.arraycopy(payload, 1, body, 0, body.length);
        var src = packet.src();
        var srcPort = packet.srcPort();

        switch (frame) {
            case FRAME_DATA -> {
                socket.deliver(src, srcPort, body);
                emit("net_message", src.toString(), socket.localPort(), new String(body, StandardCharsets.UTF_8));
            }
            case FRAME_CONNECT -> sendFrame(src, srcPort, socket.localPort(), socket.protocol(), FRAME_ACCEPT);
            case FRAME_ACCEPT -> {
                var pending = socket.pendingAddress();
                if (pending != null && pending.equals(src) && socket.pendingPort() == srcPort) {
                    socket.setConnected(src, srcPort);
                    emit("net_connect", socket.id(), src.toString(), srcPort);
                }
            }
            case FRAME_CLOSE -> {
                if (socket.isConnected()) {
                    socket.setDisconnected();
                    emit("net_disconnect", socket.id(), src.toString(), srcPort, "closed by remote");
                }
            }
            case FRAME_REJECT -> {
                var pending = socket.pendingAddress();
                if (pending != null) {
                    socket.setDisconnected();
                    emit("net_disconnect", socket.id(), pending.toString(), socket.pendingPort(), "refused");
                }
            }
            default -> {
                // Unknown frame types are dropped, so that the protocol can be extended later.
            }
        }
    }

    private boolean handleDhcp(NetPacket packet) {
        DhcpMessage message;
        try {
            message = DhcpMessage.decode(packet.payload());
        } catch (IllegalArgumentException e) {
            return false;
        }

        switch (message.operation()) {
            case DhcpMessage.OFFER -> {
                var offered = message.address();
                if (offered == null) return false;
                dhcpOffered = offered;
                requestAddress(offered, message.server(), message.hostname());
                return true;
            }
            case DhcpMessage.ACK -> {
                var assigned = message.address();
                if (assigned == null) return false;
                dhcpOffered = null;
                setAddress(assigned, message.gateway(), message.netmask());
                emit("net_dhcp", assigned.toString(), orEmpty(message.netmask()), orEmpty(message.gateway()));
                return true;
            }
            case DhcpMessage.NAK -> {
                dhcpOffered = null;
                emit("net_dhcp", "", "", "");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void requestAddress(IpAddress offered, @Nullable IpAddress server, @Nullable String hostname) {
        var destination = server != null ? server : IpAddress.BROADCAST;
        var message = new DhcpMessage(DhcpMessage.REQUEST, mac, offered, null, null, server, hostname, null);
        sendFrame(destination, DhcpServer.SERVER_PORT, DhcpServer.CLIENT_PORT, NetPacket.PROTOCOL_UDP, FRAME_NONE, message.encode());
    }

    private boolean sendFrame(IpAddress dst, int dstPort, int srcPort, int protocol, int frame) {
        return sendFrame(dst, dstPort, srcPort, protocol, frame, new byte[0]);
    }

    private boolean sendFrame(IpAddress dst, int dstPort, int srcPort, int protocol, int frame, byte[] payload) {
        if (interfaces.isEmpty()) return false;
        if (dstPort < 0 || dstPort > 65535 || srcPort < 0 || srcPort > 65535) return false;

        var body = payload;
        if (frame >= 0) {
            body = new byte[payload.length + 1];
            body[0] = (byte) frame;
            System.arraycopy(payload, 0, body, 1, payload.length);
        }

        var targets = dst.isBroadcast() ? List.copyOf(interfaces) : List.<NetInterface>of(interfaces.get(0));
        var currentAddress = address;
        var src = currentAddress == null ? IpAddress.ANY : currentAddress;
        for (var iface : targets) {
            iface.send(new NetPacket(src, dst, iface.mac(), null, protocol, srcPort, dstPort, NetPacket.DEFAULT_TTL, nextPacketId, body));
        }

        nextPacketId = nextPacketId == 0xFFFF ? 1 : nextPacketId + 1;
        return !targets.isEmpty();
    }

    private void emit(String event, Object... args) {
        var sink = this.sink;
        if (sink == null) return;
        sink.queueEvent(event, args);
    }

    private static String orEmpty(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }
}
