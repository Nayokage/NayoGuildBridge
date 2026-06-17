package com.nayoguildbridge.config

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object NgbLang {
    private val gson = Gson()
    private val cache = mutableMapOf<String, Map<String, String>>()

    fun text(key: String): String {
        val forced = NgbConfig.config.menuLanguage.trim().lowercase()
        if (forced == "ru" || forced == "en") {
            return bundle(forced)[key] ?: bundle("en")[key] ?: key
        }
        val mc = Minecraft.getInstance()
        val gameLang = mc.options.languageCode.lowercase()
        val code = if (gameLang.startsWith("ru")) "ru" else "en"
        return bundle(code)[key] ?: bundle("en")[key] ?: key
    }

    fun component(key: String): Component = Component.literal(text(key))

    fun componentOptional(key: String?): Component? {
        if (key.isNullOrBlank()) return null
        return component(key)
    }

    fun invalidate() {
        cache.clear()
    }

    private fun bundle(lang: String): Map<String, String> {
        val code = if (lang == "ru") "ru" else "en"
        return cache.getOrPut(code) {
            val path = "/assets/nayoguildbridge/menu_lang/$code.json"
            val stream = NgbLang::class.java.getResourceAsStream(path)
                ?: return@getOrPut emptyMap()
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(stream.reader(), type) ?: emptyMap()
        }
    }
}
