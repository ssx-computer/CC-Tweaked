// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A socket owned by a {@link NetStack}, created with {@link NetStack#listen(int)}.
 * <p>
 * A socket is bound to a local port, and receives every packet addressed to that port. It optionally has a remote
 * peer: once {@link NetStack#connect(int, IpAddress, int) connected}, the socket records the remote address and port,
 * and {@link #isConnected()} returns {@code true}.
 * <p>
 * Sockets do not need to be connected to receive messages - an unconnected socket behaves like a UDP socket, and is
 * simply handed everything arriving on its port. Connecting gives "TCP-ish" semantics: a connect handshake is
 * performed, and a {@code net_connect} event is emitted on success, or {@code net_disconnect} on failure.
 * <p>
 * Sockets are not thread-safe on their own; all access goes through the owning {@link NetStack}.
 */
public final class Socket {
    private static final int DEFAULT_INBOX_SIZE = 32;

    private final int id;
    private final int localPort;
    private final int protocol;
    private final ArrayDeque<Message> inbox = new ArrayDeque<>();

    private int inboxSize = DEFAULT_INBOX_SIZE;
    private @Nullable IpAddress remoteAddress;
    private int remotePort;
    private boolean connected;
    private boolean closed;
    private @Nullable IpAddress pendingAddress;
    private int pendingPort;
    private long connectStart;

    Socket(int id, int localPort, int protocol) {
        this.id = id;
        this.localPort = localPort;
        this.protocol = protocol;
    }

    /**
     * A message received by a socket.
     *
     * @param from    The address the message came from.
     * @param port    The port the message came from.
     * @param payload The message payload. This is a copy, so mutating it will not affect the socket.
     */
    public record Message(IpAddress from, int port, byte[] payload) {
        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public String toString() {
            return "Message[" + from + ":" + port + ", " + payload.length + " bytes]";
        }
    }

    /**
     * This socket's identifier, as returned by {@link NetStack#listen(int)}.
     *
     * @return The socket id.
     */
    public int id() {
        return id;
    }

    /**
     * The local port this socket is bound to.
     *
     * @return The local port.
     */
    public int localPort() {
        return localPort;
    }

    /**
     * The protocol this socket was created with.
     *
     * @return The protocol number, e.g. {@link NetPacket#PROTOCOL_UDP}.
     */
    public int protocol() {
        return protocol;
    }

    /**
     * Whether this socket is currently connected to a remote peer.
     *
     * @return Whether this socket is connected.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * The address of the remote peer, if this socket is connected.
     *
     * @return The remote address, or {@code null}.
     */
    public @Nullable IpAddress remoteAddress() {
        return remoteAddress;
    }

    /**
     * The port of the remote peer, or {@code 0} if this socket is not connected.
     *
     * @return The remote port.
     */
    public int remotePort() {
        return remotePort;
    }

    /**
     * Whether this socket has been closed.
     *
     * @return Whether this socket is closed.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Set how many unread messages this socket will buffer. Once the inbox is full, new messages are dropped.
     *
     * @param size The maximum number of buffered messages. Must not be negative.
     * @throws IllegalArgumentException If the size is negative.
     */
    public void setInboxSize(int size) {
        if (size < 0) throw new IllegalArgumentException("Inbox size must not be negative, got " + size + ".");
        inboxSize = size;
        while (inbox.size() > inboxSize) inbox.removeFirst();
    }

    /**
     * Get the number of unread messages waiting on this socket.
     *
     * @return The number of buffered messages.
     */
    public int pending() {
        return inbox.size();
    }

    /**
     * Take the oldest unread message from this socket.
     *
     * @return The oldest message, or {@code null} if the inbox is empty.
     */
    public @Nullable Message poll() {
        return inbox.pollFirst();
    }

    /**
     * Get all unread messages, emptying the inbox.
     *
     * @return All buffered messages, oldest first.
     */
    public List<Message> drain() {
        var messages = new ArrayList<Message>(inbox);
        inbox.clear();
        return messages;
    }

    void deliver(IpAddress from, int port, byte[] payload) {
        if (inboxSize == 0) return;
        while (inbox.size() >= inboxSize) inbox.removeFirst();
        inbox.addLast(new Message(from, port, payload));
    }

    void connectPending(IpAddress address, int port) {
        pendingAddress = address;
        pendingPort = port;
        connectStart = System.currentTimeMillis();
    }

    /**
     * The time at which the current connection attempt started, in milliseconds since the epoch.
     *
     * @return The start time of the current connection attempt.
     */
    long connectStart() {
        return connectStart;
    }

    @Nullable IpAddress pendingAddress() {
        return pendingAddress;
    }

    int pendingPort() {
        return pendingPort;
    }

    void clearPending() {
        pendingAddress = null;
        pendingPort = 0;
    }

    void setConnected(IpAddress address, int port) {
        remoteAddress = address;
        remotePort = port;
        connected = true;
        clearPending();
        connectStart = 0;
    }

    void setDisconnected() {
        remoteAddress = null;
        remotePort = 0;
        connected = false;
        clearPending();
        connectStart = 0;
    }

    void close() {
        closed = true;
        inbox.clear();
    }
}
