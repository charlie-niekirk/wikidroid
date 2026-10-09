package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class McVersionTest {
    @Test
    fun nativeIdsCountUpFromBeta17() {
        assertThat(McVersion.B1_7.nativeId).isEqualTo(1)
        assertThat(McVersion.entries.map { it.nativeId }).containsExactlyElementsIn(1..McVersion.entries.size).inOrder()
    }

    @Test
    fun labelsAreUnique() {
        assertThat(McVersion.entries.map { it.label }).containsNoDuplicates()
    }

    @Test
    fun newestIsTheLastEntry() {
        assertThat(McVersion.newest).isEqualTo(McVersion.entries.last())
    }

    @Test
    fun theOverworldExistsEverywhere() {
        assertThat(McVersion.entries.all { it.supports(Dimension.OVERWORLD) }).isTrue()
    }

    @Test
    fun theNetherStartsAt116() {
        assertThat(McVersion.V1_15.supports(Dimension.NETHER)).isFalse()
        assertThat(McVersion.V1_16_1.supports(Dimension.NETHER)).isTrue()
    }

    @Test
    fun theEndStartsAt19() {
        assertThat(McVersion.V1_8.supports(Dimension.END)).isFalse()
        assertThat(McVersion.V1_9.supports(Dimension.END)).isTrue()
    }

    @Test
    fun aWorldCannotUseADimensionItsVersionLacks() {
        assertThrows(IllegalArgumentException::class.java) { MapWorld(1L, McVersion.V1_12, Dimension.NETHER) }
        assertThat(MapWorld(1L, McVersion.V1_12, Dimension.END).dimension).isEqualTo(Dimension.END)
    }
}
