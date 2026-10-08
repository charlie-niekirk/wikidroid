package dev.cniekirk.wikidroid.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class ArticleTest {
    private val usage = ContentBlock.Heading(level = 2, text = "Usage", anchor = "Usage")
    private val crafting = ContentBlock.Heading(level = 3, text = "Crafting", anchor = "Crafting")
    private val trivia = ContentBlock.Heading(level = 2, text = "Trivia", anchor = "Trivia")

    @Test
    fun tableOfContents_listsSectionAndNestedHeadingsInOrder() {
        val article =
            Article(
                title = "Diamond",
                displayTitle = "Diamond",
                revisionId = 1,
                sections =
                    persistentListOf(
                        ArticleSection(
                            heading = null,
                            blocks = persistentListOf(ContentBlock.Paragraph(RichText.of("Lead"))),
                        ),
                        ArticleSection(heading = usage, blocks = persistentListOf(crafting)),
                        ArticleSection(heading = trivia, blocks = persistentListOf()),
                    ),
            )

        assertThat(article.tableOfContents).containsExactly(usage, crafting, trivia).inOrder()
    }

    @Test
    fun category_pageTitleRoundTrips() {
        val category = Category.fromPageTitle("Category:Hostile_mobs")

        assertThat(category.name).isEqualTo("Hostile_mobs")
        assertThat(category.displayName).isEqualTo("Hostile mobs")
        assertThat(category.pageTitle).isEqualTo("Category:Hostile_mobs")
    }
}
