package com.nayoguildbridge.guard

import com.nayoguildbridge.config.NgbConfig
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object EnvironmentGuard {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ngb-env-guard").apply { isDaemon = true }
    }

    @Volatile
    var operational: Boolean = true
        private set

    @Volatile
    private var lastHypixelOk: Boolean = true

    @Volatile
    private var lastGuildOk: Boolean = true

    private val guildLineRx = Pattern.compile("""(?i)guild\s*:\s*(.+)""")

    fun register() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            operational = true
            lastHypixelOk = true
            lastGuildOk = true
            scheduler.schedule({ runJoinChecks() }, 3500, TimeUnit.MILLISECONDS)
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            operational = true
        }
    }

    fun isOperational(): Boolean {
        val cfg = NgbConfig.config
        if (!cfg.environmentGuardEnabled) return true
        return operational
    }

    fun resetAfterConfigLoad() {
        operational = true
        lastHypixelOk = true
        lastGuildOk = true
    }

    fun statusLine(): String {
        val cfg = NgbConfig.config
        if (!cfg.environmentGuardEnabled) {
            return "env=off op:yes"
        }
        return "env=hypixel:${if (lastHypixelOk) "ok" else "no"} guild:${if (lastGuildOk) "ok" else "no"} op:${if (operational) "yes" else "no"}"
    }

    private fun runJoinChecks() {
        val client = Minecraft.getInstance()
        client.execute {
            val cfg = NgbConfig.config
            if (!cfg.environmentGuardEnabled) {
                operational = true
                return@execute
            }

            val onHypixel = isOnHypixel(client)
            lastHypixelOk = onHypixel

            if (cfg.requireHypixel && !onHypixel) {
                operational = false
                client.player?.displayClientMessage(
                    Component.literal("§c[NayoGuildBridge] §fМод не работает на данном сервере"),
                    false
                )
                return@execute
            }

            val expectedGuild = cfg.hypixelGuildName.trim()
            if (expectedGuild.isNotEmpty() && onHypixel) {
                val detected = detectGuildName(client)
                lastGuildOk = detected != null &&
                    detected.equals(expectedGuild, ignoreCase = true)

                if (!lastGuildOk) {
                    operational = false
                    client.player?.displayClientMessage(
                        Component.literal(
                            "§c[NGB] §fПроверка гильдии не пройдена. §7/bridge fix §f— отключить проверку"
                        ),
                        false
                    )
                    return@execute
                }
            } else {
                lastGuildOk = true
            }

            operational = true
        }
    }

    private fun isOnHypixel(client: Minecraft): Boolean {
        val addr = serverAddress(client).lowercase()
        return addr.contains("hypixel.net") || addr.contains("hypixel.io")
    }

    private fun serverAddress(client: Minecraft): String {
        val conn = client.connection
        if (conn != null) {
            try {
                val remote = conn.connection.remoteAddress
                if (remote != null) return remote.toString()
            } catch (_: Throwable) {
            }
        }
        val data: ServerData? = client.currentServer
        if (data != null && !data.ip.isNullOrBlank()) {
            return data.ip
        }
        return ""
    }

    private fun detectGuildName(client: Minecraft): String? {
        val level = client.level ?: return null
        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return null

        val scores = try {
            scoreboard.listPlayerScores(objective)
        } catch (_: Throwable) {
            emptyList()
        }
        for (score in scores) {
            val team = scoreboard.getPlayersTeam(score.owner)
            val raw = teamDisplayText(team).ifBlank { score.owner.toString() }
            val clean = raw.replace(Regex("§."), "").trim()
            val m = guildLineRx.matcher(clean)
            if (m.find()) {
                return m.group(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            if (clean.contains("Guild:", ignoreCase = true)) {
                return clean.substringAfter(":", "").trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun teamDisplayText(team: net.minecraft.world.scores.PlayerTeam?): String {
        if (team == null) return ""
        return try {
            val prefixM = team.javaClass.methods.firstOrNull {
                it.name in listOf("getPrefix", "getPlayerPrefix") && it.parameterCount == 0
            }
            val suffixM = team.javaClass.methods.firstOrNull {
                it.name in listOf("getSuffix", "getPlayerSuffix") && it.parameterCount == 0
            }
            val prefix = (prefixM?.invoke(team) as? Component)?.string.orEmpty()
            val suffix = (suffixM?.invoke(team) as? Component)?.string.orEmpty()
            prefix + suffix
        } catch (_: Throwable) {
            ""
        }
    }
}
