// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A virtual IP packet, the unit of information exchanged on the network.
 * <p>
 * A packet is immutable. It carries the source and destination addresses, an optional source and destination MAC, a
 * protocol number, source and destination ports, a TTL, an identifier and an arbitrary payload.
 * <p>
 * Packets can be serialised to and from a byte array with {@link #encode()} and {@link #decode(byte[])}. This is the
 * format a mod's transport layer should use when moving packets between blocks.
 * <h2>Wire format</h2>
 * All multi-byte integers are big-endian. The first byte is always the format {@linkplain #VERSION version}, so that
 * the format can be evolved safely.
 * <pre>
 * Offset  Size  Field
 * 0       1     version (always {@value #VERSION})
 * 1       1     flags (bit 0: source MAC present, bit 1: destination MAC present)
 * 2       1     protocol
 * 3       1     TTL
 * 4       2     packet id
 * 6       2     source port
 * 8       2     destination port
 * 10      4     source address
 * 14      4     destination address
 * 18      ..    optional source MAC: 1 length byte, then that many ASCII bytes
 * ..      ..    optional destination MAC: 1 length byte, then that many ASCII bytes
 * ..      ..    payload (remainder of the array)
 * </pre>
 * {@link #decode(byte[])} rejects any input which does not obey these rules (bad version, truncated header, unknown
 * flag bits, an over-long MAC, an invalid MAC) by throwing {@link IllegalArgumentException}.
 */
public final class NetPacket {
    /** The current wire format version. */
    public static final int VERSION = 1;

    /** The ICMP protocol number. */
    public static final int PROTOCOL_ICMP = 1;

    /** The TCP protocol number. */
    public static final int PROTOCOL_TCP = 6;

    /** The UDP protocol number. */
    public static final int PROTOCOL_UDP = 17;

    /** The default TTL used for newly created packets. */
    public static final int DEFAULT_TTL = 64;

    private static final int HEADER_LENGTH = 18;
    private static final int FLAG_SRC_MAC = 0x01;
    private static final int FLAG_DST_MAC = 0x02;
    private static final int KNOWN_FLAGS = FLAG_SRC_MAC | FLAG_DST_MAC;

    private final IpAddress src;
    private final IpAddress dst;
    private final @Nullable Mac srcMac;
    private final @Nullable Mac dstMac;
    private final int protocol;
    private final int srcPort;
    private final int dstPort;
    private final int ttl;
    private final int id;
    private final byte[] payload;

    /**
     * Create a new packet.
     *
     * @param src      The source address.
     * @param dst      The destination address.
     * @param srcMac   The source MAC, or {@code null} if unknown.
     * @param dstMac   The destination MAC, or {@code null} if unknown.
     * @param protocol The protocol number, in the range [0, 255].
     * @param srcPort  The source port, in the range [0, 65535].
     * @param dstPort  The destination port, in the range [0, 65535].
     * @param ttl      The time-to-live, in the range [0, 255].
     * @param id       The packet identifier, in the range [0, 65535].
     * @param payload  The packet payload. This is copied, and may be empty.
     * @throws IllegalArgumentException If any field is out of range.
     */
    public NetPacket(
        IpAddress src, IpAddress dst, @Nullable Mac srcMac, @Nullable Mac dstMac,
        int protocol, int srcPort, int dstPort, int ttl, int id, byte[] payload
    ) {
        this.src = Objects.requireNonNull(src, "Source address must not be null.");
        this.dst = Objects.requireNonNull(dst, "Destination address must not be null.");
        this.srcMac = srcMac;
        this.dstMac = dstMac;
        this.protocol = checkRange(protocol, 0xFF, "protocol");
        this.srcPort = checkRange(srcPort, 0xFFFF, "srcPort");
        this.dstPort = checkRange(dstPort, 0xFFFF, "dstPort");
        this.ttl = checkRange(ttl, 0xFF, "ttl");
        this.id = checkRange(id, 0xFFFF, "id");
        this.payload = Objects.requireNonNull(payload, "Payload must not be null.").clone();
    }

    /**
     * The source address.
     *
     * @return The source address.
     */
    public IpAddress src() {
        return src;
    }

    /**
     * The destination address.
     *
     * @return The destination address.
     */
    public IpAddress dst() {
        return dst;
    }

    /**
     * The source MAC, if known.
     *
     * @return The source MAC, or {@code null}.
     */
    public @Nullable Mac srcMac() {
        return srcMac;
    }

    /**
     * The destination MAC, if known.
     *
     * @return The destination MAC, or {@code null}.
     */
    public @Nullable Mac dstMac() {
        return dstMac;
    }

    /**
     * The protocol number, e.g. {@link #PROTOCOL_UDP}.
     *
     * @return The protocol number.
     */
    public int protocol() {
        return protocol;
    }

    /**
     * The source port.
     *
     * @return The source port.
     */
    public int srcPort() {
        return srcPort;
    }

    /**
     * The destination port.
     *
     * @return The destination port.
     */
    public int dstPort() {
        return dstPort;
    }

    /**
     * The time-to-live. A packet is discarded once this reaches zero.
     *
     * @return The TTL.
     */
    public int ttl() {
        return ttl;
    }

    /**
     * The packet identifier.
     *
     * @return The packet identifier.
     */
    public int id() {
        return id;
    }

    /**
     * The packet payload. This is a copy, so mutating it will not affect the packet.
     *
     * @return The payload.
     */
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Create a copy of this packet with a new TTL. This is used when forwarding a packet, so that it is eventually
     * discarded if it enters a routing loop.
     *
     * @param ttl The new TTL, in the range [0, 255].
     * @return A copy of this packet with the given TTL.
     * @throws IllegalArgumentException If the TTL is out of range.
     */
    public NetPacket withTtl(int ttl) {
        return new NetPacket(src, dst, srcMac, dstMac, protocol, srcPort, dstPort, ttl, id, payload);
    }

    /**
     * Encode this packet into a byte array, using the format described in the {@linkplain NetPacket class docs}.
     *
     * @return The encoded packet.
     */
    public byte[] encode() {
        var srcMac = this.srcMac;
        var dstMac = this.dstMac;
        var srcMacBytes = srcMac == null ? null : srcMac.value().getBytes(StandardCharsets.US_ASCII);
        var dstMacBytes = dstMac == null ? null : dstMac.value().getBytes(StandardCharsets.US_ASCII);

        var size = HEADER_LENGTH + payload.length;
        if (srcMacBytes != null) size += 1 + srcMacBytes.length;
        if (dstMacBytes != null) size += 1 + dstMacBytes.length;

        var flags = 0;
        if (srcMacBytes != null) flags |= FLAG_SRC_MAC;
        if (dstMacBytes != null) flags |= FLAG_DST_MAC;

        var buffer = ByteBuffer.allocate(size);
        buffer.put((byte) VERSION);
        buffer.put((byte) flags);
        buffer.put((byte) protocol);
        buffer.put((byte) ttl);
        buffer.putShort((short) id);
        buffer.putShort((short) srcPort);
        buffer.putShort((short) dstPort);
        buffer.putInt(src.value());
        buffer.putInt(dst.value());
        if (srcMacBytes != null) putMac(buffer, srcMacBytes);
        if (dstMacBytes != null) putMac(buffer, dstMacBytes);
        buffer.put(payload);
        return buffer.array();
    }

    /**
     * Decode a packet from a byte array.
     *
     * @param data The encoded packet.
     * @return The decoded packet.
     * @throws IllegalArgumentException If the data is not a valid, correctly-versioned packet.
     */
    public static NetPacket decode(byte[] data) {
        if (data == null) throw new IllegalArgumentException("Packet must not be null.");
        if (data.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("Packet is too short (" + data.length + " bytes, expected at least " + HEADER_LENGTH + ").");
        }

        var buffer = ByteBuffer.wrap(data);
        var version = buffer.get() & 0xFF;
        if (version != VERSION) throw new IllegalArgumentException("Unknown packet version " + version + ", expected " + VERSION + ".");

        var flags = buffer.get() & 0xFF;
        if ((flags & ~KNOWN_FLAGS) != 0) throw new IllegalArgumentException("Packet has unknown flags 0x" + Integer.toHexString(flags) + ".");

        var protocol = buffer.get() & 0xFF;
        var ttl = buffer.get() & 0xFF;
        var id = buffer.getShort() & 0xFFFF;
        var srcPort = buffer.getShort() & 0xFFFF;
        var dstPort = buffer.getShort() & 0xFFFF;
        var src = new IpAddress(buffer.getInt());
        var dst = new IpAddress(buffer.getInt());

        var srcMac = (flags & FLAG_SRC_MAC) != 0 ? readMac(buffer) : null;
        var dstMac = (flags & FLAG_DST_MAC) != 0 ? readMac(buffer) : null;

        var payload = new byte[buffer.remaining()];
        buffer.get(payload);

        return new NetPacket(src, dst, srcMac, dstMac, protocol, srcPort, dstPort, ttl, id, payload);
    }

    private static void putMac(ByteBuffer buffer, byte[] mac) {
        buffer.put((byte) mac.length);
        buffer.put(mac);
    }

    private static Mac readMac(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) throw new IllegalArgumentException("Packet ended before MAC length.");
        var length = buffer.get() & 0xFF;
        if (buffer.remaining() < length) throw new IllegalArgumentException("Packet ended before MAC data.");
        var bytes = new byte[length];
        buffer.get(bytes);
        return Mac.parse(new String(bytes, StandardCharsets.US_ASCII));
    }

    private static int checkRange(int value, int max, String name) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException("Field '" + name + "' must be in the range [0, " + max + "], got " + value + ".");
        }
        return value;
    }
}
