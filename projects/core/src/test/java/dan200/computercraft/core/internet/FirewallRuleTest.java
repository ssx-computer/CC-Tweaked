// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirewallRuleTest {
    private static final IpAddress HOST = IpAddress.parse("10.0.0.5");
    private static final IpAddress REMOTE = IpAddress.parse("8.8.8.8");

    private static NetPacket packet(IpAddress src, IpAddress dst, int dstPort) {
        return new NetPacket(src, dst, Mac.parse("cc:00:00:01"), null, NetPacket.PROTOCOL_UDP, 1000, dstPort, 64, 0, new byte[0]);
    }

    @Test
    void testMatchAll() {
        var rule = FirewallRule.matchAll(1, FirewallRule.Direction.IN, FirewallRule.Action.DENY);
        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 80)));
        assertFalse(rule.matches(FirewallRule.Direction.OUT, packet(REMOTE, HOST, 80)), "Direction is respected");
    }

    @Test
    void testCidrMatch() {
        var rule = new FirewallRule(
            1, FirewallRule.Direction.IN, IpAddress.parse("10.0.0.0"), 24, null, 0, null, null, null, FirewallRule.Action.ALLOW
        );

        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(IpAddress.parse("10.0.0.99"), HOST, 1)));
        assertFalse(rule.matches(FirewallRule.Direction.IN, packet(IpAddress.parse("10.0.1.1"), HOST, 1)));
    }

    @Test
    void testCidrMatchOnDestination() {
        var rule = new FirewallRule(
            1, FirewallRule.Direction.OUT, null, 0, IpAddress.parse("192.168.0.0"), 16, null, null, null, FirewallRule.Action.DENY
        );

        assertTrue(rule.matches(FirewallRule.Direction.OUT, packet(HOST, IpAddress.parse("192.168.4.4"), 1)));
        assertFalse(rule.matches(FirewallRule.Direction.OUT, packet(HOST, IpAddress.parse("192.169.4.4"), 1)));
    }

    @Test
    void testPortRange() {
        var rule = new FirewallRule(1, FirewallRule.Direction.IN, null, 0, null, 0, null, 80, 90, FirewallRule.Action.DENY);

        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 80)));
        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 85)));
        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 90)));
        assertFalse(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 79)));
        assertFalse(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 91)));
    }

    @Test
    void testSinglePort() {
        var rule = new FirewallRule(1, FirewallRule.Direction.IN, null, 0, null, 0, null, 80, null, FirewallRule.Action.DENY);

        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 80)));
        assertFalse(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 81)));
    }

    @Test
    void testMacMatch() {
        var rule = new FirewallRule(
            1, FirewallRule.Direction.IN, null, 0, null, 0, Mac.parse("cc:00:00:01"), null, null, FirewallRule.Action.DENY
        );

        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(REMOTE, HOST, 1)));
        var otherMac = new NetPacket(REMOTE, HOST, Mac.parse("cc:00:00:02"), null, NetPacket.PROTOCOL_UDP, 1, 1, 64, 0, new byte[0]);
        assertFalse(rule.matches(FirewallRule.Direction.IN, otherMac));

        var noMac = new NetPacket(REMOTE, HOST, null, null, NetPacket.PROTOCOL_UDP, 1, 1, 64, 0, new byte[0]);
        assertFalse(rule.matches(FirewallRule.Direction.IN, noMac), "A packet with no MAC does not match a MAC rule");
    }

    @Test
    void testCombinedCriteria() {
        var rule = new FirewallRule(
            1, FirewallRule.Direction.IN, IpAddress.parse("10.0.0.0"), 8, null, 0, Mac.parse("cc:00:00:01"), 80, 80,
            FirewallRule.Action.DENY
        );

        assertTrue(rule.matches(FirewallRule.Direction.IN, packet(IpAddress.parse("10.1.2.3"), HOST, 80)));
        assertFalse(rule.matches(FirewallRule.Direction.IN, packet(IpAddress.parse("11.1.2.3"), HOST, 80)));
        assertFalse(rule.matches(FirewallRule.Direction.IN, packet(IpAddress.parse("10.1.2.3"), HOST, 81)));
    }

    @Test
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () -> new FirewallRule(
            1, FirewallRule.Direction.IN, HOST, 33, null, 0, null, null, null, FirewallRule.Action.ALLOW
        ));
        assertThrows(IllegalArgumentException.class, () -> new FirewallRule(
            1, FirewallRule.Direction.IN, HOST, -1, null, 0, null, null, null, FirewallRule.Action.ALLOW
        ));
        assertThrows(IllegalArgumentException.class, () -> new FirewallRule(
            1, FirewallRule.Direction.IN, null, 0, null, 0, null, 90, 80, FirewallRule.Action.DENY
        ), "An empty port range is invalid");
        assertThrows(IllegalArgumentException.class, () -> new FirewallRule(
            1, FirewallRule.Direction.IN, null, 0, null, 0, null, -1, null, FirewallRule.Action.DENY
        ));
    }
}
