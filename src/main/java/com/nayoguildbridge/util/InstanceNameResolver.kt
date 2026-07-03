package com.nayoguildbridge.util

import com.nayoguildbridge.config.NgbConfig
import net.fabricmc.loader.api.FabricLoader

object InstanceNameResolver {
    @Volatile
    private var platformLabel: String? = null

    fun setPlatformLabel(label: String?) {
        platformLabel = label?.trim()?.takeIf { it.isNotBlank() }
    }

    fun resolve(): String {
        NgbConfig.config.mcInstanceName.trim().takeIf { it.isNotBlank() }?.let { return sanitize(it) }
        systemOverride()?.let { return sanitize(it) }
        platformLabel?.let { return sanitize(it) }
        launcherInstanceName()?.let { return sanitize(it) }
        return "minecraft"
    }

    private fun systemOverride(): String? {
        System.getProperty("ngb.instance")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv("NGB_INSTANCE")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun launcherInstanceName(): String? {
        val gameDir = try {
            FabricLoader.getInstance().gameDir.toAbsolutePath().normalize()
        } catch (_: Throwable) {
            return null
        }
        val path = gameDir.toString().replace('\\', '/')
        val patterns = listOf(
            Regex("""/instances/([^/]+)/minecraft/?$""", RegexOption.IGNORE_CASE),
            Regex("""/instances/([^/]+)/\.minecraft/?$""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            pattern.find(path)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (gameDir.fileName?.toString().equals("minecraft", ignoreCase = true) == true) {
            val parent = gameDir.parent?.fileName?.toString()?.trim().orEmpty()
            if (parent.isNotBlank() && !parent.equals("instances", ignoreCase = true)) {
                return parent
            }
        }
        return null
    }

    private fun sanitize(raw: String): String {
        return raw.trim()
            .replace(Regex("""[\[\]|<>]"""), "")
            .take(64)
            .ifBlank { "minecraft" }
    }
}
