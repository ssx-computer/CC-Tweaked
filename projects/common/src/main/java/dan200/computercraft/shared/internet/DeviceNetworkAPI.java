// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.core.internet.IpAddress;
import dan200.computercraft.core.internet.Mac;
import dan200.computercraft.core.internet.NetInterface;
import dan200.computercraft.core.internet.NetPacket;
import dan200.computercraft.core.internet.NetStack;
import dan200.computercraft.core.internet.Socket;
import dan200.computercraft.shared.peripheral.modem.ModemPeripheral;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code net} API, as seen by a program running on an ordinary computer which has one or more modems.
 * <p>
 * This makes the computer a host on CC's virtual network: it obtains an address with DHCP, and can then send and
 * receive messages by IP address and port. Each attached modem becomes one {@linkplain NetInterface interface} of the
 * computer's stack.
 * <p>
 * The counterpart of this API is {@link NetworkAPI}, which runs on a {@link NetworkCoreBlock} and configures the
 * network itself.
 *
 * @cc.module net
 */
public class DeviceNetworkAPI implements ILuaAPI {
    private final IComputerSystem computer;
    private final Mac mac;
    private final NetStack stack;

    /**
     * The modems we have attached to, and the interface we expose for each of them.
     */
    private final Map<String, ModemInterface> interfaces = new LinkedHashMap<>();

    private boolean started = false;

    public DeviceNetworkAPI(IComputerSystem computer) {
        this.computer = computer;

        // A computer's id is stable across reloads, which is exactly what we want for a hardware address.
        var id = computer.getID();
        mac = new Mac(String.format("cc:%02x:%02x", (id >>> 8) & 0xFF, id & 0xFF));

        stack = new NetStack(mac, (event, args) -> computer.queueEvent(event, args));
    }

    @Override
    public String[] getNames() {
        return new String[]{ "net" };
    }

    @Override
    public void startup() {
        started = true;
        refreshInterfaces();

        // Ask for an address straight away, so that a program can just wait for the net_dhcp event.
        if (!interfaces.isEmpty()) stack.requestDhcp();
    }

    @Override
    public void update() {
        if (started) refreshInterfaces();
    }

    @Override
    public void shutdown() {
        started = false;
        for (var iface : interfaces.values()) iface.detach();
        interfaces.clear();
        stack.closeAll();
    }

    /**
     * Keep our set of interfaces in step with the modems which are actually attached.
     */
    private void refreshInterfaces() {
        var available = computer.getAvailablePeripherals();

        // Attach to any modem we have not seen before.
        for (var entry : available.entrySet()) {
            if (interfaces.containsKey(entry.getKey()) || !(entry.getValue() instanceof ModemPeripheral modem)) continue;

            var iface = new ModemInterface(entry.getKey(), modem);
            interfaces.put(entry.getKey(), iface);
            stack.addInterface(iface);
            iface.attach();
        }

        // And drop any which have gone away.
        if (interfaces.size() > available.size()) {
            var removed = new ArrayList<String>();
            for (var name : interfaces.keySet()) {
                if (!available.containsKey(name)) removed.add(name);
            }
            for (var name : removed) {
                var iface = interfaces.remove(name);
                if (iface == null) continue;
                iface.detach();
                stack.removeInterface(iface);
            }
        }
    }

    /**
     * Get this computer's MAC address, as used on the virtual network.
     *
     * @return This computer's MAC address.
     */
    @LuaFunction
    public final String getMac() {
        return mac.toString();
    }

    /**
     * Get the address DHCP has given this computer.
     *
     * @return This computer's address, or {@code nil} if it does not have one yet.
     */
    @LuaFunction
    public final @Nullable String getAddress() {
        var address = stack.address();
        return address == null ? null : address.toString();
    }

    /**
     * Get the netmask of the network this computer is on.
     *
     * @return This computer's netmask, or {@code nil} if it does not have an address yet.
     */
    @LuaFunction
    public final @Nullable String getNetmask() {
        var netmask = stack.netmask();
        return netmask == null ? null : netmask.toString();
    }

    /**
     * Get the address of the network's gateway.
     *
     * @return The gateway's address, or {@code nil} if this computer does not have one.
     */
    @LuaFunction
    public final @Nullable String getGateway() {
        var gateway = stack.gateway();
        return gateway == null ? null : gateway.toString();
    }

    /**
     * Ask the network for an address.
     * <p>
     * This happens automatically when the computer starts. When the address arrives, a {@code net_dhcp} event is
     * raised with the new address, netmask and gateway.
     *
     * @return Whether the request was sent.
     * @cc.usage Ask for an address, and print it once it arrives.
     * <pre>{@code
     * net.dhcp()
     * local _, address = os.pullEvent("net_dhcp")
     * print("I am", address)
     * }</pre>
     */
    @LuaFunction
    public final boolean dhcp() {
        refreshInterfaces();
        return stack.requestDhcp();
    }

