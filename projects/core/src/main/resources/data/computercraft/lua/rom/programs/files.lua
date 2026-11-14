-- SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: MPL-2.0

-- A small file browser, intended to run inside a desktop window (but it works on any terminal).
-- Click an entry to select it, click again (or press enter) to open it. Directories are entered in place, text files
-- are opened with `edit`, and `paint`/`song` files with the matching program.

local tArgs = { ... }

-- ---------------------------------------------------------------------------------------------------------------
-- Text helpers
-- ---------------------------------------------------------------------------------------------------------------

local function cells(s)
    local n, i = 0, 1
    while i <= #s do
        local b = string.byte(s, i)
        if not b or b < 0x80 then
            n, i = n + 1, i + 1
        elseif b >= 0xF0 then
            n, i = n + 2, i + 4
        elseif b >= 0xE0 then
            n, i = n + 2, i + 3
        elseif b >= 0xC0 then
            n, i = n + 2, i + 2
        else
            n, i = n + 1, i + 1
        end
    end
    return n
end

local function fit(s, width)
    local n, i = 0, 1
    while i <= #s do
        local b = string.byte(s, i)
        local len, cellWidth
        if not b or b < 0x80 then
            len, cellWidth = 1, 1
        elseif b >= 0xF0 then
            len, cellWidth = 4, 2
        elseif b >= 0xE0 then
            len, cellWidth = 3, 2
        elseif b >= 0xC0 then
            len, cellWidth = 2, 2
        else
            len, cellWidth = 1, 1
        end
        if n + cellWidth > width then break end
        n, i = n + cellWidth, i + len
    end
    return string.sub(s, 1, i - 1), n
end

local function pad(s, width)
    s = tostring(s or "")
    if width <= 0 then return "" end
    local text, used = fit(s, width)
    if used >= width then return text end
    return text .. string.rep(" ", width - used)
end

local function now()
    if os.epoch then return os.epoch("utc") / 1000 end
    return os.clock()
end

local function formatSize(n)
    if n >= 1024 * 1024 then
        return string.format("%.1fM", n / (1024 * 1024))
    elseif n >= 1024 then
        return string.format("%.1fK", n / 1024)
    else
        return n .. "B"
    end
end

-- ---------------------------------------------------------------------------------------------------------------
-- State
-- ---------------------------------------------------------------------------------------------------------------

local w, h = term.getSize()
local sPath = shell.resolve(tArgs[1] or shell.dir())
if sPath == "" then sPath = "/" end

local bSelectFile = nil
if not fs.isDir(sPath) then
    if fs.exists(sPath) then
        bSelectFile = fs.getName(sPath)
        sPath = fs.getDir(sPath)
        if sPath == "" then sPath = "/" end
    else
        sPath = "/"
    end
end

local tPlaces = {}
local tEntries = {}
local nSelected = 1
local nTop = 1
local nPlaceSelected = 1
local sFocus = "files"
local bShowHidden = settings and settings.get("list.show_hidden") or false
local sMessage = nil
local nMessageUntil = 0
local nLastClick, nLastClickTime = nil, 0

local function message(sText)
    sMessage = tostring(sText)
    nMessageUntil = now() + 3
end

