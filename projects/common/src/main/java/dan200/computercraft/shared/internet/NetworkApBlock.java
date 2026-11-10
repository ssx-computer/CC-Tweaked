// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.shared.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A wireless access point (a "base station"): a block which joins CC's wireless modems to the wired network.
 * <p>
 * Like a switch, every side of the block is a wired {@link InternetPort}, so it can be cabled to the network core. It
 * also talks to wireless modems within {@link NetworkApBlockEntity#RANGE} blocks, bridging them onto the wired network.
 * See {@link NetworkApBlockEntity} for the details.
 */
public class NetworkApBlock extends Block implements EntityBlock {
    public NetworkApBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        // Connect up our ports as soon as we're placed, rather than waiting for the next chunk load.
        if (level.getBlockEntity(pos) instanceof NetworkApBlockEntity ap) ap.scheduleConnectionsChanged();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock, BlockPos neighbourPos, boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof NetworkApBlockEntity ap) ap.scheduleConnectionsChanged();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof NetworkApBlockEntity ap) ap.blockTick();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModRegistry.BlockEntities.NETWORK_AP.get().create(pos, state);
    }
}
