// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwitchTableTest {
    private static final Mac A = Mac.parse("aa:00:00:01");
    private static final Mac B = Mac.parse("aa:00:00:02");

    private static SwitchTable table() {
        var table = new SwitchTable();
        table.addPort(1);
        table.addPort(2);
        table.addPort(3);
        return table;
    }

    @Test
    void testLearnAndLookup() {
        var table = table();
        assertNull(table.portFor(A));

        table.learn(A, 2);
        assertEquals(2, table.portFor(A));
        assertEquals(3, table.ports().size());
    }

    @Test
    void testLearnUpdatesPort() {
        var table = table();
        table.learn(A, 1);
        assertEquals(1, table.portFor(A));

        table.learn(A, 3);
        assertEquals(3, table.portFor(A), "A MAC which moves is re-learnt on its new port");
    }

    @Test
    void testKnownMacIsNotFlooded() {
        var table = table();
        table.learn(A, 2);

        assertEquals(List.of(2), table.flood(A, 1));
        assertEquals(List.of(2), table.flood(A, 2), "Packets to a known MAC always go to its port");
    }

    @Test
    void testUnknownMacIsFlooded() {
        var table = table();
        table.learn(B, 2);

        var ports = table.flood(A, 1);
        ports.sort(Integer::compareTo);
        assertEquals(List.of(2, 3), ports, "Unknown MACs are flooded to every port except the incoming one");
        assertFalse(ports.contains(1));
    }

    @Test
    void testEntriesExpire() {
        var table = table();
        table.learn(A, 1);
        assertEquals(1, table.portFor(A));

        table.tick(System.currentTimeMillis() + SwitchTable.DEFAULT_ENTRY_TIMEOUT + 1000);
        assertNull(table.portFor(A), "Entries which are not seen for a while are forgotten");
    }

    @Test
    void testEntriesSurviveFreshTick() {
        var table = table();
        table.setEntryTimeout(60_000);
        table.learn(A, 1);

        table.tick(System.currentTimeMillis() + 1000);
        assertEquals(1, table.portFor(A));
    }

    @Test
    void testRemovingPortForgetsMacs() {
        var table = table();
        table.learn(A, 2);
        table.removePort(2);

        assertNull(table.portFor(A));
        assertEquals(2, table.ports().size());
    }

    @Test
    void testLearningOnUnknownPortRegistersIt() {
        var table = new SwitchTable();
        table.learn(A, 7);
        assertEquals(List.of(7), table.ports());
    }

    @Test
    void testValidation() {
        var table = new SwitchTable();
        assertThrows(IllegalArgumentException.class, () -> table.addPort(-1));
        assertThrows(IllegalArgumentException.class, () -> table.learn(A, -1));
        assertThrows(IllegalArgumentException.class, () -> table.setEntryTimeout(0));
    }
}
