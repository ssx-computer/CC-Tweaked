-- SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: LicenseRef-CCPL

-- A renderer for the "SONG HTML" markup language.
--
-- SONG HTML is a tiny markup language for drawing styled documents on the
-- terminal: it supports line breaks, three text sizes, foreground and
-- background colours and inline images. Tags are never closed, and any unknown
-- tag is silently ignored.

local tArgs = { ... }

-- The 3x5 bitmap font used for the "big" text sizes. Every glyph is five rows
-- of three cells, where "#" is a lit pixel and " " is an unlit one. Only
-- uppercase A-Z, the digits 0-9 and the space are covered.
local FONT = {
    [" "] = { "   ", "   ", "   ", "   ", "   " },
    ["0"] = { "###", "# #", "# #", "# #", "###" },
    ["1"] = { " # ", "## ", " # ", " # ", "###" },
    ["2"] = { "###", "  #", "###", "#  ", "###" },
    ["3"] = { "###", "  #", "###", "  #", "###" },
    ["4"] = { "# #", "# #", "###", "  #", "  #" },
    ["5"] = { "###", "#  ", "###", "  #", "###" },
    ["6"] = { "###", "#  ", "###", "# #", "###" },
    ["7"] = { "###", "  #", "  #", "  #", "  #" },
    ["8"] = { "###", "# #", "###", "# #", "###" },
    ["9"] = { "###", "# #", "###", "  #", "###" },
    ["A"] = { "###", "# #", "###", "# #", "# #" },
    ["B"] = { "## ", "# #", "## ", "# #", "## " },
    ["C"] = { "###", "#  ", "#  ", "#  ", "###" },
    ["D"] = { "## ", "# #", "# #", "# #", "## " },
    ["E"] = { "###", "#  ", "###", "#  ", "###" },
    ["F"] = { "###", "#  ", "###", "#  ", "#  " },
    ["G"] = { "###", "#  ", "# #", "# #", "###" },
    ["H"] = { "# #", "# #", "###", "# #", "# #" },
    ["I"] = { "###", " # ", " # ", " # ", "###" },
    ["J"] = { "  #", "  #", "  #", "# #", "###" },
    ["K"] = { "# #", "# #", "## ", "# #", "# #" },
    ["L"] = { "#  ", "#  ", "#  ", "#  ", "###" },
    ["M"] = { "# #", "###", "###", "# #", "# #" },
    ["N"] = { "# #", "###", "# #", "# #", "# #" },
    ["O"] = { "###", "# #", "# #", "# #", "###" },
    ["P"] = { "###", "# #", "###", "#  ", "#  " },
    ["Q"] = { "###", "# #", "# #", "###", "  #" },
    ["R"] = { "###", "# #", "###", "## ", "# #" },
    ["S"] = { "###", "#  ", "###", "  #", "###" },
    ["T"] = { "###", " # ", " # ", " # ", " # " },
    ["U"] = { "# #", "# #", "# #", "# #", "###" },
    ["V"] = { "# #", "# #", "# #", "# #", " # " },
    ["W"] = { "# #", "# #", "###", "###", "# #" },
    ["X"] = { "# #", "# #", " # ", "# #", "# #" },
    ["Y"] = { "# #", "# #", " # ", " # ", " # " },
    ["Z"] = { "###", "  #", " # ", "#  ", "###" },
}

-- Current rendering state.
local size = 1
local textColour = colours.white
local bgColour = colours.black

-- The path of the document currently being rendered, resolved in the shell.
local sPath

-- A case-insensitive lookup of every colour name, built from both the British
-- and American spellings of the colours API.
local colourNames = {}
for _, tColours in ipairs { colours, colors } do
    for k, v in pairs(tColours) do
        if type(v) == "number" then
            colourNames[k:lower()] = v
        end
    end
end

-- The default 16 colour palette, along with each colour's RGB components. Used
-- to find the closest match to an arbitrary RGB value.
local sPaletteKeys = "0123456789abcdef"
local tPalette = {}
for i = 1, #sPaletteKeys do
    local colour = colours.fromBlit(sPaletteKeys:sub(i, i))
    local r, g, b = term.getPaletteColour(colour)
    tPalette[i] = { colour = colour, r = r, g = g, b = b }
end

local function trim(sText)
    return sText:match("^%s*(.-)%s*$")
end

local function nearestColour(r, g, b)
    local best, bestDist
    for i = 1, #tPalette do
        local entry = tPalette[i]
        local dr, dg, db = r - entry.r, g - entry.g, b - entry.b
        local dist = dr * dr + dg * dg + db * db
        if bestDist == nil or dist < bestDist then
            bestDist, best = dist, entry.colour
        end
    end
    return best
end

