package dev.cniekirk.wikidroid

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Swaps [WikiDroidApp] for [TestWikiApp], so the whole app runs against a local `MockWebServer`. */
class WikiDroidTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TestWikiApp::class.java.name, context)
}
