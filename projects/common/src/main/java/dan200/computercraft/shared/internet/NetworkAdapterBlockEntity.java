// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.wired.WiredElement;
import dan200.computercraft.core.internet.NetPacket;
import dan200.computercraft.core.internet.NetStack;
import dan200.computercraft.shared.internet.ae2.Ae2Compat;
import dan200.computercraft.shared.platform.ComponentAccess;
import dan200.computercraft.shared.platform.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The block entity for a {@link NetworkAdapterBlock}: one half speaks CC's networking cable, the other speaks AE2's ME
 * network.
 * <p>
 * The CC half is an {@link InternetPort}, and so a {@link WiredElement} — a CC cable attached to the
 * {@linkplain #ccSide() CC side} joins it to CC's wired network, exactly as with a switch or a core.
 * <p>
 * The AE2 half is an AE2 grid node, which is <em>only</em> visible on the {@linkplain #ae2Side() AE2 side}. Everything
 * to do with AE2 lives in {@link Ae2Compat}, and this class never mentions an AE2 type in its signatures or fields.
 * That is deliberate: it means this class loads (and the block is usable as a plain decoration) when AE2 is not
 * installed, and only the calls which actually need AE2 fail.
 */
public class NetworkAdapterBlockEntity extends BlockEntity implements InternetPort.Handler {
    /**
     * The name AE2 is registered under. Used to decide whether the AE2 half of the adapter can be created.
     */
    static final String AE2_MOD_ID = "ae2";

    /**
     * The CC-facing port. This is the wired element CC cables connect to.
     */
    private final InternetPort ccPort;

    /**
     * The AE2 grid node host, as an {@code Ae2Compat} instance, or {@code null} when AE2 is not installed (or has not
     * been set up yet). Kept as an {@link Object} so that this class does not reference AE2.
     */
    private @Nullable Object ae2Host;

    private final ComponentAccess<WiredElement> connectedElements = PlatformHelper.get()
        .createWiredElementAccess(this, x -> scheduleConnectionsChanged());

    private boolean refreshConnections = true;

    /**
     * Whether the AE2 node has been added to the level. The host is created in the constructor (so that NBT can be
     * read into it), but the node itself cannot join the grid until the block entity is in a loaded chunk.
     */
    private boolean ae2Created = false;

    /**
     * The CC peripheral exposing the ME network, only present on the CC network while an ME network is reachable.
     */
    private final NetworkAdapterPeripheral peripheral = new NetworkAdapterPeripheral(this);

    /**
     * Whether the peripheral is currently announced on the CC network. Toggled by {@link #refreshPeripheral()}.
     */
    private boolean peripheralPresent = false;

    /**
     * This adapter's virtual IP stack, used to join the CC virtual network and answer ME requests over it.
     * <p>
     * Like {@link #ccPort}, this is created in the constructor and so is always present; it only becomes useful once
     * the adapter has been told to join the network.
     */
    private final NetStack netStack;

    /**
     * The id of the socket which serves ME requests on {@link #ME_PORT}, or {@code -1} if we have not opened one yet.
     */
    private int meSocket = -1;

    /**
     * Whether this adapter has asked to join the CC virtual network yet.
     */
    private boolean netJoined = false;

    /**
     * When we last asked for an address, in milliseconds since the epoch. Used to retry DHCP until a core appears.
     */
    private long lastDhcp = Long.MIN_VALUE;

    /**
     * How often we re-broadcast a DHCP request, in milliseconds, until we have an address.
     */
    private static final long DHCP_RETRY_MS = 5000;

    public NetworkAdapterBlockEntity(BlockEntityType<? extends NetworkAdapterBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        var mac = Macs.forBlock(pos, "adapter");
        ccPort = new InternetPort(this, DEFAULT_CC_SIDE, mac, "adapter:cc", this);
        netStack = new NetStack(mac, null);

        // Create the AE2 half up front, so that loadAdditional can feed it its saved state. This is the only place
        // which touches AE2 outside of a tick, and it is guarded, so nothing AE2-shaped is loaded without AE2.
        if (isAe2Loaded()) ae2Host = Ae2Compat.createHost(this);
    }

    /**
     * The side of the block which CC cables connect to.
     *
     * @return The CC side.
     */
    public Direction ccSide() {
        var state = getBlockState();
        return state.hasProperty(NetworkAdapterBlock.FACING) ? state.getValue(NetworkAdapterBlock.FACING) : DEFAULT_CC_SIDE;
    }

    /**
     * The side of the block which AE2 cables connect to.
     *
     * @return The AE2 side.
     */
    public Direction ae2Side() {
        return ccSide().getOpposite();
    }

    /**
     * Find the wired element for a specific side of this block.
     * <p>
     * Only the CC side is a wired element: AE2 cables must not be able to join CC's network by accident.
     *
     * @param side The side to get the element for, or {@code null} for "any" side.
     * @return The CC port if this is the CC side, otherwise {@code null}.
     */
    @Nullable
    public InternetPort getElement(@Nullable Direction side) {
        return side != null && side == ccSide() ? ccPort : null;
    }

    /**
     * The AE2 grid node host for this block, or {@code null} if AE2 is not installed.
     * <p>
     * The result is an {@code Ae2Compat} instance, which implements AE2's {@code IInWorldGridNodeHost} and returns a
     * grid node only for the AE2 side. It is returned as an {@link Object} so that callers which do need AE2 can cast
     * it, and callers which do not never have to load an AE2 class.
     *
     * @return The AE2 host, or {@code null}.
     */
    @Nullable
    public Object getAe2Host() {
        return ae2Host;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        var host = ae2Host;
        if (host != null) Ae2Compat.saveToNbt(host, nbt);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        var host = ae2Host;
        if (host != null) Ae2Compat.loadFromNbt(host, nbt);
    }

    /**
     * Tick this adapter. Called from {@link NetworkAdapterBlock#getTicker}.
     */
    void blockTick() {
        if (getLevel() == null || getLevel().isClientSide) return;

        // Bring the AE2 half into the level, once we are somewhere it can join the grid.
        var host = ae2Host;
        if (host != null && !ae2Created) {
            ae2Created = true;
            Ae2Compat.create(host, getLevel(), getBlockPos());
        }

        if (refreshConnections) connectionsChanged();

        refreshPeripheral();

        netTick();
    }

    /**
     * Announce (or withdraw) the {@link NetworkAdapterPeripheral} on the CC network, depending on whether the adapter
     * currently has an active ME network to expose.
     */
    private void refreshPeripheral() {
        if (getLevel() == null || getLevel().isClientSide) return;

        var host = ae2Host;
        var active = host != null && Ae2Compat.isGridActive(host);
        if (active == peripheralPresent) return;

        peripheralPresent = active;
        // Only update when the state actually changes, to avoid broadcasting a network change every tick.
        ccPort.getNode().updatePeripherals(active ? Map.of(peripheralName(), peripheral) : Map.of());
    }

    /**
     * The name the {@link NetworkAdapterPeripheral} is announced under on the CC network.
     *
     * @return The peripheral's name.
     */
    private String peripheralName() {
        return "me" + getBlockPos().toShortString();
    }

    /**
     * Called by {@link Ae2Compat} when the AE2 grid's state changes, so the CC peripheral can be re-announced.
     */
    public void onAe2GridChanged() {
        if (getLevel() instanceof ServerLevel level) {
            level.scheduleTick(getBlockPos(), getBlockState().getBlock(), 0);
        }
    }

    /**
     * Whether AE2 is installed, and so whether the AE2 half of this adapter can be created.
     *
     * @return Whether AE2 is present.
     */
    public boolean isAe2Loaded() {
        return PlatformHelper.get().isModLoaded(AE2_MOD_ID);
    }

    /**
     * Mark this adapter as needing to re-check its CC connections.
     */
    void scheduleConnectionsChanged() {
        refreshConnections = true;
        if (getLevel() instanceof ServerLevel level) {
            level.scheduleTick(getBlockPos(), getBlockState().getBlock(), 0);
        }
    }

    @Override
    public void onPacket(InternetPort port, Packet packet) {
        // Only virtual-network (IP) frames are of interest to us; a modem's other channels are ignored.
        if (packet.channel() != InternetTransport.CHANNEL) return;
        if (!(packet.payload() instanceof byte[] data)) return;

        NetPacket frame;
        try {
            frame = NetPacket.decode(data);
        } catch (IllegalArgumentException e) {
            // A malformed frame. Nothing sensible we can do with it.
            return;
        }

        netStack.receive(port, frame);
    }

    /**
     * Join CC's virtual network and serve ME requests over it.
     * <p>
     * The adapter only joins once AE2 is installed, and opens a single UDP socket on {@link #ME_PORT} which translates
     * between virtual-network messages and ME storage operations (see {@link Ae2Compat#handleRequest}).
     */
    private void netTick() {
        if (getLevel() == null || getLevel().isClientSide) return;
        if (ae2Host == null) return;

        if (!netJoined) {
            netJoined = true;
            // The adapter is a device on the virtual network, just as a computer or core is.
            netStack.addInterface(ccPort);
            netStack.requestDhcp();
        }

        // Retry DHCP until we have an address, so an adapter placed before its network core is set up still joins
        // once the core appears.
        var now = System.currentTimeMillis();
        if (netStack.address() == null && now - lastDhcp >= DHCP_RETRY_MS) {
            lastDhcp = now;
            netStack.requestDhcp();
        }

        // We can listen before we have an address: we simply won't receive unicast requests until DHCP replies.
        if (meSocket < 0) {
            try {
                meSocket = netStack.listen(ME_PORT);
            } catch (IllegalArgumentException e) {
                // Should not happen: we only ever bind this one port. Leave it unset so we try again next tick.
            }
        }

        netStack.tick(now);
        handleMeSocket();
    }

    /**
     * Answer any ME requests which have arrived on our socket.
     * <p>
     * Each request is answered with a unicast reply back to the sender, so a requester can open a socket, send a
     * request (broadcast or unicast) and wait for the reply on that socket.
     */
    private void handleMeSocket() {
        if (meSocket < 0) return;
        var socket = netStack.socket(meSocket);
        if (socket == null) return;

        var host = ae2Host;
        if (host == null) return;

        for (var message : socket.drain()) {
            var request = new String(message.payload(), StandardCharsets.UTF_8);
            var reply = Ae2Compat.handleRequest(host, request);
            // Sending is a no-op until we have an address, so a request we cannot answer is simply dropped.
            netStack.send(message.from(), message.port(), ME_PORT, NetPacket.PROTOCOL_UDP, reply.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || !level.isClientSide) {
            ccPort.remove();
            netStack.closeAll();
            var host = ae2Host;
            if (host != null) {
                Ae2Compat.destroy(host);
                ae2Host = null;
                ae2Created = false;
            }
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        refreshConnections = true;
    }

    private void connectionsChanged() {
        if (getLevel() == null || getLevel().isClientSide) return;
        refreshConnections = false;

        var world = getLevel();
        var pos = getBlockPos();
        var side = ccSide();
        var offset = pos.relative(side);
        if (!world.isLoaded(offset)) return;

        var element = connectedElements.get(side);
        if (element != null) ccPort.getNode().connectTo(element.getNode());
    }

    /**
     * The side used before the block state has been read — only relevant if the block state is somehow missing our
     * property, which should not happen.
     */
    private static final Direction DEFAULT_CC_SIDE = Direction.NORTH;

    /**
     * The UDP port the adapter serves ME requests on over CC's virtual network. This is the ASCII pair "ME"
     * ({@code 0x4D45}), chosen to be easy to remember and unlikely to collide with a modem's other ports.
     */
    static final int ME_PORT = 0x4D45;
}
