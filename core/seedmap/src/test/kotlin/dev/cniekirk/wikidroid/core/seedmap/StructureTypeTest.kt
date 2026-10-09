package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StructureTypeTest {
    @Test
    fun `native ids are unique`() {
        assertThat(StructureType.entries.map { it.nativeId }.toSet()).hasSize(StructureType.entries.size)
        assertThat(StructureType.entries.map { it.nativeName }.toSet()).hasSize(StructureType.entries.size)
    }

    @Test
    fun `a structure appears in the version that added it`() {
        assertThat(StructureType.VILLAGE.isAvailableIn(McVersion.B1_7)).isFalse()
        assertThat(StructureType.VILLAGE.isAvailableIn(McVersion.B1_8)).isTrue()
        assertThat(StructureType.OCEAN_RUIN.isAvailableIn(McVersion.V1_12)).isFalse()
        assertThat(StructureType.OCEAN_RUIN.isAvailableIn(McVersion.V1_13)).isTrue()
        assertThat(StructureType.ANCIENT_CITY.isAvailableIn(McVersion.V1_19_2)).isTrue()
        assertThat(StructureType.ANCIENT_CITY.isAvailableIn(McVersion.V1_18)).isFalse()
        assertThat(StructureType.ABANDONED_CAMP.isAvailableIn(McVersion.V26_2)).isFalse()
        assertThat(StructureType.ABANDONED_CAMP.isAvailableIn(McVersion.V26_3)).isTrue()
    }

    @Test
    fun `nether structures need a version the generator can build the nether for`() {
        assertThat(StructureType.FORTRESS.isAvailableIn(McVersion.V1_15)).isFalse()
        assertThat(StructureType.FORTRESS.isAvailableIn(McVersion.V1_16_1)).isTrue()
        assertThat(StructureType.BASTION.isAvailableIn(McVersion.V1_16_1)).isTrue()
    }

    @Test
    fun `a structure only exists in its own dimension`() {
        val overworld = MapWorld(1, McVersion.V26_3, Dimension.OVERWORLD)
        val nether = overworld.copy(dimension = Dimension.NETHER)
        val end = overworld.copy(dimension = Dimension.END)

        assertThat(StructureType.availableIn(overworld)).contains(StructureType.VILLAGE)
        assertThat(StructureType.availableIn(overworld)).doesNotContain(StructureType.FORTRESS)
        assertThat(StructureType.availableIn(nether))
            .containsExactly(
                StructureType.RUINED_PORTAL_NETHER,
                StructureType.FORTRESS,
                StructureType.BASTION,
                StructureType.NETHER_FOSSIL,
            ).inOrder()
        assertThat(StructureType.availableIn(end))
            .containsExactly(StructureType.END_CITY, StructureType.END_GATEWAY)
            .inOrder()
    }

    @Test
    fun `strongholds are the only structure not found by region`() {
        assertThat(StructureType.entries.filterNot { it.isRegionBased }).containsExactly(StructureType.STRONGHOLD)
    }

    @Test
    fun `the oldest version has almost nothing and the newest has everything for its overworld`() {
        assertThat(StructureType.availableIn(MapWorld(1, McVersion.B1_7))).isEmpty()
        val newest = StructureType.availableIn(MapWorld(1, McVersion.newest))
        assertThat(
            newest,
        ).containsAtLeast(StructureType.TRIAL_CHAMBERS, StructureType.ABANDONED_CAMP, StructureType.STRONGHOLD)
    }
}
