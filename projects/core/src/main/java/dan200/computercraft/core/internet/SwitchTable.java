// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The MAC learning table of a network switch.
 * <p>
 * A switch has several ports, each of which is its own network segment. As packets arrive, the switch
 * {@linkplain #learn(Mac, int) learns} which MAC is reachable from which port. When a packet needs to be sent to a
 * known MAC, it is only sent out of the port that MAC was last seen on.
 * <p>
 * If the destination MAC is unknown, the packet is {@linkplain #flood(Mac, int) flooded} out of every port except the
 * one it arrived on, exactly as a real switch does.
 * <p>
 * Entries are timestamped with {@link System#currentTimeMillis()} when learnt, and forgotten by {@link #tick(long)}
 * once they have not been seen for {@link #setEntryTimeout(long) the entry timeout}. Callers must therefore drive
 * {@code tick} with a timestamp from the same clock.
 * <p>
 * This class is thread-safe; all methods synchronise on the table.
 */
public final class SwitchTable {
    /** The default amount of time an entry may go unused before it is forgotten. */
    public static final long DEFAULT_ENTRY_TIMEOUT = 5 * 60 * 1000;

    private final Map<Mac, Entry> entries = new HashMap<>();
    private final List<Integer> ports = new ArrayList<>();
    private long entryTimeout = DEFAULT_ENTRY_TIMEOUT;

    private record Entry(int port, long lastSeen) {
    }

    /**
     * Register a port on this switch. Ports must be registered before packets can be flooded to them.
     *
     * @param port The port number. Must not be negative.
     * @throws IllegalArgumentException If the port is negative.
     */
    public synchronized void addPort(int port) {
        if (port < 0) throw new IllegalArgumentException("Port must not be negative, got " + port + ".");
        if (!ports.contains(port)) ports.add(port);
    }

    /**
     * Unregister a port, forgetting any MACs which were learnt on it.
     *
     * @param port The port number.
     */
    public synchronized void removePort(int port) {
        ports.remove((Object) port);
        entries.values().removeIf(entry -> entry.port() == port);
    }

    /**
     * Get the ports registered on this switch, in the order they were added.
     *
     * @return The registered ports.
     */
    public synchronized List<Integer> ports() {
        return List.copyOf(ports);
    }

    /**
     * Set how long a MAC may go without being seen before it is forgotten.
     *
     * @param timeoutMillis The timeout, in milliseconds. Must be positive.
     * @throws IllegalArgumentException If the timeout is not positive.
     */
    public synchronized void setEntryTimeout(long timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout must be positive, got " + timeoutMillis + ".");
        entryTimeout = timeoutMillis;
    }

    /**
     * Record that a MAC was seen on a port.
     *
     * @param mac  The MAC which was seen.
     * @param port The port it was seen on. Must not be negative.
     * @throws IllegalArgumentException If the port is negative.
     */
    public synchronized void learn(Mac mac, int port) {
        if (port < 0) throw new IllegalArgumentException("Port must not be negative, got " + port + ".");
        addPort(port);
        entries.put(mac, new Entry(port, System.currentTimeMillis()));
    }

    /**
     * Find the port a MAC is reachable from.
     *
     * @param mac The MAC to look up.
     * @return The port the MAC was last seen on, or {@code null} if it is unknown.
     */
    public synchronized @Nullable Integer portFor(Mac mac) {
        var entry = entries.get(mac);
        return entry == null ? null : entry.port();
    }

    /**
     * Get the ports a packet for a given MAC should be sent out of.
     * <p>
     * If the MAC is known, this is just the port it was learnt on. Otherwise the packet is flooded: this is every
     * registered port except {@code incomingPort}, so that we do not send the packet back where it came from.
     *
     * @param mac          The destination MAC.
     * @param incomingPort The port the packet arrived on, which is excluded from any flooding.
     * @return The ports to send the packet out of.
     */
    public synchronized List<Integer> flood(Mac mac, int incomingPort) {
        var port = portFor(mac);
        if (port != null) return List.of(port);

        var result = new ArrayList<Integer>(ports.size());
        for (var candidate : ports) {
            if (candidate != incomingPort) result.add(candidate);
        }
        return result;
    }

    /**
     * Expire any entries which have not been seen for longer than the entry timeout.
     *
     * @param nowMillis The current time, in milliseconds since the epoch.
     */
    public synchronized void tick(long nowMillis) {
        entries.values().removeIf(entry -> nowMillis - entry.lastSeen() >= entryTimeout);
    }
}
