package com.nayoguildbridge.util

import net.minecraft.client.Minecraft
import java.util.UUID

object PlayerIdentity {
    fun playerName(client: Minecraft = Minecraft.getInstance()): String =
        client.player?.name?.string?.trim().orEmpty()

    fun playerUuid(client: Minecraft = Minecraft.getInstance()): String? {
        val raw = client.player?.uuid ?: return null
        return normalizeUuid(raw)
    }

    fun normalizeUuid(uuid: UUID): String = uuid.toString().replace("-", "").lowercase()

    fun instanceId(client: Minecraft = Minecraft.getInstance()): String {
        val uuid = playerUuid(client)
        if (!uuid.isNullOrBlank()) return uuid
        val name = playerName(client)
        return name.ifBlank { "bridge-client" }
    }

    fun originInstance(client: Minecraft = Minecraft.getInstance()): String {
        val server = client.currentServer?.ip?.trim().orEmpty()
        if (server.isNotBlank()) return server
        return instanceId(client)
    }

    fun mcInstanceName(client: Minecraft = Minecraft.getInstance()): String =
        InstanceNameResolver.resolve()
}
