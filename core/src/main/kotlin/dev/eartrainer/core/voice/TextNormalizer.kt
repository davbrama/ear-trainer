package dev.eartrainer.core.voice

object TextNormalizer {
    private val wakePhrases = listOf(
        "hey ear trainer",
        "okay ear trainer",
        "ok ear trainer",
        "ear trainer",
        "היי מאמן שמיעה",
        "מאמן שמיעה",
        "היי מאמן",
    )

    private val prefixes = listOf(
        "could you please",
        "could you",
        "can you please",
        "can you",
        "would you",
        "i think its",
        "i think it is",
        "i think",
        "i believe",
        "it is a",
        "its a",
        "it is",
        "its",
        "that's a",
        "thats a",
        "that is a",
        "that's",
        "thats",
        "that is",
        "this is",
        "please",
        "maybe",
        "נראה לי שזה",
        "נראה לי",
        "אני חושבת שזה",
        "אני חושב שזה",
        "אני חושבת",
        "אני חושב",
        "אפשר בבקשה",
        "בבקשה",
        "אפשר",
        "the",
        "a",
        "an",
        "זה",
        "זאת",
        "אולי",
    ).sortedByDescending { it.length }

    /** Words that are fillers at the start of an answer, but real words at the end of a command. */
    private val prefixOnly = setOf("the", "a", "an", "זה", "זאת", "אולי", "maybe")

    fun normalize(raw: String): String {
        val stripped = buildString {
            for (ch in raw.trim()) {
                if (ch in '\u0591'..'\u05C7') continue
                if (ch == '\u05F3' || ch == '\u05F4' || ch == '\'') continue
                append(ch)
            }
        }
        return stripped
            .lowercase()
            .replace('־', ' ')
            .replace(Regex("[.,!?;:\"()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun forAnswer(raw: String): String {
        var text = normalize(raw)
        text = text.replace(Regex("\\b1st\\b"), "first")
        text = text.replace(Regex("\\b2nd\\b"), "second")
        text = text.replace(Regex("\\b3rd\\b"), "third")
        text = text.replace(Regex("\\b4th\\b"), "fourth")
        text = text.replace(Regex("\\b5th\\b"), "fifth")
        text = text.replace(Regex("\\b6th\\b"), "sixth")
        text = text.replace(Regex("\\b7th\\b"), "seventh")
        text = text.replace(Regex("\\b8th\\b"), "eighth")
        return text
    }

    fun stripWake(normalized: String): Pair<String, Boolean> {
        val hit = wakePhrases.firstOrNull { phrase ->
            normalized == phrase || normalized.startsWith("$phrase ")
        } ?: return normalized to false
        return normalized.removePrefix(hit).trim() to true
    }

    fun stripFillers(normalized: String): String {
        var text = normalized
        var changed = true
        while (changed) {
            changed = false
            val prefix = prefixes.firstOrNull { text == it || text.startsWith("$it ") }
            if (prefix != null && text != prefix) {
                text = text.removePrefix(prefix).trim()
                changed = true
            }
            val suffix = prefixes.firstOrNull { it !in prefixOnly && text.endsWith(" $it") }
            if (suffix != null) {
                text = text.removeSuffix(suffix).trim()
                changed = true
            }
        }
        return text
    }
}
