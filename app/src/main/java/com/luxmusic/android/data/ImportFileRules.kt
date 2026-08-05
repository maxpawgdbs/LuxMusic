package com.luxmusic.android.data

internal object ImportFileRules {
    const val MAX_ZIP_ENTRIES = 1_000
    const val MAX_ZIP_ENTRY_BYTES = 512L * 1_024L * 1_024L
    const val MAX_ZIP_TOTAL_BYTES = 2L * 1_024L * 1_024L * 1_024L

    private val supportedAudioExtensions = setOf(
        "mp3",
        "m4a",
        "aac",
        "flac",
        "wav",
        "ogg",
        "opus",
        "webm",
        "mp4",
    )

    fun supportedZipAudioName(
        entryName: String,
        isDirectory: Boolean,
        declaredSize: Long,
    ): String? {
        if (isDirectory || declaredSize > MAX_ZIP_ENTRY_BYTES) return null

        val displayName = entryName
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()
        if (displayName.isBlank() || displayName == "." || displayName == "..") return null

        val extension = displayName.substringAfterLast('.', "").lowercase()
        return displayName.takeIf { extension in supportedAudioExtensions }
    }
}