-- Resolve a colour argument, which may be a number, an RGB hex code or a
-- colour name. Returns nil if it could not be parsed.
local function parseColour(sValue)
    if sValue:match("^%d+$") then
        return tonumber(sValue)
    end

    local sHex = sValue:match("^#?(%x%x%x%x%x%x)$")
    if sHex then
        local r = tonumber(sHex:sub(1, 2), 16) / 255
        local g = tonumber(sHex:sub(3, 4), 16) / 255
        local b = tonumber(sHex:sub(5, 6), 16) / 255
        return nearestColour(r, g, b)
    end

    return colourNames[sValue:lower()]
end

-- Extract an argument from the text following a tag name. It may be quoted (in
-- which case everything up to the closing quote is used), or a single bare
-- token.
local function parseArg(sRest)
    if sRest == "" then return nil end
    if sRest:sub(1, 1) == "\"" then
        local nClose = sRest:find("\"", 2, true)
        if nClose then return sRest:sub(2, nClose - 1) end
        return sRest:sub(2)
    end
    return sRest:match("^(%S+)")
end

-- Move the cursor to the first column of the next terminal line, scrolling if
-- we are already on the last one.
local function moveToNextLine()
    local _, y = term.getCursorPos()
    local _, h = term.getSize()
    if y < h then
        term.setCursorPos(1, y + 1)
    else
        term.setCursorPos(1, h)
        term.scroll(1)
    end
end

-- Move the cursor to the first column of the next "big" text line, which is
-- five (scaled) rows tall.
local function bigNewLine()
    local lineHeight = 5 * (size - 1)
    local _, y = term.getCursorPos()
    local _, h = term.getSize()
    local target = y + lineHeight
    if target + lineHeight - 1 > h then
        local scroll = target + lineHeight - 1 - h
        term.scroll(scroll)
        target = target - scroll
    end
    if target < 1 then target = 1 end
    term.setCursorPos(1, target)
end

local function newLine()
    if size == 1 then
        moveToNextLine()
    else
        bigNewLine()
    end
end

-- Output a run of text at size 1, wrapping at the end of each line.
local function writeText(sText)
    if sText == "" then return end
    term.setTextColour(textColour)
    term.setBackgroundColour(bgColour)

    local w = term.getSize()
    local i = 1
    while i <= #sText do
        local x = term.getCursorPos()
        if x > w then
            moveToNextLine()
            x = term.getCursorPos()
        end
        local sChunk = sText:sub(i, i + (w - x))
        term.write(sChunk)
        i = i + #sChunk
    end
end

-- Draw a single character as a "big" (size 2 or 3) glyph. Characters which are
-- not in the font fall back to normal-sized text.
local function drawBigChar(ch)
    local glyph = FONT[ch:upper()]
    if not glyph then
        writeText(ch)
        return
    end

    local cell = size - 1
    local charW, charH = 3 * cell, 5 * cell
    local w, h = term.getSize()
    local x, y = term.getCursorPos()
    if x + charW - 1 > w or y + charH - 1 > h then
        bigNewLine()
        x, y = term.getCursorPos()
    end

    for row = 0, charH - 1 do
        if y + row > h then break end
        local sRow = glyph[math.floor(row / cell) + 1]
        term.setCursorPos(x, y + row)
        for col = 0, charW - 1 do
            if sRow:sub(math.floor(col / cell) + 1, math.floor(col / cell) + 1) == "#" then
                term.setBackgroundColour(textColour)
            else
                term.setBackgroundColour(bgColour)
            end
            term.write(" ")
        end
    end
    term.setBackgroundColour(bgColour)
    term.setCursorPos(x + charW + cell, y)
end

local function outputChar(ch)
    if size == 1 then
        writeText(ch)
    else
        drawBigChar(ch)
    end
end

local function outputText(sText)
    if size == 1 then
        writeText(sText)
    else
        for i = 1, #sText do
            drawBigChar(sText:sub(i, i))
        end
    end
end

-- Draw an image at the current cursor position, then move the cursor to the
-- first column of the line just below it.
local function drawImage(sImage)
    local path = sImage
    if path:sub(1, 1) ~= "/" then
        path = fs.combine(fs.getDir(sPath), path)
    end

    local x, y = term.getCursorPos()
    local height
    if path:sub(-4) == ".nft" then
        local ok, nft = pcall(require, "cc.image.nft")
        if not ok or not nft then return end
        local image = nft.load(path)
        if not image then return end
        nft.draw(image, x, y)
        height = #image
    else
        local image = paintutils.loadImage(path)
        if not image then return end
        paintutils.drawImage(image, x, y)
        height = #image
    end

    local _, h = term.getSize()
    local target = y + height
    if target > h then
        term.setCursorPos(1, h)
        term.scroll(target - h)
        target = h
    end
    term.setCursorPos(1, target)
end

