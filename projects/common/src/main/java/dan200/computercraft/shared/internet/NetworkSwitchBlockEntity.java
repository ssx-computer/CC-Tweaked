// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.core.internet.Mac;
import dan200.computercraft.core.internet.NetPacket;
import dan200.computercraft.core.internet.SwitchTable;
import dan200.computercraft.shared.platform.ComponentAccess;
import dan200.computercraft.shared.platform.PlatformHelper;
import dan200.computercraft.shared.util.DirectionUtil;
import dan200.computercraft.shared.util.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The block entity for a {@link NetworkSwitchBlock}.
 * <p>
 * Every side of the switch is an independent {@link InternetPort}, and so an independent wired network. Packets which
 * arrive on one port are forwarded to the others, which bridges those networks at the packet level. Only packets on
 * {@link InternetTransport#CHANNEL} are bridged: a modem's other channels never cross a switch.
 * <p>
 * This behaves like a real switch: it learns which port a MAC is reachable through, and only floods a packet to every
 * other port when it has not seen that MAC before. A switch itself has no address of its own, and so does not need a
 * {@code NetStack} — it is a layer 2 device, and the network core is what assigns addresses.
 */
public class NetworkSwitchBlockEntity extends BlockEntity implements InternetPort.Handler {
    private final InternetPort[] ports = new InternetPort[DirectionUtil.FACINGS.length];
    private final SwitchTable forwardingTable = new SwitchTable();

    private final TickScheduler.Token tickToken = new TickScheduler.Token(this);
    private final ComponentAccess<WiredElement> connectedElements = PlatformHelper.get()
        .createWiredElementAccess(this, x -> scheduleConnectionsChanged());

    private boolean refreshConnections = false;
    private long lastTableTick = 0;

    public NetworkSwitchBlockEntity(BlockEntityType<? extends NetworkSwitchBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        // A switch has no address of its own, but each port still needs a MAC to transmit with, and every port still
        // needs to be registered with the forwarding table.
        var mac = Macs.forBlock(pos, "switch");
        for (var side : DirectionUtil.FACINGS) {
            ports[side.ordinal()] = new InternetPort(this, side, mac, "switch:" + side.getSerializedName(), this);
            forwardingTable.addPort(side.ordinal());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || !level.isClientSide) {
            for (var port : ports) port.remove();
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        refreshConnections = true;
        TickScheduler.schedule(tickToken);
    }

    /**
     * Mark this switch as needing to re-check its connections to its neighbours.
     */
    void scheduleConnectionsChanged() {
        refreshConnections = true;
        getLevel().scheduleTick(getBlockPos(), getBlockState().getBlock(), 0);
    }

    /**
     * Tick this switch. This is called from {@link NetworkSwitchBlock#tick}.
     */
    void blockTick() {
        if (getLevel().isClientSide) return;
        if (refreshConnections) connectionsChanged();

        // Expire learnt addresses every so often, so that a device which moves to another port is rediscovered.
        var now = System.currentTimeMillis();
        if (now - lastTableTick >= 1000) {
            lastTableTick = now;
            forwardingTable.tick(now);
        }
    }

    /**
     * Find the wired element for a specific side of this block.
     * <p>
     * Each side has its own element (and so its own {@link WiredNode}), which is what keeps the six segments of a
     * switch separate.
     *
     * @param side The side to get the element for, or {@code null} for "any" side.
     * @return This side's port, or {@code null} if there is no such side.
     */
    @Nullable
    public InternetPort getElement(@Nullable Direction side) {
        return side == null ? null : ports[side.ordinal()];
    }

    @Override
    public void onPacket(InternetPort port, Packet packet) {
        if (packet.channel() != InternetTransport.CHANNEL) return;

        // Only our own frames are bridged. Anything else on this channel is not ours to forward.
        if (!(packet.payload() instanceof byte[] data)) return;

        NetPacket frame;
        try {
            frame = NetPacket.decode(data);
        } catch (IllegalArgumentException e) {
            // A malformed frame. Drop it rather than forwarding it onto the other segments.
            return;
        }

        var inPort = port.index();

        // Learn where this sender lives, so we only have to flood the first time.
        var source = frame.srcMac();
        if (source != null) forwardingTable.learn(source, inPort);

        // Refuse to forward a frame which would not survive the hop, so that a loop in the cabling cannot spin forever.
        if (frame.ttl() <= 1) return;

        var forwarded = frame.withTtl(frame.ttl() - 1);
        for (var outPort : forwardTo(frame.dstMac(), inPort)) ports[outPort].send(forwarded);
    }

    /**
     * Work out which ports a frame should be sent out of.
     *
     * @param destination The MAC the frame is for, or {@code null} if it is a broadcast.
     * @param inPort      The port the frame arrived on.
     * @return The ports to send the frame out of.
     */
    private List<Integer> forwardTo(@Nullable Mac destination, int inPort) {
        if (destination != null) {
            var known = forwardingTable.portFor(destination);
            // Never echo a frame back out of the port it came in on: the device which sent it is that way.
            if (known != null && known != inPort) return List.of(known);
        }

        var others = new ArrayList<Integer>(DirectionUtil.FACINGS.length - 1);
        for (var candidate : forwardingTable.ports()) {
            if (candidate != inPort) others.add(candidate);
        }
        return others;
    }

    private void connectionsChanged() {
        if (getLevel().isClientSide) return;
        refreshConnections = false;

        var world = getLevel();
        var pos = getBlockPos();
        for (var side : DirectionUtil.FACINGS) {
            var offset = pos.relative(side);
            if (!world.isLoaded(offset)) continue;

            var element = connectedElements.get(side);
            if (element == null) {
                // Nothing is wired up on this side anymore, so drop everything we learnt there.
                forwardingTable.removePort(side.ordinal());
                forwardingTable.addPort(side.ordinal());
                continue;
            }

            ports[side.ordinal()].getNode().connectTo(element.getNode());
        }
    }
}
