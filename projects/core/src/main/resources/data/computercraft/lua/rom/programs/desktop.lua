-- SPDX-FileCopyrightText: 2026 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: MPL-2.0

-- A desktop environment: a wallpaper with icons, a taskbar, and overlapping windows which you can drag around.
-- Each window runs a program, so several can be open at once.
--
-- Everything is drawn with the `window` API, so this works on a computer's screen, a monitor, or anything else which
-- can be redirected to.

local tArgs = { ... }

local STARTUP_DIR = "/startup"
local STARTUP_FILE = STARTUP_DIR .. "/desktop.lua"

-- ---------------------------------------------------------------------------------------------------------------
-- Configuration
-- ---------------------------------------------------------------------------------------------------------------

local tWallpapers = {
    { id = "stars", name = "星空" },
    { id = "grid", name = "网格" },
    { id = "ocean", name = "深海" },
    { id = "plain", name = "纯色" },
}

local tWallpaperById = {}
for _, tTheme in ipairs(tWallpapers) do
    tWallpaperById[tTheme.id] = tTheme
end

local function wallpaperId()
    local sId = settings and settings.get("desktop.wallpaper") or nil
    if type(sId) ~= "string" or not tWallpaperById[sId] then return "stars" end
    return sId
end

-- ---------------------------------------------------------------------------------------------------------------
-- Starting the desktop at boot
-- ---------------------------------------------------------------------------------------------------------------

local function startupInstalled()
    return fs.exists(STARTUP_FILE)
end

local function installStartup()
    if fs.exists(STARTUP_DIR) and not fs.isDir(STARTUP_DIR) then
        return false, "/startup 已经是一个文件，无法创建启动目录"
    end
    if not fs.isDir(STARTUP_DIR) then
        local ok = fs.makeDir(STARTUP_DIR)
        if not ok then return false, "无法创建 /startup" end
    end

    local file = fs.open(STARTUP_FILE, "w")
    if not file then return false, "无法写入 " .. STARTUP_FILE end
    file.write("-- 由 desktop 安装：开机时自动启动桌面\nshell.run(\"desktop\")\n")
    file.close()

    if settings then
        settings.set("shell.allow_startup", true)
        settings.save()
    end
    return true
end

local function uninstallStartup()
    if fs.exists(STARTUP_FILE) then fs.delete(STARTUP_FILE) end
    if fs.isDir(STARTUP_DIR) and #fs.list(STARTUP_DIR) == 0 then fs.delete(STARTUP_DIR) end
end

local function printUsage()
    local sName = arg and arg[0] or "desktop"
    print("用法: " .. sName .. " [选项]")
    print("")
    print("启动桌面：带壁纸、图标、任务栏和可以拖动的窗口。")
    print("")
    print("选项:")
    print("  --install          开机时自动启动桌面")
    print("  --uninstall        取消开机自动启动")
    print("  --status           查看桌面设置")
    print("  --wallpaper <名称>  设置壁纸 (stars, grid, ocean, plain)")
    print("  --help             显示本帮助")
end

local sOption = tArgs[1]
if sOption == "--help" or sOption == "-h" then
    printUsage()
    return
elseif sOption == "--install" then
    local ok, err = installStartup()
    if ok then
        print("已开启开机自动启动（" .. STARTUP_FILE .. "）")
    else
        printError(err)
    end
    return
elseif sOption == "--uninstall" then
    uninstallStartup()
    print("已关闭开机自动启动")
    return
elseif sOption == "--status" then
    print("壁纸: " .. wallpaperId() .. " (" .. tWallpaperById[wallpaperId()].name .. ")")
    print("开机自动启动: " .. (startupInstalled() and "已开启" or "已关闭"))
    return
elseif sOption == "--wallpaper" then
    local sId = tArgs[2]
    if sId == nil then
        print("当前壁纸: " .. wallpaperId())
        print("可选: stars, grid, ocean, plain")
        return
    end
    if not tWallpaperById[sId] then
        printError("未知壁纸: " .. tostring(sId))
        print("可选: stars, grid, ocean, plain")
        return
    end
    settings.set("desktop.wallpaper", sId)
    settings.save()
    print("壁纸已设为 " .. sId)
    return
elseif sOption ~= nil then
    printUsage()
    return
end

-- ---------------------------------------------------------------------------------------------------------------
-- Text helpers
-- ---------------------------------------------------------------------------------------------------------------

local parentTerm = term.current()
local w, h = parentTerm.getSize()

-- Declared up here as they are read while drawing, before their owning sections.
local nFocused = 0
local menu = nil

-- We measure text in cells rather than bytes: a Chinese character is three bytes but two cells, and every column in
-- the interface is laid out in cells.
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

-- The longest prefix of `s` which fits in `width` cells, and the number of cells it used.
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
    local text, used = fit(s, width)
    if used >= width then return text end
    return text .. string.rep(" ", width - used)
end

-- Centre `s` in `width` cells.
local function centre(s, width)
    s = tostring(s or "")
    local text, used = fit(s, width)
    if used >= width then return text end
    local left = math.floor((width - used) / 2)
    return string.rep(" ", left) .. text .. string.rep(" ", width - used - left)
end

-- Remove the final UTF-8 character from a string.
local function popChar(s)
    if #s == 0 then return s end
    local i = #s
    while i > 1 do
        local b = string.byte(s, i)
        if b < 0x80 or b >= 0xC0 then break end
        i = i - 1
    end
    return string.sub(s, 1, i - 1)
end

-- Draw text directly onto the desktop, behind the windows.
local function paint(x, y, text, fg, bg)
    if y < 1 or y > h or x > w or x < 1 then return end
    parentTerm.setCursorPos(x, y)
    parentTerm.setBackgroundColour(bg or colours.blue)
    parentTerm.setTextColour(fg or colours.white)
    parentTerm.write((fit(text, w - x + 1)))
