// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.internet.NetworkAdapterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The AE2 half of a {@link dan200.computercraft.shared.internet.NetworkAdapterBlock}.
 * <p>
 * <strong>This is the only class in CC which refers to an AE2 type.</strong> It is loaded lazily — only when an adapter
 * ticks and AE2 is installed — so that CC loads and runs quite happily without AE2. Every entry point here takes or
 * returns the host as an {@link Object}, so that callers do not have to be able to load an AE2 class either.
 *
 * @see NetworkAdapterBlockEntity#getAe2Host()
 */
public final class Ae2Compat {
    /**
     * How much power the adapter draws from the ME network while idle.
     */
    private static final double IDLE_POWER = 1.0;

    /**
     * Serialiser for the JSON request/response protocol spoken over CC's virtual network.
     */
    private static final Gson GSON = new Gson();

    private Ae2Compat() {
    }

    /**
     * Create the AE2 host for an adapter.
     *
     * @param adapter The adapter this host belongs to.
     * @return The host, which implements AE2's {@code IInWorldGridNodeHost}.
     */
    public static Object createHost(NetworkAdapterBlockEntity adapter) {
        return new Ae2GridNodeHost(adapter);
    }

    /**
     * Put the adapter's grid node into the level. This must be called once the block entity is in a loaded chunk.
     *
     * @param host  The host, as returned by {@link #createHost}.
     * @param level The level the adapter is in.
     * @param pos   The position of the adapter.
     */
    public static void create(Object host, Level level, BlockPos pos) {
        ((Ae2GridNodeHost) host).create(level, pos);
    }

    /**
     * Remove the adapter's grid node from the grid.
     *
     * @param host The host, as returned by {@link #createHost}.
     */
    public static void destroy(Object host) {
        ((Ae2GridNodeHost) host).destroy();
    }

    /**
     * Save the adapter's grid node to NBT.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @param nbt  The tag to write to.
     */
    public static void saveToNbt(Object host, CompoundTag nbt) {
        ((Ae2GridNodeHost) host).saveToNbt(nbt);
    }

    /**
     * Load the adapter's grid node from NBT.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @param nbt  The tag to read from.
     */
    public static void loadFromNbt(Object host, CompoundTag nbt) {
        ((Ae2GridNodeHost) host).loadFromNbt(nbt);
    }

    /**
     * Whether the adapter is connected to an active ME network, and so the CC half should present its peripheral.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @return Whether the adapter can reach a usable ME network.
     */
    public static boolean isGridActive(Object host) {
        var node = ((Ae2GridNodeHost) host).node.getNode();
        if (node == null || !node.isActive()) return false;
        return node.getGrid() != null;
    }

