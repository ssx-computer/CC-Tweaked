// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouterTest {
    private static final IpAddress SELF = IpAddress.parse("10.0.0.1");
    private static final IpAddress NETMASK = IpAddress.parse("255.255.255.0");
    private static final IpAddress SUBNET_0 = IpAddress.parse("10.0.0.0");
    private static final IpAddress SUBNET_1 = IpAddress.parse("10.0.1.0");

    private static NetPacket packet(IpAddress src, IpAddress dst, int dstPort) {
        return new NetPacket(src, dst, null, null, NetPacket.PROTOCOL_UDP, 1000, dstPort, 64, 0, new byte[0]);
    }

    private static NetPacket packet(IpAddress src, IpAddress dst, int dstPort, int ttl) {
        return new NetPacket(src, dst, null, null, NetPacket.PROTOCOL_UDP, 1000, dstPort, ttl, 0, new byte[0]);
    }

    private static Router router() {
        var router = new Router();
        router.setSubnet(0, SUBNET_0, NETMASK);
        router.setSubnet(1, SUBNET_1, NETMASK);
        return router;
    }

    @Test
    void testLocalDelivery() {
        var router = router();
        var decision = router.route(0, packet(IpAddress.parse("10.0.0.5"), SELF, 80), SELF);
        assertEquals(Router.Decision.Kind.LOCAL, decision.kind());
    }

    @Test
    void testBroadcastDelivery() {
        var router = router();
        var sender = IpAddress.parse("10.0.0.5");
        assertEquals(Router.Decision.Kind.LOCAL, router.route(0, packet(sender, IpAddress.BROADCAST, 80), SELF).kind());
        assertEquals(Router.Decision.Kind.LOCAL, router.route(0, packet(sender, IpAddress.parse("10.0.0.255"), 80), SELF).kind());
        assertEquals(Router.Decision.Kind.LOCAL, router.route(1, packet(sender, IpAddress.parse("10.0.1.255"), 80), SELF).kind());
    }

    @Test
    void testSameSubnetIsForwardedToSamePort() {
        var router = router();
        var decision = router.route(0, packet(SELF, IpAddress.parse("10.0.0.9"), 80), SELF);

        assertEquals(Router.Decision.Kind.FORWARD, decision.kind());
        assertEquals(0, decision.outPort());
        assertEquals(IpAddress.parse("10.0.0.9"), decision.nextHop());
    }

    @Test
    void testForwardedOnTheOtherSegment() {
        var router = router();
        var decision = router.route(1, packet(IpAddress.parse("10.0.1.9"), IpAddress.parse("10.0.1.10"), 80), SELF);

        assertEquals(Router.Decision.Kind.FORWARD, decision.kind());
        assertEquals(1, decision.outPort());
    }

    @Test
    void testRoutedThroughGateway() {
        var router = router();
        router.addRoute(IpAddress.parse("192.168.0.0"), NETMASK, IpAddress.parse("10.0.1.254"), 1);

        var decision = router.route(0, packet(IpAddress.parse("10.0.0.9"), IpAddress.parse("192.168.0.7"), 80), SELF);
        assertEquals(Router.Decision.Kind.FORWARD, decision.kind());
        assertEquals(1, decision.outPort());
        assertEquals(IpAddress.parse("10.0.1.254"), decision.nextHop());
    }

    @Test
    void testDefaultRoute() {
        var router = router();
        router.addRoute(IpAddress.ANY, IpAddress.ANY, IpAddress.parse("10.0.1.254"), 1);

        var decision = router.route(0, packet(IpAddress.parse("10.0.0.9"), IpAddress.parse("1.2.3.4"), 80), SELF);
        assertEquals(Router.Decision.Kind.FORWARD, decision.kind());
        assertEquals(1, decision.outPort());
    }

    @Test
    void testLongestPrefixWins() {
        var router = router();
        router.addRoute(IpAddress.ANY, IpAddress.ANY, IpAddress.parse("10.0.1.254"), 1);
        router.addRoute(IpAddress.parse("192.168.0.0"), NETMASK, IpAddress.parse("10.0.0.254"), 0);

        var decision = router.route(1, packet(IpAddress.parse("10.0.1.9"), IpAddress.parse("192.168.0.7"), 80), SELF);
        assertEquals(0, decision.outPort(), "The more specific /24 route wins over the default route");
        assertEquals(IpAddress.parse("10.0.0.254"), decision.nextHop());
    }

    @Test
    void testUnroutableIsDropped() {
        var router = router();
        var decision = router.route(0, packet(IpAddress.parse("10.0.0.9"), IpAddress.parse("192.168.0.7"), 80), SELF);
        assertEquals(Router.Decision.Kind.DROP, decision.kind());
    }

    @Test
    void testTtlIsDecremented() {
        var router = router();
        var packet = packet(SELF, IpAddress.parse("10.0.0.9"), 80, 10);
        var decision = router.route(0, packet, SELF);

        var forwarded = decision.prepare(packet);
        assertEquals(9, forwarded.ttl());
        assertEquals(10, packet.ttl(), "The original packet is not modified");

        var local = router.route(0, packet(IpAddress.parse("10.0.0.9"), SELF, 80, 10), SELF);
        assertEquals(10, local.prepare(packet).ttl(), "Locally delivered packets keep their TTL");
    }

    @Test
    void testTtlExhaustion() {
        var router = router();
        assertEquals(Router.Decision.Kind.DROP, router.route(0, packet(SELF, IpAddress.parse("10.0.0.9"), 80, 1), SELF).kind());
        assertEquals(Router.Decision.Kind.DROP, router.route(0, packet(SELF, IpAddress.parse("10.0.0.9"), 80, 0), SELF).kind());
        assertEquals(Router.Decision.Kind.FORWARD, router.route(0, packet(SELF, IpAddress.parse("10.0.0.9"), 80, 2), SELF).kind());
    }

    @Test
    void testDefaultPolicyIsAllow() {
        var router = router();
        var packet = packet(IpAddress.parse("10.0.0.5"), SELF, 64);
        assertTrue(router.allowed(FirewallRule.Direction.IN, packet));
        assertTrue(router.allowed(FirewallRule.Direction.OUT, packet));
        assertTrue(router.allowed(FirewallRule.Direction.FORWARD, packet));
    }

    @Test
    void testInboundDeny() {
        var router = router();
        router.addRule(FirewallRule.matchAll(1, FirewallRule.Direction.IN, FirewallRule.Action.DENY));

        assertEquals(Router.Decision.Kind.DROP, router.route(0, packet(IpAddress.parse("10.0.0.5"), SELF, 80), SELF).kind());
        assertTrue(router.allowed(FirewallRule.Direction.OUT, packet(SELF, IpAddress.parse("10.0.0.5"), 64)), "OUT is unaffected");
    }

    @Test
    void testFirstMatchingRuleWins() {
        var router = router();
        router.addRule(new FirewallRule(
            1, FirewallRule.Direction.IN, null, 0, null, 0, null, 80, 80, FirewallRule.Action.DENY
        ));
        router.addRule(FirewallRule.matchAll(2, FirewallRule.Direction.IN, FirewallRule.Action.ALLOW));

        assertFalse(router.allowed(FirewallRule.Direction.IN, packet(IpAddress.parse("10.0.0.5"), SELF, 80)));
        assertTrue(router.allowed(FirewallRule.Direction.IN, packet(IpAddress.parse("10.0.0.5"), SELF, 443)));
    }

    @Test
    void testForwardRule() {
        var router = router();
        router.addRoute(SUBNET_1, NETMASK, IpAddress.parse("10.0.1.254"), 1);
        router.addRule(new FirewallRule(
            1, FirewallRule.Direction.FORWARD, IpAddress.parse("10.0.0.0"), 8, null, 0, null, null, null, FirewallRule.Action.DENY
        ));

        var blocked = router.route(0, packet(IpAddress.parse("10.0.0.9"), IpAddress.parse("10.0.1.9"), 80), SELF);
        assertEquals(Router.Decision.Kind.DROP, blocked.kind(), "Forwarding from 10/8 is denied");

        var allowed = router.route(1, packet(IpAddress.parse("172.16.0.1"), IpAddress.parse("10.0.1.9"), 80), SELF);
        assertEquals(Router.Decision.Kind.FORWARD, allowed.kind());
    }

    @Test
    void testRulesCanBeRemoved() {
        var router = router();
        router.addRule(FirewallRule.matchAll(1, FirewallRule.Direction.IN, FirewallRule.Action.DENY));
        assertEquals(1, router.rules().size());

        assertTrue(router.removeRule(1));
        assertFalse(router.removeRule(1));
        assertTrue(router.rules().isEmpty());
        assertTrue(router.allowed(FirewallRule.Direction.IN, packet(IpAddress.parse("10.0.0.5"), SELF, 64)));
    }

    @Test
    void testDuplicateRuleIdIsRejected() {
        var router = router();
        router.addRule(FirewallRule.matchAll(1, FirewallRule.Direction.IN, FirewallRule.Action.DENY));
        assertThrows(IllegalArgumentException.class,
            () -> router.addRule(FirewallRule.matchAll(1, FirewallRule.Direction.OUT, FirewallRule.Action.DENY)));
    }

    @Test
    void testUnknownPortHasNoSubnet() {
        var router = router();
        var decision = router.route(5, packet(SELF, IpAddress.parse("10.0.0.9"), 80), SELF);
        assertEquals(Router.Decision.Kind.DROP, decision.kind(), "We do not know what is on port 5");
    }

    @Test
    void testRemovePort() {
        var router = router();
        router.removePort(0);
        assertEquals(Router.Decision.Kind.DROP, router.route(0, packet(SELF, IpAddress.parse("10.0.0.9"), 80), SELF).kind());
    }
}