end

-- Run a function with `term` pointing at a window, then put it back.
local function withWindow(win, fn, ...)
    local previous = term.current()
    term.redirect(win)
    local ok, err = pcall(fn, ...)
    term.redirect(previous)
    return ok, err
end

-- ---------------------------------------------------------------------------------------------------------------
-- Toasts
-- ---------------------------------------------------------------------------------------------------------------

local toast = nil

local function now()
    if os.epoch then return os.epoch("utc") / 1000 end
    return os.clock()
end

local function showToast(sText)
    toast = { text = tostring(sText), expires = now() + 4 }
end

-- ---------------------------------------------------------------------------------------------------------------
-- Wallpaper
-- ---------------------------------------------------------------------------------------------------------------

local wallpaper = {}

local function newWallpaperRows()
    local tRows = {}
    for y = 1, h do
        local tRow = { text = {}, fg = {}, bg = {} }
        for x = 1, w do
            tRow.text[x], tRow.fg[x], tRow.bg[x] = " ", "0", "f"
        end
        tRows[y] = tRow
    end
    return tRows
end

local function overlayText(tRows, x, y, sText, colour)
    for i = 1, #sText do
        local cx = x + i - 1
        if cx >= 1 and cx <= w and y >= 1 and y <= h then
            tRows[y].text[cx] = string.sub(sText, i, i)
            tRows[y].fg[cx] = colours.toBlit(colour)
        end
    end
end

local function buildWallpaper()
    local tRows = newWallpaperRows()
    local sId = wallpaperId()
    local sBlack = colours.toBlit(colours.black)
    local sBlue = colours.toBlit(colours.blue)

    if sId == "grid" then
        for y = 1, h do
            for x = 1, w do
                tRows[y].bg[x] = sBlack
                if y % 4 == 1 then
                    tRows[y].text[x], tRows[y].fg[x] = "-", colours.toBlit(colours.grey)
                end
                if x % 8 == 1 then
                    tRows[y].text[x] = tRows[y].text[x] == "-" and "+" or "|"
                    tRows[y].fg[x] = colours.toBlit(colours.grey)
                end
            end
        end
        overlayText(tRows, math.floor((w - 10) / 2) + 1, math.floor((h - 1) / 2), "CC: Tweaked", colours.blue)

    elseif sId == "ocean" then
        local sCyan = colours.toBlit(colours.cyan)
        for y = 1, h do
            for x = 1, w do
                tRows[y].bg[x] = ((x + y * 2) % 3 == 0) and sBlue or sBlack
                if y % 4 == 0 and (x + y) % 7 < 2 then
                    tRows[y].text[x], tRows[y].fg[x] = "~", sCyan
                end
            end
        end

    elseif sId == "plain" then
        for y = 1, h do
            for x = 1, w do
                tRows[y].bg[x] = sBlue
            end
        end

    else
        -- A dithered blue-to-black gradient, with stars scattered over it.
        local sLightGrey = colours.toBlit(colours.lightGrey)
        local sWhite = colours.toBlit(colours.white)
        for y = 1, h do
            local nDarkCells = 2 + math.floor(y / h * 6)
            for x = 1, w do
                tRows[y].bg[x] = ((x * 3 + y * 5) % 8 < nDarkCells) and sBlack or sBlue
            end
        end

        local seed = 20251114
        local function random()
            seed = (seed * 1103515245 + 12345) % 2147483648
            return seed / 2147483648
        end
        for _ = 1, math.floor(w * h / 14) do
            local x = math.floor(random() * w) + 1
            local y = math.floor(random() * (h - 1)) + 1
            tRows[y].text[x] = random() < 0.2 and "*" or "."
            tRows[y].fg[x] = random() < 0.3 and sWhite or sLightGrey
        end

        if w >= 12 and h >= 8 then
            local tMoon = { "  .--.  ", " (    ) ", "  '--'  " }
            for i, sLine in ipairs(tMoon) do
                overlayText(tRows, w - 8, 1 + i, sLine, colours.lightGrey)
            end
        end
    end

    wallpaper = {}
    for y = 1, h do
        wallpaper[y] = {
            text = table.concat(tRows[y].text),
            fg = table.concat(tRows[y].fg),
            bg = table.concat(tRows[y].bg),
        }
    end
end

local function drawWallpaper()
    for y = 1, h do
        local tRow = wallpaper[y]
        if tRow then
            parentTerm.setCursorPos(1, y)
            parentTerm.blit(tRow.text, tRow.fg, tRow.bg)
        end
    end
end

-- ---------------------------------------------------------------------------------------------------------------
-- The icons on the desktop
-- ---------------------------------------------------------------------------------------------------------------

local baseIcons = {
    { name = "终端", program = "shell", colour = colours.green, glyph = ">_" },
    { name = "文件", program = "files", colour = colours.orange, glyph = "F" },
    { name = "编辑", program = "edit", colour = colours.brown, glyph = "E" },
    { name = "Lua", program = "lua", colour = colours.cyan, glyph = "L" },
    { name = "帮助", program = "help", colour = colours.purple, glyph = "?" },
    { name = "设置", action = "wallpaper", colour = colours.grey, glyph = "S" },
}

local ICON_W, ICON_H = 12, 4
local ICON_X, ICON_Y = 2, 2
local TASKBAR_Y = h

local tIcons = {}
local nSelectedIcon = 1

