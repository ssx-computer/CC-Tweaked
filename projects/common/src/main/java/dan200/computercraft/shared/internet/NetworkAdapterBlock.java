// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.shared.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A network adapter: the block which joins a CC network to an AE2 one.
 * <p>
 * The block is an inverted T — a wide base plate with a post rising from it. The side it {@linkplain #FACING faces} takes
 * CC's networking cable, and the opposite side takes an AE2 cable; the two connectors are coloured differently so it is
 * obvious which is which at a glance. See {@link NetworkAdapterBlockEntity} for the two halves of the bridge.
 */
public class NetworkAdapterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /**
     * The shape of the block: a full-width base plate, with a post on top of it. As both parts are centred, the shape
     * does not depend on which way the block faces.
     */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, 4, 16),
        // The post runs the full depth of the block, so that the two connector faces are flush with the block's sides
        // and cables meet them exactly where they attach.
        Block.box(4, 4, 0, 12, 14, 16)
    );

    public NetworkAdapterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The face the player is looking at becomes the CC side, so you aim it like the other modems.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        // Connect our CC port as soon as we're placed, rather than waiting for the next chunk load.
        if (level.getBlockEntity(pos) instanceof NetworkAdapterBlockEntity adapter) adapter.scheduleConnectionsChanged();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock, BlockPos neighbourPos, boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof NetworkAdapterBlockEntity adapter) adapter.scheduleConnectionsChanged();
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof NetworkAdapterBlockEntity adapter) adapter.blockTick();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModRegistry.BlockEntities.NETWORK_ADAPTER.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only the server ticks: the adapter has no client-side state, and all of its work is server side.
        return level.isClientSide ? null : (level1, pos, state1, entity) -> {
            if (entity instanceof NetworkAdapterBlockEntity adapter) adapter.blockTick();
        };
    }
}
