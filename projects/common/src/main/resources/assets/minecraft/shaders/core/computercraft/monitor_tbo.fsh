// SPDX-FileCopyrightText: 2020 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

#version 150

#moj_import <fog.glsl>

// The logical size of a terminal cell, used to work out which cell a fragment belongs to.
#define FONT_WIDTH 6.0
#define FONT_HEIGHT 9.0

// The glyphs themselves are stored in the font atlas at 4x resolution: each glyph is 24x36, the cells are spaced
// 32x44 apart, with a 4px offset. The atlas is not square (592x784), so U and V are normalised separately.
#define GLYPH_WIDTH 24.0
#define GLYPH_HEIGHT 36.0
#define GLYPH_OFFSET 4.0
#define GLYPH_PITCH_X 32.0
#define GLYPH_PITCH_Y 44.0
#define FONT_TEXTURE_WIDTH 592.0
#define FONT_TEXTURE_HEIGHT 784.0

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

    // Our position within the cell, in logical (6x9) pixels, rescaled to cover the 24x36 region of the glyph.
    vec2 pos = (term_pos - corner) * vec2(FONT_WIDTH, FONT_HEIGHT) * vec2(GLYPH_WIDTH / FONT_WIDTH, GLYPH_HEIGHT / FONT_HEIGHT);
    vec4 charTex = recolour(texture(Sampler0, (texture_corner(character) + pos) / vec2(FONT_TEXTURE_WIDTH, FONT_TEXTURE_HEIGHT)), fg);

    // Applies the cursor on top of the current character if we're blinking and in the current cursor's cell. We do it
    // this funky way to avoid branches.
    vec4 cursorTex = recolour(texture(Sampler0, (texture_corner(95) + pos) / vec2(FONT_TEXTURE_WIDTH, FONT_TEXTURE_HEIGHT)), CursorColour); // 95 = '_'
    vec4 img = mix(charTex, cursorTex, cursorTex.a * float(CursorBlink) * (CursorPos == cell ? 1.0 : 0.0));

    vec4 colour = vec4(mix(Palette[bg], img.rgb, img.a * mult), 1.0) * ColorModulator;

    fragColor = linear_fog(colour, vertexDistance, FogStart, FogEnd, FogColor);
}
