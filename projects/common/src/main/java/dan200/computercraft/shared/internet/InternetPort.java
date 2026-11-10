// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.core.internet.Mac;
import dan200.computercraft.core.internet.NetInterface;
import dan200.computercraft.core.internet.NetPacket;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A single network port on a {@link NetworkSwitchBlock} or {@link NetworkCoreBlock}.
 * <p>
 * Each port is a {@linkplain WiredElement wired element} with its own {@link WiredNode}, and is only ever exposed to
 * the block adjacent to it: when Minecraft's cable network asks the switch for a wired element on a given side, it is
 * given the port for that side only. Consequently cables on different sides of a switch end up on different
 * {@link WiredNode}s, and Minecraft's own cable network cannot merge them. Packets are only carried between those
 * networks by the block entity which owns this port.
 * <p>
 * The port also acts as a {@link PacketReceiver}, so that the owning block entity sees every modem packet which is
 * transmitted on its side's network.
 * <p>
 * Finally, it is a {@link NetInterface}, so that a {@link NetworkCoreBlockEntity} can attach its ports to its virtual
 * network stack. Only packets on {@link InternetTransport#CHANNEL} are ever encoded or decoded; a modem's other channels
 * are ignored by the virtual network entirely.
 */
public final class InternetPort implements WiredElement, PacketReceiver, NetInterface {
    /**
     * The range packets are sent and received over. Wired networks have no notion of range, so we use the same
     * "infinite" range as a {@code WiredModemPeripheral}.
     */
    private static final double RANGE = Integer.MAX_VALUE;

    private final BlockEntity owner;
    private final Direction side;
    private final Handler handler;
    private final Mac mac;
    private final String name;

    private final WiredNode node = ComputerCraftAPI.createWiredNodeForElement(this);

    /**
     * Create a new port.
     *
     * @param owner   The block entity this port belongs to.
     * @param side    The side of the owning block this port sits on.
     * @param mac     The MAC this port transmits with. A device has a single MAC, shared by all of its ports.
     * @param name    The name of this port, as reported by the virtual network stack, e.g. {@code core:north}.
     * @param handler The callback to notify when a packet is received.
     */
    InternetPort(BlockEntity owner, Direction side, Mac mac, String name, Handler handler) {
        this.owner = owner;
        this.side = side;
        this.mac = mac;
        this.name = name;
        this.handler = handler;
        node.addReceiver(this);
    }

    /**
     * The index of this port, used by the routing and switching tables.
     *
     * @return This port's index, in the range {@code [0, 6)}.
     */
    public int index() {
        return side.ordinal();
    }

    /**
     * Get the side of the owning block this port sits on.
     *
     * @return The side of this port.
     */
    public Direction getSide() {
        return side;
    }

    @Override
    public Mac mac() {
        return mac;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void send(NetPacket packet) {
        send(new Packet(InternetTransport.CHANNEL, InternetTransport.CHANNEL, packet.encode(), this));
    }

    @Override
    public WiredNode getNode() {
        return node;
    }

    @Override
    public String getSenderID() {
        return "network";
    }

    @Override
    public Level getLevel() {
        return owner.getLevel();
    }

    @Override
    public Vec3 getPosition() {
        return Vec3.atCenterOf(owner.getBlockPos().relative(side));
    }

    @Override
    public double getRange() {
        return RANGE;
    }

    @Override
    public boolean isInterdimensional() {
        return true;
    }

    @Override
    public void receiveSameDimension(Packet packet, double distance) {
        receive(packet);
    }

    @Override
    public void receiveDifferentDimension(Packet packet) {
        receive(packet);
    }

    /**
     * Send a packet out of this port, and so onto this port's network.
     *
     * @param packet The packet to send. The packet's sender is replaced with this port.
     */
    public void send(Packet packet) {
        node.transmitSameDimension(new Packet(packet.channel(), packet.replyChannel(), packet.payload(), this), RANGE);
    }

    /**
     * Remove this port from its network.
     * <p>
     * This should be called on the server when the owning block entity is removed.
     */
    public void remove() {
        node.removeReceiver(this);
        node.remove();
    }

    private void receive(Packet packet) {
        // Sending a packet out of this port also delivers it back to our own receiver, as we're part of that network.
        // We must ignore those, or we'd forward the same packet backwards and forwards forever.
        if (packet.sender() == this) return;

        handler.onPacket(this, packet);
    }

    /**
     * A callback which is notified when a packet is received on one of a block's ports.
     */
    @FunctionalInterface
    public interface Handler {
        /**
         * Handle a packet which was received on a port.
         *
         * @param port   The port the packet was received on.
         * @param packet The packet which was received.
         */
        void onPacket(InternetPort port, Packet packet);
    }
}
