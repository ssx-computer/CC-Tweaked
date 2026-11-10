// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The routing and firewalling logic of the network core block.
 * <p>
 * The core has one "port" per network segment it is attached to (a switch port, a wired modem or a wireless
 * interface). Each port knows the subnet it is connected to, and {@link #route(int, NetPacket, IpAddress)} decides
 * what to do with a packet which arrives on a given port.
 * <p>
 * Packets addressed to the core itself, or broadcast, are {@linkplain Decision.Kind#LOCAL delivered locally}. Packets
 * for another host are {@linkplain Decision.Kind#FORWARD forwarded} out of the appropriate port, with the TTL
 * decremented; if the TTL runs out, the packet is {@linkplain Decision.Kind#DROP dropped}, as it would loop forever.
 * <p>
 * Firewall rules are checked in insertion order and the first match wins. If no rule matches, the packet is allowed.
 * <p>
 * This class is thread-safe; all methods synchronise on the router.
 */
public final class Router {
    private final Map<Integer, Subnet> subnets = new HashMap<>();
    private final List<Route> routes = new ArrayList<>();
    private final List<FirewallRule> rules = new ArrayList<>();

    /**
     * What should happen to a packet.
     *
     * @param kind    What to do with the packet.
     * @param outPort The port to send the packet out of. Only meaningful for {@link Kind#FORWARD}.
     * @param nextHop The address to send the packet to. Only meaningful for {@link Kind#FORWARD}.
     */
    public record Decision(Kind kind, int outPort, @Nullable IpAddress nextHop) {
        /** The action to take on a packet. */
        public enum Kind {
            /** Discard the packet. */
            DROP,
            /** Deliver the packet to this device. */
            LOCAL,
            /** Send the packet out of another port. */
            FORWARD,
        }

        static Decision drop() {
            return new Decision(Kind.DROP, -1, null);
        }

        static Decision local() {
            return new Decision(Kind.LOCAL, -1, null);
        }

        static Decision forward(int port, IpAddress nextHop) {
            return new Decision(Kind.FORWARD, port, nextHop);
        }

        /**
         * Prepare a packet for sending, after this decision has been made. For forwarded packets this decrements the
         * TTL, so that the packet is eventually discarded if it is stuck in a routing loop.
         *
         * @param packet The packet being routed.
         * @return The packet to actually send.
         */
        public NetPacket prepare(NetPacket packet) {
            return kind == Kind.FORWARD ? packet.withTtl(packet.ttl() - 1) : packet;
        }
    }

    private record Subnet(IpAddress address, IpAddress netmask) {
        boolean contains(IpAddress address) {
            return address.inSubnet(this.address, IpAddress.prefixLength(netmask));
        }
    }

    private record Route(IpAddress network, IpAddress netmask, IpAddress gateway, int port, int prefix) {
        boolean matches(IpAddress address) {
            return address.inSubnet(network, prefix);
        }
    }

    /**
     * Record the subnet which a port is connected to.
     *
     * @param port    The port number.
     * @param subnet  The network address of the attached subnet, e.g. {@code 10.0.0.0}.
     * @param netmask The subnet's netmask, e.g. {@code 255.255.255.0}.
     */
    public synchronized void setSubnet(int port, IpAddress subnet, IpAddress netmask) {
        subnets.put(port, new Subnet(subnet, netmask));
    }

    /**
     * Remove a port from the router, forgetting its subnet.
     *
     * @param port The port number.
     */
    public synchronized void removePort(int port) {
        subnets.remove(port);
    }

    /**
     * Add a static route.
     *
     * @param network The network address of the destination network.
     * @param netmask The netmask of the destination network.
     * @param gateway The next hop for matching packets.
     * @param port    The port to send matching packets out of.
     */
    public synchronized void addRoute(IpAddress network, IpAddress netmask, IpAddress gateway, int port) {
        routes.add(new Route(network, netmask, gateway, port, IpAddress.prefixLength(netmask)));
        // Longest-prefix match wins, so keep the table sorted. This is stable, so equal prefixes keep insertion order.
        routes.sort(Comparator.comparingInt(Route::prefix).reversed());
    }

    /**
     * Remove all static routes.
     */
    public synchronized void clearRoutes() {
        routes.clear();
    }

    /**
     * Add a firewall rule.
     *
     * @param rule The rule to add.
     * @throws IllegalArgumentException If a rule with the same {@linkplain FirewallRule#id() id} already exists.
     */
    public synchronized void addRule(FirewallRule rule) {
        for (var existing : rules) {
            if (existing.id() == rule.id()) throw new IllegalArgumentException("Duplicate firewall rule id " + rule.id() + ".");
        }
        rules.add(rule);
    }

    /**
     * Remove a firewall rule.
     *
     * @param id The id of the rule to remove.
     * @return Whether a rule was removed.
     */
    public synchronized boolean removeRule(int id) {
        return rules.removeIf(rule -> rule.id() == id);
    }

    /**
     * Get the current firewall rules, in match order.
     *
     * @return The current firewall rules.
     */
    public synchronized List<FirewallRule> rules() {
        return List.copyOf(rules);
    }

    /**
     * Whether a packet travelling in a given direction is permitted by the firewall. The first matching rule decides;
     * if no rule matches, the packet is allowed.
     *
     * @param direction The direction the packet is travelling in.
     * @param packet    The packet to check.
     * @return Whether the packet is allowed.
     */
    public synchronized boolean allowed(FirewallRule.Direction direction, NetPacket packet) {
        for (var rule : rules) {
            if (rule.matches(direction, packet)) return rule.action() == FirewallRule.Action.ALLOW;
        }
        return true;
    }

    /**
     * Decide what to do with a packet which arrived on a given port.
     *
     * @param inPort The port the packet arrived on.
     * @param packet The packet.
     * @param self   This device's own address, used to detect packets addressed to us.
     * @return What to do with the packet.
     */
    public synchronized Decision route(int inPort, NetPacket packet, IpAddress self) {
        if (packet.ttl() <= 0) return Decision.drop();

        var dst = packet.dst();
        if (dst.equals(self) || dst.isBroadcast() || isLocalBroadcast(inPort, dst)) {
            return allowed(FirewallRule.Direction.IN, packet) ? Decision.local() : Decision.drop();
        }

        // Packets for our own subnet are sent directly to the destination.
        var subnet = subnets.get(inPort);
        if (subnet != null && subnet.contains(dst)) {
            return forward(packet, inPort, dst);
        }

        // Otherwise consult the routing table. Routes are sorted by prefix length, so this is a longest-prefix match.
        for (var route : routes) {
            if (route.matches(dst)) return forward(packet, route.port(), route.gateway());
        }

        return Decision.drop();
    }

    private Decision forward(NetPacket packet, int port, IpAddress nextHop) {
        // A TTL of 1 will not survive the hop, so drop it now rather than sending a packet with a zero TTL.
        if (packet.ttl() <= 1) return Decision.drop();
        return allowed(FirewallRule.Direction.FORWARD, packet) ? Decision.forward(port, nextHop) : Decision.drop();
    }

    private boolean isLocalBroadcast(int inPort, IpAddress dst) {
        var subnet = subnets.get(inPort);
        if (subnet == null) return false;

        var mask = IpAddress.prefixMask(IpAddress.prefixLength(subnet.netmask()));
        return dst.value() == (subnet.address().value() | ~mask);
    }
}
