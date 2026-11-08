-- SPDX-FileCopyrightText: 2017 Daniel Ratcliffe
--
-- SPDX-License-Identifier: LicenseRef-CCPL

--[[- A [terminal redirect][`term.Redirect`] occupying a smaller area of an
existing terminal. This allows for easy definition of spaces within the display
that can be written/drawn to, then later redrawn/repositioned/etc as need
be. The API itself contains only one function, [`window.create`], which returns
the windows themselves.

Windows are considered terminal objects - as such, they have access to nearly
all the commands in the term API (plus a few extras of their own, listed within
said API) and are valid targets to redirect to.

Each window has a "parent" terminal object, which can be the computer's own
display, a monitor, another window or even other, user-defined terminal
objects. Whenever a window is rendered to, the actual screen-writing is
performed via that parent (or, if that has one too, then that parent, and so
forth). Bear in mind that the cursor of a window's parent will hence be moved
around etc when writing a given child window.

Windows retain a memory of everything rendered "through" them (hence acting as
display buffers), and if the parent's display is wiped, the window's content can
be easily redrawn later. A window may also be flagged as invisible, preventing
any changes to it from being rendered until it's flagged as visible once more.

A parent terminal object may have multiple children assigned to it, and windows
may overlap. For example, the Multishell system functions by assigning each tab
a window covering the screen, each using the starting terminal display as its
parent, and only one of which is visible at a time.

@module window
@since 1.6
]]

local expect = dofile("rom/modules/main/cc/expect.lua").expect

local tHex = {
    [colors.white] = "0",
    [colors.orange] = "1",
    [colors.magenta] = "2",
    [colors.lightBlue] = "3",
    [colors.yellow] = "4",
    [colors.lime] = "5",
    [colors.pink] = "6",
    [colors.gray] = "7",
    [colors.lightGray] = "8",
    [colors.cyan] = "9",
    [colors.purple] = "a",
    [colors.blue] = "b",
    [colors.brown] = "c",
    [colors.green] = "d",
    [colors.red] = "e",
    [colors.black] = "f",
}

local type = type
local string_rep = string.rep
local string_sub = string.sub

--- A custom version of [`colors.toBlit`], specialised for the window API.
local function parse_color(color)
    if type(color) ~= "number" then
        -- By tail-calling expect, we ensure expect has the right error level.
        return expect(1, color, "number")
    end

    if color < 1 or color > 0xffff then error("Colour out of range", 3) end
    return 2 ^ math.floor(math.log(color, 2))
end

