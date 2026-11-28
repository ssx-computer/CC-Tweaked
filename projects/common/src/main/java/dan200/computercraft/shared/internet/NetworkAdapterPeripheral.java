// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.internet.ae2.Ae2Compat;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public boolean isConnected() {
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
    public List<Map<String, Object>> getItems() throws LuaException {
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
    public List<Map<String, Object>> getCells() throws LuaException {
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
    public Map<String, Object> getPower() throws LuaException {
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
    public List<Map<String, Object>> getCraftable() throws LuaException {
        var host = checkHost();
        return Ae2Compat.getCraftable(host);
    }

    /**
     * Push a stack of items into the connected ME network.
     *
     * @param name  The Minecraft item id, such as {@code minecraft:cobblestone}.
     * @param count The number of items to push.
     * @param key   The authentication key, or {@code null}. Only required once the adapter is locked.
     * @return The number of items actually stored.
     * @throws LuaException If the adapter is not connected to an ME network, or the write is unauthorised.
     */
    @LuaFunction(mainThread = true)
    public long pushItem(String name, long count, @Nullable String key) throws LuaException {
        var host = checkHost();
        var req = new JsonObject();
        req.addProperty("op", "pushItem");
        req.addProperty("name", name);
        req.addProperty("count", count);
        if (key != null) req.addProperty("key", key);
        var reply = handleAuth(host, req);
        return reply.get("result").getAsJsonObject().get("stored").getAsLong();
    }

    /**
     * Pull a stack of items out of the connected ME network.
     *
     * @param name  The Minecraft item id, such as {@code minecraft:cobblestone}.
     * @param count The number of items to pull.
     * @param key   The authentication key, or {@code null}. Only required once the adapter is locked.
     * @return The number of items actually extracted.
     * @throws LuaException If the adapter is not connected to an ME network, or the write is unauthorised.
     */
    @LuaFunction(mainThread = true)
    public long extractItem(String name, long count, @Nullable String key) throws LuaException {
        var host = checkHost();
        var req = new JsonObject();
        req.addProperty("op", "extractItem");
        req.addProperty("name", name);
        req.addProperty("count", count);
        if (key != null) req.addProperty("key", key);
        var reply = handleAuth(host, req);
        return reply.get("result").getAsJsonObject().get("extracted").getAsLong();
    }

    /**
     * Send an authentication-aware request to the adapter and turn a failure into a Lua error.
     *
     * @param host The adapter host.
     * @param req  The request to send.
     * @return The parsed reply.
     * @throws LuaException If the request failed.
     */
    private static JsonObject handleAuth(Object host, JsonObject req) throws LuaException {
        var reply = JsonParser.parseString(Ae2Compat.handleRequest(host, req.toString())).getAsJsonObject();
        if (!reply.get("ok").getAsBoolean()) throw new LuaException(reply.get("error").getAsString());
        return reply;
    }

    /**
     * Register or replace a key's write permissions.
     *
     * @param key   The key to register.
     * @param items The item ids the key may write, or {@code null} for every item.
     * @return Whether the key already existed.
     * @throws LuaException If the adapter is not connected to an ME network.
     */
    @LuaFunction(mainThread = true)
    public boolean addAuthKey(String key, @Nullable String[] items) throws LuaException {
        var host = checkHost();
        if (items == null) return Ae2Compat.addAuthKey(host, key, Set.of(NetworkAdapterBlockEntity.AUTH_ALL));
        return Ae2Compat.addAuthKey(host, key, Set.of(items));
    }

    /**
     * Remove a key's permissions.
     *
     * @param key The key to remove.
     * @return Whether the key existed.
     * @throws LuaException If the adapter is not connected to an ME network.
     */
    @LuaFunction(mainThread = true)
    public boolean removeAuthKey(String key) throws LuaException {
        var host = checkHost();
        return Ae2Compat.removeAuthKey(host, key);
    }

    /**
     * List every key and the items it may write to.
     *
     * @return A map of each key to the list of item ids it may write.
     * @throws LuaException If the adapter is not connected to an ME network.
     */
    @LuaFunction(mainThread = true)
    public Map<String, List<String>> listAuth() throws LuaException {
        var host = checkHost();
        return Ae2Compat.listAuth(host);
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
