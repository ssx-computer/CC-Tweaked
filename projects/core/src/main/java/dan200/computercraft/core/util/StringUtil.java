// SPDX-FileCopyrightText: 2017 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.util;

import dan200.computercraft.core.input.ComputerInput;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class StringUtil {
    public static final int MAX_PASTE_LENGTH = 512;

    private StringUtil() {
    }

    /**
     * Convert a Unicode character to a terminal one.
     *
     * @param chr The Unicode character.
     * @return The terminal character. This is either in the range [0, 255] (if a valid character) or {@code -1} if
     * it cannot be mapped to CC's charset.
     */
    public static int unicodeToTerminal(int chr) {
        // ASCII and latin1 map to themselves
        if (chr == 0 || chr == '\t' || chr == '\n' || chr == '\r' || (chr >= ' ' && chr <= '~') || (chr >= 160 && chr <= 255)) {
            return chr;
        }

        // Teletext block mosaics are *fairly* contiguous.
        if (chr >= 0x1FB00 && chr <= 0x1FB13) return chr + (129 - 0x1fb00);
        if (chr >= 0x1FB14 && chr <= 0x1FB1D) return chr + (150 - 0x1fb14);

        // Everything else is just a manual lookup. For now, we just use a big switch statement, which we spin into a
        // separate function to hopefully avoid inlining it here.
        return unicodeToCraftOsFallback(chr);
    }

    private static int unicodeToCraftOsFallback(int c) {
        return switch (c) {
            case 0x263A -> 1;
            case 0x263B -> 2;
            case 0x2665 -> 3;
            case 0x2666 -> 4;
            case 0x2663 -> 5;
            case 0x2660 -> 6;
            case 0x2022 -> 7;
            case 0x25D8 -> 8;
            case 0x2642 -> 11;
            case 0x2640 -> 12;
            case 0x266A -> 14;
            case 0x266B -> 15;
            case 0x25BA -> 16;
            case 0x25C4 -> 17;
            case 0x2195 -> 18;
            case 0x203C -> 19;
            case 0x25AC -> 22;
            case 0x21A8 -> 23;
            case 0x2191 -> 24;
            case 0x2193 -> 25;
            case 0x2192 -> 26;
            case 0x2190 -> 27;
            case 0x221F -> 28;
            case 0x2194 -> 29;
            case 0x25B2 -> 30;
            case 0x25BC -> 31;
            case 0x1FB99 -> 127;
            case 0x258C -> 149;
            default -> -1;
        };
    }

    /**
     * Check if a character is capable of being input and passed to a {@linkplain ComputerInput#charTyped(int) "char"
     * event}.
     *
     * @param chr The character to check.
     * @return Whether this character can be typed.
     */
    public static boolean isTypableChar(byte chr) {
        return isTypableChar(chr & 0xFF);
    }

    /**
     * Check if a character is capable of being input and passed to a {@linkplain ComputerInput#charTyped(int) "char"
     * * event}.
     *
     * @param chr The character to check.
     * @return Whether this character can be typed.
     */
    public static boolean isTypableChar(int chr) {
        return chr >= 0 && chr <= 255 && chr != 0 && chr != '\r' && chr != '\n';
    }

    /**
     * The ranges of Unicode code points which are rendered as "wide" (East Asian Wide or Fullwidth), and so should
     * occupy two terminal cells.
     * <p>
     * This is a flattened array of {@code [start, end]} pairs, sorted by {@code start}, covering the East Asian
     * Wide/Fullwidth ranges within the BMP. Note that several wide ranges (namely CJK Unified Ideographs extensions
     * B–I and most emoji) live outside the BMP, and so cannot currently be stored in a terminal cell - see
     * {@link #isWide(int)}.
     *
     * @see <a href="https://www.unicode.org/reports/tr11/">Unicode Standard Annex #11</a>
     */
    private static final int[] WIDE_RANGES = {
        0x1100, 0x115F, // Hangul Jamo
        // These are East Asian Ambiguous rather than Wide, but are conventionally rendered at full width in CJK text
        // (and so are stored as twice as wide in the font atlas). Keep in sync with CjkTier2.
        0x2013, 0x2014, // En dash, em dash
        0x2018, 0x2019, // Single quotation marks
        0x201C, 0x201D, // Double quotation marks
        0x2026, 0x2026, // Horizontal ellipsis
        0x231A, 0x231B, // Watch, hourglass
        0x2329, 0x232A, // Angle brackets
        0x23E9, 0x23EC, // Media control symbols
        0x23F0, 0x23F0, // Alarm clock
        0x23F3, 0x23F3, // Hourglass
        0x25FD, 0x25FE, // Small squares
        0x2614, 0x2615, // Umbrella, hot beverage
        0x2648, 0x2653, // Zodiac
        0x267F, 0x267F, // Wheelchair
        0x2693, 0x2693, // Anchor
        0x26A1, 0x26A1, // High voltage
        0x26AA, 0x26AB, // Circles
        0x26BD, 0x26BE, // Sports
        0x26C4, 0x26C5, // Snowman, sun
        0x26CE, 0x26CE, // Ophiuchus
        0x26D4, 0x26D4, // No entry
        0x26EA, 0x26EA, // Church
        0x26F2, 0x26F3, // Fountain, golf
        0x26F5, 0x26F5, // Sailboat
        0x26FA, 0x26FA, // Tent
        0x26FD, 0x26FD, // Fuel pump
        0x2705, 0x2705, // Check mark
        0x270A, 0x270B, // Fist, hand
        0x2728, 0x2728, // Sparkles
        0x274C, 0x274C, // Cross mark
        0x274E, 0x274E, // Cross mark
        0x2753, 0x2755, // Question, exclamation
        0x2757, 0x2757, // Exclamation
        0x2795, 0x2797, // Plus, minus, divide
        0x27B0, 0x27B0, // Curly loop
        0x27BF, 0x27BF, // Double curly loop
        0x2B1B, 0x2B1C, // Squares
        0x2B50, 0x2B50, // Star
        0x2B55, 0x2B55, // Circle
        0x2E80, 0x303F, // CJK Radicals, Kangxi Radicals, CJK Symbols and Punctuation
        0x3041, 0x33FF, // Kana, Bopomofo, Hangul Compatibility Jamo, Kanbun, Enclosed CJK, CJK Compatibility
        0x3400, 0x4DBF, // CJK Unified Ideographs Extension A
        0x4E00, 0x9FFF, // CJK Unified Ideographs
        0xA000, 0xA4CF, // Yi Syllables and Radicals
        0xA960, 0xA97C, // Hangul Jamo Extended-A
        0xAC00, 0xD7A3, // Hangul Syllables
        0xD7B0, 0xD7FF, // Hangul Jamo Extended-B
        0xF900, 0xFAFF, // CJK Compatibility Ideographs
        0xFE10, 0xFE19, // Vertical Forms
        0xFE30, 0xFE6F, // CJK Compatibility Forms, Small Form Variants
        0xFF00, 0xFF60, // Fullwidth Forms
        0xFFE0, 0xFFE6, // Fullwidth Signs
    };

    /**
     * Check if a Unicode code point is "wide" - that is, whether it should occupy two terminal cells.
     * <p>
     * This covers the East Asian Wide and Fullwidth ranges (CJK ideographs, kana, hangul, fullwidth forms and a
     * number of symbols and emoji). Code points outside the BMP are not handled, as they cannot be stored in a
     * {@link dan200.computercraft.core.terminal.TextBuffer} yet.
     *
     * @param codepoint The code point to check.
     * @return Whether this code point should be rendered across two cells.
     */
    public static boolean isWide(int codepoint) {
        // Binary search over the flattened [start, end] ranges.
        var low = 0;
        var high = WIDE_RANGES.length / 2 - 1;
        while (low <= high) {
            var mid = (low + high) >>> 1;
            var start = WIDE_RANGES[mid * 2];
            var end = WIDE_RANGES[mid * 2 + 1];
            if (codepoint < start) {
                high = mid - 1;
            } else if (codepoint > end) {
                low = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedInLabel(char c) {
        // Limit to ASCII and latin1, excluding '§' (Minecraft's formatting character).
        return (c >= ' ' && c <= '~') || (c >= 161 && c <= 255 && c != 167);
    }

    public static String normaliseLabel(String text) {
        var length = Math.min(32, text.length());
        var builder = new StringBuilder(length);
        for (var i = 0; i < length; i++) {
            var c = text.charAt(i);
            builder.append(isAllowedInLabel(c) ? c : '?');
        }
        return builder.toString();
    }

    /**
     * Convert a Java string to a Lua one (using the terminal charset), suitable for pasting into a computer.
     * <p>
     * This removes special characters and strips to the first line of text.
     *
     * @param clipboard The text from the clipboard.
     * @return The encoded clipboard text.
     */
    public static ByteBuffer getClipboardString(String clipboard) {
        var output = new byte[MAX_PASTE_LENGTH];
        var idx = 0;

        var iterator = clipboard.codePoints().iterator();
        while (iterator.hasNext()) {
            var codepoint = iterator.next();

            var chr = unicodeToTerminal(codepoint);
            if (chr >= 0) {
                if (!isTypableChar(chr)) break; // Stop at untypable ones.
                if (idx >= output.length) break;
                output[idx++] = (byte) chr;
            } else if (codepoint > 0 && codepoint <= 0xFFFF && isWide(codepoint)) {
                // Wide characters (such as CJK ideographs) are sent as UTF-8. The terminal decodes these back into a
                // single, two cell wide character. See EventComputerInput, which does the same when typing.
                var bytes = Character.toString(codepoint).getBytes(StandardCharsets.UTF_8);
                if (idx + bytes.length > output.length) break;
                System.arraycopy(bytes, 0, output, idx, bytes.length);
                idx += bytes.length;
            }
            // Anything else has no equivalent in CC's charset, and so is stripped.
        }

        return ByteBuffer.wrap(output, 0, idx).asReadOnlyBuffer();
    }
}
