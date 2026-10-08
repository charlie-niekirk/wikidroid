package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.model.ArticleSummary
import dev.cniekirk.wikidroid.core.model.Category
import dev.cniekirk.wikidroid.core.model.CategoryMember
import dev.cniekirk.wikidroid.core.network.dto.PageDto
import dev.cniekirk.wikidroid.core.network.dto.RestPageDto
import org.jsoup.Jsoup

/** Namespace id of `Category:` pages. */
internal const val CATEGORY_NAMESPACE = 14

internal fun PageDto.toSummary(): ArticleSummary =
    ArticleSummary(
        title = title,
        description = extract?.trim()?.takeIf { it.isNotEmpty() },
        thumbnailUrl = thumbnail?.source,
        pageUrl = fullUrl,
    )

/** Autocomplete rows carry a short `description` when the wiki has one, else a highlighted HTML `excerpt`. */
internal fun RestPageDto.toSummary(): ArticleSummary =
    ArticleSummary(
        title = title,
        description = (description ?: excerpt?.let(::stripMarkup))?.trim()?.takeIf { it.isNotEmpty() && it != title },
        thumbnailUrl = thumbnail?.url,
    )

internal fun PageDto.toMember(): CategoryMember =
    if (ns == CATEGORY_NAMESPACE) {
        CategoryMember.Subcategory(Category.fromPageTitle(title))
    } else {
        CategoryMember.Page(toSummary())
    }

/** Subcategories first, then pages, each group by title. The server orders a batch by page id. */
internal val categoryMemberOrder: Comparator<CategoryMember> =
    compareBy<CategoryMember> { it is CategoryMember.Page }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortKey }

private val CategoryMember.sortKey: String
    get() =
        when (this) {
            is CategoryMember.Page -> summary.title
            is CategoryMember.Subcategory -> category.displayName
        }

/** `Dirt_Block` and `Dirt Block` are the same page; the database keys on the spaced form. */
internal fun normalizeTitle(title: String): String = title.trim().replace('_', ' ')

/** `displaytitle` is HTML such as `<span class="mw-page-title-main">Diamond</span>`. */
internal fun stripMarkup(html: String): String = Jsoup.parse(html).text()
