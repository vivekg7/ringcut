package com.crylo.ringcut.core

/**
 * Turns user- or source-supplied names into safe file names. MediaStore would reject or
 * silently mangle some characters, so it is done here where it can be tested.
 */
object FileNaming {
    /** AAC in an MP4 container: the only audio format Android can both encode and play everywhere. */
    const val EXTENSION = ".m4a"
    const val MIME_TYPE = "audio/mp4"
    const val MAX_LENGTH = 80
    const val FALLBACK = "Tone"

    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val whitespace = Regex("""\s+""")

    /** Replaces characters no filesystem accepts, collapses whitespace, caps the length. */
    fun sanitize(name: String): String {
        val cleaned = name.replace(illegal, " ").replace(whitespace, " ").trim().trim('.', ' ')
        return cleaned.take(MAX_LENGTH).trimEnd('.', ' ')
    }

    /**
     * The source's display name without its extension, as the starting title. The Photo
     * Picker hides real file names behind numeric IDs ("48.mp4"), which make useless
     * titles, so those fall back to [fallback] too.
     */
    fun titleFromSource(displayName: String?, fallback: String = FALLBACK): String {
        val name = displayName.orEmpty()
        val dot = name.lastIndexOf('.')
        val base = sanitize(if (dot > 0) name.substring(0, dot) else name)
        return if (base.isEmpty() || base.all { it.isDigit() }) sanitize(fallback).ifEmpty { FALLBACK } else base
    }

    /** File name for a title, always ending in [EXTENSION]. */
    fun fileName(title: String): String {
        var base = sanitize(title)
        if (base.endsWith(EXTENSION, ignoreCase = true)) base = sanitize(base.dropLast(EXTENSION.length))
        return base.ifEmpty { FALLBACK } + EXTENSION
    }
}
