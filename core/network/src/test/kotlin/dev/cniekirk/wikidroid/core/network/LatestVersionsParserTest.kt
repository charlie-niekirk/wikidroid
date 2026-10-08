package dev.cniekirk.wikidroid.core.network

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.model.LatestVersions
import org.junit.Test

class LatestVersionsParserTest {
    @Test
    fun `reads the label of each link in order`() {
        val wikitext =
            "[[Java Edition_26.3|26.3]]|[[Java Edition_26.4 Snapshot 3|26.4 Snapshot 3]]|" +
                "[[Bedrock Edition_26.52|26.52]]|[[Bedrock Edition_Preview 26.60.30|Preview 26.60.30]]"

        assertThat(parseLatestVersions(wikitext))
            .isEqualTo(LatestVersions("26.3", "26.4 Snapshot 3", "26.52", "Preview 26.60.30"))
    }

    @Test
    fun `an unlabelled link uses its target`() {
        assertThat(parseLatestVersions("[[26.3]]|[[26.4]]|[[1.0]]|[[2.0]]").java).isEqualTo("26.3")
    }

    @Test
    fun `plain text without links is split on the separator`() {
        assertThat(parseLatestVersions("26.3|26.4 Snapshot 3|26.52|Preview 26.60.30"))
            .isEqualTo(LatestVersions("26.3", "26.4 Snapshot 3", "26.52", "Preview 26.60.30"))
    }

    @Test
    fun `missing and blank entries are null`() {
        assertThat(parseLatestVersions("[[A|1.0]]|| ")).isEqualTo(LatestVersions("1.0", null, null, null))
        assertThat(parseLatestVersions("")).isEqualTo(LatestVersions(null, null, null, null))
    }
}
