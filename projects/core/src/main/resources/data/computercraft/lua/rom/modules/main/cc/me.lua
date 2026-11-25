-- SPDX-FileCopyrightText: 2025 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: MPL-2.0

--- Talk to an AE2 ME network over CC's virtual network, without a wired connection.
--
-- This module lets a computer reach the ME storage behind a network_adapter block through
-- the virtual network, exactly as it would any other host: it opens a socket, sends a JSON
-- request to the adapter's service port, and waits for the reply. It works wherever an
-- ordinary net message does, so it does not need the adapter to be physically wired to the
-- computer.
--
-- To use it, the computer must have a modem and an address (which happens automatically on
-- startup), and there must be a network_core and a network_adapter somewhere on the virtual
-- network.
--
-- @module cc.me
-- @since 1.120.2

local expect = require("cc.expect").expect

--- The port the adapter's ME service listens on.
local ME_PORT = 0x4D45

--- The broadcast address, used to find an adapter without knowing its address.
local BROADCAST = "255.255.255.255"

--- A small counter used to make request ids unique within this program.
local next_id = 0

--- Wait until this computer has an address, asking for one if necessary.
local function ensure_address()
    if net.getAddress() then return end
    net.dhcp()
    while not net.getAddress() do
        os.pullEvent("net_dhcp")
    end
end

--- Send a single request to the adapter and wait for its reply.
--
-- This sends the request to every adapter on the virtual network, and returns the first
-- matching reply. It reuses a single socket and correlates replies by a unique id, so a
-- request is sent at most once.
--
-- @tparam string op The operation to perform: "isConnected", "getItems", "getCells",
--     "getPower", "getCraftable", "pushItem" or "extractItem".
-- @tparam[opt] table args Extra fields for the request, such as "name" and "count".
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn table The "result" field of the reply.
-- @usage Read the ME network's power use over the virtual network.
--
--     local me = require("cc.me")
--     local power = me.request("getPower")
--     print(("%.1f / %.1f AE"):format(power.stored, power.max))
local function request(op, args, address)
    expect(1, op, "string")
    expect(2, args, "table", "nil")
    expect(3, address, "string", "nil")
    ensure_address()

    next_id = next_id + 1
    local id = tostring(next_id)
    local req = { id = id, op = op }
    if args then
        for k, v in pairs(args) do req[k] = v end
    end

    local socket = net.listen(ME_PORT)
    if not net.send(address or BROADCAST, ME_PORT, textutils.serializeJSON(req), socket) then
        net.close(socket)
        error("Could not send ME request: no address.", 2)
    end

    local timer = os.startTimer(5)
    while true do
        for _, msg in ipairs(net.receive(socket)) do
            local parsed = textutils.unserializeJSON(msg.message)
            if parsed and parsed.id == id then
                net.close(socket)
                os.cancelTimer(timer)
                if parsed.ok then return parsed.result end
                error(parsed.error, 2)
            end
        end

        local event = { os.pullEvent() }
        if event[1] == "timer" and event[2] == timer then
            net.close(socket)
            error("Timed out waiting for an ME network reply.", 2)
        end
    end
end

--- Whether the adapter currently has an active ME network to talk to.
--
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn boolean Whether the adapter is connected to an active ME network.
-- @usage Check that an adapter is available before using it.
--
--     local me = require("cc.me")
--     if me.isConnected() then print("ready") end
local function isConnected(address)
    return request("isConnected", nil, address)
end

--- List every stack stored in the ME network.
--
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn { table... } A list of { name, display, count, type, amountPerUnit } tables.
-- @usage Print the name and count of every item in storage.
--
--     local me = require("cc.me")
--     for _, item in ipairs(me.getItems()) do
--         print(item.display, item.count)
--     end
local function getItems(address)
    return request("getItems", nil, address)
end

--- List every storage cell in the ME drives and chests on the network.
--
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn { table... } A list of { slot, name, display, status, location } tables.
-- @usage Print the state of every cell.
--
--     local me = require("cc.me")
--     for _, cell in ipairs(me.getCells()) do print(cell.display, cell.status) end
local function getCells(address)
    return request("getCells", nil, address)
end

--- Read the ME network's power state.
--
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn { table... } A { stored, max, avgUsage, idlePower, channelPower, powered } table.
-- @usage Read how much power the network is using.
--
--     local me = require("cc.me")
--     print(("%.1f / %.1f AE"):format(me.getPower().stored, me.getPower().max))
local function getPower(address)
    return request("getPower", nil, address)
end

--- List every item the ME network can craft.
--
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn { table... } A list of { name, display, count, type } tables.
-- @usage Print every craftable item.
--
--     local me = require("cc.me")
--     for _, item in ipairs(me.getCraftable()) do print(item.display) end
local function getCraftable(address)
    return request("getCraftable", nil, address)
end

--- Store some items into the ME network.
--
-- @tparam table args The request arguments: { name = "minecraft:cobblestone", count = 64 }.
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn number The number of items actually stored.
-- @usage Store a stack of cobblestone.
--
--     local me = require("cc.me")
--     me.pushItem({ name = "minecraft:cobblestone", count = 64 })
local function pushItem(args, address)
    expect(1, args, "table")
    return request("pushItem", args, address)
end

--- Pull some items out of the ME network.
--
-- @tparam table args The request arguments: { name = "minecraft:cobblestone", count = 64 }.
-- @tparam[opt] string address The address of a specific adapter, or nil to broadcast.
-- @treturn number The number of items actually extracted.
-- @usage Pull a stack of cobblestone.
--
--     local me = require("cc.me")
--     me.extractItem({ name = "minecraft:cobblestone", count = 64 })
local function extractItem(args, address)
    expect(1, args, "table")
    return request("extractItem", args, address)
end

return {
    request = request,
    isConnected = isConnected,
    getItems = getItems,
    getCells = getCells,
    getPower = getPower,
    getCraftable = getCraftable,
    pushItem = pushItem,
    extractItem = extractItem,
}
