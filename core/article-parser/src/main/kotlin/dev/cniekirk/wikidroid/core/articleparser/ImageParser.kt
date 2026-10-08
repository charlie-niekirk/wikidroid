package dev.cniekirk.wikidroid.core.articleparser

import dev.cniekirk.wikidroid.core.model.ContentBlock
import dev.cniekirk.wikidroid.core.model.InlineImage
import dev.cniekirk.wikidroid.core.model.RichText
import kotlinx.collections.immutable.toImmutableList
import org.jsoup.nodes.Element

internal object ImageParser {
    /** An `<img>` as an [ContentBlock.Image], or `null` when it has no source. */
    fun image(
        img: Element,
        caption: RichText? = null,
    ): ContentBlock.Image? {
        val src = img.attr("src").trim()
        if (src.isEmpty()) return null
        return ContentBlock.Image(
            url = WikiUrls.absolute(src),
            width = img.intAttr("width"),
            height = img.intAttr("height"),
            caption = caption?.takeUnless { it.isBlank },
            altText = img.attr("alt").trim().takeIf { it.isNotEmpty() },
            pixelated = isPixelArt(img),
        )
    }

    /** An `<img>` inside running text, such as an item sprite, or `null` when it has no source. */
    fun inline(img: Element): InlineImage? {
        val src = img.attr("src").trim()
        if (src.isEmpty()) return null
        return InlineImage(
            url = WikiUrls.absolute(src),
            width = img.intAttr("width"),
            height = img.intAttr("height"),
            pixelated = isPixelArt(img),
        )
    }

    /** A `<figure typeof="mw:File/Thumb">`: the image plus its `<figcaption>`. */
    fun figure(figure: Element): ContentBlock.Image? {
        val img = figure.selectFirst("img") ?: return null
        val caption = figure.selectFirst("figcaption")?.let(InlineParser::richText)
        return image(img, caption)
    }

    /** A bare `<span typeof="mw:File">` sitting between blocks. */
    fun file(wrapper: Element): ContentBlock.Image? = wrapper.selectFirst("img")?.let { image(it) }

    /** A `<ul class="gallery">`; each `<li>` holds a thumbnail and a `.gallerytext` caption. */
    fun gallery(list: Element): ContentBlock.Gallery? {
        val images =
            list.children().mapNotNull { box ->
                val img = box.selectFirst("img") ?: return@mapNotNull null
                image(img, box.selectFirst(".gallerytext")?.let(InlineParser::richText))
            }
        return images.takeIf { it.isNotEmpty() }?.let { ContentBlock.Gallery(it.toImmutableList()) }
    }

    /** Sprites and pixel art must be scaled without smoothing. MediaWiki marks them with these classes. */
    private fun isPixelArt(img: Element): Boolean = img.closest(".pixel-image, .sprite-file, .pixelated") != null
}
