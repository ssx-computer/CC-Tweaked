// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

/**
 * A network port ("interface") on a device.
 * <p>
 * A {@link NetStack} sends packets through the interface's {@link #send(NetPacket)} method. The mod layer is
 * responsible for delivering received packets back to the stack with {@link NetStack#receive(NetInterface, NetPacket)}.
 *
 * @see NetStack
 */
public interface NetInterface {
    /**
     * The MAC (hardware address) of this interface.
     *
     * @return This interface's MAC.
     */
    Mac mac();

    /**
     * A human-readable name for this interface, used in logs and reported by {@code net.interfaces()}. For example
     * {@code eth0} or {@code sw1:2}.
     *
     * @return This interface's name.
     */
    String name();

    /**
     * Send a packet out of this interface, onto the wire.
     *
     * @param packet The packet to send.
     */
    void send(NetPacket packet);
}
