// SPDX-FileCopyrightText: 2020 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

#version 150

#moj_import <fog.glsl>

// The logical size of a terminal cell, used to work out which cell a fragment belongs to.
#define FONT_WIDTH 6.0
#define FONT_HEIGHT 9.0

// The glyphs themselves are stored in the font atlas at 4x resolution: each glyph is 24x36, the cells are spaced
// 32x44 apart, with a 4px offset. The atlas also has a region of CJK glyphs below the ASCII ones, so it is not square
// (896x5613), and U and V are normalised separately.
#define GLYPH_WIDTH 24.0
#define GLYPH_HEIGHT 36.0
#define GLYPH_OFFSET 4.0
#define GLYPH_PITCH_X 32.0
#define GLYPH_PITCH_Y 44.0
#define FONT_TEXTURE_WIDTH 896.0
#define FONT_TEXTURE_HEIGHT 5613.0

// Below the ASCII glyphs is a second region holding CJK ideographs. Unlike the ASCII glyphs, these are stored at 1x
// resolution - each glyph is 12x9 (i.e. two terminal cells wide), with a 1px offset and a 14x11 pitch. See
// FixedWidthFontRenderer (and DirectFixedWidthFontRenderer) for the Java equivalent of this layout.
#define CJK_REGION_Y 784.0
#define CJK_PITCH_X 14.0
#define CJK_PITCH_Y 11.0
#define CJK_COLUMNS 64

uniform sampler2D Sampler0; // Font
uniform usamplerBuffer Tbo;

layout(std140) uniform MonitorData {
    vec3 Palette[16];
    int Width;
    int Height;
    ivec2 CursorPos;
    int CursorColour;
};
uniform int CursorBlink;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 fontPos;

out vec4 fragColor;

vec2 texture_corner(int index) {
    float x = GLYPH_OFFSET + float(index % 16) * GLYPH_PITCH_X;
    float y = GLYPH_OFFSET + float(index / 16) * GLYPH_PITCH_Y;
    return vec2(x, y);
}

// Whether this codepoint has a glyph in the CJK region of the atlas.
bool is_cjk(int c) {
    return (c >= 0x3400 && c <= 0x4DBF) || (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0xF900 && c <= 0xFAFF);
}

// The top-left corner of this codepoint's glyph within the atlas.
vec2 cjk_corner(int c) {
    int slot = c <= 0x4DBF ? c - 0x3400 : (c <= 0x9FFF ? 6592 + c - 0x4E00 : 27584 + c - 0xF900);
    return vec2(1.0 + float(slot % CJK_COLUMNS) * CJK_PITCH_X, CJK_REGION_Y + 1.0 + float(slot / CJK_COLUMNS) * CJK_PITCH_Y);
}

vec4 recolour(vec4 texture, int colour) {
    return vec4(texture.rgb * Palette[colour], texture.rgba);
}

void main() {
    vec2 term_pos = vec2(fontPos.x / FONT_WIDTH, fontPos.y / FONT_HEIGHT);
    vec2 corner = floor(term_pos);

    ivec2 cell = ivec2(corner);
    int index = 3 * (clamp(cell.x, 0, Width - 1) + clamp(cell.y, 0, Height - 1) * Width);

    // 1 if 0 <= x, y < Width, Height, 0 otherwise
    vec2 outside = step(vec2(0.0, 0.0), vec2(cell)) * step(vec2(cell), vec2(float(Width) - 1.0, float(Height) - 1.0));
    float mult = outside.x * outside.y;

    int character = int(texelFetch(Tbo, index).r);
    int fg = int(texelFetch(Tbo, index + 1).r);
    int bg = int(texelFetch(Tbo, index + 2).r);

    // Our position within the cell, in logical (6x9) pixels.
    vec2 local = (term_pos - corner) * vec2(FONT_WIDTH, FONT_HEIGHT);

    // A wide (CJK) glyph occupies two cells: the left one holds the codepoint, the right one a '\0' placeholder. If we
    // are drawing such a placeholder then steal the glyph from our left neighbour and shift our horizontal position
    // across by a whole cell, so that the two cells together draw the full 12x9 glyph.
    int glyph = character;
    float glyphX = local.x;
    if (character == 0 && cell.x > 0) {
        int left = int(texelFetch(Tbo, 3 * (clamp(cell.x - 1, 0, Width - 1) + clamp(cell.y, 0, Height - 1) * Width)).r);
        if (is_cjk(left)) {
            glyph = left;
            glyphX += FONT_WIDTH;
        }
    }

    // Our position within the cell, rescaled to cover the 24x36 region of an ASCII glyph.
    vec2 pos = local * vec2(GLYPH_WIDTH / FONT_WIDTH, GLYPH_HEIGHT / FONT_HEIGHT);

    vec4 charTex;
    if (is_cjk(glyph)) {
        // The CJK glyphs are stored at 1x, so one logical pixel covers exactly one texel of the atlas.
        charTex = recolour(texture(Sampler0, (cjk_corner(glyph) + vec2(glyphX, local.y)) / vec2(FONT_TEXTURE_WIDTH, FONT_TEXTURE_HEIGHT)), fg);
    } else {
        // Fall back to '?' (63) for any codepoint without a glyph in the ASCII region, as the VBO renderer does.
        charTex = recolour(texture(Sampler0, (texture_corner(glyph <= 255 ? glyph : 63) + pos) / vec2(FONT_TEXTURE_WIDTH, FONT_TEXTURE_HEIGHT)), fg);
    }

    // Applies the cursor on top of the current character if we're blinking and in the current cursor's cell. We do it
    // this funky way to avoid branches.
    vec4 cursorTex = recolour(texture(Sampler0, (texture_corner(95) + pos) / vec2(FONT_TEXTURE_WIDTH, FONT_TEXTURE_HEIGHT)), CursorColour); // 95 = '_'
    vec4 img = mix(charTex, cursorTex, cursorTex.a * float(CursorBlink) * (CursorPos == cell ? 1.0 : 0.0));

    vec4 colour = vec4(mix(Palette[bg], img.rgb, img.a * mult), 1.0) * ColorModulator;

    fragColor = linear_fog(colour, vertexDistance, FogStart, FogEnd, FogColor);
}
