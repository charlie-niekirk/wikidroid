package dev.cniekirk.wikidroid.feature.article

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Only web pages are opened from article content; other schemes (`intent:`, `javascript:`, `file:`) are dropped. */
internal fun isWebUrl(url: String): Boolean {
    val scheme = url.toUri().scheme?.lowercase()
    return scheme == "http" || scheme == "https"
}

/** Opens [url] in a Custom Tab, or in whatever browser is installed if Custom Tabs aren't available. */
internal fun Context.openWebUrl(url: String) {
    if (!isWebUrl(url)) return
    val uri = url.toUri()
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        // No browser can open the page; there is nothing more to offer.
    }
}

/** Offers the system share sheet with the article's title and address. */
internal fun Context.shareArticle(
    title: String,
    url: String,
    chooserTitle: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    try {
        startActivity(Intent.createChooser(send, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device accepts shared text.
    }
}