local function refreshPlaces()
    tPlaces = {
        { label = "根目录", path = "/" },
        { label = "ROM", path = "/rom" },
    }
    if disk then
        for _, sName in ipairs(peripheral.getNames()) do
            if disk.isPresent(sName) then
                local sMount = disk.getMountPath(sName)
                if sMount then
                    local sLabel = disk.getLabel(sName)
                    tPlaces[#tPlaces + 1] = {
                        label = "磁盘: " .. (sLabel or sName),
                        path = sMount,
                        disk = sName,
                    }
                end
            end
        end
    end
    nPlaceSelected = math.min(math.max(nPlaceSelected, 1), math.max(#tPlaces, 1))
end

local function refresh()
    -- The directory may have vanished (for instance, a disk was ejected).
    if not fs.isDir(sPath) then
        sPath = "/"
        nSelected, nTop = 1, 1
    end

    local sOldName = tEntries[nSelected] and tEntries[nSelected].name
    tEntries = {}

    local ok, tList = pcall(fs.list, sPath)
    if not ok or not tList then tList = {} end

    local tDirs, tFiles = {}, {}
    for _, sName in ipairs(tList) do
        if bShowHidden or sName:sub(1, 1) ~= "." then
            local sFull = fs.combine(sPath, sName)
            local ok, bDir = pcall(fs.isDir, sFull)
            if not ok then bDir = false end
            local entry = { name = sName, path = sFull, isDir = bDir }
            if bDir then
                tDirs[#tDirs + 1] = entry
            else
                tFiles[#tFiles + 1] = entry
            end
        end
    end

    table.sort(tDirs, function(a, b) return a.name:lower() < b.name:lower() end)
    table.sort(tFiles, function(a, b) return a.name:lower() < b.name:lower() end)
    for _, entry in ipairs(tDirs) do tEntries[#tEntries + 1] = entry end
    for _, entry in ipairs(tFiles) do tEntries[#tEntries + 1] = entry end

    local nNew = nil
    if sOldName then
        for i, entry in ipairs(tEntries) do
            if entry.name == sOldName then nNew = i break end
        end
    end
    nSelected = nNew or math.min(nSelected, math.max(#tEntries, 1))
    nTop = 1
end

local function layout()
    local nSidebar = w >= 48 and math.min(20, math.floor(w / 3)) or 0
    local nPaneX = nSidebar > 0 and nSidebar + 2 or 1
    return nSidebar, nPaneX, math.max(w - nPaneX + 1, 1)
end

local function bodyRows()
    return math.max(h - 2, 1)
end

local function setPath(sNewPath)
    if not sNewPath or not fs.isDir(sNewPath) then return end
    sPath = sNewPath == "" and "/" or sNewPath
    nSelected, nTop = 1, 1
    bSelectFile = nil
    nLastClick = nil
    refresh()
end

local function parentPath()
    if sPath == "/" or sPath == "" then return nil end
    local sParent = fs.getDir(sPath)
    if sParent == "" then sParent = "/" end
    return sParent
end

local function diskForPath(sFile)
    for _, place in ipairs(tPlaces) do
        if place.disk and (sFile == place.path or sFile:sub(1, #place.path + 1) == place.path .. "/") then
            return place.disk
        end
    end
    return nil
end

-- ---------------------------------------------------------------------------------------------------------------
-- Drawing
-- ---------------------------------------------------------------------------------------------------------------

local function fillRow(x, y, width, text, fg, bg)
    if y < 1 or y > h or width <= 0 then return end
    term.setCursorPos(x, y)
    term.setBackgroundColour(bg)
    term.setTextColour(fg)
    term.write(pad(text, width))
end

local function draw()
    w, h = term.getSize()

    local nSidebar, nPaneX, nPaneWidth = layout()
    local nRows = bodyRows()

    if nSelected < nTop then nTop = nSelected end
    if nSelected > nTop + nRows - 1 then nTop = nSelected - nRows + 1 end
    if nTop < 1 then nTop = 1 end

    term.setBackgroundColour(colours.black)
    term.setTextColour(colours.white)
    term.clear()

    -- The current path, across the top.
    fillRow(1, 1, w, " " .. fit(sPath, w - 1), colours.white, colours.lightBlue)

    -- Locations (root, rom, and any mounted disks).
    if nSidebar > 0 then
        for i, place in ipairs(tPlaces) do
            local y = i + 1
            if y > h - 1 then break end
            local bSelected = sFocus == "places" and i == nPlaceSelected
            fillRow(1, y, nSidebar, " " .. place.label, bSelected and colours.black or colours.lightGrey,
                bSelected and colours.lightBlue or colours.grey)
        end
        for y = 2, h - 1 do
            fillRow(nSidebar + 1, y, 1, "|", colours.grey, colours.black)
        end
    end

    -- The contents of the current directory.
    for i = 1, nRows do
        local index = nTop + i - 1
        local entry = tEntries[index]
        if not entry then break end

        local y = i + 1
        local bSelected = sFocus == "files" and index == nSelected
        local bg = bSelected and colours.lightBlue or colours.black
        local fg = bSelected and colours.black or (entry.isDir and colours.lime or colours.white)
        local sLabel = " " .. entry.name .. (entry.isDir and "/" or "")
        fillRow(nPaneX, y, nPaneWidth, sLabel, fg, bg)

        if nPaneWidth >= 12 then
            local sSize
            if entry.isDir then
                sSize = "<DIR>"
            else
                local ok, nSize = pcall(fs.getSize, entry.path)
                sSize = ok and formatSize(nSize) or ""
            end
            if sSize ~= "" and nPaneWidth - cells(sSize) - 2 > 0 then
                term.setCursorPos(nPaneX + nPaneWidth - cells(sSize), y)
                term.setBackgroundColour(bg)
                term.setTextColour(fg)
                term.write(sSize)
            end
        end
    end

    -- Status and key hints.
    local sStatus
    if sMessage and now() < nMessageUntil then
        sStatus = sMessage
    elseif sFocus == "places" then
        sStatus = #tPlaces .. " 个位置"
    else
        sStatus = #tEntries .. " 项"
    end
    fillRow(1, h, w, " " .. sStatus, colours.white, colours.grey)

    local sHints
    if sFocus == "places" then
        sHints = " Enter 打开  E 弹出  Tab 文件  Q 退出 "
    else
        sHints = " Enter 打开  Backspace 上级  Tab 位置  R 刷新  Q 退出 "
    end
    if w >= 30 and cells(sHints) < w then
        term.setCursorPos(w - cells(sHints) + 1, h)
        term.setBackgroundColour(colours.grey)
        term.setTextColour(colours.lightGrey)
        term.write(sHints)
    end

    term.setBackgroundColour(colours.black)
    term.setTextColour(colours.white)
end

-- ---------------------------------------------------------------------------------------------------------------
-- Actions
-- ---------------------------------------------------------------------------------------------------------------

local function openFile(sFile)
    local sLower = sFile:lower()
    if sLower:sub(-4) == ".nfp" then
        shell.execute("paint", sFile)
    elseif sLower:sub(-5) == ".song" then
        shell.execute("song", sFile)
    else
        shell.execute("edit", sFile)
    end
end

local function openSelected()
    if sFocus == "places" then
        local place = tPlaces[nPlaceSelected]
        if place then
            setPath(place.path)
            sFocus = "files"
        end
        return
    end

    local entry = tEntries[nSelected]
    if not entry then return end
    if entry.isDir then
        setPath(entry.path)
    else
        openFile(entry.path)
        refresh()
        draw()
    end
end

local function goParent()
    local sParent = parentPath()
    if sParent then setPath(sParent) end
end

local function eject()
    local sDisk
    if sFocus == "places" then
        local place = tPlaces[nPlaceSelected]
        sDisk = place and place.disk
    else
        sDisk = diskForPath(sPath)
    end

    if sDisk then
        disk.eject(sDisk)
        refreshPlaces()
        refresh()
    else
        message("这里没有可弹出的磁盘")
    end
end

local function selectEntry(index)
    if index < 1 or index > #tEntries then return end
    nSelected = index
end

-- ---------------------------------------------------------------------------------------------------------------
-- Events
-- ---------------------------------------------------------------------------------------------------------------

local function click(x, y, button)
    local nSidebar, nPaneX = layout()

    if button == 2 then
        goParent()
        return
    end

    if y == 1 or y == h then return end

    if nSidebar > 0 and x <= nSidebar then
        local index = y - 1
        if tPlaces[index] then
            sFocus = "places"
            nPlaceSelected = index
            setPath(tPlaces[index].path)
        end
        return
    end

    if x < nPaneX then return end
    sFocus = "files"

    local index = nTop + (y - 2)
    if not tEntries[index] then return end

    local t = now()
    local bDouble = nLastClick == index and t - nLastClickTime < 0.4
    nLastClick, nLastClickTime = index, t

    selectEntry(index)
    if bDouble then openSelected() end
end

local function handleKey(key)
    if key == keys.q then return false end

    if sFocus == "places" then
        if key == keys.up then
            nPlaceSelected = math.max(nPlaceSelected - 1, 1)
        elseif key == keys.down then
            nPlaceSelected = math.min(nPlaceSelected + 1, math.max(#tPlaces, 1))
        elseif key == keys.enter or key == keys.numPadEnter or key == keys.right then
            openSelected()
        elseif key == keys.left or key == keys.backspace or key == keys.tab then
            sFocus = "files"
        elseif key == keys.e then
            eject()
        elseif key == keys.r then
            refreshPlaces()
            refresh()
        elseif key == keys.h then
            bShowHidden = not bShowHidden
            refresh()
        end
    else
        local nRows = bodyRows()
        if key == keys.up then
            nSelected = math.max(nSelected - 1, 1)
        elseif key == keys.down then
            nSelected = math.min(nSelected + 1, math.max(#tEntries, 1))
        elseif key == keys.pageUp then
            nSelected = math.max(nSelected - nRows, 1)
        elseif key == keys.pageDown then
            nSelected = math.min(nSelected + nRows, math.max(#tEntries, 1))
        elseif key == keys.home then
            nSelected = 1
        elseif key == keys["end"] then
            nSelected = math.max(#tEntries, 1)
        elseif key == keys.enter or key == keys.numPadEnter or key == keys.right then
            openSelected()
        elseif key == keys.backspace or key == keys.left then
            goParent()
        elseif key == keys.tab then
            if layout() > 0 then sFocus = "places" end
        elseif key == keys.r then
            refresh()
        elseif key == keys.e then
            eject()
        elseif key == keys.h then
            bShowHidden = not bShowHidden
            refresh()
        end
    end

    draw()
    return true
end

local function handleScroll(direction)
    if sFocus == "places" then return end
    nSelected = math.min(math.max(nSelected + direction, 1), math.max(#tEntries, 1))
    draw()
end

-- ---------------------------------------------------------------------------------------------------------------
-- Main loop
-- ---------------------------------------------------------------------------------------------------------------

refreshPlaces()
refresh()
if bSelectFile then
    for i, entry in ipairs(tEntries) do
        if entry.name == bSelectFile then nSelected = i break end
    end
end
draw()

while true do
    local event, p1, p2, p3 = os.pullEventRaw()

    if event == "key" then
        if not handleKey(p1) then break end
    elseif event == "mouse_click" then
        click(p2, p3, p1)
        draw()
    elseif event == "mouse_scroll" then
        handleScroll(p1)
    elseif event == "term_resize" then
        draw()
    elseif event == "disk" or event == "disk_eject" then
        refreshPlaces()
        refresh()
        draw()
    elseif event == "terminate" then
        break
    end
end

term.setBackgroundColour(colours.black)
term.setTextColour(colours.white)
term.clear()
term.setCursorPos(1, 1)
