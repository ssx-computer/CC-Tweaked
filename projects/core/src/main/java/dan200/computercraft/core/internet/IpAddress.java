// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

/**
 * An IPv4-style virtual address, such as {@code 10.0.0.5}.
 * <p>
 * The address is stored as a raw 32-bit value: the most-significant byte is the first octet, so
 * {@code of(10, 0, 0, 5).value() == 0x0A000005}.
 * <p>
 * This type is a pure value type - every 32-bit value is a valid address, including {@link #ANY} and
 * {@link #BROADCAST}.
 *
 * @param value The raw 32-bit address.
 */
public record IpAddress(int value) {
    /** The {@code 0.0.0.0} address. This is used as the source address of DHCP requests, and as a "match anything" wildcard. */
    public static final IpAddress ANY = new IpAddress(0);

    /** The {@code 255.255.255.255} address, used for broadcasts. */
    public static final IpAddress BROADCAST = new IpAddress(0xFFFFFFFF);

    /**
     * Create an address from its four octets.
     *
     * @param a The first octet, in the range [0, 255].
     * @param b The second octet, in the range [0, 255].
     * @param c The third octet, in the range [0, 255].
     * @param d The fourth octet, in the range [0, 255].
     * @return The new address.
     * @throws IllegalArgumentException If any octet is out of range.
     */
    public static IpAddress of(int a, int b, int c, int d) {
        checkOctet(a);
        checkOctet(b);
        checkOctet(c);
        checkOctet(d);
        return new IpAddress((a << 24) | (b << 16) | (c << 8) | d);
    }

    /**
     * Parse a dotted-decimal address, such as {@code 10.0.0.5}.
     *
     * @param address The address to parse.
     * @return The parsed address.
     * @throws IllegalArgumentException If the address is not a valid dotted-decimal IPv4 address.
     */
    public static IpAddress parse(String address) {
        if (address == null) throw new IllegalArgumentException("Address must not be null.");

        var parts = address.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Address '" + address + "' must have four octets.");

        var octets = new int[4];
        for (var i = 0; i < 4; i++) {
            octets[i] = parseOctet(parts[i], address);
        }
        return of(octets[0], octets[1], octets[2], octets[3]);
    }

    /**
     * Whether this address is the {@link #BROADCAST} address.
     *
     * @return Whether this is the broadcast address.
     */
    public boolean isBroadcast() {
        return value == BROADCAST.value;
    }

    /**
     * Whether this address is the {@link #ANY} (all zeroes) address.
     *
     * @return Whether this is the wildcard address.
     */
    public boolean isAny() {
        return value == 0;
    }

    /**
     * Whether this address falls within the network described by an address and prefix length.
     *
     * @param network The network address, e.g. {@code 10.0.0.0}.
     * @param prefix  The prefix length, in the range [0, 32]. For example, a {@code /24} network has a prefix of 24.
     * @return Whether this address is part of {@code network}/{@code prefix}.
     * @throws IllegalArgumentException If the prefix is out of range.
     */
    public boolean inSubnet(IpAddress network, int prefix) {
        var mask = prefixMask(prefix);
        return (value & mask) == (network.value & mask);
    }

    @Override
    public String toString() {
        return ((value >>> 24) & 0xFF)
            + "." + ((value >>> 16) & 0xFF)
            + "." + ((value >>> 8) & 0xFF)
            + "." + (value & 0xFF);
    }

    /**
     * Get the netmask for a given prefix length.
     *
     * @param prefix The prefix length, in the range [0, 32].
     * @return The netmask with {@code prefix} leading bits set.
     * @throws IllegalArgumentException If the prefix is out of range.
     */
    static int prefixMask(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Prefix length must be in the range [0, 32], got " + prefix + ".");
        }
        return prefix == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix));
    }

    /**
     * Count the number of leading one-bits in a netmask, i.e. the inverse of {@link #prefixMask(int)}.
     *
     * @param netmask The netmask, e.g. {@code 255.255.255.0}.
     * @return The prefix length, in the range [0, 32].
     */
    static int prefixLength(IpAddress netmask) {
        return Integer.bitCount(netmask.value == 0 ? 0 : netmask.value);
    }

    private static void checkOctet(int octet) {
        if (octet < 0 || octet > 255) throw new IllegalArgumentException("Octet must be in the range [0, 255], got " + octet + ".");
    }

    private static int parseOctet(String part, String address) {
        if (part.isEmpty() || part.length() > 3) {
            throw new IllegalArgumentException("Address '" + address + "' has an invalid octet '" + part + "'.");
        }
        for (var i = 0; i < part.length(); i++) {
            var c = part.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Address '" + address + "' has an invalid octet '" + part + "'.");
            }
        }
        var value = Integer.parseInt(part);
        if (value > 255) throw new IllegalArgumentException("Address '" + address + "' has an octet out of range: '" + part + "'.");
        return value;
    }
}
