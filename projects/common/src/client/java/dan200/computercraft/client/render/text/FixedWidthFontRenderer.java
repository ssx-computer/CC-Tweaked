// Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
//
// SPDX-License-Identifier: LicenseRef-CCPL

package dan200.computercraft.client.render.text;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dan200.computercraft.client.FrameInfo;
import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.core.util.Colour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static dan200.computercraft.client.render.RenderTypes.FULL_BRIGHT_LIGHTMAP;

/**
 * Handles rendering fixed width text and computer terminals.
 * <p>
 * This class has several modes of usage:
 * <ul>
 * <li>{@link #drawString}: Drawing basic text without a terminal (such as for printouts). Unlike the other methods,
 * this accepts a lightmap coordinate as, unlike terminals, printed pages render fullbright.</li>
 * <li>{@link #drawTerminal}: Draw a terminal with a cursor. This is used by the various computer GUIs to render the
 * whole term.</li>
 * </ul>
 *
 * <strong>IMPORTANT: </strong> When making changes to this class, please check if you need to make the same changes to
 * {@link DirectFixedWidthFontRenderer}.
 */
public final class FixedWidthFontRenderer {
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("computercraft", "textures/gui/term_font.png");

    public static final int FONT_HEIGHT = 9;
    public static final int FONT_WIDTH = 6;

    // Each glyph is drawn as a 6x9 logical quad, but the texture stores it at 4x resolution: glyphs are 24x36, spaced
    // 32x44 apart, with a 4px offset. The atlas also carries a second region of CJK glyphs below the ASCII ones,
    // making it decidedly not square (896x5613), and so U and V must be normalised by the width and height
    // respectively.
    static final float WIDTH = 896.0f;
    static final float HEIGHT = 5613.0f;

    static final int GLYPH_WIDTH = 24;
    static final int GLYPH_HEIGHT = 36;
    static final int GLYPH_PITCH_X = 32;
    static final int GLYPH_PITCH_Y = 44;
    static final int GLYPH_OFFSET = 4;

    // Sample the centre of the solid white block ([520, 584) x [712, 776)) rather than its edge, so that linear
    // filtering cannot bleed transparent pixels into terminal backgrounds.
    static final float BACKGROUND_U1 = 550.0f / WIDTH;
    static final float BACKGROUND_U2 = 553.0f / WIDTH;
    static final float BACKGROUND_V1 = 742.0f / HEIGHT;
    static final float BACKGROUND_V2 = 745.0f / HEIGHT;

    // Below the ASCII glyphs is a second region holding CJK ideographs. Each glyph occupies two terminal cells: it is
    // 12x9 logical pixels, packed at a 14x11 pitch with a (1, 1) offset and 64 glyphs to a row. Slots are assigned in
    // codepoint order across three contiguous ranges (see cjkSlot), so unassigned codepoints still consume a slot.
    static final int CJK_REGION_Y = 784;
    static final int CJK_GLYPH_WIDTH = 12;
    static final int CJK_GLYPH_HEIGHT = 9;
    static final int CJK_PITCH_X = 14;
    static final int CJK_PITCH_Y = 11;
    static final int CJK_COLUMNS = 64;

    // The slot of the first codepoint in each of those ranges.
    static final int CJK_EXT_A_BASE = 0;
    static final int CJK_UNIFIED_BASE = 6592;
    static final int CJK_COMPAT_BASE = 27584;

    private static final int BLACK = FastColor.ARGB32.color(255, byteColour(Colour.BLACK.getR()), byteColour(Colour.BLACK.getR()), byteColour(Colour.BLACK.getR()));
    private static final float Z_OFFSET = 1e-4f;

    private FixedWidthFontRenderer() {
    }

    private static byte byteColour(float c) {
        return (byte) (int) (c * 255);
    }

    public static float toGreyscale(double[] rgb) {
        return (float) ((rgb[0] + rgb[1] + rgb[2]) / 3);
    }

    public static int getColour(char c, Colour def) {
        return 15 - Terminal.getColour(c, def);
    }

    /**
     * Find the atlas slot of a CJK codepoint.
     * <p>
     * The CJK region stores three codepoint ranges back to back, with no gaps left for unassigned characters.
     *
     * @param codepoint The codepoint to look up.
     * @return The atlas slot, or {@code -1} if this codepoint has no glyph.
     */
    static int cjkSlot(int codepoint) {
        // CJK Unified Ideographs Extension A.
        if (codepoint >= 0x3400 && codepoint <= 0x4DBF) return CJK_EXT_A_BASE + (codepoint - 0x3400);
        // CJK Unified Ideographs.
        if (codepoint >= 0x4E00 && codepoint <= 0x9FFF) return CJK_UNIFIED_BASE + (codepoint - 0x4E00);
        // CJK Compatibility Ideographs.
        if (codepoint >= 0xF900 && codepoint <= 0xFAFF) return CJK_COMPAT_BASE + (codepoint - 0xF900);
        return -1;
    }

