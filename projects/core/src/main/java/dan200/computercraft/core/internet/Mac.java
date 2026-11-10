// SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.internet;

import java.util.Locale;

/**
 * A device's unique identifier, the moral equivalent of a network card's hardware address. For example
 * {@code cc:12:34:56}.
 * <p>
 * A MAC is a series of colon-separated, two-digit hexadecimal groups. We deliberately allow between 1 and 8 groups
 * (rather than the usual 6), so that the mod layer is free to encode e.g. a computer ID in the address.
 * <p>
 * Values are normalised to lower case on construction, so MACs compare case-insensitively.
 *
 * @param value The textual representation of this MAC.
 */
public record Mac(String value) {
    private static final int MAX_GROUPS = 8;

    public Mac {
        if (value == null) throw new IllegalArgumentException("MAC must not be null.");

        value = value.toLowerCase(Locale.ROOT);
        var parts = value.split(":", -1);
        if (parts.length < 1 || parts.length > MAX_GROUPS) {
            throw new IllegalArgumentException("MAC '" + value + "' must have between 1 and " + MAX_GROUPS + " groups.");
        }

        for (var part : parts) {
            if (part.length() != 2) {
                throw new IllegalArgumentException("MAC '" + value + "' has an invalid group '" + part + "'.");
            }
            for (var i = 0; i < 2; i++) {
                var c = part.charAt(i);
                if (hexDigit(c) < 0) throw new IllegalArgumentException("MAC '" + value + "' has an invalid group '" + part + "'.");
            }
        }
    }

    /**
     * Parse a MAC from its textual representation.
     *
     * @param value The MAC to parse.
     * @return The parsed MAC.
     * @throws IllegalArgumentException If the MAC is not a valid colon-separated hex string.
     */
    public static Mac parse(String value) {
        return new Mac(value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }
}
