// Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
//
// SPDX-License-Identifier: LicenseRef-CCPL

package dan200.computercraft.core.terminal;

import dan200.computercraft.core.util.Colour;
import dan200.computercraft.core.util.StringUtil;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

public class Terminal {
    protected static final String BASE_16 = "0123456789abcdef";

    protected int width;
    protected int height;
    protected final boolean colour;

    protected int cursorX = 0;
    protected int cursorY = 0;
    protected boolean cursorBlink = false;
    protected int cursorColour = 0;
    protected int cursorBackgroundColour = 15;

    protected TextBuffer[] text;
    protected TextBuffer[] textColour;
    protected TextBuffer[] backgroundColour;

    protected final Palette palette;

    private final @Nullable Runnable onChanged;

    public Terminal(int width, int height, boolean colour) {
        this(width, height, colour, null);
    }

    public Terminal(int width, int height, boolean colour, @Nullable Runnable changedCallback) {
        this.width = width;
        this.height = height;
        this.colour = colour;
        palette = new Palette(colour);
        onChanged = changedCallback;

        text = new TextBuffer[height];
        textColour = new TextBuffer[height];
        backgroundColour = new TextBuffer[height];
        for (var i = 0; i < this.height; i++) {
            text[i] = new TextBuffer(' ', this.width);
            textColour[i] = new TextBuffer(BASE_16.charAt(cursorColour), this.width);
            backgroundColour[i] = new TextBuffer(BASE_16.charAt(cursorBackgroundColour), this.width);
        }
    }

