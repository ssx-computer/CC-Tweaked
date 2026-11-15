// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A MAC-style forwarding table, mapping a segment address to the port it was last seen on.
 * <p>
 * This is the forwarding logic of a {@link NetworkSwitchBlockEntity}, and is deliberately free of any Minecraft (or
 * even ComputerCraft) types so it can be tested standalone. Addresses are learnt with {@link #learn(long, int)} and
 * queried with {@link #lookup(long)}, while {@link #clearPort(int)} drops everything learnt on a port whose network has
 * gone away.
 * <p>
 * TODO(core): Once the virtual network stack exists, {@link NetworkSwitchBlockEntity} should learn the source address
 *  of each packet it receives and only flood to the other ports when {@link #lookup(long)} doesn't know the
 *  destination. The core layer is growing an equivalent table keyed by a MAC address ({@code SwitchTable}, in
 *  {@code dan200.computercraft.core.internet}) — once that API is final, this class should either be replaced by it or
 *  delegate to it, and the addresses here will become MACs rather than {@code long}s.
 */
public final class LearningSwitch {
    /**
     * The number of ports on a switch.
     */
    public static final int PORTS = 6;

    /**
     * The maximum number of addresses we remember. If we exceed this, we drop the whole table, which is equivalent to
     * flooding until we learn the addresses again.
     */
    private static final int MAX_ENTRIES = 256;

    private final Map<Long, Integer> table = new ConcurrentHashMap<>();

    /**
     * Record that an address was seen on a specific port.
     *
     * @param address The address which was seen.
     * @param port    The port the address was seen on.
     */
    public void learn(long address, int port) {
        checkPort(port);
        if (table.size() >= MAX_ENTRIES) table.clear();
        table.put(address, port);
    }

    /**
     * Look up the port an address was last seen on.
     *
     * @param address The address to look up.
     * @return The port this address was seen on, or {@code null} if we've not seen it before.
     */
    public @Nullable Integer lookup(long address) {
        return table.get(address);
    }

    /**
     * Forget every address which was learnt on a specific port.
     *
     * @param port The port which has gone away.
     */
    public void clearPort(int port) {
        checkPort(port);
        table.values().removeIf(seenOn -> seenOn == port);
    }

    /**
     * Forget every address we know about.
     */
    public void clear() {
        table.clear();
    }

    private static void checkPort(int port) {
        if (port < 0 || port >= PORTS) throw new IllegalArgumentException("Invalid port " + port);
    }
}
