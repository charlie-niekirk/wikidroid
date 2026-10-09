package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps the Kotlin tables ([StructureType], [BiomeCatalog]) and the pinned cubiomes submodule in step. */
@RunWith(AndroidJUnit4::class)
class NativeContractTest {
    @Test
    fun everyStructureHasTheNameCubiomesGivesIt() {
        for (type in StructureType.entries) {
            assertThat(NativeSeedMap.nativeStructureName(type.nativeId)).isEqualTo(type.nativeName)
        }
    }

    @Test
    fun theAvailabilityTableAgreesWithCubiomesForEveryVersionAndDimension() {
        for (type in StructureType.entries.filter { it.isRegionBased }) {
            for (version in McVersion.entries) {
                for (dimension in Dimension.entries) {
                    val expected = type.dimension == dimension && type.isAvailableIn(version)
                    val actual =
                        NativeSeedMap.nativeStructureSupported(
                            type.nativeId,
                            version.nativeId,
                            dimension.nativeId,
                        )

                    assertThat(actual).isEqualTo(expected)
                }
            }
        }
    }

    @Test
    fun structuresTheMapDoesNotShowAreRefusedByNativeCode() {
        // cubiomes would call exit() for some of these, so the native whitelist is what keeps the app alive.
        val listed = StructureType.entries.map { it.nativeId }.toSet()
        for (id in -2..40) {
            if (id in listed) continue
            for (version in McVersion.entries) {
                for (dimension in Dimension.entries) {
                    assertThat(
                        NativeSeedMap.nativeStructureSupported(id, version.nativeId, dimension.nativeId),
                    ).isFalse()
                }
            }
        }
    }

    @Test
    fun strongholdsAreNotARegionStructureNativeCodeKnows() {
        val stronghold = StructureType.STRONGHOLD
        assertThat(NativeSeedMap.nativeStructureSupported(stronghold.nativeId, McVersion.newest.nativeId, 0)).isFalse()
    }

    @Test
    fun theCatalogHoldsEveryBiomeIdAnyVersionCanReturn() {
        val fromNative = mutableMapOf<Int, String>()
        for (version in McVersion.entries) {
            for (id in 0..255) {
                // Oldest to newest, so a later version's name replaces an older one's.
                NativeSeedMap.nativeBiomeName(version.nativeId, id)?.let { fromNative[id] = it }
            }
        }

        assertThat(BiomeCatalog.names).isEqualTo(fromNative)
    }

    @Test
    fun everyCatalogBiomeHasATitleAndAColour() {
        val colors = NativeSeedMap.nativeBiomeColors()
        for (id in BiomeCatalog.names.keys - THE_VOID) {
            assertThat(BiomeWikiTitles.titleFor(id)).isNotNull()
            // A biome with no entry in cubiomes' table would draw as plain black.
            assertThat(colors[id] and 0xFFFFFF).isNotEqualTo(0)
        }
        // The void is black on purpose.
        assertThat(BiomeCatalog.names[THE_VOID]).isEqualTo("the_void")
        assertThat(colors[THE_VOID] and 0xFFFFFF).isEqualTo(0)
    }

    private companion object {
        const val THE_VOID = 127
    }
}