    public synchronized void reset() {
        cursorColour = 0;
        cursorBackgroundColour = 15;
        cursorX = 0;
        cursorY = 0;
        cursorBlink = false;
        clear();
        setChanged();
        palette.resetColours();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isColour() {
        return colour;
    }

    public synchronized void resize(int width, int height) {
        if (width == this.width && height == this.height) {
            return;
        }

        var oldHeight = this.height;
        var oldWidth = this.width;
        var oldText = text;
        var oldTextColour = textColour;
        var oldBackgroundColour = backgroundColour;

        this.width = width;
        this.height = height;

        text = new TextBuffer[height];
        textColour = new TextBuffer[height];
        backgroundColour = new TextBuffer[height];
        for (var i = 0; i < this.height; i++) {
            if (i >= oldHeight) {
                text[i] = new TextBuffer(' ', this.width);
                textColour[i] = new TextBuffer(BASE_16.charAt(cursorColour), this.width);
                backgroundColour[i] = new TextBuffer(BASE_16.charAt(cursorBackgroundColour), this.width);
            } else if (this.width == oldWidth) {
                text[i] = oldText[i];
                textColour[i] = oldTextColour[i];
                backgroundColour[i] = oldBackgroundColour[i];
            } else {
                text[i] = new TextBuffer(' ', this.width);
                textColour[i] = new TextBuffer(BASE_16.charAt(cursorColour), this.width);
                backgroundColour[i] = new TextBuffer(BASE_16.charAt(cursorBackgroundColour), this.width);
                text[i].write(oldText[i]);
                textColour[i].write(oldTextColour[i]);
                backgroundColour[i].write(oldBackgroundColour[i]);
            }
        }
        setChanged();
    }

    public void setCursorPos(int x, int y) {
        if (cursorX != x || cursorY != y) {
            cursorX = x;
            cursorY = y;
            setChanged();
        }
    }

    public void setCursorBlink(boolean blink) {
        if (cursorBlink != blink) {
            cursorBlink = blink;
            setChanged();
        }
    }

    public void setTextColour(int colour) {
        if (cursorColour != colour) {
            cursorColour = colour;
            setChanged();
        }
    }

    public void setBackgroundColour(int colour) {
        if (cursorBackgroundColour != colour) {
            cursorBackgroundColour = colour;
            setChanged();
        }
    }

    public int getCursorX() {
        return cursorX;
    }

    public int getCursorY() {
        return cursorY;
    }

    public boolean getCursorBlink() {
        return cursorBlink;
    }

    public int getTextColour() {
        return cursorColour;
    }

    public int getBackgroundColour() {
        return cursorBackgroundColour;
    }

    public Palette getPalette() {
        return palette;
    }

    public synchronized void blit(ByteBuffer text, ByteBuffer textColour, ByteBuffer backgroundColour) {
        var x = cursorX;
        var y = cursorY;
        if (y >= 0 && y < height) {
            this.text[y].write(text, x);
            this.textColour[y].write(textColour, x);
            this.backgroundColour[y].write(backgroundColour, x);
            setChanged();
        }
    }

    public synchronized void write(String text) {
        var x = cursorX;
        var y = cursorY;

        // Fast path for ASCII text, which is by far the most common case. Bytes in the range [0, 127] are always
        // terminal characters, never take more than one cell and never wrap, so this behaves exactly as before.
        if (isAscii(text)) {
            if (y >= 0 && y < height) {
                this.text[y].write(text, x);
                textColour[y].fill(BASE_16.charAt(cursorColour), x, x + text.length());
                backgroundColour[y].fill(BASE_16.charAt(cursorBackgroundColour), x, x + text.length());
                setChanged();
            }
            cursorX = x + text.length();
            return;
        }

        writeUnicode(text, x, y);
    }

    /**
     * Write a string which may contain UTF-8 encoded characters and wide (East Asian) characters.
     * <p>
     * Cobalt represents Lua strings as a sequence of bytes, so each Java {@code char} in {@code text} is a byte. We
     * decode those bytes as UTF-8, falling back to treating malformed sequences as Latin-1 characters (preserving the
     * old behaviour). Wide characters occupy two cells: the character itself, followed by a zero-valued placeholder
     * which the renderer draws as the background colour.
     *
     * @param text   The text to write.
     * @param startX The initial cursor X position.
     * @param startY The initial cursor Y position.
     */
    private void writeUnicode(String text, int startX, int startY) {
        var x = startX;
        var y = startY;
        var changed = false;
        var textColourChar = BASE_16.charAt(cursorColour);
        var backgroundColourChar = BASE_16.charAt(cursorBackgroundColour);

        var i = 0;
        while (i < text.length()) {
            var c = text.charAt(i);
            int chr;
            if (c < 0x80) {
                chr = c;
                i++;
            } else if (c <= 0xFF) {
                // A byte. Attempt to decode a UTF-8 sequence, or keep it as a Latin-1 character if that fails.
                var decoded = decodeUtf8(text, i);
                if (decoded < 0) {
                    chr = c;
                    i++;
                } else {
                    chr = mapCharacter(decoded);
                    i += utf8Length(c);
                }
            } else {
                // A Java string which already contains code points rather than bytes - never decode it again.
                chr = mapCharacter(c);
                i++;
            }

            if (chr < 0) {
                // The character cannot be represented. Skip a cell so that the cursor still advances.
                x++;
            } else if (chr > 0xFF) {
                // A wide character (e.g. a CJK ideograph), which occupies two cells.
                if (x == width - 1 && width > 1 && y + 1 < height) {
                    // Only one cell remains on this line, so move to the next one rather than splitting the character.
                    x = 0;
                    y++;
                }
                if (x >= 0 && x + 1 < width && y >= 0 && y < height) {
                    this.text[y].setChar(x, (char) chr);
                    this.text[y].setChar(x + 1, '\0');
                    textColour[y].setChar(x, textColourChar);
                    textColour[y].setChar(x + 1, textColourChar);
                    backgroundColour[y].setChar(x, backgroundColourChar);
                    backgroundColour[y].setChar(x + 1, backgroundColourChar);
                    changed = true;
                }
                x += 2;
            } else {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    this.text[y].setChar(x, (char) chr);
                    textColour[y].setChar(x, textColourChar);
                    backgroundColour[y].setChar(x, backgroundColourChar);
                    changed = true;
                }
                x++;
            }
        }

        cursorX = x;
        cursorY = y;
        if (changed) setChanged();
    }

