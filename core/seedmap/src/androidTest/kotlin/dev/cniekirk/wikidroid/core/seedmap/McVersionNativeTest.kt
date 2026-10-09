package dev.cniekirk.wikidroid.core.seedmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps [McVersion] and the pinned cubiomes submodule from drifting apart unnoticed. */
@RunWith(AndroidJUnit4::class)
class McVersionNativeTest {
    @Test
    fun everyVersionHasTheNameCubiomesGivesIt() {
        for (version in McVersion.entries) {
            assertThat(NativeSeedMap.nativeVersionName(version.nativeId)).isEqualTo(version.label)
        }
    }

    @Test
    fun theNewestVersionIsTheNewestCubiomesKnows() {
        assertThat(McVersion.newest.nativeId).isEqualTo(NativeSeedMap.nativeNewestVersion())
    }

    @Test
    fun noCubiomesVersionIsMissingFromTheEnum() {
        val nativeIds = (1..NativeSeedMap.nativeNewestVersion()).toSet()
        assertThat(McVersion.entries.map { it.nativeId }.toSet()).isEqualTo(nativeIds)
    }

    @Test
    fun theNativeDimensionRulesAgreeWithSupports() {
        for (version in McVersion.entries) {
            val handle = NativeSeedMap.nativeCreate(version.nativeId, 0)
            assertThat(handle).isNotEqualTo(0L)
            try {
                for (dimension in Dimension.entries) {
                    assertThat(NativeSeedMap.nativeApplySeed(handle, dimension.nativeId, 1L))
                        .isEqualTo(version.supports(dimension))
                }
            } finally {
                NativeSeedMap.nativeDestroy(handle)
            }
        }
    }

    @Test
    fun nativeCodeRefusesValuesTheEnumsCannotProduce() {
        assertThat(NativeSeedMap.nativeCreate(0, 0)).isEqualTo(0L)
        assertThat(NativeSeedMap.nativeCreate(NativeSeedMap.nativeNewestVersion() + 1, 0)).isEqualTo(0L)
        assertThat(NativeSeedMap.nativeCreate(McVersion.newest.nativeId, flags = -1)).isEqualTo(0L)
        assertThat(NativeSeedMap.nativeVersionName(0)).isNull()

        val handle = NativeSeedMap.nativeCreate(McVersion.newest.nativeId, 0)
        try {
            // No seed applied yet, an unknown dimension and an unsupported scale all come back as "no answer".
            assertThat(NativeSeedMap.nativeGetBiomeAt(handle, 1, 0, 63, 0)).isEqualTo(-1)
            assertThat(NativeSeedMap.nativeApplySeed(handle, 7, 1L)).isFalse()
            assertThat(NativeSeedMap.nativeApplySeed(handle, 0, 1L)).isTrue()
            assertThat(NativeSeedMap.nativeGetBiomeAt(handle, 16, 0, 63, 0)).isEqualTo(-1)
            assertThat(NativeSeedMap.nativeGetBiomeAt(0L, 1, 0, 63, 0)).isEqualTo(-1)
            assertThat(NativeSeedMap.nativeGetSpawn(0L)).isNull()
        } finally {
            NativeSeedMap.nativeDestroy(handle)
        }
    }
}
