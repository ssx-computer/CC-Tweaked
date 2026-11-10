// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A DHCP server, run by the network core block. It hands out addresses from a pool to devices which ask for one.
 * <p>
 * The server listens on port {@value #SERVER_PORT} and replies to clients on {@value #CLIENT_PORT}, using the message
 * format described in {@link DhcpMessage}. A typical exchange is:
 * <ol>
 *     <li>Client broadcasts {@code DISCOVER}.</li>
 *     <li>Server replies with {@code OFFER} (or {@code NAK} if the client is not permitted).</li>
 *     <li>Client broadcasts {@code REQUEST} for the offered address.</li>
 *     <li>Server replies with {@code ACK} (recording the lease) or {@code NAK}.</li>
 * </ol>
 * Addresses can be reserved for a particular MAC with {@link #reserve(Mac, IpAddress)} (such leases never expire), and
 * clients can be explicitly allowed or denied with {@link #allow(Mac)} and {@link #deny(Mac)}. By default all clients
 * are allowed.
 * <p>
 * This class is thread-safe; all methods synchronise on the server.
 */
public final class DhcpServer {
    /** The port the server receives requests on. */
    public static final int SERVER_PORT = 67;

    /** The port the client receives replies on. */
    public static final int CLIENT_PORT = 68;

    /** The default lease time, in seconds. */
    public static final int DEFAULT_LEASE_TIME = 60 * 60;

    private final IpAddress server;
    private final IpAddress netmask;

    private IpAddress poolFrom;
    private IpAddress poolTo;
    private int leaseTimeSeconds = DEFAULT_LEASE_TIME;
    private @Nullable IpAddress gateway;

    private final Map<Mac, Lease> leases = new HashMap<>();
    private final Map<Mac, IpAddress> reservations = new HashMap<>();
    private final Set<Mac> allowed = new HashSet<>();
    private final Set<Mac> denied = new HashSet<>();
    private boolean defaultPolicy = true;

    private int nextId;

    /**
     * A snapshot of a single address lease.
     *
     * @param mac             The client this lease belongs to.
     * @param address         The leased address.
     * @param expiresAtMillis The time at which the lease expires, in milliseconds since the epoch. Reserved leases
     *                        use {@link Long#MAX_VALUE}.
     * @param hostname        The client's requested hostname, if any.
     * @param reserved        Whether this lease is a fixed reservation.
     */
    public record Lease(Mac mac, IpAddress address, long expiresAtMillis, @Nullable String hostname, boolean reserved) {
    }

    /**
     * Create a new DHCP server.
     *
     * @param server  The server's own address.
     * @param netmask The subnet mask handed out to clients.
     */
    public DhcpServer(IpAddress server, IpAddress netmask) {
        this.server = server;
        this.netmask = netmask;
        this.poolFrom = server;
        this.poolTo = server;
    }

    /**
     * Set the range of addresses that may be handed out. The range is inclusive.
     *
     * @param from The first address in the pool.
     * @param to   The last address in the pool.
     * @throws IllegalArgumentException If {@code from} is greater than {@code to}.
     */
    public synchronized void setPool(IpAddress from, IpAddress to) {
        if (Integer.compareUnsigned(from.value(), to.value()) > 0) {
            throw new IllegalArgumentException("Pool start " + from + " must not be after pool end " + to + ".");
        }
        poolFrom = from;
        poolTo = to;
    }

    /**
     * Set how long a lease lasts, in seconds.
     *
     * @param seconds The lease time, in seconds. Must be positive.
     * @throws IllegalArgumentException If the lease time is not positive.
     */
    public synchronized void setLeaseTime(int seconds) {
        if (seconds <= 0) throw new IllegalArgumentException("Lease time must be positive, got " + seconds + ".");
        leaseTimeSeconds = seconds;
    }

    /**
     * Set the gateway handed out to clients.
     *
     * @param gateway The gateway, or {@code null} for none.
     */
    public synchronized void setGateway(@Nullable IpAddress gateway) {
        this.gateway = gateway;
    }

    /**
     * Get a snapshot of the current leases.
     *
     * @return The current leases.
     */
    public synchronized List<Lease> leases() {
        return List.copyOf(leases.values());
    }

    /**
     * Reserve a fixed address for a MAC. Reserved leases never expire and are not handed to any other client.
     *
     * @param mac     The MAC to reserve an address for.
     * @param address The address to reserve.
     */
    public synchronized void reserve(Mac mac, IpAddress address) {
        reservations.put(mac, address);
    }

    /**
     * Explicitly allow a MAC to obtain a lease, overriding a {@link #deny(Mac)}.
     *
     * @param mac The MAC to allow.
     */
    public synchronized void allow(Mac mac) {
        allowed.add(mac);
        denied.remove(mac);
    }

    /**
     * Explicitly deny a MAC, so that requests from it are answered with a {@code NAK}.
     *
     * @param mac The MAC to deny.
     */
    public synchronized void deny(Mac mac) {
        denied.add(mac);
        allowed.remove(mac);
    }

    /**
     * Set the policy for clients which are neither explicitly allowed nor denied.
     *
     * @param allow Whether unknown clients should be allowed. Defaults to {@code true}.
     */
    public synchronized void setDefaultPolicy(boolean allow) {
        defaultPolicy = allow;
    }

    /**
     * Handle a DHCP request, returning the packet to reply with.
     *
     * @param request   The incoming request. Its payload should be a {@link DhcpMessage} from port {@value #CLIENT_PORT}.
     * @param nowMillis The current time, in milliseconds since the epoch.
     * @return The reply packet, or {@code null} if the request should be silently ignored.
     */
    public synchronized @Nullable NetPacket handle(NetPacket request, long nowMillis) {
        DhcpMessage message;
        try {
            message = DhcpMessage.decode(request.payload());
        } catch (IllegalArgumentException e) {
            return null;
        }

        tick(nowMillis);

        var messageMac = message.mac();
        var mac = messageMac != null ? messageMac : request.srcMac();
        if (mac == null) return null;

        if (!isPermitted(mac)) return nak(mac, "This device is not permitted to join the network.", request);

        return switch (message.operation()) {
            case DhcpMessage.DISCOVER -> {
                var address = allocate(mac, nowMillis, message.address());
                yield address == null
                    ? nak(mac, "No addresses available.", request)
                    : reply(DhcpMessage.OFFER, mac, address, message.hostname(), null, request);
            }
            case DhcpMessage.REQUEST -> {
                var requested = message.address();
                var address = allocate(mac, nowMillis, requested);
                if (address == null) {
                    yield nak(mac, "No addresses available.", request);
                }
                if (requested == null || !requested.equals(address)) {
                    yield nak(mac, "The requested address is not available.", request);
                }
                commit(mac, address, message.hostname(), nowMillis);
                yield reply(DhcpMessage.ACK, mac, address, message.hostname(), null, request);
            }
            default -> null;
        };
    }

    /**
     * Expire any leases which are past their lease time. Reserved leases are never expired.
     *
     * @param nowMillis The current time, in milliseconds since the epoch.
     */
    public synchronized void tick(long nowMillis) {
        leases.values().removeIf(lease -> !lease.reserved() && lease.expiresAtMillis() <= nowMillis);
    }

    private boolean isPermitted(Mac mac) {
        if (denied.contains(mac)) return false;
        if (allowed.contains(mac)) return true;
        return defaultPolicy;
    }

    private @Nullable IpAddress allocate(Mac mac, long nowMillis, @Nullable IpAddress preferred) {
        var reserved = reservations.get(mac);
        if (reserved != null) return reserved;

        if (preferred != null && inPool(preferred) && isFree(preferred, mac, nowMillis)) return preferred;

        var existing = leases.get(mac);
        if (existing != null && existing.expiresAtMillis() > nowMillis) return existing.address();

        for (var value = poolFrom.value(); ; value++) {
            var candidate = new IpAddress(value);
            if (isFree(candidate, mac, nowMillis)) return candidate;
            if (value == poolTo.value()) break;
        }
        return null;
    }

    private boolean isFree(IpAddress address, Mac mac, long nowMillis) {
        var reservedFor = reservationFor(address);
        if (reservedFor != null) return reservedFor.equals(mac);

        for (var lease : leases.values()) {
            if (lease.address().equals(address) && !lease.mac().equals(mac)) return false;
        }
        return true;
    }

    private @Nullable Mac reservationFor(IpAddress address) {
        for (var entry : reservations.entrySet()) {
            if (entry.getValue().equals(address)) return entry.getKey();
        }
        return null;
    }

    private boolean inPool(IpAddress address) {
        return Integer.compareUnsigned(address.value(), poolFrom.value()) >= 0
            && Integer.compareUnsigned(address.value(), poolTo.value()) <= 0;
    }

    private void commit(Mac mac, IpAddress address, @Nullable String hostname, long nowMillis) {
        var reservation = reservations.get(mac);
        var reserved = reservation != null && reservation.equals(address);
        var expires = reserved ? Long.MAX_VALUE : nowMillis + leaseTimeSeconds * 1000L;
        leases.put(mac, new Lease(mac, address, expires, hostname, reserved));
    }

    private NetPacket reply(String operation, Mac mac, IpAddress address, @Nullable String hostname, @Nullable String reason, NetPacket request) {
        var message = new DhcpMessage(operation, mac, address, netmask, gateway, server, hostname, reason);
        var dst = request.src().isAny() || request.src().isBroadcast() ? IpAddress.BROADCAST : request.src();
        return new NetPacket(
            server, dst, null, request.srcMac(),
            NetPacket.PROTOCOL_UDP, SERVER_PORT, CLIENT_PORT, NetPacket.DEFAULT_TTL, nextId++, message.encode()
        );
    }

    private NetPacket nak(Mac mac, String reason, NetPacket request) {
        return reply(DhcpMessage.NAK, mac, null, null, reason, request);
    }
}
