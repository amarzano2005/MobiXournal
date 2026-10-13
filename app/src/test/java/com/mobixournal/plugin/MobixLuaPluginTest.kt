package com.mobixournal.plugin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MobixLuaPluginTest {

    private val pluginDir = listOf(
        File("plugins/mobix-importer"),
        File("../plugins/mobix-importer"),
        File("../../plugins/mobix-importer"),
    ).firstOrNull { it.exists() } ?: File("plugins/mobix-importer")

    @Test
    fun pluginDirectoryContainsRequiredManifestAndScripts() {
        val pluginIni = File(pluginDir, "plugin.ini")
        assertTrue("plugin.ini exists", pluginIni.isFile)
        val iniContent = pluginIni.readText()
        assertTrue("contains [about]", iniContent.contains("[about]"))
        assertTrue("contains [plugin]", iniContent.contains("[plugin]"))
        assertTrue("points to main.lua", iniContent.contains("mainfile=main.lua"))

        val mainLua = File(pluginDir, "main.lua")
        assertTrue("main.lua exists", mainLua.isFile)
        val luaContent = mainLua.readText()
        assertTrue("defines initUi callback", luaContent.contains("function initUi()"))
        assertTrue("defines onImportMobix callback", luaContent.contains("function onImportMobix"))
        assertTrue("defines elementToXml", luaContent.contains("elementToXml"))

        val jsonLua = File(pluginDir, "json.lua")
        assertTrue("json.lua exists", jsonLua.isFile)

        val readme = File(pluginDir, "README.md")
        assertTrue("README.md exists", readme.isFile)
    }
}
