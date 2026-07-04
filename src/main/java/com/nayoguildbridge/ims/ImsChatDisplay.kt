package com.nayoguildbridge.ims

import com.google.gson.JsonObject
import com.nayoguildbridge.bridge.BridgeChatDedupe
import com.nayoguildbridge.bridge.IncomingBridgeFormatter
import com.nayoguildbridge.config.NgbConfig
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object ImsChatDisplay {
    fun displayIncoming(root: JsonObject, defaultTag: String, defaultColor: String) {
        val from = root.get("from")?.asString ?: return
        val combined = root.get("combinedbridge")?.asString == "true"
        val cfg = NgbConfig.config

        if ((from == "discord" || from == "telegram" || from == "tg") && !cfg.imsBridgeReceiveEnabled) return
        if (combined && !cfg.imsCombinedBridgeEnabled) return
        // Guild bot already injects [Discord]/[Telegram] lines into Guild Chat; overlay would duplicate.
        if (
            cfg.guildBridgeFormatEnabled &&
            (from == "discord" || from == "telegram" || from == "tg" || from == "mc")
        ) return

        val isShow = root.get("show")?.asString == "true"
        val fromPlayer = root.get("fromplayer")?.asString?.trim().orEmpty()
        if (isShow && fromPlayer.isNotBlank()) {
            val me = Minecraft.getInstance().player?.name?.string?.trim().orEmpty()
            if (me.isNotBlank() && me.equals(fromPlayer, ignoreCase = true)) return
        }

        val msgText = root.get("msg")?.asString?.trim().orEmpty()
        val incoming = when {
            msgText.startsWith(">") || msgText.startsWith("[QUOTE]", ignoreCase = true) || msgText.contains('\n') ->
                IncomingBridgeFormatter.fromPollText(msgText, "chat", null, root)
            else -> IncomingBridgeFormatter.fromWsJson(root, defaultTag)
        } ?: return
        if (!BridgeChatDedupe.claimDisplay(incoming.username, incoming.body)) return

        val formatted = IncomingBridgeFormatter.format(incoming, defaultTag, defaultColor)

        val mc = Minecraft.getInstance()
        mc.execute {
            mc.player?.sendSystemMessage(formatted)
        }
    }

    fun showOnlinePlayers(response: JsonObject) {
        var total = 0
        for (key in response.keySet()) {
            total += response.getAsJsonArray(key).size()
        }
        val sb = StringBuilder("§aОнлайн гильдий: §e$total\n")
        for (key in response.keySet()) {
            val arr = response.getAsJsonArray(key)
            sb.append("§6").append(key).append(" §7(").append(arr.size()).append("):§f\n")
            if (arr.size() == 0) {
                sb.append("  §7— нет игроков\n")
            } else {
                for (i in 0 until arr.size()) {
                    if (i > 0) sb.append("§7, ")
                    sb.append("§f").append(arr[i].asString)
                }
                sb.append("\n")
            }
        }
        val mc = Minecraft.getInstance()
        mc.execute {
            mc.player?.sendSystemMessage(Component.literal(sb.toString().trimEnd()))
        }
    }
}