local function refreshIcons()
    tIcons = {}
    for _, tIcon in ipairs(baseIcons) do
        tIcons[#tIcons + 1] = tIcon
    end

    if disk then
        for _, sName in ipairs(peripheral.getNames()) do
            if disk.isPresent(sName) and disk.hasData(sName) then
                local sPath = disk.getMountPath(sName)
                if sPath then
                    local sLabel = disk.getLabel(sName) or "磁盘"
                    tIcons[#tIcons + 1] = {
                        name = sLabel, program = "files", args = { sPath },
                        colour = colours.red, glyph = "D", disk = sName,
                    }
                end
            end
        end
    end

    if nSelectedIcon > #tIcons then nSelectedIcon = math.max(#tIcons, 1) end
end

local function iconColumns()
    -- Each icon is three rows tall, and the last one must fit above the taskbar.
    return math.max(math.floor((h - 5) / ICON_H) + 1, 1)
end

local function iconRect(index)
    local perColumn = iconColumns()
    local col = math.floor((index - 1) / perColumn)
    local row = (index - 1) % perColumn
    return ICON_X + col * (ICON_W + 2), ICON_Y + row * ICON_H
end

local function drawIcons()
    local bHighlight = nFocused == 0
    for i, tIcon in ipairs(tIcons) do
        local x, y = iconRect(i)
        if x + ICON_W - 1 > w or y + 2 > h - 1 then break end

        local bSelected = bHighlight and i == nSelectedIcon
        paint(x, y, string.rep(" ", ICON_W), colours.white, tIcon.colour)
        paint(x, y + 1, centre(tIcon.glyph, ICON_W), colours.white, tIcon.colour)
        paint(x, y + 2, centre(tIcon.name, ICON_W), colours.white,
            bSelected and colours.lightBlue or colours.black)
    end
end

local function iconAt(x, y)
    for i in ipairs(tIcons) do
        local ix, iy = iconRect(i)
        if ix + ICON_W - 1 <= w and iy + 2 <= h - 1 and
            x >= ix and x < ix + ICON_W and y >= iy and y <= iy + 2 then
            return i
        end
    end
    return nil
end

-- ---------------------------------------------------------------------------------------------------------------
-- Windows
-- ---------------------------------------------------------------------------------------------------------------

local MIN_W, MIN_H = 10, 4

local tWindows = {}
local nNextId = 1
local dragging = nil
local resizing = nil
local bRunning = true

local function windowById(id)
    for _, item in ipairs(tWindows) do
        if item.id == id then return item end
    end
    return nil
end

local function focusedWindow()
    if nFocused == 0 then return nil end
    return windowById(nFocused)
end

local function topmostVisible()
    for i = #tWindows, 1, -1 do
        if tWindows[i].visible then return tWindows[i] end
    end
    return nil
end

local function raise(item)
    for i, other in ipairs(tWindows) do
        if other == item then
            table.remove(tWindows, i)
            break
        end
    end
    tWindows[#tWindows + 1] = item
    nFocused = item.id
end

local function drawError(item)
    withWindow(item.window, function()
        term.setBackgroundColour(colours.black)
        term.setTextColour(colours.red)
        term.clear()
        term.setCursorPos(1, 1)
        term.write(fit("程序出错: " .. tostring(item.error or "未知错误"), math.max(item.width - 2, 1)))
        term.setCursorPos(1, 2)
        term.setTextColour(colours.lightGrey)
        term.write(fit("点击 [x] 关闭窗口", math.max(item.width - 2, 1)))
    end)
end

local function resumeWindow(item, ...)
    if not item.co or coroutine.status(item.co) == "dead" then return end
    local args = table.pack(...)

    -- Programs may redirect `term` (to a monitor, for instance), so remember the terminal they last used and restore it
    -- before the next resume. This mirrors how multishell handles its processes.
    local previous = term.current()
    term.redirect(item.terminal or item.window)
    local ok, err = coroutine.resume(item.co, table.unpack(args, 1, args.n))
    item.terminal = term.current()
    term.redirect(previous)

    if not ok then
        item.error = tostring(err)
        item.co = nil
        drawError(item)
    end
end

local function terminateWindow(item)
    if not item.co or coroutine.status(item.co) == "dead" then return end

    -- A program may be running another one (the file browser opens an editor, for instance). The first "terminate"
    -- stops the inner program, so keep sending it until the whole coroutine has unwound.
    local previous = term.current()
    term.redirect(item.terminal or item.window)
    for _ = 1, 20 do
        if not item.co or coroutine.status(item.co) == "dead" then break end
        if not pcall(coroutine.resume, item.co, "terminate") then break end
    end
    term.redirect(previous)
end

local function close(item)
    item.visible = false
    if item.windowVisible then
        item.window.setVisible(false)
        item.windowVisible = false
    end
    -- Give the program a chance to shut down cleanly, the same way closing a tab does.
    terminateWindow(item)
    for i, other in ipairs(tWindows) do
        if other == item then
            table.remove(tWindows, i)
            break
        end
    end
    if nFocused == item.id then
        local tTop = topmostVisible()
        nFocused = tTop and tTop.id or 0
    end
end

local function toggleMinimize(item)
    if item.visible then
        item.visible = false
        if nFocused == item.id then
            local tTop = topmostVisible()
            nFocused = tTop and tTop.id or 0
        end
    else
        item.visible = true
        raise(item)
        if item.needResize then
            item.needResize = false
            resumeWindow(item, "term_resize")
        end
    end
end

local function nextWindowRect()
    local width = math.min(52, math.max(math.floor(w * 0.7), MIN_W))
    local height = math.min(16, math.max(math.floor((h - 1) * 0.7), MIN_H))
    width = math.min(width, w)
    height = math.min(height, h - 1)

    local offset = (#tWindows % 6) * 2
    local x = math.max(math.floor((w - width) / 2) + offset, 1)
    local y = math.max(math.floor(((h - 1) - height) / 2) + offset, 1)

    x = math.min(x, math.max(w - width + 1, 1))
    y = math.min(y, math.max((h - 1) - height + 1, 1))
    return x, y, width, height
end

local function resolveProgram(sProgram)
    if not shell then return nil end
    local ok, sPath = pcall(shell.resolveProgram, sProgram)
    if ok then return sPath end
    return nil
end

local function openProgram(sTitle, sProgram, tProgramArgs)
    local sPath = resolveProgram(sProgram)
    if not sPath then
        showToast("找不到程序: " .. tostring(sProgram))
        return nil
    end

    local x, y, width, height = nextWindowRect()
    local win
    local ok = pcall(function()
        win = window.create(parentTerm, x + 1, y + 1, math.max(width - 2, 1), math.max(height - 2, 1), false)
    end)
    if not ok then
        showToast("无法创建窗口")
        return nil
    end

    local item = {
        id = nNextId,
        title = sTitle or sProgram,
        program = sProgram,
        x = x, y = y, width = width, height = height,
        window = win,
        visible = true,
        windowVisible = false,
    }
    nNextId = nNextId + 1
    tWindows[#tWindows + 1] = item
    nFocused = item.id

    local tArgsForProgram = tProgramArgs or {}
    item.co = coroutine.create(function()
        local ran, result = pcall(shell.execute, sPath, table.unpack(tArgsForProgram))
        if not ran then
            item.error = tostring(result)
            drawError(item)
        else
            -- The program has exited, so this window can go away.
            item.finished = true
        end
    end)

    -- Give the program a chance to draw its first frame.
    item.terminal = win
    resumeWindow(item)
    if not item.error and coroutine.status(item.co) == "dead" and not item.finished then
        item.error = "程序意外退出"
        item.co = nil
        drawError(item)
    end
    return item
end

-- Close any window whose program has exited.
local function reap()
    local bRemoved = false
    for i = #tWindows, 1, -1 do
        local item = tWindows[i]
        if item.finished then
            item.window.setVisible(false)
            item.windowVisible = false
            table.remove(tWindows, i)
            bRemoved = true
            if nFocused == item.id then
                local tTop = topmostVisible()
                nFocused = tTop and tTop.id or 0
            end
        end
    end
    return bRemoved
end

local function resizeWindow(item, x, y, width, height)
    width = math.min(math.max(width, MIN_W), w)
    height = math.min(math.max(height, MIN_H), h - 1)
    x = math.max(math.min(x, w - width + 1), 1)
    y = math.max(math.min(y, (h - 1) - height + 1), 1)

    if x == item.x and y == item.y and width == item.width and height == item.height then return false end
    item.x, item.y, item.width, item.height = x, y, width, height
    item.window.reposition(x + 1, y + 1, math.max(width - 2, 1), math.max(height - 2, 1), parentTerm)
    item.needResize = true
    return true
end

local function toggleMaximize(item)
    if item.maximized then
        item.maximized = false
        local r = item.prevRect
        if r then resizeWindow(item, r.x, r.y, r.width, r.height) end
    else
        item.prevRect = { x = item.x, y = item.y, width = item.width, height = item.height }
        item.maximized = true
        resizeWindow(item, 1, 1, w, h - 1)
    end
    if item.visible and item.needResize then
        item.needResize = false
        resumeWindow(item, "term_resize")
    end
    raise(item)
end

-- ---------------------------------------------------------------------------------------------------------------
-- Taskbar
-- ---------------------------------------------------------------------------------------------------------------

local MENU_LABEL = " 菜单 "

local function clockText()
    if textutils and textutils.formatTime then
        local ok, formatted = pcall(textutils.formatTime, os.time(), true)
        if ok and formatted then return formatted end
    end
    return ""
end

local function taskbarButtons()
    local buttons = {}
    local x = 1
    local clock = clockText()
    local limit = w - (clock ~= "" and cells(clock) or 0) - 1

    buttons[#buttons + 1] = { kind = "menu", x = x, width = cells(MENU_LABEL) }
    x = x + cells(MENU_LABEL) + 1

    for _, item in ipairs(tWindows) do
        local label = " " .. fit(item.title, 12) .. " "
        local width = cells(label)
        if x + width > limit then break end
        buttons[#buttons + 1] = { kind = "window", id = item.id, x = x, width = width, label = label }
        x = x + width
    end

    return buttons
end

local function drawTaskbar()
    parentTerm.setCursorPos(1, TASKBAR_Y)
    parentTerm.setBackgroundColour(colours.grey)
    parentTerm.setTextColour(colours.white)
    parentTerm.write(string.rep(" ", w))

    for _, button in ipairs(taskbarButtons()) do
        parentTerm.setCursorPos(button.x, TASKBAR_Y)
        if button.kind == "menu" then
            parentTerm.setBackgroundColour(menu and menu.kind == "start" and colours.lightBlue or colours.green)
            parentTerm.setTextColour(colours.white)
            parentTerm.write(fit(MENU_LABEL, w))
        else
            local item = windowById(button.id)
            local bActive = item and item.id == nFocused and item.visible
            parentTerm.setBackgroundColour(bActive and colours.lightBlue or colours.grey)
            parentTerm.setTextColour(item and item.visible and colours.white or colours.lightGrey)
            parentTerm.write(button.label)
        end
    end

    local clock = clockText()
    if clock ~= "" then
        parentTerm.setCursorPos(math.max(w - cells(clock) + 1, 1), TASKBAR_Y)
        parentTerm.setBackgroundColour(colours.grey)
        parentTerm.setTextColour(colours.lightGrey)
        parentTerm.write(clock)
    end

    parentTerm.setBackgroundColour(colours.black)
    parentTerm.setTextColour(colours.white)
end

-- ---------------------------------------------------------------------------------------------------------------
-- Menus
-- ---------------------------------------------------------------------------------------------------------------

local function closeMenu()
    menu = nil
end

local function menuExtras()
    local n = 0
    if menu.header then n = n + 1 end
    if menu.filterable then n = n + 1 end
    return n
end

local function layoutMenu()
    if not menu then return end

    local width = 12
    for _, item in ipairs(menu.items) do
        if item.label and not item.separator then
            local itemWidth = cells(item.label) + 6 + (item.checked and 4 or 0)
            if itemWidth > width then width = itemWidth end
        end
    end

    menu.width = math.min(width, math.max(w - 2, 4))
    local extras = menuExtras()
    menu.rows = math.min(#menu.items, math.max(h - 1 - extras, 1))
    menu.height = extras + menu.rows
    menu.selected = math.min(math.max(menu.selected, 1), math.max(#menu.items, 1))
    menu.top = math.max(menu.top, 1)

    local x = menu.anchorX
    local y = menu.anchorY
    if x + menu.width - 1 > w then x = w - menu.width + 1 end
    if y + menu.height - 1 > h - 1 then y = h - 1 - menu.height + 1 end
    menu.x = math.max(x, 1)
    menu.y = math.max(y, 1)
end

local function openMenu(items, x, y, options)
    options = options or {}
    menu = {
        kind = options.kind,
        items = items,
        all = options.all,
        header = options.header,
        filterable = options.filterable,
        filter = "",
        anchorX = x,
        anchorY = y,
        top = 1,
        selected = 1,
    }
    layoutMenu()
end

local function menuItemAt(x, y)
    if not menu then return nil end
    local top = menu.y + menuExtras()
    if x < menu.x or x >= menu.x + menu.width then return nil end
    local row = y - top + 1
    if row < 1 or row > menu.rows then return nil end
    local index = menu.top + row - 1
    if index > #menu.items then return nil end
    return index
end

local function menuMove(delta)
    if not menu or #menu.items == 0 then return end
    local index = menu.selected
    for _ = 1, #menu.items do
        index = index + delta
        if index < 1 then
            index = #menu.items
        elseif index > #menu.items then
            index = 1
        end
        local item = menu.items[index]
        if item and not item.separator and not item.disabled then
            menu.selected = index
            break
        end
    end

    if menu.selected < menu.top then menu.top = menu.selected end
    if menu.selected > menu.top + menu.rows - 1 then menu.top = menu.selected - menu.rows + 1 end
    if menu.top < 1 then menu.top = 1 end
end

local function chooseMenu()
    if not menu then return end
    local item = menu.items[menu.selected]
    if not item or item.separator or item.disabled then return end
    local action = item.action
    menu = nil
    if action then action() end
end

local function rebuildStartMenu()
    if not menu or not menu.filterable then return end
    local sFilter = menu.filter:lower()
    local items = {}
    for _, item in ipairs(menu.all) do
        if sFilter == "" or string.find(item.search, sFilter, 1, true) then
            items[#items + 1] = item
        end
    end
    if #items == 0 then
        items[1] = { label = "无匹配程序", disabled = true }
    end
    menu.items = items
    menu.selected = 1
    menu.top = 1
    layoutMenu()
end

local function drawMenu()
    local x, y, width, height = menu.x, menu.y, menu.width, menu.height

    -- A simple drop shadow, then the menu itself.
    for row = y + 1, math.min(y + height, h - 1) do
        paint(x + 1, row, string.rep(" ", width), colours.black, colours.black)
    end
    for row = y, y + height - 1 do
        paint(x, row, string.rep(" ", width), colours.white, colours.grey)
    end

    local row = y
    if menu.header then
        paint(x, row, pad(" " .. menu.header, width), colours.white, colours.lightBlue)
        row = row + 1
    end
    if menu.filterable then
        local sFilter = menu.filter ~= "" and menu.filter or "输入关键字"
        paint(x, row, pad(" 搜索: " .. sFilter, width), menu.filter ~= "" and colours.black or colours.lightGrey,
            colours.white)
        row = row + 1
    end

    for i = 1, menu.rows do
        local index = menu.top + i - 1
        local item = menu.items[index]
        if not item then break end

        if item.separator then
            paint(x, row, string.rep("-", width), colours.lightGrey, colours.grey)
        else
            local bSelected = index == menu.selected
            local fg = item.disabled and colours.grey or colours.black
            local bg = bSelected and colours.lightBlue or colours.white
            local label = (item.checked and "[*] " or "") .. item.label
            paint(x, row, pad(" " .. label, width - 1) .. " ", fg, bg)
        end
        row = row + 1
    end
end

-- ---------------------------------------------------------------------------------------------------------------
-- Painting the desktop
-- ---------------------------------------------------------------------------------------------------------------

local function drawWindowFrame(item)
    local x, y, width, height = item.x, item.y, item.width, item.height
    local bActive = item.id == nFocused
    local nButtons = 3
    if width >= 14 then
        nButtons = 10
    elseif width >= 10 then
        nButtons = 6
    end

    local sHeading = pad(
        " " .. fit(item.title, math.max(width - 3 - nButtons - 1, 1)), math.max(width - 3 - nButtons, 1))
    paint(x, y, string.rep(" ", width), colours.white, bActive and colours.lightBlue or colours.grey)
    paint(x, y, sHeading, colours.white, bActive and colours.lightBlue or colours.grey)

    paint(x + width - 3, y, "[x]", colours.white, colours.red)
    if nButtons >= 6 then
        paint(x + width - 7, y, "[O]", colours.white, bActive and colours.blue or colours.grey)
    end
    if nButtons >= 10 then
        paint(x + width - 11, y, "[-]", colours.white, bActive and colours.blue or colours.grey)
    end

    local frameFg, frameBg = colours.grey, colours.black
    for row = y + 1, y + height - 2 do
        paint(x, row, " ", frameFg, frameBg)
        paint(x + width - 1, row, " ", frameFg, frameBg)
    end
    paint(x, y + height - 1, string.rep("-", width), frameFg, frameBg)
    paint(x, y + height - 1, "+", colours.lightGrey, frameBg)
    paint(x + width - 1, y + height - 1, "+", colours.lightGrey, frameBg)
end

local function drawToast()
    if not toast then return end
    if now() >= toast.expires then
        toast = nil
        return
    end
    local width = math.min(cells(toast.text) + 4, math.max(w - 2, 4))
    local x = math.max(math.floor((w - width) / 2) + 1, 1)
    paint(x, math.max(h - 3, 1), pad(" " .. toast.text, width), colours.white, colours.black)
end

local function repaint()
    w, h = parentTerm.getSize()
    TASKBAR_Y = h

    drawWallpaper()
    drawIcons()

    -- Windows are drawn in order, so the last one is on top.
    for _, item in ipairs(tWindows) do
        if item.visible then
            drawWindowFrame(item)
            if item.windowVisible then
                item.window.redraw()
            else
                item.window.setVisible(true)
                item.windowVisible = true
            end
        elseif item.windowVisible then
            item.window.setVisible(false)
            item.windowVisible = false
        end
    end

    drawTaskbar()
    drawToast()
    if menu then drawMenu() end

    parentTerm.setCursorPos(1, 1)
    parentTerm.setCursorBlink(false)
    parentTerm.setBackgroundColour(colours.black)
    parentTerm.setTextColour(colours.white)
end

-- ---------------------------------------------------------------------------------------------------------------
-- Launching programs
-- ---------------------------------------------------------------------------------------------------------------

local function openIcon(index)
    local tIcon = tIcons[index]
    if not tIcon then return end
    nSelectedIcon = index
    if tIcon.action == "wallpaper" then
        local items = {}
        for _, theme in ipairs(tWallpapers) do
            items[#items + 1] = {
                label = theme.name,
                checked = wallpaperId() == theme.id,
                action = function()
                    settings.set("desktop.wallpaper", theme.id)
                    settings.save()
                    buildWallpaper()
                end,
            }
        end
        openMenu(items, ICON_X, ICON_Y, { kind = "wallpaper", header = "壁纸" })
    else
        openProgram(tIcon.name, tIcon.program, tIcon.args)
    end
end

local function buildProgramItems()
    local items, seen = {}, {}
    local function add(sFile, sFull, sCategory)
        if not sFull or seen[sFull] then return end
        seen[sFull] = true

        local sName = fs.getName(sFull)
        if sName == "desktop.lua" or sName == "shutdown.lua" or sName == "reboot.lua" or sName == "exit.lua" then
            return
        end
        if sName:sub(-4) == ".lua" then sName = sName:sub(1, -5) end

        local sLabel = sName
        if sCategory and sCategory ~= "" then sLabel = sName .. "  [" .. sCategory .. "]" end
        items[#items + 1] = {
            label = sLabel,
            search = (sLabel .. " " .. sFull):lower(),
            action = function() openProgram(sName, sFull) end,
        }
    end

    local sPath = shell and shell.path() or ""
    for sDir in string.gmatch(sPath .. ":", "([^:]*):") do
        if sDir ~= "" then
            local sAbs = sDir
            if sDir:sub(1, 1) ~= "/" then sAbs = shell.resolve(sDir) end
            if fs.isDir(sAbs) then
                local sCategory = fs.getName(sAbs)
                if sCategory == "" or sCategory == "." or sCategory == "programs" or sAbs == "/" then
                    sCategory = nil
                end
                for _, sFile in ipairs(fs.list(sAbs)) do
                    if sFile:sub(1, 1) ~= "." then
                        local sFull = fs.combine(sAbs, sFile)
                        if not fs.isDir(sFull) then add(sFile, sFull, sCategory) end
                    end
                end
            end
        end
    end

    if disk then
        for _, sName in ipairs(peripheral.getNames()) do
            if disk.isPresent(sName) and disk.hasData(sName) then
                local sMount = disk.getMountPath(sName)
                if sMount then
                    for _, sDir in ipairs({ sMount, fs.combine(sMount, "programs") }) do
                        if fs.isDir(sDir) then
                            for _, sFile in ipairs(fs.list(sDir)) do
                                if sFile:sub(1, 1) ~= "." then
                                    local sFull = fs.combine(sDir, sFile)
                                    if not fs.isDir(sFull) then add(sFile, sFull, "磁盘") end
                                end
                            end
                        end
                    end
                end
            end
        end
    end

    table.sort(items, function(a, b) return a.label:lower() < b.label:lower() end)
    return items
end

local function openStartMenu()
    local all = buildProgramItems()
    openMenu(all, 1, h, { kind = "start", header = "程序", filterable = true, all = all })
end

local function openWallpaperMenu(x, y)
    local items = {}
    for _, theme in ipairs(tWallpapers) do
        items[#items + 1] = {
            label = theme.name,
            checked = wallpaperId() == theme.id,
            action = function()
                settings.set("desktop.wallpaper", theme.id)
                settings.save()
                buildWallpaper()
            end,
        }
    end
    openMenu(items, x, y, { kind = "wallpaper", header = "壁纸" })
end

local function openDesktopMenu(x, y)
    openMenu({
        { label = "打开终端", action = function() openProgram("终端", "shell") end },
        { label = "程序", action = function() openStartMenu() end },
        { label = "更换壁纸", action = function() openWallpaperMenu(x, y) end },
        { separator = true },
        {
            label = "开机自动启动", checked = startupInstalled(),
            action = function()
                if startupInstalled() then
                    uninstallStartup()
                    showToast("已关闭开机自动启动")
                else
                    local ok, err = installStartup()
                    showToast(ok and "已开启开机自动启动" or err)
                end
            end,
        },
        { label = "关于桌面", action = function() openProgram("关于", "help", { "desktop" }) end },
        { separator = true },
        { label = "关闭桌面", action = function() bRunning = false end },
    }, x, y, { kind = "desktop", header = "桌面" })
end

local function openWindowMenu(item, x, y)
    openMenu({
        {
            label = item.visible and "最小化" or "还原",
            action = function() toggleMinimize(item) end,
        },
        {
            label = item.maximized and "还原大小" or "最大化",
            action = function() toggleMaximize(item) end,
        },
        { separator = true },
        { label = "关闭窗口", action = function() close(item) end },
    }, x, y, { kind = "window", header = fit(item.title, 20) })
end

-- ---------------------------------------------------------------------------------------------------------------
-- Event handling
-- ---------------------------------------------------------------------------------------------------------------

local function windowUnder(x, y)
    for i = #tWindows, 1, -1 do
        local item = tWindows[i]
        if item.visible and x >= item.x and x < item.x + item.width and y >= item.y and y < item.y + item.height then
            return item
        end
    end
    return nil
end

local function contentUnder(x, y)
    for i = #tWindows, 1, -1 do
        local item = tWindows[i]
        if item.visible and x > item.x and x < item.x + item.width - 1 and y > item.y and
            y < item.y + item.height - 1 then
            return item
        end
    end
    return nil
end

local function hitTest(item, x, y)
    if x < item.x or x >= item.x + item.width or y < item.y or y >= item.y + item.height then return nil end

    if y == item.y then
        if x >= item.x + item.width - 3 then return "close" end
        if item.width >= 10 and x >= item.x + item.width - 7 and x < item.x + item.width - 4 then return "maximize" end
        if item.width >= 14 and x >= item.x + item.width - 11 and x < item.x + item.width - 8 then return "minimize" end
        return "title"
    elseif x == item.x then
        return "border"
    elseif x == item.x + item.width - 1 or y == item.y + item.height - 1 then
        return "resize"
    else
        return "content"
    end
end

local lastClickState = { item = nil, part = nil, time = 0 }

local function lastClick(item, part)
    local bDouble = lastClickState.item == item and lastClickState.part == part and now() - lastClickState.time < 0.4
    lastClickState.item, lastClickState.part, lastClickState.time = item, part, now()
    return bDouble
end

local function handleMenuClick(button, x, y)
    if button ~= 1 then
        closeMenu()
        return true
    end
    local index = menuItemAt(x, y)
    if index then
        local item = menu.items[index]
        if item and not item.separator and not item.disabled then
            menu.selected = index
            chooseMenu()
        end
    else
        closeMenu()
    end
    return true
end

local function handleTaskbarClick(x)
    for _, button in ipairs(taskbarButtons()) do
        if x >= button.x and x < button.x + button.width then
            if button.kind == "menu" then
                if menu and menu.kind == "start" then
                    closeMenu()
                else
                    openStartMenu()
                end
            else
                local item = windowById(button.id)
                if item then
                    if item.visible and item.id == nFocused then
                        -- Clicking the focused window's label minimises it.
                        toggleMinimize(item)
                    else
                        item.visible = true
                        raise(item)
                    end
                end
            end
            return true
        end
    end
    return true
end

local function handleClick(button, x, y)
    if menu then return handleMenuClick(button, x, y) end

    if button == 2 then
        if y == TASKBAR_Y then return true end
        local item = windowUnder(x, y)
        if item then
            raise(item)
            openWindowMenu(item, x, y)
        else
            openDesktopMenu(x, y)
        end
        return true
    end

    if y == TASKBAR_Y then return handleTaskbarClick(x) end

    local item = windowUnder(x, y)
    if item then
        raise(item)
        local part = hitTest(item, x, y)
        if part == "close" then
            close(item)
        elseif part == "maximize" then
            toggleMaximize(item)
        elseif part == "minimize" then
            toggleMinimize(item)
        elseif part == "title" then
            if lastClick(item, "title") then
                toggleMaximize(item)
            elseif not item.maximized then
                dragging = { item = item, dx = x - item.x, dy = y - item.y }
            end
        elseif part == "resize" then
            item.maximized = false
            resizing = {
                item = item,
                right = x == item.x + item.width - 1,
                bottom = y == item.y + item.height - 1,
            }
        elseif part == "content" then
            resumeWindow(item, "mouse_click", button, x - item.x, y - item.y)
        end
        return true
    end

    local index = iconAt(x, y)
    if index then
        openIcon(index)
        return true
    end

    nSelectedIcon = math.min(nSelectedIcon, math.max(#tIcons, 1))
    return true
end

local function handleDrag(button, x, y)
    if menu then return false end

    if dragging then
        local item = dragging.item
        local newX = math.max(math.min(x - dragging.dx, w - item.width + 1), 1)
        local newY = math.max(math.min(y - dragging.dy, (h - 1) - item.height + 1), 1)
        if newX ~= item.x or newY ~= item.y then
            item.x, item.y = newX, newY
            item.window.reposition(newX + 1, newY + 1, math.max(item.width - 2, 1), math.max(item.height - 2, 1),
                parentTerm)
            return true
        end
        return false
    elseif resizing then
        local item = resizing.item
        local newWidth, newHeight = item.width, item.height
        if resizing.right then newWidth = x - item.x + 1 end
        if resizing.bottom then newHeight = y - item.y + 1 end
        return resizeWindow(item, item.x, item.y, newWidth, newHeight)
    end

    local item = contentUnder(x, y)
    if item then
        resumeWindow(item, "mouse_drag", button, x - item.x, y - item.y)
    end
    return false
end

local function handleMouseUp(button, x, y)
    if menu then return false end

    if dragging or resizing then
        local item = (dragging and dragging.item) or (resizing and resizing.item)
        dragging, resizing = nil, nil
        if item and item.needResize then
            item.needResize = false
            resumeWindow(item, "term_resize")
        end
        return true
    end

    local item = contentUnder(x, y)
    if item then
        resumeWindow(item, "mouse_up", button, x - item.x, y - item.y)
    end
    return false
end

local function handleScroll(direction, x, y)
    if menu then
        menuMove(direction > 0 and 1 or -1)
        return true
    end
    local item = contentUnder(x, y)
    if item then
        resumeWindow(item, "mouse_scroll", direction, x - item.x, y - item.y)
    end
    return false
end

local function handleMove(x, y)
    if menu then
        local index = menuItemAt(x, y)
        if index and index ~= menu.selected then
            local item = menu.items[index]
            if item and not item.separator and not item.disabled then
                menu.selected = index
                return true
            end
        end
        return false
    end

    local item = contentUnder(x, y)
    if item then
        resumeWindow(item, "mouse_move", x - item.x, y - item.y)
    end
    return false
end

local function handleKey(key)
    if menu then
        if key == keys.up then
            menuMove(-1)
        elseif key == keys.down then
            menuMove(1)
        elseif key == keys.enter or key == keys.numPadEnter then
            chooseMenu()
        elseif key == keys.backspace then
            if menu.filterable and #menu.filter > 0 then
                menu.filter = popChar(menu.filter)
                rebuildStartMenu()
            else
                closeMenu()
            end
        end
        return true
    end

    local item = focusedWindow()
    if item and item.visible then
        resumeWindow(item, "key", key)
        return false
    end

    if key == keys.left then
        nSelectedIcon = math.max(nSelectedIcon - 1, 1)
    elseif key == keys.right then
        nSelectedIcon = math.min(nSelectedIcon + 1, math.max(#tIcons, 1))
    elseif key == keys.up then
        nSelectedIcon = math.max(nSelectedIcon - iconColumns(), 1)
    elseif key == keys.down then
        nSelectedIcon = math.min(nSelectedIcon + iconColumns(), math.max(#tIcons, 1))
    elseif key == keys.enter or key == keys.numPadEnter then
        openIcon(nSelectedIcon)
    elseif key == keys.m or key == keys.f1 then
        openStartMenu()
    end
    return true
end

local function handleChar(char)
    if menu then
        if menu.filterable then
            menu.filter = menu.filter .. char
            rebuildStartMenu()
        end
        return true
    end

    local item = focusedWindow()
    if item and item.visible then resumeWindow(item, "char", char) end
    return false
end

local function forwardEvent(...)
    if menu then return end
    local item = focusedWindow()
    if item and item.visible and item.co then resumeWindow(item, ...) end
end

-- Some events, such as disks being inserted or removed, are relevant to every window.
local function broadcastEvent(...)
    for _, item in ipairs(tWindows) do
        if item.visible and item.co then resumeWindow(item, ...) end
    end
end

local function onResize()
    w, h = parentTerm.getSize()
    TASKBAR_Y = h
    buildWallpaper()

    for _, item in ipairs(tWindows) do
        local width = math.min(math.max(item.width, MIN_W), w)
        local height = math.min(math.max(item.height, MIN_H), h - 1)
        local x = math.min(item.x, w - width + 1)
        local y = math.min(item.y, (h - 1) - height + 1)
        if x ~= item.x or y ~= item.y or width ~= item.width or height ~= item.height then
            item.x, item.y, item.width, item.height = math.max(x, 1), math.max(y, 1), width, height
            item.window.reposition(item.x + 1, item.y + 1, math.max(width - 2, 1), math.max(height - 2, 1),
                parentTerm)
            item.needResize = true
        end
        if item.visible and item.needResize then
            item.needResize = false
            resumeWindow(item, "term_resize")
        end
    end
    if menu then layoutMenu() end
end

-- ---------------------------------------------------------------------------------------------------------------
-- Main loop
-- ---------------------------------------------------------------------------------------------------------------

refreshIcons()
buildWallpaper()
repaint()

local timer = os.startTimer(1)

while bRunning do
    local tEvent = table.pack(os.pullEventRaw())
    local event = tEvent[1]
    local bRepaint = false

    if event == "mouse_click" then
        bRepaint = handleClick(tEvent[2], tEvent[3], tEvent[4])
    elseif event == "mouse_drag" then
        bRepaint = handleDrag(tEvent[2], tEvent[3], tEvent[4])
    elseif event == "mouse_up" then
        bRepaint = handleMouseUp(tEvent[2], tEvent[3], tEvent[4])
    elseif event == "mouse_scroll" then
        bRepaint = handleScroll(tEvent[2], tEvent[3], tEvent[4])
    elseif event == "mouse_move" then
        bRepaint = handleMove(tEvent[3], tEvent[4])
    elseif event == "key" then
        bRepaint = handleKey(tEvent[2])
    elseif event == "char" then
        bRepaint = handleChar(tEvent[2])
    elseif event == "term_resize" then
        onResize()
        bRepaint = true
    elseif event == "timer" then
        for _, item in ipairs(tWindows) do
            if item.co and coroutine.status(item.co) ~= "dead" then
                resumeWindow(item, "timer", tEvent[2])
            end
        end
        if tEvent[2] == timer then
            timer = os.startTimer(1)
            bRepaint = true
        end
    elseif event == "disk" or event == "disk_eject" then
        refreshIcons()
        bRepaint = true
        broadcastEvent(table.unpack(tEvent, 1, tEvent.n))
    elseif event == "terminate" then
        bRunning = false
    elseif event == "paste" or event == "key_up" or event == "file_transfer" then
        -- These only make sense for the window the user is interacting with.
        forwardEvent(table.unpack(tEvent, 1, tEvent.n))
    else
        -- Everything else (peripherals, rednet, http, ...) is relevant to every window.
        broadcastEvent(table.unpack(tEvent, 1, tEvent.n))
    end

    if reap() then bRepaint = true end
    if bRepaint then repaint() end
end

-- Shut down the programs we started, then hand the screen back to the shell.
for _, item in ipairs(tWindows) do
    terminateWindow(item)
end

parentTerm.setBackgroundColour(colours.black)
parentTerm.setTextColour(colours.white)
parentTerm.clear()
parentTerm.setCursorPos(1, 1)
parentTerm.setCursorBlink(true)
