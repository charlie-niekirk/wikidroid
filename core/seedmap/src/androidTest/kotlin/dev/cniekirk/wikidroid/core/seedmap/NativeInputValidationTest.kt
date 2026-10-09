package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The native functions are the last line of defence: a bad argument that got past Kotlin must come back
 * as null or false, never as a crash, because a crash takes the whole app down. Every call here would be
 * undefined behaviour or an `exit()` in plain cubiomes.
 */
@RunWith(AndroidJUnit4::class)
class NativeInputValidationTest {
    private var overworld = 0L
    private var nether = 0L
    private var unseeded = 0L

    @Before
    fun create() {
        overworld = NativeSeedMap.nativeCreate(McVersion.V1_18.nativeId, 0)
        NativeSeedMap.nativeApplySeed(overworld, Dimension.OVERWORLD.nativeId, 262)
        nether = NativeSeedMap.nativeCreate(McVersion.V26_3.nativeId, 0)
        NativeSeedMap.nativeApplySeed(nether, Dimension.NETHER.nativeId, 262)
        unseeded = NativeSeedMap.nativeCreate(McVersion.V1_18.nativeId, 0)
    }

    @After
    fun destroy() {
        listOf(overworld, nether, unseeded).forEach { NativeSeedMap.nativeDestroy(it) }
    }

    private fun biomes(
        handle: Long = overworld,
        scale: Int = 4,
        x: Int = 0,
        z: Int = 0,
        size: Int = 16,
        y: Int = 15,
    ) = NativeSeedMap.nativeGenBiomes(handle, scale, x, z, size, size, y)

    @Test
    fun aValidBiomeRequestWorks() {
        assertThat(biomes()).hasLength(256)
        assertThat(biomes(scale = 1, y = 63)).hasLength(256)
        assertThat(biomes(scale = 256, x = -5, z = -5)).hasLength(256)
    }

