// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A single DHCP message, exchanged between a client and the {@link DhcpServer} on ports 68 and 67 respectively.
 * <p>
 * This is a deliberately simple, line-based text format rather than the real DHCP binary layout:
 * <pre>
 * Operation
 * key=value
 * key=value
 * ...
 * </pre>
 * The first line is one of {@code DISCOVER}, {@code OFFER}, {@code REQUEST}, {@code ACK} or {@code NAK}. The remaining
 * lines are optional {@code key=value} pairs, using the following keys:
 * <ul>
 *     <li>{@code mac} - the client's MAC address.</li>
 *     <li>{@code ip} - the address being offered, requested or assigned.</li>
 *     <li>{@code mask} - the subnet mask.</li>
 *     <li>{@code gw} - the gateway (optional).</li>
 *     <li>{@code server} - the DHCP server's own address (optional).</li>
 *     <li>{@code host} - the client's hostname (optional).</li>
 *     <li>{@code reason} - the reason for a {@code NAK} (optional).</li>
 * </ul>
 * Unknown keys are ignored, so the format can be extended without breaking older implementations.
 *
 * @param operation The operation, e.g. {@link #DISCOVER}.
 * @param mac       The client's MAC.
 * @param address   The address being offered, requested or assigned.
 * @param netmask   The subnet mask.
 * @param gateway   The gateway.
 * @param server    The DHCP server's address.
 * @param hostname  The client's hostname.
 * @param reason    The reason for a {@link #NAK}.
 */
record DhcpMessage(
    String operation, @Nullable Mac mac, @Nullable IpAddress address, @Nullable IpAddress netmask,
    @Nullable IpAddress gateway, @Nullable IpAddress server, @Nullable String hostname, @Nullable String reason
) {
    static final String DISCOVER = "DISCOVER";
    static final String OFFER = "OFFER";
    static final String REQUEST = "REQUEST";
    static final String ACK = "ACK";
    static final String NAK = "NAK";

    private static final List<String> OPERATIONS = List.of(DISCOVER, OFFER, REQUEST, ACK, NAK);

    /**
     * Encode this message into a byte array.
     *
     * @return The encoded message.
     */
    byte[] encode() {
        var mac = this.mac;
        var address = this.address;
        var netmask = this.netmask;
        var gateway = this.gateway;
        var server = this.server;
        var hostname = this.hostname;
        var reason = this.reason;

        var builder = new StringBuilder(operation).append('\n');
        if (mac != null) builder.append("mac=").append(mac.value()).append('\n');
        if (address != null) builder.append("ip=").append(address).append('\n');
        if (netmask != null) builder.append("mask=").append(netmask).append('\n');
        if (gateway != null) builder.append("gw=").append(gateway).append('\n');
        if (server != null) builder.append("server=").append(server).append('\n');
        if (hostname != null) builder.append("host=").append(hostname).append('\n');
        if (reason != null) builder.append("reason=").append(reason).append('\n');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decode a message from a byte array.
     *
     * @param data The encoded message.
     * @return The decoded message.
     * @throws IllegalArgumentException If the data is not a valid DHCP message.
     */
    static DhcpMessage decode(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Message must not be null.");

        var text = new String(data, StandardCharsets.UTF_8);
        var lines = text.split("\n", -1);
        if (lines.length == 0 || lines[0].isEmpty()) throw new IllegalArgumentException("Message is missing an operation.");

        var operation = lines[0];
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Unknown DHCP operation '" + operation + "'.");

        Mac mac = null;
        IpAddress address = null, netmask = null, gateway = null, server = null;
        String hostname = null, reason = null;

        for (var i = 1; i < lines.length; i++) {
            var line = lines[i];
            if (line.isEmpty()) continue;

            var separator = line.indexOf('=');
            if (separator < 0) throw new IllegalArgumentException("Malformed DHCP field '" + line + "'.");
            var key = line.substring(0, separator);
            var value = line.substring(separator + 1);

            switch (key) {
                case "mac" -> mac = Mac.parse(value);
                case "ip" -> address = IpAddress.parse(value);
                case "mask" -> netmask = IpAddress.parse(value);
                case "gw" -> gateway = IpAddress.parse(value);
                case "server" -> server = IpAddress.parse(value);
                case "host" -> hostname = value;
                case "reason" -> reason = value;
                // Unknown keys are ignored, so the format can be extended later.
                default -> {
                }
            }
        }

        return new DhcpMessage(operation, mac, address, netmask, gateway, server, hostname, reason);
    }
}
