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
 * A network switch: a block with six independent network ports, which bridges packets between them.
 * <p>
 * Each side of the block is a separate wired network, see {@link NetworkSwitchBlockEntity} and {@link InternetPort}.
 */
public class NetworkSwitchBlock extends Block implements EntityBlock {
    public NetworkSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        // Connect up our ports as soon as we're placed. We also refresh connections in clearRemoved, but this means we
        // don't have to wait for a chunk load.
        if (level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity modem) modem.scheduleConnectionsChanged();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock, BlockPos neighbourPos, boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity modem) modem.scheduleConnectionsChanged();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity modem) modem.blockTick();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModRegistry.BlockEntities.NETWORK_SWITCH.get().create(pos, state);
    }
}
