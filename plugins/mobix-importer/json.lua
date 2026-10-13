-- Lightweight JSON decoder for the MobiXournal Lua companion plugin
local json = {}

local function kind_of(obj)
    if type(obj) ~= "table" then return type(obj) end
    local i = 1
    for _ in pairs(obj) do
        if obj[i] ~= nil then i = i + 1 else return "table" end
    end
    if i == 1 then return "table" else return "array" end
end

local function escape_str(s)
    local in_char  = {'\\', '"', '/', '\b', '\f', '\n', '\r', '\t'}
    local out_char = {'\\', '"', '/',  'b',  'f',  'n',  'r',  't'}
    for i, c in ipairs(in_char) do
        s = s:gsub(c, '\\' .. out_char[i])
    end
    return s
end

local function parse_string(str, pos)
    local has_esc = false
    local i = pos + 1
    while i <= #str do
        local c = str:sub(i, i)
        if c == '"' and not has_esc then
            return str:sub(pos + 1, i - 1), i + 1
        elseif c == '\\' then
            has_esc = not has_esc
        else
            has_esc = false
        end
        i = i + 1
    end
    error("Unterminated string starting at " .. pos)
end

local function skip_whitespace(str, pos)
    while pos <= #str and str:sub(pos, pos):match("%s") do
        pos = pos + 1
    end
    return pos
end

local parse_value

local function parse_array(str, pos)
    local arr = {}
    pos = skip_whitespace(str, pos + 1)
    if str:sub(pos, pos) == "]" then return arr, pos + 1 end
    while pos <= #str do
        local val
        val, pos = parse_value(str, pos)
        table.insert(arr, val)
        pos = skip_whitespace(str, pos)
        local c = str:sub(pos, pos)
        if c == "]" then return arr, pos + 1 end
        if c == "," then pos = skip_whitespace(str, pos + 1) else error("Expected ',' or ']' at " .. pos) end
    end
    error("Unterminated array at " .. pos)
end

local function parse_object(str, pos)
    local obj = {}
    pos = skip_whitespace(str, pos + 1)
    if str:sub(pos, pos) == "}" then return obj, pos + 1 end
    while pos <= #str do
        local key
        if str:sub(pos, pos) ~= '"' then error("Expected string key at " .. pos) end
        key, pos = parse_string(str, pos)
        pos = skip_whitespace(str, pos)
        if str:sub(pos, pos) ~= ":" then error("Expected ':' after key at " .. pos) end
        pos = skip_whitespace(str, pos + 1)
        local val
        val, pos = parse_value(str, pos)
        obj[key] = val
        pos = skip_whitespace(str, pos)
        local c = str:sub(pos, pos)
        if c == "}" then return obj, pos + 1 end
        if c == "," then pos = skip_whitespace(str, pos + 1) else error("Expected ',' or '}' at " .. pos) end
    end
    error("Unterminated object at " .. pos)
end

parse_value = function(str, pos)
    pos = skip_whitespace(str, pos)
    local c = str:sub(pos, pos)
    if c == '"' then
        return parse_string(str, pos)
    elseif c == "{" then
        return parse_object(str, pos)
    elseif c == "[" then
        return parse_array(str, pos)
    elseif str:sub(pos, pos + 3) == "true" then
        return true, pos + 4
    elseif str:sub(pos, pos + 4) == "false" then
        return false, pos + 5
    elseif str:sub(pos, pos + 3) == "null" then
        return nil, pos + 4
    else
        local num_str = str:match("^-?%d+%.?%d*[eE]?[+-]?%d*", pos)
        if num_str and #num_str > 0 then
            return tonumber(num_str), pos + #num_str
        end
        error("Unexpected token at " .. pos .. ": " .. c)
    end
end

function json.decode(str)
    if type(str) ~= "string" or #str == 0 then return nil end
    local val, _ = parse_value(str, 1)
    return val
end

return json
