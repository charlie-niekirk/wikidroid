package dev.cniekirk.wikidroid.core.network

import dev.cniekirk.wikidroid.core.model.LatestVersions

// Positions in LATEST_VERSIONS_TEMPLATE.
private const val JAVA = 0
private const val JAVA_SNAPSHOT = 1
private const val BEDROCK = 2
private const val BEDROCK_PREVIEW = 3

/** `[[Page|label]]` or `[[label]]`; group 1 is the label. */
private val WikiLink = Regex("""\[\[(?:[^\]|]*\|)?([^\]]*)]]""")

/** The `{{Version|...}}` calls sent to `expandtemplates`, in the order [parseLatestVersions] expects. */
internal const val LATEST_VERSIONS_TEMPLATE =
    "{{Version|java}}|{{Version|java-snap}}|{{Version|bedrock}}|{{Version|bedrock-preview}}"

/**
 * Reads the expanded [LATEST_VERSIONS_TEMPLATE], e.g. `[[Java Edition_26.3|26.3]]|[[Java Edition_26.4
 * Snapshot 3|26.4 Snapshot 3]]|...`. Entries that are missing or blank come back as `null`.
 */
internal fun parseLatestVersions(wikitext: String): LatestVersions {
    val raw =
        if ("[[" in wikitext) {
            WikiLink.findAll(wikitext).map { it.groupValues[1] }.toList()
        } else {
            wikitext.split('|')
        }
    val labels = raw.map { it.trim().ifEmpty { null } }

    return LatestVersions(
        java = labels.getOrNull(JAVA),
        javaSnapshot = labels.getOrNull(JAVA_SNAPSHOT),
        bedrock = labels.getOrNull(BEDROCK),
        bedrockPreview = labels.getOrNull(BEDROCK_PREVIEW),
    )
}
