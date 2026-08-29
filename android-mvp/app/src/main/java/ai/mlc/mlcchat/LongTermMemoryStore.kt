package ai.mlc.mlcchat

import android.content.Context
import java.io.File

/** Small, human-readable long-term memory store. Only model-extracted facts are written. */
class LongTermMemoryStore(context: Context) {
    private val file = File(context.getExternalFilesDir(null), "agent-memory.md")
    fun read(): String = if (file.isFile) file.readText().takeLast(MAX_CHARS) else ""
    fun append(extracted: String) {
        val clean = extracted.lines().filter { it.trim().startsWith("-") }.joinToString("\n").take(MAX_ENTRY)
        if (clean.isBlank()) return
        file.parentFile?.mkdirs()
        file.writeText("# Long-term memory\n${(read() + "\n" + clean).takeLast(MAX_CHARS)}\n")
    }
    companion object { private const val MAX_CHARS = 6000; private const val MAX_ENTRY = 1200 }
}
