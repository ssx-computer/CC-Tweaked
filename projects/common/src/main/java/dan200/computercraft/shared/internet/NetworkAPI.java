// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.internet;

import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.core.internet.FirewallRule;
import dan200.computercraft.core.internet.IpAddress;
import dan200.computercraft.core.internet.Mac;
import dan200.computercraft.core.internet.NetInterface;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code net} API, as seen by a program running on a {@link NetworkCoreBlock}.
 * <p>
 * This is the network's control plane: it exposes the leases DHCP has handed out, the firewall and routing rules, and
 * lets a program revoke a device's access by denying its MAC address.
 *
 * @cc.module net
 */
public class NetworkAPI implements ILuaAPI {
    private final NetworkCoreAccess core;

    /**
     * The id to give the next firewall rule. Rule ids are only meaningful to the program which created them.
     */
    private int nextRuleId = 1;

    public NetworkAPI(NetworkCoreAccess core) {
        this.core = core;
    }

    @Override
    public String[] getNames() {
        return new String[]{ "net" };
    }

    /**
     * Get the address this core uses, and hands out as the default gateway.
     *
     * @return The core's address.
     * @cc.usage Print the address of the network's gateway.
     * <pre>{@code
     * print(net.getAddress())
     * }</pre>
     */
    @LuaFunction
    public final String getAddress() {
        var address = core.getStack().address();
        return address == null ? NetworkCoreAccess.ADDRESS.toString() : address.toString();
    }

    /**
     * Get the netmask of the network this core manages.
     *
     * @return The network's netmask.
     */
    @LuaFunction
    public final String getNetmask() {
        var netmask = core.getStack().netmask();
        return netmask == null ? NetworkCoreAccess.NETMASK.toString() : netmask.toString();
    }

    /**
     * Get this core's MAC address.
     *
     * @return The core's MAC address.
     */
    @LuaFunction
    public final String getMac() {
        return core.getStack().mac().toString();
    }

