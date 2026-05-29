package com.example.games.util

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.size - 1)
    val value = size / 1024.0.pow(digitGroups.toDouble())
    val unit = units[digitGroups]

    return if (unit == "GB" || unit == "TB") {
        if (value % 1.0 == 0.0) {
            String.format(Locale.ROOT, "%.0f %s", value, unit)
        } else {
            String.format(Locale.ROOT, "%.1f %s", value, unit)
        }
    } else {
        String.format(Locale.ROOT, "%.0f %s", value, unit)
    }
}

fun cleanGameName(name: String): String {
    return name.replace(Regex("\\s*\\(.*?\\)"), "").replace(Regex("\\s*\\[.*?\\]"), "").trim()
}

data class GameParsedInfo(
    val regions: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val version: String? = null,
    val disc: String? = null,
    val isKiosk: Boolean = false,
    val isDemo: Boolean = false,
    val isBeta: Boolean = false,
    val isSample: Boolean = false,
    val isPrototype: Boolean = false,
    val other: List<String> = emptyList()
)

fun parseGameInfo(name: String): GameParsedInfo {
    val regions = mutableListOf<String>()
    val languages = mutableListOf<String>()
    var version: String? = null
    var disc: String? = null
    var isKiosk = false
    var isDemo = false
    var isBeta = false
    var isSample = false
    var isPrototype = false
    val other = mutableListOf<String>()

    val bracketRegex = Regex("[\\(\\[](.*?)[\\)\\]]")
    val matches = bracketRegex.findAll(name)

    val regionList = listOf("USA", "Europe", "Japan", "Asia", "World", "Australia", "Canada", "Korea", "China", "Brazil", "France", "Germany", "Italy", "Spain", "UK", "Jap", "Ger", "Fre", "Spa", "Ita")
    val langMap = mapOf(
        "En" to "EN", "Fr" to "FR", "De" to "DE", "Es" to "ES", "It" to "IT", "Nl" to "NL", "Pt" to "PT", "Sv" to "SV", "No" to "NO", "Da" to "DA", "Fi" to "FI",
        "Ja" to "JA", "Ko" to "KO", "Zh" to "ZH"
    )

    matches.forEach { match ->
        val content = match.groupValues[1]
        val parts = content.split(",").map { it.trim() }
        
        var handled = false

        if (content.contains("Kiosk", ignoreCase = true)) {
            isKiosk = true
            handled = true
        }
        if (content.contains("Demo", ignoreCase = true)) {
            isDemo = true
            handled = true
        }
        if (content.contains("Beta", ignoreCase = true)) {
            isBeta = true
            handled = true
        }
        if (content.contains("Sample", ignoreCase = true)) {
            isSample = true
            handled = true
        }
        if (content.contains("Prototype", ignoreCase = true)) {
            isPrototype = true
            handled = true
        }

        if (content.contains("Disc", ignoreCase = true) || content.contains("Disk", ignoreCase = true)) {
            disc = content
            handled = true
        } 
        else if (content.lowercase().startsWith("v") && content.getOrNull(1)?.isDigit() == true || content.contains("version", ignoreCase = true)) {
            val vPart = if (content.lowercase().startsWith("v")) {
                content.substring(1).trim()
            } else {
                content.lowercase().substringAfter("version").trim()
            }
            val formattedV = vPart.toDoubleOrNull()?.let {
                if (it % 1.0 == 0.0) String.format(Locale.ROOT, "%.1f", it)
                else vPart
            } ?: vPart
            version = formattedV
            handled = true
        }
        else {
            val potentialRegions = parts.filter { part -> regionList.any { it.equals(part, ignoreCase = true) } }
            if (potentialRegions.isNotEmpty()) {
                regions.addAll(potentialRegions)
                handled = true
            }

            val potentialLangs = parts.mapNotNull { part -> 
                langMap.entries.find { it.key.equals(part, ignoreCase = true) || it.value.equals(part, ignoreCase = true) }?.value
            }
            if (potentialLangs.isNotEmpty()) {
                languages.addAll(potentialLangs)
                handled = true
            }
        }

        if (!handled) {
            other.add(content)
        }
    }

    return GameParsedInfo(
        regions = regions.distinct(),
        languages = languages.distinct(),
        version = version,
        disc = disc,
        isKiosk = isKiosk,
        isDemo = isDemo,
        isBeta = isBeta,
        isSample = isSample,
        isPrototype = isPrototype,
        other = other
    )
}