    /**
     * List every item in the ME network's storage.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @return A list of {@code { name, display, count, type, amountPerUnit }} maps for each stored stack.
     */
    public static List<Map<String, Object>> getItems(Object host) {
        var node = ((Ae2GridNodeHost) host).node.getNode();
        if (node == null || node.getGrid() == null) return List.of();

        var storageService = node.getGrid().getStorageService();
        var stacks = storageService.getCachedInventory();
        if (stacks == null) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : stacks) {
            var key = entry.getKey();
            if (key == null || entry.getLongValue() <= 0) continue;
            var item = itemInfo(key, entry.getLongValue());
            if (item != null) result.add(item);
        }
        return result;
    }

    /**
     * List the storage cells attached to the ME network (in drives and ME chests).
     *
     * @param host The host, as returned by {@link #createHost}.
     * @return A list of {@code { slot, id, display, status, count, location }} maps for each cell.
     */
    public static List<Map<String, Object>> getCells(Object host) {
        var node = ((Ae2GridNodeHost) host).node.getNode();
        if (node == null || node.getGrid() == null) return List.of();

        var grid = node.getGrid();
        List<Map<String, Object>> result = new ArrayList<>();

        for (var drive : grid.getMachines(DriveBlockEntity.class)) {
            var pos = drive.getBlockPos();
            for (int slot = 0; slot < drive.getCellCount(); slot++) {
                var item = drive.getCellItem(slot);
                if (item == null) continue;
                var stack = new ItemStack(item);
                result.add(cellInfo(stack, slot, drive.getCellStatus(slot).name(), pos));
            }
        }

        for (var chest : grid.getMachines(MEChestBlockEntity.class)) {
            var stack = chest.getCell();
            if (stack == null || stack.isEmpty()) continue;
            result.add(cellInfo(stack, 0, chest.getCellStatus(0).name(), chest.getBlockPos()));
        }

        return result;
    }

    /**
     * Get the ME network's energy/power state.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @return A {@code { stored, max, avgUsage, idlePower, channelPower, powered }} map.
     */
    public static Map<String, Object> getPower(Object host) {
        var node = ((Ae2GridNodeHost) host).node.getNode();
        if (node == null || node.getGrid() == null) return Map.of();

        var energy = node.getGrid().getEnergyService();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stored", energy.getStoredPower());
        result.put("max", energy.getMaxStoredPower());
        result.put("avgUsage", energy.getAvgPowerUsage());
        result.put("idlePower", energy.getIdlePowerUsage());
        result.put("channelPower", energy.getChannelPowerUsage());
        result.put("powered", energy.isNetworkPowered());
        return result;
    }

    /**
     * List the items the ME network can craft.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @return A list of {@code { name, display, count, type }} maps for each craftable stack.
     */
    public static List<Map<String, Object>> getCraftable(Object host) {
        var node = ((Ae2GridNodeHost) host).node.getNode();
        if (node == null || node.getGrid() == null) return List.of();

        var crafting = node.getGrid().getCraftingService();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var key : crafting.getCraftables(key -> true)) {
            var item = itemInfo(key, 0);
            if (item != null) result.add(item);
        }
        return result;
    }

    /**
     * Push a stack of items into the ME network's storage.
     *
     * @param host  The host, as returned by {@link #createHost}.
     * @param name  The Minecraft item id, such as {@code minecraft:cobblestone}.
     * @param count The number of items to push (at least 1).
     * @return The number of items actually stored (may be fewer than {@code count} if storage is full).
     */
    public static long pushItem(Object host, String name, long count) {
        var node = ((Ae2GridNodeHost) host).node.getNode();
        if (node == null || node.getGrid() == null || count <= 0) return 0;

        var stack = findItemStack(name);
        if (stack.isEmpty()) return 0;

        var storage = node.getGrid().getStorageService().getInventory();
        return storage.insert(AEItemKey.of(stack), count, Actionable.MODULATE, IActionSource.empty());
    }

    /**
     * Pull a stack of items out of the ME network's storage.
     *
     * @param host  The host, as returned by {@link #createHost}.
     * @param name  The Minecraft item id, such as {@code minecraft:cobblestone}.
     * @param count The number of items to pull (at least 1).
     * @return The number of items actually extracted (may be fewer than {@code count} if not enough are stored).
     */
    public static long extractItem(Object host, String name, long count) {
        var node = ((Ae2GridNodeHost) host).node.getNode();
        if (node == null || node.getGrid() == null || count <= 0) return 0;

        var stack = findItemStack(name);
        if (stack.isEmpty()) return 0;

        var storage = node.getGrid().getStorageService().getInventory();
        return storage.extract(AEItemKey.of(stack), count, Actionable.MODULATE, IActionSource.empty());
    }

    /**
     * Handle a single request from a CC computer over the virtual network, returning a response to send back.
     * <p>
     * The request is a JSON object with an {@code op} field naming the operation, and any fields it needs. The
     * response is a JSON object with an {@code ok} boolean: {@code {"ok":true,"result":...}} on success, or
     * {@code {"ok":false,"error":"..."}} on failure.
     * <p>
     * This is the only entry point through which CC's virtual network talks to the ME network; it keeps every AE2
     * call inside this class.
     *
     * @param host    The host, as returned by {@link #createHost}.
     * @param request The JSON request.
     * @return The JSON response.
     */
    public static String handleRequest(Object host, String request) {
        JsonObject req;
        try {
            req = JsonParser.parseString(request).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return GSON.toJson(Map.of("ok", false, "error", "Malformed request."));
        }

        String op;
        try {
            op = req.has("op") ? req.get("op").getAsString() : "";
        } catch (IllegalStateException e) {
            return GSON.toJson(Map.of("ok", false, "error", "'op' must be a string."));
        }

        try {
            Object result = switch (op) {
                case "isConnected" -> Map.of("connected", isGridActive(host));
                case "getItems" -> getItems(host);
                case "getCells" -> getCells(host);
                case "getPower" -> getPower(host);
                case "getCraftable" -> getCraftable(host);
                case "pushItem" -> {
                    var name = getString(req, "name");
                    var count = getLong(req, "count", 1);
                    if (count <= 0) throw new IllegalArgumentException("count must be positive.");
                    checkAuthorized(host, req, name);
                    yield Map.of("stored", pushItem(host, name, count));
                }
                case "extractItem" -> {
                    var name = getString(req, "name");
                    var count = getLong(req, "count", 1);
                    if (count <= 0) throw new IllegalArgumentException("count must be positive.");
                    checkAuthorized(host, req, name);
                    yield Map.of("extracted", extractItem(host, name, count));
                }
                case "addAuthKey" -> {
                    var key = getString(req, "key");
                    var items = new HashSet<String>();
                    if (req.has("items")) {
                        for (var el : req.get("items").getAsJsonArray()) items.add(el.getAsString());
                    } else {
                        items.add(NetworkAdapterBlockEntity.AUTH_ALL);
                    }
                    yield Map.of("added", addAuthKey(host, key, items));
                }
                case "removeAuthKey" -> {
                    var key = getString(req, "key");
                    yield Map.of("removed", removeAuthKey(host, key));
                }
                case "listAuth" -> listAuth(host);
                default -> throw new IllegalArgumentException("Unknown operation '" + op + "'.");
            };

            // Echo any request id back, so a caller can correlate replies with pipelined requests.
            var reply = new LinkedHashMap<String, Object>();
            reply.put("ok", true);
            if (req.has("id")) reply.put("id", req.get("id").getAsString());
            reply.put("result", result);
            return GSON.toJson(reply);
        } catch (IllegalArgumentException e) {
            return GSON.toJson(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /**
     * Read a required string field from a request.
     *
     * @param req The request object.
     * @param key The field name.
     * @return The field's value.
     * @throws IllegalArgumentException If the field is missing or not a string.
     */
    private static String getString(JsonObject req, String key) {
        if (!req.has(key)) throw new IllegalArgumentException("'" + key + "' is required.");
        try {
            return req.get(key).getAsString();
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("'" + key + "' must be a string.");
        }
    }

    /**
     * Read an optional number field from a request.
     *
     * @param req    The request object.
     * @param key    The field name.
     * @param def    The default value.
     * @return The field's value, or the default if the field is absent.
     * @throws IllegalArgumentException If the field is present but not a number.
     */
    private static long getLong(JsonObject req, String key, long def) {
        if (!req.has(key)) return def;
        try {
            return req.get(key).getAsLong();
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("'" + key + "' must be a number.");
        }
    }

    /**
     * Reject a write to an item if the request's key does not authorise it.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @param req  The request object, whose optional "key" field names the key.
     * @param item The Minecraft item id being written.
     * @throws IllegalArgumentException If the request is unauthorised.
     */
    private static void checkAuthorized(Object host, JsonObject req, String item) {
        var adapter = ((Ae2GridNodeHost) host).adapter;
        // With no key configured the adapter is unsecured: writes are allowed directly, exactly as before auth.
        if (adapter.getAuth().isEmpty()) return;
        String key = null;
        if (req.has("key")) {
            try {
                key = req.get("key").getAsString();
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException("'key' must be a string.");
            }
        }
        if (!adapter.isAuthorized(key, item)) {
            throw new IllegalArgumentException("unauthorized");
        }
    }

    /**
     * Register or replace a key's write permissions.
     *
     * @param host  The host, as returned by {@link #createHost}.
     * @param key   The key to register.
     * @param items The item ids the key may write to; empty means "no items", or use
     *              {@link NetworkAdapterBlockEntity#AUTH_ALL} for every item.
     * @return Whether the key already existed.
     */
    public static boolean addAuthKey(Object host, String key, Set<String> items) {
        var adapter = ((Ae2GridNodeHost) host).adapter;
        var existed = adapter.getAuth().containsKey(key);
        adapter.setAuthKey(key, items);
        return existed;
    }

    /**
     * Remove a key's permissions.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @param key  The key to remove.
     * @return Whether the key existed.
     */
    public static boolean removeAuthKey(Object host, String key) {
        var adapter = ((Ae2GridNodeHost) host).adapter;
        var existed = adapter.getAuth().containsKey(key);
        adapter.removeAuthKey(key);
        return existed;
    }

    /**
     * List every key and the items it may write to.
     *
     * @param host The host, as returned by {@link #createHost}.
     * @return A map of key to its list of allowed item ids.
     */
    public static Map<String, List<String>> listAuth(Object host) {
        var adapter = ((Ae2GridNodeHost) host).adapter;
        var result = new LinkedHashMap<String, List<String>>();
        for (var entry : adapter.getAuth().entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return result;
    }

    /**
     * Resolve an item id to an {@link ItemStack}. Unknown ids resolve to an empty stack.
     *
     * @param name The Minecraft item id, such as {@code minecraft:cobblestone}.
     * @return An {@link ItemStack} for the item, or {@link ItemStack#EMPTY} if the id is unknown.
     */
    private static ItemStack findItemStack(String name) {
        if (name == null || name.isEmpty()) return ItemStack.EMPTY;
        var key = ResourceLocation.tryParse(name);
        if (key == null) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    /**
     * Build the info map for a single stored stack.
     *
     * @param key   The key.
     * @param count The amount stored (0 for a craftable with no known quantity).
     * @return A {@code { name, display, count, type, amountPerUnit }} map, or {@code null} if the stack cannot be
     *     described.
     */
    private static @Nullable Map<String, Object> itemInfo(AEKey key, long count) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        result.put("amountPerUnit", key.getAmountPerUnit());
        result.put("type", key.getType().getId().toString());

        if (key instanceof AEItemKey itemKey) {
            var stack = itemKey.getReadOnlyStack();
            result.put("name", stack.getItem().builtInRegistryHolder().key().location().toString());
        } else {
            result.put("name", key.toString());
        }
        result.put("display", key.getDisplayName().getString());
        return result;
    }

    /**
     * Build the info map for a single storage cell.
     *
     * @param stack  The cell's item.
     * @param slot   The slot in its drive (or 0 for an ME chest).
     * @param status The AE2 cell status, such as {@code EMPTY}, {@code TYPES} or {@code CELLS}.
     * @param pos    The block the cell lives in.
     * @return A {@code { slot, id, display, status, location }} map.
     */
    private static Map<String, Object> cellInfo(ItemStack stack, int slot, String status, BlockPos pos) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", slot);
        result.put("name", stack.getItem().builtInRegistryHolder().key().location().toString());
        result.put("display", stack.getHoverName().getString());
        result.put("status", status.toLowerCase());
        result.put("location", List.of(pos.getX(), pos.getY(), pos.getZ()));
        return result;
    }

    /**
     * The adapter's node, as a node in an AE2 grid.
     * <p>
     * The node is only exposed on the {@linkplain NetworkAdapterBlockEntity#ae2Side() AE2 side} of the block, so an AE2
     * cable attached to the CC side cannot join the ME network.
     */
    private static final class Ae2GridNodeHost implements IInWorldGridNodeHost, IGridNodeListener<NetworkAdapterBlockEntity> {
        private final NetworkAdapterBlockEntity adapter;
        private final IManagedGridNode node;

        Ae2GridNodeHost(NetworkAdapterBlockEntity adapter) {
            this.adapter = adapter;
            node = GridHelper.createManagedNode(adapter, this)
                .setInWorldNode(true)
                .setExposedOnSides(Set.of(adapter.ae2Side()))
                .setIdlePowerUsage(IDLE_POWER)
                // The adapter is a real device on the network, so it occupies a channel, as any other machine does.
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setTagName("network_adapter")
                .setVisualRepresentation(new ItemStack(ModRegistry.Items.NETWORK_ADAPTER.get()));
        }

        void create(Level level, BlockPos pos) {
            node.create(level, pos);
        }

        void destroy() {
            node.destroy();
        }

        void saveToNbt(CompoundTag nbt) {
            node.saveToNBT(nbt);
        }

        void loadFromNbt(CompoundTag nbt) {
            node.loadFromNBT(nbt);
        }

        @Override
        public @Nullable IGridNode getGridNode(Direction dir) {
            // Only the AE2 side of the block is a node in the ME network; the CC side is not.
            return dir == adapter.ae2Side() ? node.getNode() : null;
        }

        @Override
        public AECableType getCableConnectionType(Direction dir) {
            // Smart cables, so the cable renders its channel usage as it would for any other machine.
            return dir == adapter.ae2Side() ? AECableType.SMART : AECableType.NONE;
        }

        @Override
        public void onStateChanged(NetworkAdapterBlockEntity owner, IGridNode node, State state) {
            // When the grid (dis)connects or powers up/down, the CC peripheral may need to appear or disappear.
            owner.onAe2GridChanged();
        }

        @Override
        public void onSaveChanges(NetworkAdapterBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    }
}
