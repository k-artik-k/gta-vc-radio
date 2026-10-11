package com.kartikeya.vcradio.radio

import android.content.res.AssetManager
import java.util.Locale

/** Builds the catalog from the installed assets, so adding an MP3 needs no Kotlin edit. */
object StationCatalog {
    private val supportedExtensions = setOf("mp3", "m4a")
    private val familiarOrder = listOf(
        "WILDSTYLE", "FLASH", "KCHAT", "FEVER", "VROCK", "VCPR", "ESPANT", "EMOTION", "WAVE",
    )

    fun fromAssets(assetManager: AssetManager): List<Station> =
        discover(assetManager, "stations")
            .filter { it.substringAfterLast('.', "").lowercase(Locale.ROOT) in supportedExtensions }
            .map { assetPath ->
                val fileName = assetPath.substringAfterLast('/')
                Station(
                    // Keep this identity stable if a bundled source is transcoded from MP3 to M4A.
                    id = assetPath.substringBeforeLast('.').lowercase(Locale.ROOT),
                    assetPath = assetPath,
                    displayName = titleFor(fileName),
                    preset = 0,
                )
            }
            .sortedWith(compareBy<Station>({ knownRank(it.assetPath) }, { it.displayName.lowercase(Locale.ROOT) }, { it.assetPath }))
            .mapIndexed { index, station -> station.copy(preset = index + 1) }

    private fun discover(assets: AssetManager, path: String): List<String> {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) return listOf(path)
        return children.flatMap { child -> discover(assets, "$path/$child") }
    }

    private fun knownRank(path: String): Int {
        val token = path.substringAfterLast('/').substringBeforeLast('.').uppercase(Locale.ROOT)
        return familiarOrder.indexOfFirst { token.contains(it) }.let { if (it < 0) Int.MAX_VALUE else it }
    }

    private fun titleFor(fileName: String): String {
        val token = fileName.substringBeforeLast('.').uppercase(Locale.ROOT)
        return when {
            "WILD" in token -> "Wildstyle Pirate Radio"
            "FLASH" in token -> "Flash FM"
            "VROCK" in token || "V-ROCK" in token -> "V-Rock"
            "FEVER" in token -> "Fever 105"
            "KCHAT" in token || "K-CHAT" in token -> "K-Chat"
            "VCPR" in token -> "VCPR"
            "ESPANT" in token -> "Radio Espantoso"
            "EMOTION" in token -> "Emotion 98.3"
            "WAVE" in token -> "Wave 103"
            else -> fileName.substringBeforeLast('.').replace(Regex("[_-]+"), " ")
                .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .joinToString(" ") { it.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase) }
                .ifBlank { "Unknown Station" }
        }
    }
}
