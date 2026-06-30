package com.nayoguildbridge.quote

object QuoteDetector {
    data class Result(
        val quoted: Boolean,
        val body: String,
        val quotedMessage: String? = null,
        val quotedFromInstance: String? = null,
        val quotedFromUser: String? = null,
        val replyToMessageId: String? = null
    )

    data class IncomingQuote(
        val quotedText: String,
        val replyText: String,
        val quotedFromInstance: String? = null,
        val quotedFromUser: String? = null,
        val replyToMessageId: String? = null
    )

    fun detect(rawMessage: String): Result {
        val msg = rawMessage.trimStart()
        val withoutPrefix = when {
            msg.startsWith(">") -> msg.removePrefix(">").trimStart()
            msg.startsWith("[q]", ignoreCase = true) -> msg.substring(3).trimStart()
            else -> return Result(false, rawMessage)
        }.replace(Regex("""\s*\[q]\s*$""", RegexOption.IGNORE_CASE), "").trimStart()

        val lines = withoutPrefix.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return Result(true, "")

        if (lines.size >= 2 && lines.first().trimStart().startsWith(">")) {
            val quotedLine = lines.first().trimStart().removePrefix(">").trimStart()
            val replyBody = lines.drop(1).joinToString("\n").trim()
            val parsed = parseQuotedLine(quotedLine)
            return Result(
                quoted = true,
                body = replyBody,
                quotedMessage = parsed.third ?: quotedLine,
                quotedFromInstance = parsed.first,
                quotedFromUser = parsed.second,
                replyToMessageId = parsed.fourth
            )
        }

        if (lines.size >= 2) {
            val quotedLine = lines.first()
            val replyBody = lines.drop(1).joinToString("\n").trim()
            val parsed = parseQuotedLine(quotedLine)
            return Result(
                quoted = true,
                body = replyBody,
                quotedMessage = parsed.third ?: quotedLine,
                quotedFromInstance = parsed.first,
                quotedFromUser = parsed.second,
                replyToMessageId = parsed.fourth
            )
        }

        parseSingleLineWithPipe(withoutPrefix.trim())?.let { return it }
        parseColonQuoteLine(withoutPrefix.trim())?.let { return it }
        parseSingleLineQuote(withoutPrefix.trim())?.let { return it }

        return Result(true, withoutPrefix.trim())
    }

    private fun parseColonQuoteLine(line: String): Result? {
        val pipe = line.indexOf(" | ")
        val quotePart: String
        val replyPart: String
        if (pipe >= 0) {
            quotePart = line.substring(0, pipe).trim()
            replyPart = line.substring(pipe + 3).trim()
            if (quotePart.isEmpty()) return null
            if (replyPart.isEmpty()) return null
            parseHeaderOnlyQuote(quotePart, replyPart)?.let { return it }
        } else {
            quotePart = line.trim()
            replyPart = ""
        }

        val parsed = parseQuotedLine(quotePart.removePrefix(">").trimStart())
        val (source, user, quotedText) = parsed
        if (user.isNullOrBlank() && source.isNullOrBlank() && quotedText.isNullOrBlank()) return null
        if (replyPart.isBlank() && quotedText.isNullOrBlank()) return null

        return Result(
            quoted = true,
            body = replyPart,
            quotedMessage = quotedText?.takeIf { it.isNotBlank() } ?: "—",
            quotedFromInstance = source,
            quotedFromUser = user,
            replyToMessageId = parsed.fourth
        )
    }

    fun parseIncomingQuoteBody(text: String): Pair<String, String>? =
        parseIncomingQuote(text)?.let { it.quotedText to it.replyText }

    fun cleanIncomingQuoteText(text: String): String =
        cleanIncomingMessageLine(text)

