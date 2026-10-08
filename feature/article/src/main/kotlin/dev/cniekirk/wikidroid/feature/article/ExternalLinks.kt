package dev.cniekirk.wikidroid.feature.article

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

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