    /**
     * Check whether a string only contains ASCII characters.
     *
     * @param text The string to check.
     * @return Whether every character is in the range [0, 127].
     */
    private static boolean isAscii(String text) {
        for (var i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x80) return false;
        }
        return true;
    }

    /**
     * Decode the UTF-8 sequence at a given position, returning {@code -1} if it is not a valid, complete sequence.
     *
     * @param text  The string to decode. Each character is treated as a byte.
     * @param index The position of the first byte of the sequence.
     * @return The decoded code point, or {@code -1} if the sequence is not valid UTF-8.
     */
    private static int decodeUtf8(String text, int index) {
        var b0 = text.charAt(index) & 0xFF;
        if (b0 < 0xC2 || b0 > 0xF4) return -1;

        if (b0 < 0xE0) {
            if (index + 1 >= text.length()) return -1;
            var b1 = text.charAt(index + 1) & 0xFF;
            if ((b1 & 0xC0) != 0x80) return -1;
            return ((b0 & 0x1F) << 6) | (b1 & 0x3F);
        }

        if (b0 < 0xF0) {
            if (index + 2 >= text.length()) return -1;
            var b1 = text.charAt(index + 1) & 0xFF;
            var b2 = text.charAt(index + 2) & 0xFF;
            if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) return -1;
            if (b0 == 0xE0 && b1 < 0xA0) return -1; // Overlong encoding.
            if (b0 == 0xED && b1 >= 0xA0) return -1; // UTF-16 surrogate
            return ((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
        }

        if (index + 3 >= text.length()) return -1;
        var b1 = text.charAt(index + 1) & 0xFF;
        var b2 = text.charAt(index + 2) & 0xFF;
        var b3 = text.charAt(index + 3) & 0xFF;
        if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) return -1;
        if (b0 == 0xF0 && b1 < 0x90) return -1; // Overlong encoding.
        if (b0 == 0xF4 && b1 >= 0x90) return -1; // Code points above U+10FFFF.
        return ((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
    }

    /**
     * Get the length of a UTF-8 sequence from its leading byte.
     *
     * @param b0 The leading byte.
     * @return The length of the sequence, in bytes.
     */
    private static int utf8Length(int b0) {
        if (b0 < 0xE0) return 2;
        if (b0 < 0xF0) return 3;
        return 4;
    }

    /**
     * Convert a Unicode code point into a terminal character.
     *
     * @param codepoint The code point to convert.
     * @return The terminal character (a value in {@code [0, 255]}, or the code point itself for a wide character), or
     * {@code -1} if the character cannot be represented. Code points above the BMP are not supported yet.
     */
    private static int mapCharacter(int codepoint) {
        var chr = StringUtil.unicodeToTerminal(codepoint);
        if (chr >= 0) return chr;
        // Wide characters are stored directly as their code point. Everything else is dropped.
        return codepoint <= 0xFFFF && StringUtil.isWide(codepoint) ? codepoint : -1;
    }

    public synchronized void scroll(int yDiff) {
        if (yDiff != 0) {
            var newText = new TextBuffer[height];
            var newTextColour = new TextBuffer[height];
            var newBackgroundColour = new TextBuffer[height];
            for (var y = 0; y < height; y++) {
                var oldY = y + yDiff;
                if (oldY >= 0 && oldY < height) {
                    newText[y] = text[oldY];
                    newTextColour[y] = textColour[oldY];
                    newBackgroundColour[y] = backgroundColour[oldY];
                } else {
                    newText[y] = new TextBuffer(' ', width);
                    newTextColour[y] = new TextBuffer(BASE_16.charAt(cursorColour), width);
                    newBackgroundColour[y] = new TextBuffer(BASE_16.charAt(cursorBackgroundColour), width);
                }
            }
            text = newText;
            textColour = newTextColour;
            backgroundColour = newBackgroundColour;
            setChanged();
        }
    }

    public synchronized void clear() {
        for (var y = 0; y < height; y++) {
            text[y].fill(' ');
            textColour[y].fill(BASE_16.charAt(cursorColour));
            backgroundColour[y].fill(BASE_16.charAt(cursorBackgroundColour));
        }
        setChanged();
    }

    public synchronized void clearLine() {
        var y = cursorY;
        if (y >= 0 && y < height) {
            text[y].fill(' ');
            textColour[y].fill(BASE_16.charAt(cursorColour));
            backgroundColour[y].fill(BASE_16.charAt(cursorBackgroundColour));
            setChanged();
        }
    }

    public synchronized TextBuffer getLine(int y) {
        return text[y];
    }

    public synchronized void setLine(int y, String text, String textColour, String backgroundColour) {
        this.text[y].write(text);
        this.textColour[y].write(textColour);
        this.backgroundColour[y].write(backgroundColour);
        setChanged();
    }

    public synchronized TextBuffer getTextColourLine(int y) {
        return textColour[y];
    }

    public synchronized TextBuffer getBackgroundColourLine(int y) {
        return backgroundColour[y];
    }

    public final void setChanged() {
        if (onChanged != null) onChanged.run();
    }

    public static int getColour(char c, Colour def) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return 15 - def.ordinal();
    }
}
