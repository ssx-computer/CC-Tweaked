// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.api.network.wired.WiredNode;
import dan200.computercraft.core.internet.DhcpServer;
import dan200.computercraft.core.internet.Mac;
import dan200.computercraft.core.internet.NetPacket;
import dan200.computercraft.core.internet.NetStack;
import dan200.computercraft.core.internet.Router;
import dan200.computercraft.core.internet.SwitchTable;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.TerminalSize;
import dan200.computercraft.shared.config.ConfigSpec;
import dan200.computercraft.shared.platform.ComponentAccess;
import dan200.computercraft.shared.platform.PlatformHelper;
import dan200.computercraft.shared.util.DirectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The block entity for a {@link NetworkCoreBlock}.
 * <p>
 * This is an ordinary computer ({@link ComputerBlockEntity}), with six additional {@link InternetPort}s. Reusing the
 * computer block entity is what gives us a terminal, and so the standard computer GUI, without any client-side code.
 * <p>
 * The core is the network's gateway: it runs a {@link NetStack} (so programs on it can use the {@code net} API), hands
 * out addresses with a {@link DhcpServer}, and enforces a {@link Router}'s routing and firewall rules. As well as
 * routing between subnets, it bridges its ports at the packet level, so that several switches (each an independent
 * segment) form a single logical network.
 */
public class NetworkCoreBlockEntity extends ComputerBlockEntity implements InternetPort.Handler, NetworkCoreAccess {
    private final InternetPort[] ports = new InternetPort[DirectionUtil.FACINGS.length];
    private final SwitchTable forwardingTable = new SwitchTable();

    private final Mac mac;
    private final NetStack stack;
    private final DhcpServer dhcp = new DhcpServer(ADDRESS, NETMASK);
    private final Router router = new Router();

    private final ComponentAccess<WiredElement> connectedElements = PlatformHelper.get()
        .createWiredElementAccess(this, x -> refreshConnections = true);

    private boolean refreshConnections = true;
    private long lastTick = 0;

    public NetworkCoreBlockEntity(BlockEntityType<? extends NetworkCoreBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state, ComputerFamily.NORMAL);

        mac = Macs.forBlock(pos, "core");
        stack = new NetStack(mac, (event, args) -> {
            // The computer may not exist yet (or the chunk may be unloading); queueing is then simply dropped.
            var computer = getServerComputer();
            if (computer != null) computer.queueEvent(event, args);
        });

        for (var side : DirectionUtil.FACINGS) {
            var port = new InternetPort(this, side, mac, "core:" + side.getSerializedName(), this);
            ports[side.ordinal()] = port;
            forwardingTable.addPort(side.ordinal());
            stack.addInterface(port);
        }

        stack.setAddress(ADDRESS, null, NETMASK);

        dhcp.setPool(POOL_FROM, POOL_TO);
        dhcp.setGateway(ADDRESS);
    }

    @Override
    public NetStack getStack() {
        return stack;
    }

    @Override
    public DhcpServer getDhcp() {
        return dhcp;
    }

    @Override
    public Router getRouter() {
        return router;
    }

    @Override
    protected ServerComputer createComputer(int id) {
        // Passing ourselves as a computer component is what lets the `net` API find our stack, DHCP server and router.
        return new ServerComputer((ServerLevel) getLevel(), getBlockPos(), ServerComputer.properties(id, getFamily())
            .label(getLabel())
            .terminalSize(new TerminalSize(ConfigSpec.computerTermWidth.get(), ConfigSpec.computerTermHeight.get()))
            .addComponent(NetworkCoreAccess.COMPONENT, this)
        );
    }

    @Override
    protected void serverTick() {
        // We tick our ports before the computer, as the computer doesn't tick until it's been turned on at least once.
        if (getLevel().isClientSide) return;
        if (refreshConnections) connectionsChanged();

        var now = System.currentTimeMillis();
        if (now - lastTick >= 1000) {
            lastTick = now;
            dhcp.tick(now);
            stack.tick(now);
            forwardingTable.tick(now);
        }

        super.serverTick();
    }

    @Override
    public void neighborChanged(BlockPos neighbour) {
        super.neighborChanged(neighbour);
        refreshConnections = true;
    }

    @Override
    public void neighbourShapeChanged(Direction direction) {
        super.neighbourShapeChanged(direction);
        refreshConnections = true;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        refreshConnections = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || !level.isClientSide) {
            for (var port : ports) port.remove();
        }
    }

    /**
     * Find the wired element for a specific side of this block.
     * <p>
     * Each side has its own element (and so its own {@link WiredNode}), which is what keeps the six segments a core is
     * attached to separate.
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
        if (!(packet.payload() instanceof byte[] data)) return;

        NetPacket frame;
        try {
            frame = NetPacket.decode(data);
        } catch (IllegalArgumentException e) {
            // A malformed frame. Nothing sensible we can do with it.
            return;
        }

        var inPort = port.index();

        // Learn where this sender lives, so that we can reply directly rather than flooding.
        var source = frame.srcMac();
        if (source != null) forwardingTable.learn(source, inPort);

        // Anything addressed to us (or broadcast) is ours to handle. That covers DHCP, and the sockets behind the
        // `net` API.
        var forUs = frame.dst().equals(ADDRESS) || frame.dst().isBroadcast();
        if (forUs) {
            if (frame.protocol() == NetPacket.PROTOCOL_UDP && frame.dstPort() == DhcpServer.SERVER_PORT) {
                var reply = dhcp.handle(frame, System.currentTimeMillis());
                // Reply straight back out of the port the request arrived on: the device which asked is that way.
                if (reply != null) port.send(reply);
            }

            stack.receive(port, frame);
        }

        // Then bridge the frame onto our other segments, so that devices on different switches can still reach each
        // other. Broadcasts are bridged too (as a real switch would), which is what lets a device find the core.
        if (frame.ttl() <= 1) return;

        var forwarded = frame.withTtl(frame.ttl() - 1);
        for (var outPort : forwardTo(frame.dstMac(), inPort)) {
            // Do not send a packet back out of the port it came in on, or back to a port which already handled it.
            ports[outPort].send(forwarded);
        }
    }

    /**
     * Work out which ports a frame should be bridged out of.
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

        var others = new ArrayList<Integer>(DirectionUtil.FACINGS.length - 1);
        for (var candidate : forwardingTable.ports()) {
            if (candidate != inPort) others.add(candidate);
        }
        return others;
    }

    private void connectionsChanged() {
        refreshConnections = false;

        var world = getLevel();
        var pos = getBlockPos();
        for (var side : DirectionUtil.FACINGS) {
            var offset = pos.relative(side);
            if (!world.isLoaded(offset)) continue;

            var element = connectedElements.get(side);
            if (element == null) {
                forwardingTable.removePort(side.ordinal());
                forwardingTable.addPort(side.ordinal());
                continue;
            }

            ports[side.ordinal()].getNode().connectTo(element.getNode());
        }
    }
}