    @Test
    fun badBiomeScalesAreRefused() {
        for (scale in listOf(-4, 0, 2, 3, 5, 8, 32, 128, 512, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertThat(biomes(scale = scale)).isNull()
        }
    }

    @Test
    fun badBiomeSizesAreRefused() {
        assertThat(NativeSeedMap.nativeGenBiomes(overworld, 4, 0, 0, 0, 16, 15)).isNull()
        assertThat(NativeSeedMap.nativeGenBiomes(overworld, 4, 0, 0, 16, 0, 15)).isNull()
        assertThat(NativeSeedMap.nativeGenBiomes(overworld, 4, 0, 0, -1, 16, 15)).isNull()
        assertThat(NativeSeedMap.nativeGenBiomes(overworld, 4, 0, 0, 1025, 16, 15)).isNull()
        assertThat(NativeSeedMap.nativeGenBiomes(overworld, 4, 0, 0, 16, Int.MAX_VALUE, 15)).isNull()
        assertThat(NativeSeedMap.nativeGenBiomes(overworld, 4, 0, 0, 1024, 1024, 15)).hasLength(1024 * 1024)
    }

    @Test
    fun biomeRequestsOutsideTheWorldAreRefused() {
        assertThat(biomes(x = Int.MAX_VALUE)).isNull()
        assertThat(biomes(z = Int.MIN_VALUE)).isNull()
        assertThat(biomes(scale = 256, x = 1_000_000)).isNull()
        assertThat(biomes(y = 100_000)).isNull()
        assertThat(biomes(y = -100_000)).isNull()
    }

    @Test
    fun aGeneratorWithoutASeedOrAHandleAnswersNothing() {
        assertThat(biomes(handle = unseeded)).isNull()
        assertThat(biomes(handle = 0)).isNull()
        assertThat(NativeSeedMap.nativeStructures(0, StructureType.VILLAGE.nativeId, 0, 0, 512, 512)).isNull()
        assertThat(NativeSeedMap.nativeStructures(unseeded, StructureType.VILLAGE.nativeId, 0, 0, 512, 512)).isNull()
        assertThat(NativeSeedMap.nativeStrongholds(0, 3)).isNull()
        assertThat(NativeSeedMap.nativeStrongholds(unseeded, 3)).isNull()
        assertThat(NativeSeedMap.nativeGetSpawn(0)).isNull()
        assertThat(NativeSeedMap.nativeGetBiomeAt(0, 1, 0, 63, 0)).isEqualTo(-1)
        assertThat(NativeSeedMap.nativeGetBiomeAt(unseeded, 1, 0, 63, 0)).isEqualTo(-1)
    }

    @Test
    fun structureTypesCubiomesWouldExitOnAreRefused() {
        // 0 Feature, 15 Mineshaft, 16 Desert_Well, 17 Geode, 23 End_Island, 27 Stronghold, and ids that are not types.
        for (type in listOf(-1, 0, 15, 16, 17, 23, 27, 28, 29, 100, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertThat(NativeSeedMap.nativeStructures(overworld, type, 0, 0, 512, 512)).isNull()
            assertThat(NativeSeedMap.nativeStructures(nether, type, 0, 0, 512, 512)).isNull()
        }
    }

    @Test
    fun aStructureFromAnotherDimensionOrVersionIsRefused() {
        assertThat(NativeSeedMap.nativeStructures(overworld, StructureType.FORTRESS.nativeId, 0, 0, 512, 512)).isNull()
        assertThat(NativeSeedMap.nativeStructures(nether, StructureType.VILLAGE.nativeId, 0, 0, 512, 512)).isNull()
        // Ancient cities arrived in 1.19.2 and this generator is 1.18.
        assertThat(
            NativeSeedMap.nativeStructures(overworld, StructureType.ANCIENT_CITY.nativeId, 0, 0, 512, 512),
        ).isNull()
    }

    @Test
    fun badStructureAreasAreRefused() {
        val village = StructureType.VILLAGE.nativeId

        assertThat(NativeSeedMap.nativeStructures(overworld, village, 10, 0, 10, 512)).isNull()
        assertThat(NativeSeedMap.nativeStructures(overworld, village, 512, 0, 0, 512)).isNull()
        assertThat(NativeSeedMap.nativeStructures(overworld, village, 0, 512, 512, 0)).isNull()
        assertThat(NativeSeedMap.nativeStructures(overworld, village, 0, 0, 16_385, 16)).isNull()
        assertThat(NativeSeedMap.nativeStructures(overworld, village, Int.MIN_VALUE, 0, Int.MAX_VALUE, 16)).isNull()
        assertThat(NativeSeedMap.nativeStructures(overworld, village, 90_000_000, 0, 90_000_100, 16)).isNull()
        assertThat(NativeSeedMap.nativeStructures(overworld, village, 0, 0, 512, 512)).isNotNull()
    }

    @Test
    fun theLargestAcceptedStructureAreaStaysWithinItsRegionBudget() {
        // Buried treasure has the smallest regions (one chunk): 16384 / 16 squared is 1M regions, over the budget.
        assertThat(
            NativeSeedMap.nativeStructures(overworld, StructureType.BURIED_TREASURE.nativeId, 0, 0, 16_384, 16_384),
        ).isNull()
        assertThat(
            NativeSeedMap.nativeStructures(overworld, StructureType.BURIED_TREASURE.nativeId, 0, 0, 8192, 8192),
        ).isNotNull()
    }

    @Test
    fun badStrongholdCountsAreRefused() {
        for (count in listOf(Int.MIN_VALUE, -1, 0, 129, 1000, Int.MAX_VALUE)) {
            assertThat(NativeSeedMap.nativeStrongholds(overworld, count)).isNull()
        }
        assertThat(NativeSeedMap.nativeStrongholds(nether, 3)).isNull()
        assertThat(NativeSeedMap.nativeStrongholds(overworld, 128)).hasLength(256)
    }

    @Test
    fun theNetherCannotBeCreatedBeforeItExisted() {
        val old = NativeSeedMap.nativeCreate(McVersion.V1_12.nativeId, 0)
        try {
            assertThat(NativeSeedMap.nativeApplySeed(old, Dimension.NETHER.nativeId, 1)).isFalse()
            assertThat(NativeSeedMap.nativeApplySeed(old, 7, 1)).isFalse()
            assertThat(NativeSeedMap.nativeApplySeed(old, Dimension.OVERWORLD.nativeId, 1)).isTrue()
        } finally {
            NativeSeedMap.nativeDestroy(old)
        }
    }

    @Test
    fun unknownVersionsAndFlagsCannotCreateAGenerator() {
        assertThat(NativeSeedMap.nativeCreate(0, 0)).isEqualTo(0L)
        assertThat(NativeSeedMap.nativeCreate(McVersion.newest.nativeId + 1, 0)).isEqualTo(0L)
        assertThat(NativeSeedMap.nativeCreate(McVersion.V1_18.nativeId, 1 shl 20)).isEqualTo(0L)
    }

    @Test
    fun nameLookupsToleratePoorInput() {
        assertThat(NativeSeedMap.nativeBiomeName(0, 1)).isNull()
        assertThat(NativeSeedMap.nativeBiomeName(McVersion.newest.nativeId + 1, 1)).isNull()
        assertThat(NativeSeedMap.nativeBiomeName(McVersion.newest.nativeId, -1)).isNull()
        assertThat(NativeSeedMap.nativeBiomeName(McVersion.newest.nativeId, 256)).isNull()
        assertThat(NativeSeedMap.nativeBiomeName(McVersion.newest.nativeId, 100)).isNull()
        assertThat(NativeSeedMap.nativeStructureName(-1)).isNull()
        assertThat(NativeSeedMap.nativeStructureName(1000)).isNull()
    }
}
