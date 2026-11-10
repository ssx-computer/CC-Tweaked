// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helpers for wiring several {@link NetStack}s together.
 */
final class TestNetwork {
    private TestNetwork() {
    }

    /**
     * A {@link NetInterface} whose behaviour is provided by the test, rather than a real modem.
     */
    static class TestInterface implements NetInterface {
        interface Link {
            void deliver(TestInterface from, NetPacket packet);
        }

        private final Mac mac;
        private final String name;
        private @Nullable Link link;

        TestInterface(Mac mac, String name) {
            this.mac = mac;
            this.name = name;
        }

        TestInterface connect(Link link) {
            this.link = link;
            return this;
        }

        @Override
        public Mac mac() {
            return mac;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void send(NetPacket packet) {
            var link = this.link;
            if (link != null) link.deliver(this, packet);
        }
    }

    /**
     * A two-ended wire between two stacks, which simply hands packets to the other stack.
     */
    static final class Wire {
        private final NetStack a;
        private final TestInterface aIface;
        private final NetStack b;
        private final TestInterface bIface;

        Wire(NetStack a, TestInterface aIface, NetStack b, TestInterface bIface) {
            this.a = a;
            this.aIface = aIface;
            this.b = b;
            this.bIface = bIface;

            aIface.connect((from, packet) -> b.receive(bIface, packet));
            bIface.connect((from, packet) -> a.receive(aIface, packet));
        }

        NetStack a() {
            return a;
        }

        NetStack b() {
            return b;
        }
    }

    static TestInterface iface(String mac, String name) {
        return new TestInterface(new Mac(mac), name);
    }

    static Wire connect(NetStack a, TestInterface aIface, NetStack b, TestInterface bIface) {
        return new Wire(a, aIface, b, bIface);
    }

    /**
     * An event sink which records everything it is given.
     */
    static final class RecordingSink implements NetEventSink {
        record Event(String name, Object[] args) {
        }

        private final List<Event> events = new ArrayList<>();

        @Override
        public void queueEvent(String event, Object... args) {
            events.add(new Event(event, args));
        }

        List<Event> events() {
            return events;
        }

        List<Event> events(String name) {
            return events.stream().filter(e -> e.name().equals(name)).toList();
        }

        @Nullable
        Event last() {
            return events.isEmpty() ? null : events.get(events.size() - 1);
        }
    }
}
