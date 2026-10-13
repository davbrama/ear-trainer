package dev.eartrainer.data

import dev.eartrainer.core.model.FeedbackEntry
import dev.eartrainer.core.model.Interval
import dev.eartrainer.core.model.IntervalMemory
import dev.eartrainer.core.model.PracticeSettings
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class JsonStores(root: File) {
    private val dir = File(root, "eartrainer").apply { mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun loadSettings(): PracticeSettings = read(File(dir, "settings.json"), PracticeSettings())

    fun saveSettings(settings: PracticeSettings) {
        write(File(dir, "settings.json"), settings)
    }

    fun loadProgress(): Map<Interval, IntervalMemory> {
        val file = read(File(dir, "progress.json"), ProgressFile())
        return file.memories.mapNotNull { (name, memory) ->
            runCatching { Interval.valueOf(name) to memory }.getOrNull()
        }.toMap()
    }

    fun saveProgress(memories: Map<Interval, IntervalMemory>) {
        val file = ProgressFile(memories.mapKeys { it.key.name })
        write(File(dir, "progress.json"), file)
    }

    fun loadFeedback(): List<FeedbackEntry> = read(File(dir, "feedback.json"), FeedbackFile()).entries

    fun appendFeedback(entry: FeedbackEntry): List<FeedbackEntry> {
        val next = loadFeedback() + entry
        write(File(dir, "feedback.json"), FeedbackFile(next))
        return next
    }

    fun exportFeedback(entries: List<FeedbackEntry>): File {
        val file = File(dir, "feedback-export.txt")
        val body = buildString {
            appendLine("Ear Trainer feedback")
            if (entries.isEmpty()) appendLine("(empty)")
            entries.forEach { entry ->
                appendLine("---")
                appendLine(entry.createdAtEpochMs.toString())
                appendLine(entry.text)
            }
        }
        file.writeText(body)
        return file
    }

    private inline fun <reified T> read(file: File, fallback: T): T {
        if (!file.exists()) return fallback
        return runCatching { json.decodeFromString(serializer<T>(), file.readText()) }.getOrDefault(fallback)
    }

    private inline fun <reified T> write(file: File, value: T) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer<T>(), value))
    }
}

@Serializable
private data class ProgressFile(val memories: Map<String, IntervalMemory> = emptyMap())

@Serializable
private data class FeedbackFile(val entries: List<FeedbackEntry> = emptyList())