    /**
     * List this core's network ports.
     *
     * @return A list of ports, each with {@code name} and {@code mac} fields.
     */
    @LuaFunction
    public final List<Map<String, Object>> getInterfaces() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (NetInterface iface : core.getStack().interfaces()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", iface.name());
            entry.put("mac", iface.mac().toString());
            result.add(entry);
        }
        return result;
    }

    /**
     * List the addresses which have been handed out, and to which device.
     *
     * @return A list of leases, each with {@code mac}, {@code address}, {@code expiresIn} (in seconds), {@code hostname}
     * and {@code reserved} fields.
     * @cc.usage Print every device which currently holds an address.
     * <pre>{@code
     * for _, lease in ipairs(net.leases()) do
     *   print(lease.mac, lease.address)
     * end
     * }</pre>
     */
    @LuaFunction
    public final List<Map<String, Object>> leases() {
        var now = System.currentTimeMillis();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var lease : core.getDhcp().leases()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("mac", lease.mac().toString());
            entry.put("address", lease.address().toString());
            entry.put("expiresIn", Math.max(0, (lease.expiresAtMillis() - now) / 1000));
            entry.put("hostname", lease.hostname() == null ? "" : lease.hostname());
            entry.put("reserved", lease.reserved());
            result.add(entry);
        }
        return result;
    }

    /**
     * Always give a device the same address.
     *
     * @param mac     The MAC address of the device.
     * @param address The address to give it.
     * @throws LuaException If the address is not on this network.
     */
    @LuaFunction
    public final void reserve(String mac, String address) throws LuaException {
        var parsedAddress = checkAddress(address);
        if (!parsedAddress.inSubnet(NetworkCoreAccess.ADDRESS, 24)) {
            throw new LuaException("Address " + address + " is not on this network.");
        }
        core.getDhcp().reserve(checkMac(mac), parsedAddress);
    }

    /**
     * Set the range of addresses which are handed out.
     *
     * @param from The first address of the range.
     * @param to   The last address of the range.
     * @throws LuaException If either address is not on this network.
     */
    @LuaFunction
    public final void setPool(String from, String to) throws LuaException {
        var start = checkAddress(from);
        var end = checkAddress(to);
        if (!start.inSubnet(NetworkCoreAccess.ADDRESS, 24) || !end.inSubnet(NetworkCoreAccess.ADDRESS, 24)) {
            throw new LuaException("Both addresses must be on this network.");
        }
        core.getDhcp().setPool(start, end);
    }

    /**
     * Allow a device onto the network. This overrides a previous {@link #deny(String)}.
     *
     * @param mac The MAC address of the device.
     * @throws LuaException If the MAC address is malformed.
     */
    @LuaFunction
    public final void allow(String mac) throws LuaException {
        core.getDhcp().allow(checkMac(mac));
    }

    /**
     * Refuse to give a device an address, which keeps it off the network.
     *
     * @param mac The MAC address of the device.
     * @throws LuaException If the MAC address is malformed.
     * @cc.usage Kick a device off the network.
     * <pre>{@code
     * net.deny("cc:12:34:56:78")
     * }</pre>
     */
    @LuaFunction
    public final void deny(String mac) throws LuaException {
        core.getDhcp().deny(checkMac(mac));
    }

    /**
     * Set whether devices are allowed onto the network by default.
     *
     * @param allowed Whether unknown devices may join.
     */
    @LuaFunction
    public final void setDefaultAllow(boolean allowed) {
        core.getDhcp().setDefaultPolicy(allowed);
    }

    /**
     * Add a firewall rule. Rules are checked in the order they were added, and the first matching one wins.
     *
     * @param rule A table describing the rule. {@code action} must be {@code "allow"} or {@code "deny"}, and
     *             {@code direction} one of {@code "in"}, {@code "out"} or {@code "forward"} (the default).
     *             {@code from}, {@code to} (addresses, optionally with a {@code /prefix}), {@code fromMac},
     *             {@code dstPort} (a port, or a {@code {from, to}} table) are all optional.
     * @return The id of the rule, which can be passed to {@link #removeRule(int)}.
     * @throws LuaException If the rule is malformed.
     * @cc.usage Stop devices on the network from reaching a machine on port 80.
     * <pre>{@code
     * net.addRule({ to = "10.0.0.5", dstPort = 80, action = "deny" })
     * }</pre>
     */
    @LuaFunction
    public final int addRule(Map<?, ?> rule) throws LuaException {
        var action = parseAction(rule.get("action"));
        var direction = parseDirection(rule.get("direction"));

        var from = parseNetwork(rule.get("from"), "from");
        var to = parseNetwork(rule.get("to"), "to");

        Mac fromMac = null;
        if (rule.get("fromMac") != null) {
            if (!(rule.get("fromMac") instanceof String mac)) throw new LuaException("Expected a string for 'fromMac'.");
            fromMac = checkMac(mac);
        }

        Integer portFrom = null;
        Integer portTo = null;
        var port = rule.get("dstPort");
        if (port instanceof Number number) {
            portFrom = checkPort(number);
        } else if (port instanceof Map<?, ?> range) {
            if (!(range.get(1) instanceof Number first) || !(range.get(2) instanceof Number last)) {
                throw new LuaException("Expected a {from, to} table for 'dstPort'.");
            }
            portFrom = checkPort(first);
            portTo = checkPort(last);
            if (portTo < portFrom) throw new LuaException("'dstPort' range is empty.");
        } else if (port != null) {
            throw new LuaException("Expected a number or table for 'dstPort'.");
        }

        var id = nextRuleId++;
        FirewallRule built;
        try {
            built = new FirewallRule(
                id, direction,
                from == null ? null : from.address(), from == null ? 0 : from.prefix(),
                to == null ? null : to.address(), to == null ? 0 : to.prefix(),
                fromMac, portFrom, portTo, action
            );
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }

        try {
            core.getRouter().addRule(built);
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
        return id;
    }

    /**
     * Remove a firewall rule.
     *
     * @param id The id of the rule, as returned by {@link #addRule(Map)}.
     * @return Whether the rule existed.
     */
    @LuaFunction
    public final boolean removeRule(int id) {
        return core.getRouter().removeRule(id);
    }

    /**
     * List the firewall rules currently in place.
     *
     * @return A list of rules, each with {@code id}, {@code direction} and {@code action} fields.
     */
    @LuaFunction
    public final List<Map<String, Object>> rules() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var rule : core.getRouter().rules()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", rule.id());
            entry.put("direction", rule.direction().name().toLowerCase(Locale.ROOT));
            entry.put("action", rule.action().name().toLowerCase(Locale.ROOT));
            var from = rule.fromAddress();
            entry.put("from", from == null ? "" : from + "/" + rule.fromPrefix());
            var to = rule.toAddress();
            entry.put("to", to == null ? "" : to + "/" + rule.toPrefix());
            var mac = rule.fromMac();
            entry.put("fromMac", mac == null ? "" : mac.toString());
            var portFrom = rule.dstPortFrom();
            entry.put("dstPort", portFrom == null ? "" : portFrom.toString());
            result.add(entry);
        }
        return result;
    }

    private record Network(IpAddress address, int prefix) {
    }

    private static @Nullable Network parseNetwork(@Nullable Object value, String field) throws LuaException {
        if (value == null) return null;
        if (!(value instanceof String text)) throw new LuaException("Expected a string for '" + field + "'.");

        var slash = text.indexOf('/');
        var addressPart = slash < 0 ? text : text.substring(0, slash);
        var address = checkAddress(addressPart);
        if (slash < 0) return new Network(address, 32);

        try {
            var prefix = Integer.parseInt(text.substring(slash + 1));
            if (prefix < 0 || prefix > 32) throw new LuaException("Invalid prefix in '" + text + "'.");
            return new Network(address, prefix);
        } catch (NumberFormatException e) {
            throw new LuaException("Invalid prefix in '" + text + "'.");
        }
    }

    private static int checkPort(Number value) throws LuaException {
        var port = value.intValue();
        if (port < 0 || port > 65535) throw new LuaException("Port " + port + " is out of range.");
        return port;
    }

    private static FirewallRule.Action parseAction(@Nullable Object value) throws LuaException {
        if (value instanceof String name) {
            if (name.equalsIgnoreCase("allow")) return FirewallRule.Action.ALLOW;
            if (name.equalsIgnoreCase("deny")) return FirewallRule.Action.DENY;
        }
        throw new LuaException("Expected 'allow' or 'deny' for 'action'.");
    }

    private static FirewallRule.Direction parseDirection(@Nullable Object value) throws LuaException {
        if (value == null) return FirewallRule.Direction.FORWARD;
        if (value instanceof String name) {
            for (var direction : FirewallRule.Direction.values()) {
                if (direction.name().equalsIgnoreCase(name)) return direction;
            }
        }
        throw new LuaException("Expected 'in', 'out' or 'forward' for 'direction'.");
    }

    private static IpAddress checkAddress(String address) throws LuaException {
        try {
            return IpAddress.parse(address);
        } catch (IllegalArgumentException e) {
            throw new LuaException("Invalid address '" + address + "'.");
        }
    }

    private static Mac checkMac(String mac) throws LuaException {
        try {
            return Mac.parse(mac);
        } catch (IllegalArgumentException e) {
            throw new LuaException("Invalid MAC address '" + mac + "'.");
        }
    }
}
