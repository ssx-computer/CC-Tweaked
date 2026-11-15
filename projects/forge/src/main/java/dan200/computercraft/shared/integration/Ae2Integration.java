// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.integration;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import dan200.computercraft.shared.ModRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes CC's network adapter to AE2.
 * <p>
 * AE2 looks for an {@link IInWorldGridNodeHost} through the {@link AECapabilities#IN_WORLD_GRID_NODE_HOST} capability,
 * and then asks that host for a grid node <em>per side</em>. Handing it the adapter is enough: the adapter only returns
 * a node on its AE2-facing side, so an AE2 cable attached to the CC side finds nothing there.
 * <p>
 * Note that AE2 has no release for Minecraft 1.21.1 on Fabric, so this integration is NeoForge only. The block itself is
 * registered on both loaders (and simply has no AE2 half where AE2 is missing).
 */
public final class Ae2Integration {
    public static final String MOD_ID = "ae2";

    private Ae2Integration() {
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            ModRegistry.BlockEntities.NETWORK_ADAPTER.get(),
            (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity.getAe2Host()
        );
    }

    public static void setup(IEventBus bus) {
        bus.addListener(Ae2Integration::registerCapabilities);
    }
}
