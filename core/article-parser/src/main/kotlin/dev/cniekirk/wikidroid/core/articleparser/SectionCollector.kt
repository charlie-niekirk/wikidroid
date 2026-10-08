package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ArticleSection
import dev.cniekirk.wikidroid.core.model.ContentBlock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Groups the flat block stream into sections: the lead (no heading), then one per level-2 heading. */
internal class SectionCollector : BlockSink {
    private val sections = mutableListOf<ArticleSection>()
    private var heading: ContentBlock.Heading? = null
    private var blocks = mutableListOf<ContentBlock>()

    override fun add(block: ContentBlock) {
        blocks += block
    }

    override fun startSection(heading: ContentBlock.Heading) {
        closeSection()
        this.heading = heading
    }

    fun finish(): ImmutableList<ArticleSection> {
        closeSection()
        return sections.toImmutableList()
    }

    private fun closeSection() {
        // An empty lead (the page opens with a heading) is not worth a section.
        if (heading != null || blocks.isNotEmpty()) {
            sections += ArticleSection(heading, blocks.toImmutableList())
        }
        heading = null
        blocks = mutableListOf()
    }
}
