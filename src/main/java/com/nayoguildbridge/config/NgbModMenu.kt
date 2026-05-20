package com.nayoguildbridge.config

import com.nayoguildbridge.NayoGuildBridge
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screens.Screen

class NgbModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent -> if (NayoGuildBridge.hasYacl()) buildYaclScreen(parent) else null }
    }

    private fun buildYaclScreen(parent: Screen?): Screen? {
        return try {
            val clazz = Class.forName("com.nayoguildbridge.config.NgbConfigManager")
            val method = clazz.getDeclaredMethod("build", Screen::class.java)
            method.invoke(null, parent) as Screen
        } catch (_: Throwable) {
            null
        }
    }
}