--[[- Returns a terminal object that is a space within the specified parent
terminal object. This can then be used (or even redirected to) in the same
manner as eg a wrapped monitor. Refer to [the term API][`term`] for a list of
functions available to it.

[`term`] itself may not be passed as the parent, though [`term.native`] is
acceptable. Generally, [`term.current`] or a wrapped monitor will be most
suitable, though windows may even have other windows assigned as their
parents.

@tparam term.Redirect parent The parent terminal redirect to draw to.
@tparam number nX The x coordinate this window is drawn at in the parent terminal
@tparam number nY The y coordinate this window is drawn at in the parent terminal
@tparam number nWidth The width of this window
@tparam number nHeight The height of this window
@tparam[opt] boolean bStartVisible Whether this window is visible by
default. Defaults to `true`.
@treturn Window The constructed window
@since 1.6
@usage Create a smaller window, fill it red and write some text to it.

    local my_window = window.create(term.current(), 1, 1, 20, 5)
    my_window.setBackgroundColour(colours.red)
    my_window.setTextColour(colours.white)
    my_window.clear()
    my_window.write("Testing my window!")

@usage Create a smaller window and redirect to it.

    local my_window = window.create(term.current(), 1, 1, 25, 5)
    term.redirect(my_window)
    print("Writing some long text which will wrap around and show the bounds of this window.")

]]
function create(parent, nX, nY, nWidth, nHeight, bStartVisible)
    expect(1, parent, "table")
    expect(2, nX, "number")
    expect(3, nY, "number")
    expect(4, nWidth, "number")
    expect(5, nHeight, "number")
    expect(6, bStartVisible, "boolean", "nil")

    if parent == term then
        error("term is not a recommended window parent, try term.current() instead", 2)
    end

    local sEmptySpaceLine
    local tEmptyColorLines = {}
    local function createEmptyLines(nWidth)
        sEmptySpaceLine = string_rep(" ", nWidth)
        for n = 0, 15 do
            local nColor = 2 ^ n
            local sHex = tHex[nColor]
            tEmptyColorLines[nColor] = string_rep(sHex, nWidth)
        end
    end

    createEmptyLines(nWidth)

    -- Setup
    local bVisible = bStartVisible ~= false
    local nCursorX = 1
    local nCursorY = 1
    local bCursorBlink = false
    local nTextColor = colors.white
    local nBackgroundColor = colors.black
    local tLines = {}
    local tPalette = {}
    do
        local sEmptyText = sEmptySpaceLine
        local sEmptyTextColor = tEmptyColorLines[nTextColor]
        local sEmptyBackgroundColor = tEmptyColorLines[nBackgroundColor]
        for y = 1, nHeight do
            tLines[y] = { sEmptyText, sEmptyTextColor, sEmptyBackgroundColor }
        end

        for i = 0, 15 do
            local c = 2 ^ i
            tPalette[c] = { parent.getPaletteColour(c) }
        end
    end

    -- Helper functions

    -- Text is buffered as a byte string, but the window's width and cursor are measured in terminal cells.
    -- Characters outside CC's charset (such as CJK ideographs) are sent to us as UTF-8, occupying several bytes but
    -- two cells, so we need to convert between the two.
    --
    -- We assume every multi-byte character is a wide one (which is true of the characters CC's font contains); a stray
    -- or truncated byte is treated as a single cell, matching the terminal's own UTF-8 decoding.

    -- The number of bytes in the UTF-8 character starting at byte `i` of `s`.
    local function charLen(s, i)
        local b = string.byte(s, i)
        if not b or b < 0x80 then
            return 1
        elseif b >= 0xF0 then
            return 4
        elseif b >= 0xE0 then
            return 3
        elseif b >= 0xC0 then
            return 2
        else
            -- A stray continuation byte. Treat it as a character of its own, so that we always make progress.
            return 1
        end
    end

    -- The number of cells occupied by the character starting at byte `i` of `s`.
    local function charCells(s, i)
        local b = string.byte(s, i)
        if b and b >= 0xC0 then
            return 2
        else
            return 1
        end
    end

    -- The number of cells occupied by the whole of `s`.
    local function cellWidth(s)
        local nCells, i = 0, 1
        while i <= #s do
            nCells = nCells + charCells(s, i)
            i = i + charLen(s, i)
        end
        return nCells
    end

    -- The byte index of the last byte of the longest prefix of `s` which fits in `nCells` cells. A multi-byte
    -- character which would straddle that boundary is left out, so we never emit half a character.
    local function bytePrefix(s, nCells)
        local i, n = 1, 0
        while i <= #s do
            local nCharCells = charCells(s, i)
            if n + nCharCells > nCells then break end
            n = n + nCharCells
            i = i + charLen(s, i)
        end
        return i - 1
    end

    -- Pad (or truncate) a line so that it occupies exactly `width` cells, keeping the text and colour strings
    -- aligned byte for byte (colours are stored one byte per byte of text).
    local function fitLine(sText, sTextColor, sBackgroundColor, width)
        local nCells = cellWidth(sText)
        if nCells > width then
            local nBytes = bytePrefix(sText, width)
            return string_sub(sText, 1, nBytes), string_sub(sTextColor, 1, nBytes), string_sub(sBackgroundColor, 1, nBytes)
        elseif nCells < width then
            local nPad = width - nCells
            return sText .. string_rep(" ", nPad),
                sTextColor .. string_rep(tHex[nTextColor], nPad),
                sBackgroundColor .. string_rep(tHex[nBackgroundColor], nPad)
        end
        return sText, sTextColor, sBackgroundColor
    end

    local function updateCursorPos()
        if nCursorX >= 1 and nCursorY >= 1 and
           nCursorX <= nWidth and nCursorY <= nHeight then
            parent.setCursorPos(nX + nCursorX - 1, nY + nCursorY - 1)
        else
            parent.setCursorPos(0, 0)
        end
    end

    local function updateCursorBlink()
        parent.setCursorBlink(bCursorBlink)
    end

    local function updateCursorColor()
        parent.setTextColor(nTextColor)
    end

    local function redrawLine(n)
        local tLine = tLines[n]
        -- The buffer holds bytes, but this window is `nWidth` *cells* wide. Clip in cells before handing the line to
        -- the parent: a multi-byte character occupies two cells, so blitting the whole buffer could spill past the
        -- window's right hand edge (and blitting a character which straddles that edge would poke one cell out).
        local nBytes = bytePrefix(tLine[1], nWidth)
        parent.setCursorPos(nX, nY + n - 1)
        parent.blit(string_sub(tLine[1], 1, nBytes), string_sub(tLine[2], 1, nBytes), string_sub(tLine[3], 1, nBytes))
    end

    local function redraw()
        for n = 1, nHeight do
            redrawLine(n)
        end
    end

    local function updatePalette()
        for k, v in pairs(tPalette) do
            parent.setPaletteColour(k, v[1], v[2], v[3])
        end
    end

    local function internalBlit(sText, sTextColor, sBackgroundColor)
        -- The cursor and the window width are in cells, so work out where the text ends in cells too. The buffer
        -- itself stays a byte string, but a character which would spill out of the window (in particular a wide one
        -- straddling the right hand edge) is dropped, so what we store never overflows this window when redrawn.
        local nStart = nCursorX
        local nEnd = nStart + cellWidth(sText) - 1
        if nCursorY >= 1 and nCursorY <= nHeight then
            if nStart <= nWidth and nEnd >= 1 then
                -- Modify line
                local tLine = tLines[nCursorY]
                if nStart == 1 and nEnd == nWidth then
                    tLine[1] = sText
                    tLine[2] = sTextColor
                    tLine[3] = sBackgroundColor
                else
                    -- The cells of sText which actually land on screen are those in [1, nWidth]. Discard any
                    -- before the window (when the cursor has been scrolled off to the left) and any beyond it.
                    local nSkip = math.max(nStart, 1) - nStart
                    local nKeep = math.min(nEnd, nWidth) - math.max(nStart, 1) + 1
                    local nByteFrom = bytePrefix(sText, nSkip) + 1
                    local nByteTo = bytePrefix(sText, nSkip + nKeep)
                    if nByteTo < nByteFrom then nByteTo = nByteFrom - 1 end

                    local sClippedText = string_sub(sText, nByteFrom, nByteTo)
                    local sClippedTextColor = string_sub(sTextColor, nByteFrom, nByteTo)
                    local sClippedBackgroundColor = string_sub(sBackgroundColor, nByteFrom, nByteTo)

                    local sOldText = tLine[1]
                    local sOldTextColor = tLine[2]
                    local sOldBackgroundColor = tLine[3]
                    local sNewText, sNewTextColor, sNewBackgroundColor
                    if nStart > 1 then
                        local nOldEnd = bytePrefix(sOldText, nStart - 1)
                        sNewText = string_sub(sOldText, 1, nOldEnd) .. sClippedText
                        sNewTextColor = string_sub(sOldTextColor, 1, nOldEnd) .. sClippedTextColor
                        sNewBackgroundColor = string_sub(sOldBackgroundColor, 1, nOldEnd) .. sClippedBackgroundColor
                    else
                        sNewText = sClippedText
                        sNewTextColor = sClippedTextColor
                        sNewBackgroundColor = sClippedBackgroundColor
                    end
                    if nEnd < nWidth then
                        local nOldStart = bytePrefix(sOldText, nEnd) + 1
                        sNewText = sNewText .. string_sub(sOldText, nOldStart)
                        sNewTextColor = sNewTextColor .. string_sub(sOldTextColor, nOldStart)
                        sNewBackgroundColor = sNewBackgroundColor .. string_sub(sOldBackgroundColor, nOldStart)
                    end

                    -- Keep every buffered line exactly nWidth cells long, so it always fully covers the window.
                    tLine[1], tLine[2], tLine[3] = fitLine(sNewText, sNewTextColor, sNewBackgroundColor, nWidth)
                end

                -- Redraw line
                if bVisible then
                    redrawLine(nCursorY)
                end
            end
        end

        -- Move and redraw cursor
        nCursorX = nEnd + 1
        if bVisible then
            updateCursorColor()
            updateCursorPos()
        end
    end

    --- The window object. Refer to the [module's documentation][`window`] for
    -- a full description.
    --
    -- @type Window
    -- @see term.Redirect
    local window = {}

    function window.write(sText)
        sText = tostring(sText)
        internalBlit(sText, string_rep(tHex[nTextColor], #sText), string_rep(tHex[nBackgroundColor], #sText))
    end

    function window.blit(sText, sTextColor, sBackgroundColor)
        if type(sText) ~= "string" then expect(1, sText, "string") end
        if type(sTextColor) ~= "string" then expect(2, sTextColor, "string") end
        if type(sBackgroundColor) ~= "string" then expect(3, sBackgroundColor, "string") end
        if #sTextColor ~= #sText or #sBackgroundColor ~= #sText then
            error("Arguments must be the same length", 2)
        end
        sTextColor = sTextColor:lower()
        sBackgroundColor = sBackgroundColor:lower()
        internalBlit(sText, sTextColor, sBackgroundColor)
    end

    function window.clear()
        local sEmptyText = sEmptySpaceLine
        local sEmptyTextColor = tEmptyColorLines[nTextColor]
        local sEmptyBackgroundColor = tEmptyColorLines[nBackgroundColor]
        for y = 1, nHeight do
            local line = tLines[y]
            line[1] = sEmptyText
            line[2] = sEmptyTextColor
            line[3] = sEmptyBackgroundColor
        end
        if bVisible then
            redraw()
            updateCursorColor()
            updateCursorPos()
        end
    end

    function window.clearLine()
        if nCursorY >= 1 and nCursorY <= nHeight then
            local line = tLines[nCursorY]
            line[1] = sEmptySpaceLine
            line[2] = tEmptyColorLines[nTextColor]
            line[3] = tEmptyColorLines[nBackgroundColor]
            if bVisible then
                redrawLine(nCursorY)
                updateCursorColor()
                updateCursorPos()
            end
        end
    end

    function window.getCursorPos()
        return nCursorX, nCursorY
    end

    function window.setCursorPos(x, y)
        if type(x) ~= "number" then expect(1, x, "number") end
        if type(y) ~= "number" then expect(2, y, "number") end
        nCursorX = math.floor(x)
        nCursorY = math.floor(y)
        if bVisible then
            updateCursorPos()
        end
    end

    function window.setCursorBlink(blink)
        if type(blink) ~= "boolean" then expect(1, blink, "boolean") end
        bCursorBlink = blink
        if bVisible then
            updateCursorBlink()
        end
    end

    function window.getCursorBlink()
        return bCursorBlink
    end

    local function isColor()
        return parent.isColor()
    end

    function window.isColor()
        return isColor()
    end

    function window.isColour()
        return isColor()
    end

    local function setTextColor(color)
        if tHex[color] == nil then color = parse_color(color) end

        nTextColor = color
        if bVisible then
            updateCursorColor()
        end
    end

    window.setTextColor = setTextColor
    window.setTextColour = setTextColor

    function window.setPaletteColour(colour, r, g, b)
        if tHex[colour] == nil then colour = parse_color(colour) end

        local tCol
        if type(r) == "number" and g == nil and b == nil then
            tCol = { colours.unpackRGB(r) }
            tPalette[colour] = tCol
        else
            if type(r) ~= "number" then expect(2, r, "number") end
            if type(g) ~= "number" then expect(3, g, "number") end
            if type(b) ~= "number" then expect(4, b, "number") end

            tCol = tPalette[colour]
            tCol[1] = r
            tCol[2] = g
            tCol[3] = b
        end

        if bVisible then
            return parent.setPaletteColour(colour, tCol[1], tCol[2], tCol[3])
        end
    end

    window.setPaletteColor = window.setPaletteColour

    function window.getPaletteColour(colour)
        if tHex[colour] == nil then colour = parse_color(colour) end
        local tCol = tPalette[colour]
        return tCol[1], tCol[2], tCol[3]
    end

    window.getPaletteColor = window.getPaletteColour

    local function setBackgroundColor(color)
        if tHex[color] == nil then color = parse_color(color) end
        nBackgroundColor = color
    end

    window.setBackgroundColor = setBackgroundColor
    window.setBackgroundColour = setBackgroundColor

    function window.getSize()
        return nWidth, nHeight
    end

    function window.scroll(n)
        if type(n) ~= "number" then expect(1, n, "number") end
        if n ~= 0 then
            local tNewLines = {}
            local sEmptyText = sEmptySpaceLine
            local sEmptyTextColor = tEmptyColorLines[nTextColor]
            local sEmptyBackgroundColor = tEmptyColorLines[nBackgroundColor]
            for newY = 1, nHeight do
                local y = newY + n
                if y >= 1 and y <= nHeight then
                    tNewLines[newY] = tLines[y]
                else
                    tNewLines[newY] = { sEmptyText, sEmptyTextColor, sEmptyBackgroundColor }
                end
            end
            tLines = tNewLines
            if bVisible then
                redraw()
                updateCursorColor()
                updateCursorPos()
            end
        end
    end

    function window.getTextColor()
        return nTextColor
    end

    function window.getTextColour()
        return nTextColor
    end

    function window.getBackgroundColor()
        return nBackgroundColor
    end

    function window.getBackgroundColour()
        return nBackgroundColor
    end

    --- Get the buffered contents of a line in this window.
    --
    -- @tparam number y The y position of the line to get.
    -- @treturn string The textual content of this line.
    -- @treturn string The text colours of this line, suitable for use with [`term.blit`].
    -- @treturn string The background colours of this line, suitable for use with [`term.blit`].
    -- @throws If `y` is not between 1 and this window's height.
    -- @since 1.84.0
    function window.getLine(y)
        if type(y) ~= "number" then expect(1, y, "number") end

        if y < 1 or y > nHeight then
            error("Line is out of range.", 2)
        end

        local line = tLines[y]
        return line[1], line[2], line[3]
    end

    -- Other functions

    --- Set whether this window is visible. Invisible windows will not be drawn
    -- to the screen until they are made visible again.
    --
    -- Making an invisible window visible will immediately draw it.
    --
    -- @tparam boolean visible Whether this window is visible.
    function window.setVisible(visible)
        if type(visible) ~= "boolean" then expect(1, visible, "boolean") end
        if bVisible ~= visible then
            bVisible = visible
            if bVisible then
                window.redraw()
            end
        end
    end

    --- Get whether this window is visible. Invisible windows will not be
    -- drawn to the screen until they are made visible again.
    --
    -- @treturn boolean Whether this window is visible.
    -- @see Window:setVisible
    -- @since 1.94.0
    function window.isVisible()
        return bVisible
    end
    --- Draw this window. This does nothing if the window is not visible.
    --
    -- @see Window:setVisible
    function window.redraw()
        if bVisible then
            redraw()
            updatePalette()
            updateCursorBlink()
            updateCursorColor()
            updateCursorPos()
        end
    end

    --- Set the current terminal's cursor to where this window's cursor is. This
    -- does nothing if the window is not visible.
    function window.restoreCursor()
        if bVisible then
            updateCursorBlink()
            updateCursorColor()
            updateCursorPos()
        end
    end

    --- Get the position of the top left corner of this window.
    --
    -- @treturn number The x position of this window.
    -- @treturn number The y position of this window.
    function window.getPosition()
        return nX, nY
    end

    --- Reposition or resize the given window.
    --
    -- This function also accepts arguments to change the size of this window.
    -- It is recommended that you fire a `term_resize` event after changing a
    -- window's, to allow programs to adjust their sizing.
    --
    -- @tparam number new_x The new x position of this window.
    -- @tparam number new_y The new y position of this window.
    -- @tparam[opt] number new_width The new width of this window.
    -- @tparam number new_height The new height of this window.
    -- @tparam[opt] term.Redirect new_parent The new redirect object this
    -- window should draw to.
    -- @changed 1.85.0 Add `new_parent` parameter.
    function window.reposition(new_x, new_y, new_width, new_height, new_parent)
        if type(new_x) ~= "number" then expect(1, new_x, "number") end
        if type(new_y) ~= "number" then expect(2, new_y, "number") end
        if new_width ~= nil or new_height ~= nil then
            expect(3, new_width, "number")
            expect(4, new_height, "number")
        end
        if new_parent ~= nil and type(new_parent) ~= "table" then expect(5, new_parent, "table") end

        nX = new_x
        nY = new_y

        if new_parent then parent = new_parent end

        if new_width and new_height then
            local tNewLines = {}
            createEmptyLines(new_width)
            local sEmptyText = sEmptySpaceLine
            local sEmptyTextColor = tEmptyColorLines[nTextColor]
            local sEmptyBackgroundColor = tEmptyColorLines[nBackgroundColor]
            for y = 1, new_height do
                if y > nHeight then
                    tNewLines[y] = { sEmptyText, sEmptyTextColor, sEmptyBackgroundColor }
                else
                    local tOldLine = tLines[y]
                    if new_width == nWidth then
                        tNewLines[y] = tOldLine
                    else
                        -- `new_width` is in cells, so trim/pad using the cell-aware helper rather than by byte count.
                        tNewLines[y] = { fitLine(tOldLine[1], tOldLine[2], tOldLine[3], new_width) }
                    end
                end
            end
            nWidth = new_width
            nHeight = new_height
            tLines = tNewLines
        end
        if bVisible then
            window.redraw()
        end
    end

    if bVisible then
        window.redraw()
    end
    return window
end
