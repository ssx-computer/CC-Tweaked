// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.core.internet.Mac;
import net.minecraft.core.BlockPos;

/**
 * Generates stable {@link Mac}s for our blocks.
 * <p>
 * A MAC has to survive a world reload (otherwise a device would lose its address every time the chunk it sits in is
 * unloaded), but does not have to be meaningful. We derive one from the block's position and a per-block-type salt,
 * which gives a stable, effectively unique address without needing to store anything in the block entity's NBT.
 */
final class Macs {
    private Macs() {
    }

    /**
     * Derive a MAC for a block at a specific position.
     *
     * @param pos  The position of the block.
     * @param kind A short string identifying the sort of block, so that two different blocks in the same position (in
     *             different dimensions, say) do not share an address.
     * @return A MAC for this block.
     */
    static Mac forBlock(BlockPos pos, String kind) {
        // We deliberately do not use Objects.hash here: we want this to be stable across both worlds and versions.
        var hash = pos.getX() * 0x9E3779B1L ^ pos.getY() * 0x85EBCA77L ^ pos.getZ() * 0xC2B2AE3DL ^ kind.hashCode();
        return new Mac(String.format("cc:%02x:%02x:%02x:%02x",
            (hash >>> 24) & 0xFF, (hash >>> 16) & 0xFF, (hash >>> 8) & 0xFF, hash & 0xFF));
    }
}
