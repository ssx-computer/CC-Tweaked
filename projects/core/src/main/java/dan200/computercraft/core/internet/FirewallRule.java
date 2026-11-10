// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

/**
 * A single firewall rule. Rules are matched in the order they were added to the {@link Router}, and the first matching
 * rule decides the fate of a packet.
 * <p>
 * Every criterion is optional: a {@code null} address or MAC matches anything, and a {@code null} port range matches
 * any port. Addresses are matched against a CIDR block given by {@code address}/{@code prefix}.
 *
 * @param id           A unique identifier for this rule, used to remove it with {@link Router#removeRule(int)}.
 * @param direction    The direction this rule applies to.
 * @param fromAddress  The source network to match, or {@code null} to match any.
 * @param fromPrefix   The source prefix length, in the range [0, 32]. Only used if {@code fromAddress} is non-null.
 * @param toAddress    The destination network to match, or {@code null} to match any.
 * @param toPrefix     The destination prefix length, in the range [0, 32]. Only used if {@code toAddress} is non-null.
 * @param fromMac      The source MAC to match, or {@code null} to match any.
 * @param dstPortFrom  The first destination port in the matched range, or {@code null} to match any.
 * @param dstPortTo    The last destination port in the matched range, or {@code null} to match only {@code dstPortFrom}.
 * @param action       What to do with matching packets.
 */
public record FirewallRule(
    int id, Direction direction,
    @Nullable IpAddress fromAddress, int fromPrefix,
    @Nullable IpAddress toAddress, int toPrefix,
    @Nullable Mac fromMac,
    @Nullable Integer dstPortFrom, @Nullable Integer dstPortTo,
    Action action
) {
    /**
     * Create a rule which matches all traffic in the given direction.
     *
     * @param id        The rule's identifier.
     * @param direction The direction this rule applies to.
     * @param action    What to do with matching packets.
     * @return The new rule.
     */
    public static FirewallRule matchAll(int id, Direction direction, Action action) {
        return new FirewallRule(id, direction, null, 0, null, 0, null, null, null, action);
    }

    public FirewallRule {
        if (fromAddress == null ? fromPrefix != 0 : fromPrefix < 0 || fromPrefix > 32) {
            throw new IllegalArgumentException("Source prefix must be in the range [0, 32] when a source address is set.");
        }
        if (toAddress == null ? toPrefix != 0 : toPrefix < 0 || toPrefix > 32) {
            throw new IllegalArgumentException("Destination prefix must be in the range [0, 32] when a destination address is set.");
        }

        if (dstPortFrom != null && (dstPortFrom < 0 || dstPortFrom > 65535)) {
            throw new IllegalArgumentException("Destination port must be in the range [0, 65535], got " + dstPortFrom + ".");
        }
        if (dstPortTo != null && (dstPortTo < 0 || dstPortTo > 65535)) {
            throw new IllegalArgumentException("Destination port must be in the range [0, 65535], got " + dstPortTo + ".");
        }
        if (dstPortFrom != null && dstPortTo != null && dstPortTo < dstPortFrom) {
            throw new IllegalArgumentException("Destination port range " + dstPortFrom + ".." + dstPortTo + " is empty.");
        }
    }

    /**
     * Whether this rule matches a packet being handled in a given direction.
     *
     * @param packetDirection The direction the packet is travelling in.
     * @param packet          The packet to test.
     * @return Whether this rule matches. This does not consider the rule's {@linkplain #action() action}.
     */
    public boolean matches(Direction packetDirection, NetPacket packet) {
        if (direction != packetDirection) return false;

        var from = fromAddress;
        if (from != null && !packet.src().inSubnet(from, fromPrefix)) return false;

        var to = toAddress;
        if (to != null && !packet.dst().inSubnet(to, toPrefix)) return false;

        var mac = fromMac;
        if (mac != null) {
            var srcMac = packet.srcMac();
            if (srcMac == null || !srcMac.equals(mac)) return false;
        }

        var portFrom = dstPortFrom;
        if (portFrom != null) {
            var portTo = dstPortTo;
            var port = packet.dstPort();
            if (portTo == null ? port != portFrom : port < portFrom || port > portTo) return false;
        }

        return true;
    }

    /** The direction a rule (or packet) applies to. */
    public enum Direction {
        /** Packets arriving at this device from the network. */
        IN,
        /** Packets leaving this device for the network. */
        OUT,
        /** Packets being routed through this device, from one port to another. */
        FORWARD,
    }

    /** What to do with a packet which matches a rule. */
    public enum Action {
        /** Allow the packet through. */
        ALLOW,
        /** Silently discard the packet. */
        DENY,
    }
}