    private static void drawChar(QuadEmitter emitter, float x, float y, int index, int colour, int light) {
        // Short circuit to avoid the common case - the texture should be blank here after all.
        if (index == '\0' || index == ' ') return;

        if (index > 255) {
            // Anything outside of Latin-1 must be a CJK codepoint to have a glyph; otherwise fall back to '?'.
            var slot = cjkSlot(index);
            if (slot >= 0) {
                drawCjkChar(emitter, x, y, slot, colour, light);
                return;
            }
            index = '?';
        }

        var column = index % 16;
        var row = index / 16;

        // The quad stays 6x9 logical pixels, but its UVs cover the (4x larger) vector glyph, so that it is sampled
        // smoothly rather than being drawn as a pixelated bitmap.
        var xStart = GLYPH_OFFSET + column * GLYPH_PITCH_X;
        var yStart = GLYPH_OFFSET + row * GLYPH_PITCH_Y;

        quad(
            emitter, x, y, x + FONT_WIDTH, y + FONT_HEIGHT, 0, colour,
            xStart / WIDTH, yStart / HEIGHT, (xStart + GLYPH_WIDTH) / WIDTH, (yStart + GLYPH_HEIGHT) / HEIGHT, light
        );
    }

    /**
     * Draw a wide (two cell) CJK glyph, starting at the left edge of {@code x}.
     *
     * @param emitter The emitter to draw to.
     * @param x       The left edge of the glyph, in logical pixels.
     * @param y       The top edge of the glyph, in logical pixels.
     * @param slot    The atlas slot of this glyph, as returned by {@link #cjkSlot(int)}.
     * @param colour  The colour of the glyph.
     * @param light   The lightmap coordinate.
     */
    private static void drawCjkChar(QuadEmitter emitter, float x, float y, int slot, int colour, int light) {
        var column = slot % CJK_COLUMNS;
        var row = slot / CJK_COLUMNS;

        // As elsewhere, the quad is the glyph's logical size, while the UVs cover the exact block of the atlas.
        var xStart = 1 + column * CJK_PITCH_X;
        var yStart = CJK_REGION_Y + 1 + row * CJK_PITCH_Y;

        quad(
            emitter, x, y, x + CJK_GLYPH_WIDTH, y + CJK_GLYPH_HEIGHT, 0, colour,
            xStart / WIDTH, yStart / HEIGHT, (xStart + CJK_GLYPH_WIDTH) / WIDTH, (yStart + CJK_GLYPH_HEIGHT) / HEIGHT, light
        );
    }

    public static void drawQuad(QuadEmitter emitter, float x, float y, float z, float width, float height, int colour, int light) {
        quad(emitter, x, y, x + width, y + height, z, colour, BACKGROUND_U1, BACKGROUND_V1, BACKGROUND_U2, BACKGROUND_V2, light);
    }

    private static void drawQuad(QuadEmitter emitter, float x, float y, float width, float height, Palette palette, char colourIndex, int light) {
        var colour = palette.getRenderColours(getColour(colourIndex, Colour.BLACK));
        drawQuad(emitter, x, y, 0, width, height, colour, light);
    }

    private static void drawBackground(
        QuadEmitter emitter, float x, float y, TextBuffer backgroundColour, Palette palette,
        float leftMarginSize, float rightMarginSize, float height, int light
    ) {
        if (leftMarginSize > 0) {
            drawQuad(emitter, x - leftMarginSize, y, leftMarginSize, height, palette, backgroundColour.charAt(0), light);
        }

        if (rightMarginSize > 0) {
            drawQuad(emitter, x + backgroundColour.length() * FONT_WIDTH, y, rightMarginSize, height, palette, backgroundColour.charAt(backgroundColour.length() - 1), light);
        }

        // Batch together runs of identical background cells.
        var blockStart = 0;
        var blockColour = '\0';
        for (var i = 0; i < backgroundColour.length(); i++) {
            var colourIndex = backgroundColour.charAt(i);
            if (colourIndex == blockColour) continue;

            if (blockColour != '\0') {
                drawQuad(emitter, x + blockStart * FONT_WIDTH, y, FONT_WIDTH * (i - blockStart), height, palette, blockColour, light);
            }

            blockColour = colourIndex;
            blockStart = i;
        }

        if (blockColour != '\0') {
            drawQuad(emitter, x + blockStart * FONT_WIDTH, y, FONT_WIDTH * (backgroundColour.length() - blockStart), height, palette, blockColour, light);
        }
    }

    public static void drawString(QuadEmitter emitter, float x, float y, TextBuffer text, TextBuffer textColour, Palette palette, int light) {
        for (var i = 0; i < text.length(); i++) {
            var colour = palette.getRenderColours(getColour(textColour.charAt(i), Colour.BLACK));

            int index = text.charAt(i);
            if (index > 255 && cjkSlot(index) < 0) index = '?';
            drawChar(emitter, x + i * FONT_WIDTH, y, index, colour, light);

            // A CJK glyph occupies two cells, the second of which holds a '\0' placeholder. We've already drawn the
            // whole glyph, so skip over that cell.
            if (index > 255) i++;
        }

    }

