// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.internet.ae2.Ae2Compat;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The CC-side peripheral of a {@link NetworkAdapterBlock}. It exposes the connected ME network's storage to a computer
 * on the CC network, read-only, via a handful of {@code get*} methods.
 * <p>
 * This class <em>never</em> references an AE2 type: every call is forwarded to {@link Ae2Compat}, which is the only
 * class which does. If AE2 is not installed (or the adapter is not currently joined to an ME network) the calls return
 * empty results.
 * <p>
 * The peripheral is only attached to the CC network when the adapter actually has an active ME network to talk to —
 * see {@link NetworkAdapterBlockEntity#refreshPeripheral()}.
 */
public final class NetworkAdapterPeripheral implements IPeripheral {
    private final NetworkAdapterBlockEntity adapter;

    NetworkAdapterPeripheral(NetworkAdapterBlockEntity adapter) {
        this.adapter = adapter;
    }

    @Override
    public String getType() {
        return "network_adapter";
    }

    /**
     * Whether the adapter is connected to an active ME network.
     *
     * @return Whether an ME network is reachable.
     */
    @LuaFunction(mainThread = true)
    public final boolean isConnected() {
        var host = adapter.getAe2Host();
        return host != null && Ae2Compat.isGridActive(host);
    }

    /**
     * List every item stored in the connected ME network.
     *
     * @return A list of {@code { name, display, count, type, amountPerUnit }} maps, one per stored stack.
     * @throws LuaException If the adapter is not connected to an ME network.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getItems() throws LuaException {
        var host = checkHost();
        return Ae2Compat.getItems(host);
    }

    /**
     * List the storage cells attached to the connected ME network.
     *
     * @return A list of {@code { slot, id, display, status, location }} maps, one per cell.
     * @throws LuaException If the adapter is not connected to an ME network.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getCells() throws LuaException {
        var host = checkHost();
        return Ae2Compat.getCells(host);
    }

    /**
     * Get the connected ME network's power state.
     *
     * @return A {@code { stored, max, avgUsage, powered }} map.
     * @throws LuaException If the adapter is not connected to an ME network.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getPower() throws LuaException {
        var host = checkHost();
        return Ae2Compat.getPower(host);
    }

    /**
     * List the items the connected ME network can craft.
     *
     * @return A list of {@code { name, display, count, type }} maps, one per craftable stack.
     * @throws LuaException If the adapter is not connected to an ME network.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getCraftable() throws LuaException {
        var host = checkHost();
        return Ae2Compat.getCraftable(host);
    }

    /**
     * Get the AE2 host, or fail if the adapter has no AE2 half.
     *
     * @return The AE2 host.
     * @throws LuaException If AE2 is not installed.
     */
    private Object checkHost() throws LuaException {
        var host = adapter.getAe2Host();
        if (host == null) throw new LuaException("AE2 is not installed.");
        return host;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return this == other || other instanceof NetworkAdapterPeripheral that && that.adapter == adapter;
    }

    @Override
    public Object getTarget() {
        return adapter;
    }
}
