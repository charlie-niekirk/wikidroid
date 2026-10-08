package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.CraftingSlot
import dev.cniekirk.wikidroid.core.model.Link
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.nodes.Element

/** Reads the `span.mcui` inventory widgets the wiki draws recipes with. */
internal object CraftingGridParser {
    private const val GRID_SIZE = 3
    private const val ARROW = " → "
    private const val SLOT_SELECTOR = ".invslot-item"

    /** A `mcui-Crafting_Table` widget as a 3x3 grid. Missing rows or slots are padded with empty slots. */
    fun grid(widget: Element): ContentBlock.CraftingGrid {
        val rows = widget.select(".mcui-input .mcui-row")
        val slots: List<CraftingSlot?> =
            if (rows.isNotEmpty()) {
                rows.take(GRID_SIZE).flatMap { row -> row.select(".invslot").map(::slot).padded() }
            } else {
                widget.select(".mcui-input .invslot").map(::slot)
            }
        val cells = slots.padded(GRID_SIZE * GRID_SIZE)
        val output = widget.selectFirst(".mcui-output .invslot")?.let(::slot)
        return ContentBlock.CraftingGrid(
            slots = cells.chunked(GRID_SIZE).map { it.toImmutableList() }.toImmutableList(),
            output = output,
        )
    }

    /** A single `.invslot`, or `null` when it is empty. For animated slots, the first alternative wins. */
    fun slot(invslot: Element): CraftingSlot? {
        val item = invslot.selectFirst(SLOT_SELECTOR) ?: return null
        val img = item.selectFirst("img")
        val anchor = item.selectFirst("a[href]")
        val name =
            anchor?.attr("title")?.takeIf { it.isNotBlank() }
                ?: item.select("[title]").firstOrNull { !it.hasClass("invslot-stacksize") }?.attr("title")
                ?: img?.attr("alt")?.let(::nameFromAlt)
                ?: item.text()
        if (name.isBlank()) return null
        val count = (item.selectFirst(".invslot-stacksize") ?: invslot.selectFirst(".invslot-stacksize"))?.text()
        return CraftingSlot(
            name = name.trim(),
            imageUrl = img?.attr("src")?.takeIf { it.isNotBlank() }?.let(WikiUrls::absolute),
            count = count?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 1,
            link = anchor?.let(WikiUrls::link) as? Link.Internal,
        )
    }

    /** Text standing in for a widget inside running text or a table cell, which can't hold a grid. */
    fun summary(widget: Element): String {
        val output = widget.selectFirst(".mcui-output .invslot")
        val inputs =
            widget
                .select(".invslot")
                .filter { it !== output && it.parents().none { parent -> parent === output } }
                .mapNotNull(::slot)
                .map(::label)
                .distinct()
        val result = output?.let(::slot)?.let(::label)
        return when {
            inputs.isEmpty() -> result.orEmpty()
            result == null -> inputs.joinToString(", ")
            else -> inputs.joinToString(", ") + ARROW + result
        }
    }

    fun label(slot: CraftingSlot): String = if (slot.count > 1) "${slot.name} ×${slot.count}" else slot.name

    // "Invicon Oak Planks.png: Inventory sprite for Oak Planks in Minecraft ..." is only a last resort.
    private fun nameFromAlt(alt: String): String? =
        alt
            .substringBefore(':')
            .removeSuffix(".png")
            .removePrefix("Invicon ")
            .takeIf { it.isNotBlank() }

    private fun List<CraftingSlot?>.padded(size: Int = GRID_SIZE): ImmutableList<CraftingSlot?> =
        (take(size) + List((size - this.size).coerceAtLeast(0)) { null }).toImmutableList()
}