    fun parseIncomingQuote(text: String): IncomingQuote? {
        val pipe = text.indexOf(" | ")
        if (pipe > 0) {
            val left = text.substring(0, pipe).trim()
            val right = text.substring(pipe + 3).trim()
            if (right.isNotEmpty()) {
                val quoteRaw = left.removePrefix(">").trimStart()
                if (quoteRaw.isNotEmpty()) {
                    val parsed = parseQuotedLine(quoteRaw)
                    val quotedText = parsed.third?.takeIf { it.isNotBlank() } ?: quoteRaw
                    val replyText = cleanIncomingMessageLine(right)
                    if (quotedText.isNotBlank() && replyText.isNotBlank()) {
                        return IncomingQuote(
                            quotedText = quotedText,
                            replyText = replyText,
                            quotedFromInstance = parsed.first,
                            quotedFromUser = parsed.second,
                            replyToMessageId = parsed.fourth
                        )
                    }
                }
            }
        }
        val lines = text.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val quoteLineRaw = lines.first()
        if (!quoteLineRaw.trimStart().startsWith(">")) return null
        val quoteRaw = quoteLineRaw.trimStart().removePrefix(">").trimStart()
        val parsed = parseQuotedLine(quoteRaw)
        val quoteText = parsed.third?.takeIf { it.isNotBlank() } ?: quoteRaw
        if (quoteText.isEmpty()) return null
        val replyText = cleanIncomingMessageLine(lines.drop(1).joinToString(" ").trim())
        if (replyText.isEmpty()) return null
        return IncomingQuote(
            quotedText = quoteText,
            replyText = replyText,
            quotedFromInstance = parsed.first,
            quotedFromUser = parsed.second,
            replyToMessageId = parsed.fourth
        )
    }

    private fun cleanIncomingMessageLine(line: String): String {
        val cleaned = line.trim().removePrefix(">").trimStart()
        val parsed = parseQuotedLine(cleaned)
        return parsed.third?.takeIf { it.isNotBlank() } ?: cleaned
    }

    private fun parseSingleLineWithPipe(line: String): Result? {
        val pipe = line.indexOf(" | ")
        if (pipe < 0) return null
        val quotePart = line.substring(0, pipe).trim()
        val replyPart = line.substring(pipe + 3).trim()
        if (quotePart.isEmpty() || replyPart.isEmpty()) return null
        parseHeaderOnlyQuote(quotePart, replyPart)?.let { return it }
        val quotedBody = quotePart.removePrefix(">").trimStart()
        val parsed = parseQuotedLine(quotedBody)
        return Result(
            quoted = true,
            body = replyPart,
            quotedMessage = parsed.third ?: quotedBody,
            quotedFromInstance = parsed.first,
            quotedFromUser = parsed.second,
            replyToMessageId = parsed.fourth
        )
    }

    private fun parseSingleLineQuote(line: String): Result? {
        if (line.isBlank()) return null
        val m = Regex("^(?:\\[([^\\]]+)]\\s+)?(.{1,64}?)\\s*>\\s*(.+)$").find(line) ?: return null
        val source = m.groupValues.getOrNull(1)?.trim().orEmpty().ifBlank { null }
        val user = m.groupValues.getOrNull(2)?.trim().orEmpty().ifBlank { null }
        val body = m.groupValues.getOrNull(3)?.trim().orEmpty()
        if (body.isBlank()) return null
        val preview = if (user.isNullOrBlank()) "—" else "→ ${user.trim()}"
        return Result(
            quoted = true,
            body = body,
            quotedMessage = preview,
            quotedFromInstance = source,
            quotedFromUser = user,
            replyToMessageId = null
        )
    }

    private fun parseHeaderOnlyQuote(quotePart: String, replyPart: String): Result? {
        val base = quotePart.removePrefix(">").trimStart()
        val m = Regex("""^\[([^\]]+)]\s+([^:]{1,64}):\s*$""").find(base) ?: return null
        if (replyPart.isBlank()) return null
        return Result(
            quoted = true,
            body = replyPart,
            quotedMessage = "—",
            quotedFromInstance = m.groupValues[1].trim(),
            quotedFromUser = m.groupValues[2].trim(),
            replyToMessageId = null
        )
    }

    private fun parseQuotedLine(line: String): Quad<String?, String?, String?, String?> {
        var base = line
        var messageId: String? = null
        val idMatch = Regex("\\s*\\[id:([a-zA-Z0-9_\\-:.]{6,128})]\\s*$").find(line)
        if (idMatch != null) {
            messageId = idMatch.groupValues[1].trim()
            base = line.substring(0, idMatch.range.first).trimEnd()
        }

        val bracket = Regex("^\\[([^\\]]+)]\\s+([^:>]{1,64})\\s*:\\s*(.+)$").find(base)
        if (bracket != null) {
            return Quad(
                bracket.groupValues[1].trim(),
                bracket.groupValues[2].trim(),
                bracket.groupValues[3].trim(),
                messageId
            )
        }

        val plain = Regex("^([^:>]{1,64})\\s*:\\s*(.+)$").find(base)
        if (plain != null) {
            return Quad(null, plain.groupValues[1].trim(), plain.groupValues[2].trim(), messageId)
        }

        return Quad(null, null, base.ifBlank { null }, messageId)
    }

    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
