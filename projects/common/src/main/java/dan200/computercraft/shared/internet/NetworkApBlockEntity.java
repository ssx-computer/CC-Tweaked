// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketNetwork;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.PacketSender;
import dan200.computercraft.api.network.wired.WiredElement;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The block entity for a {@link NetworkApBlock}: a wireless access point, which joins CC's wireless modems to the
 * wired network.
 * <p>
 * Every side of the access point is a wired {@link InternetPort} (so it can be cabled to the network core), and it also
 * listens on CC's wireless network within {@link #RANGE} blocks. Frames are bridged between the two, so a pocket
 * computer (or a computer with a wireless modem) on the wireless side can talk to anything on the wired network.
 * <p>
 * The wireless side behaves like one more switch port: frames arriving over the air are flooded to the wired ports, and
 * frames for a device which was last seen over the air are transmitted wirelessly rather than out of a wired port.
 */
public class NetworkApBlockEntity extends BlockEntity implements InternetPort.Handler, PacketReceiver, PacketSender {
    /**
     * The port number we use for the wireless side. The wired ports use the index of their side, so this just has to be
     * a number which cannot collide with them.
     */
    static final int WIRELESS_PORT = DirectionUtil.FACINGS.length;

    /**
     * How far, in blocks, this access point can talk to wireless devices.
     * <p>
     * CC's range is symmetric (a packet is delivered if the distance is within either endpoint's range), so this is what
     * bounds the size of the wireless cell.
     */
    public static final double RANGE = 64.0;

    private final InternetPort[] ports = new InternetPort[DirectionUtil.FACINGS.length];
    private final SwitchTable forwardingTable = new SwitchTable();

    private final TickScheduler.Token tickToken = new TickScheduler.Token(this);
    private final ComponentAccess<WiredElement> connectedElements = PlatformHelper.get()
        .createWiredElementAccess(this, x -> scheduleConnectionsChanged());

    private @Nullable PacketNetwork wirelessNetwork;
    private boolean refreshConnections = false;

    public NetworkApBlockEntity(BlockEntityType<? extends NetworkApBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        var mac = Macs.forBlock(pos, "ap");
        for (var side : DirectionUtil.FACINGS) {
            ports[side.ordinal()] = new InternetPort(this, side, mac, "ap:" + side.getSerializedName(), this);
            forwardingTable.addPort(side.ordinal());
        }
        forwardingTable.addPort(WIRELESS_PORT);
    }

    // --- PacketReceiver: the wireless side ---------------------------------------------------

    @Override
    public Vec3 getPosition() {
        return Vec3.atCenterOf(getBlockPos());
    }

    @Override
    public String getSenderID() {
        return "network_ap";
    }

    @Override
    public double getRange() {
        return RANGE;
    }

    @Override
    public boolean isInterdimensional() {
        return false;
    }

    @Override
    public void receiveSameDimension(Packet packet, double distance) {
        onWirelessPacket(packet);
    }

    @Override
    public void receiveDifferentDimension(Packet packet) {
        // We are not an interdimensional access point, so ignore anything from another dimension.
    }

    /**
     * Handle a frame which arrived over the air.
     *
     * @param packet The packet which was received.
     */
    private void onWirelessPacket(Packet packet) {
        if (packet.channel() != InternetTransport.CHANNEL) return;
        if (!(packet.payload() instanceof byte[] data)) return;

        NetPacket frame;
        try {
            frame = NetPacket.decode(data);
        } catch (IllegalArgumentException e) {
            return;
        }

        var source = frame.srcMac();
        if (source != null) forwardingTable.learn(source, WIRELESS_PORT);

        if (frame.ttl() <= 1) return;

        var forwarded = frame.withTtl(frame.ttl() - 1);
        for (var outPort : forwardTo(frame.dstMac(), WIRELESS_PORT)) ports[outPort].send(forwarded);
    }

    // --- InternetPort.Handler: the wired side ------------------------------------------------

    @Override
    public void onPacket(InternetPort port, Packet packet) {
        if (packet.channel() != InternetTransport.CHANNEL) return;
        if (!(packet.payload() instanceof byte[] data)) return;

        NetPacket frame;
        try {
            frame = NetPacket.decode(data);
        } catch (IllegalArgumentException e) {
            return;
        }

        var inPort = port.index();
        var source = frame.srcMac();
        if (source != null) forwardingTable.learn(source, inPort);

        if (frame.ttl() <= 1) return;

        var forwarded = frame.withTtl(frame.ttl() - 1);
        for (var outPort : forwardTo(frame.dstMac(), inPort)) sendTo(outPort, forwarded);
    }

    /**
     * Send a frame out of a port, which may be a wired port or the wireless side.
     *
     * @param port  The port to send from.
     * @param frame The frame to send.
     */
    private void sendTo(int port, NetPacket frame) {
        if (port == WIRELESS_PORT) {
            var network = wirelessNetwork;
            if (network != null) network.transmitSameDimension(
                new Packet(InternetTransport.CHANNEL, InternetTransport.CHANNEL, frame.encode(), this), RANGE
            );
        } else {
            ports[port].send(frame);
        }
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
            if (known != null && known != inPort) return List.of(known);
        }

        var others = new ArrayList<Integer>(DirectionUtil.FACINGS.length);
        for (var candidate : forwardingTable.ports()) {
            if (candidate != inPort) others.add(candidate);
        }
        return others;
    }

    // --- Lifecycle --------------------------------------------------------------------------

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || !level.isClientSide) {
            for (var port : ports) port.remove();
            setWirelessNetwork(null);
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        refreshConnections = true;
        TickScheduler.schedule(tickToken);
    }

    /**
     * Mark this access point as needing to re-check its wired connections.
     */
    void scheduleConnectionsChanged() {
        refreshConnections = true;
        getLevel().scheduleTick(getBlockPos(), getBlockState().getBlock(), 0);
    }

    /**
     * Tick this access point. This is called from {@link NetworkApBlock#tick}.
     */
    void blockTick() {
        if (getLevel().isClientSide) return;

        // Join (or rejoin) the wireless network. This has to happen on the server, and may change if the world is
        // reloaded, so we check each time rather than caching it forever.
        var server = getLevel().getServer();
        if (server != null && wirelessNetwork == null) setWirelessNetwork(ComputerCraftAPI.getWirelessNetwork(server));

        if (refreshConnections) connectionsChanged();
    }

    private void setWirelessNetwork(@Nullable PacketNetwork network) {
        if (wirelessNetwork == network) return;
        if (wirelessNetwork != null) wirelessNetwork.removeReceiver(this);
        wirelessNetwork = network;
        if (wirelessNetwork != null) wirelessNetwork.addReceiver(this);
    }

    /**
     * Find the wired element for a specific side of this block.
     *
     * @param side The side to get the element for, or {@code null} for "any" side.
     * @return This side's port, or {@code null} if there is no such side.
     */
    @Nullable
    public InternetPort getElement(@Nullable Direction side) {
        return side == null ? null : ports[side.ordinal()];
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
            if (element != null) ports[side.ordinal()].getNode().connectTo(element.getNode());
        }
    }
}
