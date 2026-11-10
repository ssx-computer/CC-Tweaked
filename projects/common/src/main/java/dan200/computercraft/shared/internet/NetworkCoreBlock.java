// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import com.mojang.serialization.MapCodec;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.computer.blocks.ComputerBlock;
import dan200.computercraft.shared.util.BlockCodecs;

/**
 * The network core: a computer which also has six network ports, and which acts as the DHCP server, router and
 * firewall for the virtual network.
 * <p>
 * This is a {@link ComputerBlock}, and so inherits all of a computer's behaviour — the terminal, its GUI, turning it
 * on and off, redstone and peripherals. The only thing it adds is the six {@link InternetPort}s from
 * {@link NetworkCoreBlockEntity}.
 */
public class NetworkCoreBlock extends ComputerBlock<NetworkCoreBlockEntity> {
    private static final MapCodec<NetworkCoreBlock> CODEC = BlockCodecs.blockWithBlockEntityCodec(NetworkCoreBlock::new, x -> x.type);

    public NetworkCoreBlock(Properties properties) {
        super(properties, ModRegistry.BlockEntities.NETWORK_CORE);
    }

    @Override
    protected MapCodec<? extends NetworkCoreBlock> codec() {
        return CODEC;
    }
}
