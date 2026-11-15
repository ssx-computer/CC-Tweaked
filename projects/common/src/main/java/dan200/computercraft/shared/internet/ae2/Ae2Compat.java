// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.internet.NetworkAdapterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

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
        public void onSaveChanges(NetworkAdapterBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    }
}
