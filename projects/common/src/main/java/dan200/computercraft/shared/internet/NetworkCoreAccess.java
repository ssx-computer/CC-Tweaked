// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.component.ComputerComponent;
import dan200.computercraft.core.internet.DhcpServer;
import dan200.computercraft.core.internet.IpAddress;
import dan200.computercraft.core.internet.NetStack;
import dan200.computercraft.core.internet.Router;

/**
 * The parts of a {@link NetworkCoreBlockEntity} which its {@code net} API needs.
 * <p>
 * This exists so that the API can be registered as a {@linkplain ComputerComponent computer component}, without the
 * API having to know about block entities (or Minecraft at all).
 */
public interface NetworkCoreAccess {
    /**
     * The computer component which marks a computer as running on a {@link NetworkCoreBlock}.
     */
    ComputerComponent<NetworkCoreAccess> COMPONENT = ComputerComponent.create("computercraft", "network_core");

    /**
     * The address a network core claims for itself, and hands out as the default gateway.
     */
    IpAddress ADDRESS = IpAddress.of(10, 0, 0, 1);

    /**
     * The netmask of the network a core manages.
     */
    IpAddress NETMASK = IpAddress.of(255, 255, 255, 0);

    /**
     * The first address handed out by DHCP.
     */
    IpAddress POOL_FROM = IpAddress.of(10, 0, 0, 100);

    /**
     * The last address handed out by DHCP.
     */
    IpAddress POOL_TO = IpAddress.of(10, 0, 0, 200);

    /**
     * The virtual network stack the core's computer runs on.
     *
     * @return The core's stack.
     */
    NetStack getStack();

    /**
     * The DHCP server which hands out addresses to the devices on this network.
     *
     * @return The core's DHCP server.
     */
    DhcpServer getDhcp();

    /**
     * The routing and firewall rules for this network.
     *
     * @return The core's router.
     */
    Router getRouter();
}