local function applyTag(name, closing, sValue)
    if name == "br" then
        newLine()
    elseif name == "size" then
        if closing or sValue == nil then
            size = 1
        else
            local n = tonumber(sValue)
            if n then
                n = math.floor(n)
                if n < 1 then n = 1 elseif n > 3 then n = 3 end
                size = n
            end
        end
    elseif name == "colour" or name == "color" then
        if closing or sValue == nil then
            textColour = colours.white
        else
            local colour = parseColour(sValue)
            if colour then textColour = colour end
        end
    elseif name == "bg" or name == "background" then
        if not closing and sValue ~= nil then
            local colour = parseColour(sValue)
            if colour then bgColour = colour end
        end
    elseif name == "image" then
        if not closing and sValue ~= nil then
            drawImage(sValue)
        end
    end
end

-- Read the tag beginning at `i` (the position of the opening "<") and apply it,
-- returning the index to continue scanning from.
local function readTag(source, i)
    local nClose = source:find(">", i + 1, true)
    if not nClose then return nil end
    local nextIndex = nClose + 1

    local sInner = trim(source:sub(i + 1, nClose - 1))
    local closing = false
    if sInner:sub(1, 1) == "/" then
        closing = true
        sInner = trim(sInner:sub(2))
    end

    local name, sRest = sInner:match("^([%a%d_%-]+)%s*(.*)$")
    if not name then return nextIndex end
    name = name:lower()
    sRest = trim(sRest)
    if sRest == "/" then sRest = "" end

    local sValue = parseArg(sRest)

    -- Allow the argument to follow the tag, e.g. "<colour>\"red\"" or "<size>3".
    if sValue == nil and not closing then
        local sNext = source:sub(nextIndex, nextIndex)
        if name == "size" then
            local sDigits = source:match("^%d+", nextIndex)
            if sDigits then
                sValue = sDigits
                nextIndex = nextIndex + #sDigits
            end
        elseif name == "colour" or name == "color" or name == "bg" or name == "background" or name == "image" then
            if sNext == "\"" then
                local nQuote = source:find("\"", nextIndex + 1, true)
                if nQuote then
                    sValue = source:sub(nextIndex + 1, nQuote - 1)
                    nextIndex = nQuote + 1
                end
            end
        end
    end

    applyTag(name, closing, sValue)

    -- A quoted string may follow a <br>, giving the text to write on the new
    -- line, e.g. `<br>"Hello, world"`.
    if name == "br" and not closing then
        if sValue ~= nil then
            outputText(sValue)
        elseif source:sub(nextIndex, nextIndex) == "\"" then
            local nQuote = source:find("\"", nextIndex + 1, true)
            if nQuote then
                outputText(source:sub(nextIndex + 1, nQuote - 1))
                nextIndex = nQuote + 1
            end
        end
    end

    return nextIndex
end

local function render(source)
    local i, n = 1, #source
    while i <= n do
        local c = source:sub(i, i)
        if c == "\\" then
            if i < n then
                outputChar(source:sub(i + 1, i + 1))
                i = i + 2
            else
                outputChar("\\")
                i = i + 1
            end
        elseif c == "<" then
            if source:sub(i, i + 3) == "<!--" then
                local nEnd = source:find("-->", i + 4, true)
                i = nEnd and (nEnd + 3) or (n + 1)
            else
                local nextIndex = readTag(source, i)
                if nextIndex then
                    i = nextIndex
                else
                    outputText(source:sub(i))
                    i = n + 1
                end
            end
        elseif c == "\n" then
            newLine()
            i = i + 1
        elseif c == "\r" then
            i = i + 1
        else
            outputChar(c)
            i = i + 1
        end
    end
end

local function printUsage()
    print("Usage: song <file>")
    print("Renders a document written in the SONG HTML markup language.")
    print("")
    print("Tags (the argument may be written either inside the tag, as <size 2>, or")
    print("straight after it, as <size>2):")
    print("  <br>                  Start a new line. May be followed by a quoted string,")
    print("                        as <br>\"Hello\", which is written on the new line")
    print("  <size N> / <size>N    Set the text size, between 1 and 3 (default 1)")
    print("  <size> or </size>     Reset the text size to 1")
    print("  <colour>\"name\"       Set the text colour")
    print("  <colour> or </colour> Reset the text colour to white")
    print("  <bg>\"name\"           Set the background colour")
    print("  <image>\"path\"        Draw an image at the cursor")
    print("  <!-- comment -->      Ignored")
    print("")
    print("Colours may be a number, an RGB hex code (#rrggbb) or a colour name.")
    print("Use a backslash to escape a character, such as \\< or \\\\.")
end

if #tArgs < 1 or tArgs[1] == "--help" or tArgs[1] == "-h" then
    printUsage()
    return
end

sPath = shell.resolve(tArgs[1])
local file = fs.open(sPath, "r")
if not file then
    printError("Cannot open " .. sPath)
    return
end
local source = file.readAll()
file.close()

-- Skip a hashbang line, as used when running a document directly.
if source:sub(1, 2) == "#!" then
    local nLine = source:find("\n", 1, true)
    source = nLine and source:sub(nLine + 1) or ""
end

render(source)
