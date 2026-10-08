package dev.cniekirk.wikidroid.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Only web pages are opened from content; other schemes (`intent:`, `javascript:`, `file:`) are dropped. */
fun isWebUrl(url: String): Boolean {
    val scheme = url.toUri().scheme?.lowercase()
    return scheme == "http" || scheme == "https"
}

/** Opens [url] in a Custom Tab, or in whatever browser is installed if Custom Tabs aren't available. */
fun Context.openWebUrl(url: String) {
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
