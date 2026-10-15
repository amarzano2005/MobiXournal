-- MobiXournal (.mobix) Companion Importer for desktop Xournal++
-- Extracts .mobix containers, parses JSON manifests and pages,
-- synthesizes a temporary .xopp/.xoj document, and opens it via app.openFile.

local json = nil
local ok, mod = pcall(require, "json")
if ok then
    json = mod
else
    -- Fallback to relative script directory lookup
    local script_dir = debug.getinfo(1, "S").source:sub(2):match("(.*/)") or "./"
    package.path = package.path .. ";" .. script_dir .. "?.lua"
    json = require("json")
end

local mobix = {}

-- Register the plugin UI in Xournal++
function initUi()
    if app and app.registerUi then
        app.registerUi({
            ["menu"] = "Import .mobix Document...",
            ["callback"] = "onImportMobix",
            ["accelerator"] = "<Control><Alt>m"
        })
    end
end

-- Read an entire text file into a string
function mobix.readFile(path)
    local f = io.open(path, "r")
    if not f then return nil end
    local content = f:read("*all")
    f:close()
    return content
end

-- Write a string to a file
function mobix.writeFile(path, content)
    local f = io.open(path, "w")
    if not f then return false end
    f:write(content)
    f:close()
    return true
end

-- Cross-platform temporary directory
function mobix.getTempDir()
    local dir = os.getenv("TMPDIR") or os.getenv("TEMP") or os.getenv("TMP") or "/tmp"
    return dir .. "/mobix_staging_" .. tostring(os.time())
end

-- Extract a .mobix ZIP archive into a destination directory
function mobix.extractArchive(mobixPath, destDir)
    os.execute("mkdir -p \"" .. destDir .. "\" 2>/dev/null || mkdir \"" .. destDir .. "\" 2>nul")
    local cmd
    if package.config:sub(1,1) == "\\" then
        -- Windows: use tar or PowerShell Expand-Archive
        cmd = string.format("tar -xf \"%s\" -C \"%s\" 2>nul || powershell -Command \"Expand-Archive -Path '%s' -DestinationPath '%s' -Force\"",
            mobixPath, destDir, mobixPath, destDir)
    else
        -- Linux / macOS: use unzip
        cmd = string.format("unzip -q \"%s\" -d \"%s\"", mobixPath, destDir)
    end
    return os.execute(cmd)
end

-- Convert color ARGB int to desktop Xournal++ #rrggbbaa hex format
function mobix.formatColor(colorInt)
    if not colorInt then return "#000000ff" end
    local a = (colorInt >> 24) & 0xFF
    local r = (colorInt >> 16) & 0xFF
    local g = (colorInt >> 8) & 0xFF
    local b = colorInt & 0xFF
    return string.format("#%02x%02x%02x%02x", r, g, b, a)
end

-- Convert an element to Xournal++ XML
function mobix.elementToXml(elem)
    local t = elem.type or "stroke"
    if t == "stroke" then
        local tool = elem.tool or "pen"
        local color = mobix.formatColor(elem.color)
        local width = elem.lineWidth or 1.41
        local pts = elem.points or {}
        local ptStrs = {}
        for _, p in ipairs(pts) do
            table.insert(ptStrs, string.format("%.2f %.2f", p.x or 0, p.y or 0))
        end
        local extra = ""
        if elem.audioRef then
            -- `filename` is the sidecar the stroke replayed from, and it is the name the desktop
            -- document must reference; `assetId` is the entry the recording is bundled in, which is
            -- only a fallback for a file that names no sidecar. Neither extension is rewritten:
            -- the app bundles a recording in the container it was recorded in (`.wav` for its own
            -- recorder), so the entry is `aud_<sha>.wav` and no `.opus` guess is needed.
            local fn = elem.audioRef.filename or ""
            if fn == "" then
                fn = elem.audioRef.assetId or ""
            end
            fn = fn:gsub("^assets/", "")
            extra = string.format(" fn=\"%s\" ts=\"%d\"", fn, elem.audioRef.startMs or 0)
        elseif elem.attrs and elem.attrs.fn then
            local fn = elem.attrs.fn:gsub("^assets/", "")
            extra = string.format(" fn=\"%s\" ts=\"%s\"", fn, elem.attrs.ts or "0")
        end
        return string.format("<stroke tool=\"%s\" color=\"%s\" width=\"%.2f\"%s>\n%s\n</stroke>",
            tool:lower(), color, width, extra, table.concat(ptStrs, " "))
    elseif t == "text" then
        local font = elem.font or "Sans"
        local size = elem.size or 12.0
        local x = elem.x or 0.0
        local y = elem.y or 0.0
        local color = mobix.formatColor(elem.color)
        local content = elem.content or ""
        return string.format("<text font=\"%s\" size=\"%.2f\" x=\"%.2f\" y=\"%.2f\" color=\"%s\">\n%s\n</text>",
            font, size, x, y, color, content)
    end
    return ""
end

-- Synthesize a temporary .xoj XML document from the extracted .mobix folder
function mobix.synthesizeXopp(stagingDir)
    local manifestJson = mobix.readFile(stagingDir .. "/manifest.json")
    if not manifestJson then return nil end
    local manifest = json.decode(manifestJson)
    if not manifest then return nil end

    local title = manifest.title or "MobiXournal Document"
    local xml = {}
    table.insert(xml, "<?xml version=\"1.0\" standalone=\"no\"?>")
    table.insert(xml, "<xournal version=\"0.4.8\">")
    table.insert(xml, "<title>" .. title .. "</title>")

    local pages = manifest.pages or {}
    for idx, pageEntry in ipairs(pages) do
        local pagePath = stagingDir .. "/" .. pageEntry.entry
        local pageContent = mobix.readFile(pagePath)
        if pageContent then
            local pageData = json.decode(pageContent)
            local w = pageData.widthPt or 595.28
            local h = pageData.heightPt or 841.89
            table.insert(xml, string.format("<page width=\"%.2f\" height=\"%.2f\">", w, h))
            table.insert(xml, "<background type=\"solid\" color=\"#ffffffff\" style=\"plain\"/>")

            for _, layer in ipairs(pageData.layers or {}) do
                table.insert(xml, "<layer>")
                for _, elem in ipairs(layer.elements or {}) do
                    local elemXml = mobix.elementToXml(elem)
                    if #elemXml > 0 then
                        table.insert(xml, elemXml)
                    end
                end
                table.insert(xml, "</layer>")
            end
            table.insert(xml, "</page>")
        end
    end

    table.insert(xml, "</xournal>")
    local targetPath = stagingDir .. "/document.xoj"
    if mobix.writeFile(targetPath, table.concat(xml, "\n")) then
        return targetPath
    end
    return nil
end

-- Primary import routine called when user triggers menu action
function onImportMobix(filePath)
    local path = filePath
    if not path or #path == 0 then
        if app and app.openFileDialog then
            path = app.openFileDialog("Choose .mobix document", {"*.mobix"})
        end
    end
    if not path or #path == 0 then return end

    local stagingDir = mobix.getTempDir()
    mobix.extractArchive(path, stagingDir)
    local xoppPath = mobix.synthesizeXopp(stagingDir)

    if xoppPath and app and app.openFile then
        app.openFile(xoppPath)
    end
end

-- Export module for scripting or testing
return mobix
