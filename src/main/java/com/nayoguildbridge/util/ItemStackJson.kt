package com.nayoguildbridge.util

import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ItemStackJson {
    fun toJsonStack(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val id = BuiltInRegistries.ITEM.getKey(stack.item)?.toString() ?: "minecraft:air"
        val name = stack.hoverName.string.replace("\"", "\\\"")
        return """{"id":"$id","count":${stack.count},"name":"$name"}"""
    }

    fun fromJsonStack(raw: String?): ItemStack? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JsonParser.parseString(raw).asJsonObject
            val id = obj.get("id")?.asString ?: return null
            val count = obj.get("count")?.asInt?.coerceAtLeast(1) ?: 1
            val loc = ResourceLocation.tryParse(id) ?: return null
            val item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.AIR)
            if (item == Items.AIR) return null
            ItemStack(item, count)
        } catch (_: Throwable) {
            null
        }
    }
}
