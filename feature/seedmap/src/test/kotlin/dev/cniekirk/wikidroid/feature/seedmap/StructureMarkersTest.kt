package dev.cniekirk.wikidroid.feature.seedmap

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import org.junit.Test

class StructureMarkersTest {
    @Test
    fun everyStructureHasItsOwnColourAndShape() {
        val styles = StructureType.entries.map { it.markerStyle() }

        assertThat(styles.toSet()).hasSize(StructureType.entries.size)
    }

    @Test
    fun noTwoStructuresShareAShape() {
        // Colour alone must never be the key, so types that share a colour differ in shape.
        val byColour = StructureType.entries.groupBy { it.markerStyle().color }

        byColour.values.forEach { group ->
            assertThat(group.map { it.markerStyle().shape }.toSet()).hasSize(group.size)
        }
    }

    @Test
    fun spawnAndStrongholdsLookLikeNoStructure() {
        val others = StructureType.entries.filter { it != StructureType.STRONGHOLD }.map { it.markerStyle().shape }

        assertThat(StructureType.STRONGHOLD.markerStyle().shape).isEqualTo(MarkerShape.Ring)
        assertThat(others).doesNotContain(MarkerShape.Ring)
        assertThat(others).doesNotContain(SpawnMarkerStyle.shape)
    }
}
