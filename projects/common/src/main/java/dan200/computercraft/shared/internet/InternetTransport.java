// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

/**
 * Constants for CC's virtual network, which is layered on top of ComputerCraft's modem networks.
 *
 * @see InternetPort
 */
public final class InternetTransport {
    private InternetTransport() {
    }

    /**
     * The modem channel used to carry virtual network (IP) packets.
     * <p>
     * This channel is reserved for the virtual network stack: {@link NetworkSwitchBlockEntity switches} and
     * {@link NetworkCoreBlockEntity cores} only ever bridge traffic on this channel, so a modem's other channels are
     * never leaked between two segments of a switch. Programs should not send on this channel directly.
     * <p>
     * TODO(core): The core layer ({@code dan200.computercraft.core.internet}) must transmit and listen on this same
     *  channel. Note that {@code projects/core} cannot depend on {@code projects/common}, so the core stack has to
     *  declare its own constant with this value (20037).
     */
    public static final int CHANNEL = 0x4E45;
}