    /**
     * List this computer's network interfaces.
     *
     * @return A list of interfaces, each with {@code name}, {@code mac} and {@code modem} fields.
     */
    @LuaFunction
    public final List<Map<String, Object>> getInterfaces() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : interfaces.entrySet()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", entry.getValue().name());
            info.put("mac", entry.getValue().mac().toString());
            info.put("modem", entry.getKey());
            result.add(info);
        }
        return result;
    }

    /**
     * Open a socket, and start listening for messages on a port.
     *
     * @param port     The port to listen on.
     * @param protocol The protocol, either {@code "udp"} (the default) or {@code "tcp"}.
     * @return The id of the new socket.
     * @throws LuaException If the port is invalid.
     * @cc.usage Listen on port 80 and print everything which arrives.
     * <pre>{@code
     * local socket = net.listen(80)
     * while true do
     *   local _, id, data, from = os.pullEvent("net_message")
     *   print("Received from " .. from .. ": " .. data)
     * end
     * }</pre>
     */
    @LuaFunction
    public final int listen(int port, Optional<String> protocol) throws LuaException {
        var proto = parseProtocol(protocol.orElse("udp"));
        try {
            return stack.listen(port, proto);
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
    }

    /**
     * Close a socket.
     *
     * @param socket The id of the socket to close.
     */
    @LuaFunction
    public final void close(int socket) {
        stack.close(socket);
    }

    /**
     * Send a message to another computer.
     *
     * @param address  The address to send to.
     * @param port     The port to send to.
     * @param message  The message to send.
     * @param socket   The id of a socket to send from, which sets the source port.
     * @param protocol The protocol, either {@code "udp"} (the default) or {@code "tcp"}.
     * @return Whether the message was sent. This will be {@code false} if this computer has no address yet.
     * @throws LuaException If the address or port is invalid.
     * @cc.usage Send a message to the gateway.
     * <pre>{@code
     * net.send("10.0.0.1", 80, "hello")
     * }</pre>
     */
    @LuaFunction
    public final boolean send(String address, int port, String message, Optional<Integer> socket, Optional<String> protocol) throws LuaException {
        var destination = checkAddress(address);
        var proto = parseProtocol(protocol.orElse("udp"));

        int sourcePort;
        if (socket.isPresent()) {
            var handle = stack.socket(socket.get());
            if (handle == null) throw new LuaException("No such socket.");
            sourcePort = handle.localPort();
        } else {
            sourcePort = port;
        }

        try {
            return stack.send(destination, port, sourcePort, proto, message.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
    }

    /**
     * Connect a socket to a remote computer.
     *
     * @param socket  The id of the socket to connect.
     * @param address The address to connect to.
     * @param port    The port to connect to.
     * @return Whether the connection attempt was started.
     * @throws LuaException If the address is invalid.
     */
    @LuaFunction
    public final boolean connect(int socket, String address, int port) throws LuaException {
        return stack.connect(socket, checkAddress(address), port);
    }

    /**
     * Receive any messages which are waiting on a socket.
     *
     * @param socket The id of the socket to read.
     * @return The messages waiting on this socket, each with {@code from}, {@code port} and {@code message} fields.
     * @throws LuaException If there is no such socket.
     */
    @LuaFunction
    public final List<Map<String, Object>> receive(int socket) throws LuaException {
        var handle = stack.socket(socket);
        if (handle == null) throw new LuaException("No such socket.");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Socket.Message message : handle.drain()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("from", message.from().toString());
            entry.put("port", message.port());
            entry.put("message", new String(message.payload(), StandardCharsets.UTF_8));
            result.add(entry);
        }
        return result;
    }

    private static int parseProtocol(String protocol) throws LuaException {
        if (protocol.equalsIgnoreCase("udp")) return NetPacket.PROTOCOL_UDP;
        if (protocol.equalsIgnoreCase("tcp")) return NetPacket.PROTOCOL_TCP;
        throw new LuaException("Expected 'udp' or 'tcp'.");
    }

    /**
     * Check whether a computer has any modem attached, and so could join the virtual network.
     *
     * @param computer The computer to check.
     * @return Whether this computer has a modem.
     */
    public static boolean hasModem(IComputerSystem computer) {
        for (var peripheral : computer.getAvailablePeripherals().values()) {
            if (peripheral instanceof ModemPeripheral) return true;
        }
        return false;
    }

    private static IpAddress checkAddress(String address) throws LuaException {
        try {
            return IpAddress.parse(address);
        } catch (IllegalArgumentException e) {
            throw new LuaException("Invalid address '" + address + "'.");
        }
    }

    /**
     * A {@link NetInterface} backed by one of the computer's modems.
     */
    private final class ModemInterface implements NetInterface {
        private final String name;
        private final ModemPeripheral modem;

        ModemInterface(String name, ModemPeripheral modem) {
            this.name = name;
            this.modem = modem;
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
            try {
                modem.transmit(InternetTransport.CHANNEL, InternetTransport.CHANNEL, packet.encode());
            } catch (LuaException e) {
                // transmit only fails if the channel or payload is invalid, and neither can be here.
            }
        }

        void attach() {
            modem.setVirtualNetListener(data -> {
                NetPacket packet;
                try {
                    packet = NetPacket.decode(data);
                } catch (IllegalArgumentException e) {
                    return;
                }
                stack.receive(this, packet);
            });
        }

        void detach() {
            modem.setVirtualNetListener(null);
        }
    }
}