    public static void drawTerminalForeground(QuadEmitter emitter, float x, float y, Terminal terminal) {
        var palette = terminal.getPalette();
        var height = terminal.getHeight();

        // The main text
        for (var i = 0; i < height; i++) {
            var rowY = y + FONT_HEIGHT * i;
            drawString(
                emitter, x, rowY, terminal.getLine(i), terminal.getTextColourLine(i),
                palette, FULL_BRIGHT_LIGHTMAP
            );
        }
    }

    public static void drawTerminalBackground(
        QuadEmitter emitter, float x, float y, Terminal terminal,
        float topMarginSize, float bottomMarginSize, float leftMarginSize, float rightMarginSize
    ) {
        var palette = terminal.getPalette();
        var height = terminal.getHeight();

        // Top and bottom margins
        drawBackground(
            emitter, x, y - topMarginSize, terminal.getBackgroundColourLine(0), palette,
            leftMarginSize, rightMarginSize, topMarginSize, FULL_BRIGHT_LIGHTMAP
        );

        drawBackground(
            emitter, x, y + height * FONT_HEIGHT, terminal.getBackgroundColourLine(height - 1), palette,
            leftMarginSize, rightMarginSize, bottomMarginSize, FULL_BRIGHT_LIGHTMAP
        );

        // The main text
        for (var i = 0; i < height; i++) {
            var rowY = y + FONT_HEIGHT * i;
            drawBackground(
                emitter, x, rowY, terminal.getBackgroundColourLine(i), palette,
                leftMarginSize, rightMarginSize, FONT_HEIGHT, FULL_BRIGHT_LIGHTMAP
            );
        }
    }

    public static boolean isCursorVisible(Terminal terminal) {
        if (!terminal.getCursorBlink()) return false;

        var cursorX = terminal.getCursorX();
        var cursorY = terminal.getCursorY();
        return cursorX >= 0 && cursorX < terminal.getWidth() && cursorY >= 0 && cursorY < terminal.getHeight();
    }

    public static void drawCursor(QuadEmitter emitter, float x, float y, Terminal terminal) {
        if (isCursorVisible(terminal) && FrameInfo.getGlobalCursorBlink()) {
            var colour = terminal.getPalette().getRenderColours(15 - terminal.getTextColour());
            drawChar(emitter, x + terminal.getCursorX() * FONT_WIDTH, y + terminal.getCursorY() * FONT_HEIGHT, '_', colour, FULL_BRIGHT_LIGHTMAP);
        }
    }

    public static void drawTerminal(
        QuadEmitter emitter, float x, float y, Terminal terminal,
        float topMarginSize, float bottomMarginSize, float leftMarginSize, float rightMarginSize
    ) {
        drawTerminalBackground(
            emitter, x, y, terminal,
            topMarginSize, bottomMarginSize, leftMarginSize, rightMarginSize
        );

        // Render the foreground with a slight offset. By calling .translate() on the matrix itself, we're translating
        // in screen space, rather than in model/view space.
        // It's definitely not perfect, but better than z fighting!
        var transformBackup = new Matrix4f(emitter.poseMatrix());
        emitter.poseMatrix().translate(new Vector3f(0, 0, Z_OFFSET));

        drawTerminalForeground(emitter, x, y, terminal);
        drawCursor(emitter, x, y, terminal);

        emitter.poseMatrix().set(transformBackup);
    }

    public static void drawEmptyTerminal(QuadEmitter emitter, float x, float y, float width, float height) {
        drawQuad(emitter, x, y, 0, width, height, BLACK, FULL_BRIGHT_LIGHTMAP);
    }

    public record QuadEmitter(Matrix4f poseMatrix, VertexConsumer consumer) {
    }

    public static QuadEmitter toVertexConsumer(PoseStack transform, VertexConsumer consumer) {
        return new QuadEmitter(transform.last().pose(), consumer);
    }

    private static void quad(QuadEmitter c, float x1, float y1, float x2, float y2, float z, int colour, float u1, float v1, float u2, float v2, int light) {
        var poseMatrix = c.poseMatrix();
        var consumer = c.consumer();
        int r = FastColor.ARGB32.red(colour), g = FastColor.ARGB32.green(colour), b = FastColor.ARGB32.blue(colour), a = FastColor.ARGB32.alpha(colour);

        consumer.addVertex(poseMatrix, x1, y1, z).setColor(r, g, b, a).setUv(u1, v1).setLight(light);
        consumer.addVertex(poseMatrix, x1, y2, z).setColor(r, g, b, a).setUv(u1, v2).setLight(light);
        consumer.addVertex(poseMatrix, x2, y2, z).setColor(r, g, b, a).setUv(u2, v2).setLight(light);
        consumer.addVertex(poseMatrix, x2, y1, z).setColor(r, g, b, a).setUv(u2, v1).setLight(light);
    }
}
