package dev.cniekirk.wikidroid.feature.seedmap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.StructureType
import dev.cniekirk.wikidroid.core.testing.ComposeTest
import org.junit.Test

/** Robolectric, because resource ids only resolve with an Android `Resources`. */
class StructureIconsTest : ComposeTest() {
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun everyStructureHasAnIconThatResolvesToAPngSprite() {
        StructureType.entries.forEach { type ->
            val name = resources.getResourceEntryName(type.icon().resId)
            assertThat(name).startsWith("structure_")
            assertThat(resources.getResourceTypeName(type.icon().resId)).isEqualTo("drawable")
        }
    }

    @Test
    fun theSpawnHasAnIconToo() {
        assertThat(resources.getResourceEntryName(SpawnIcon.resId)).isEqualTo("structure_spawn")
    }

    @Test
    fun anIconIsStablePerType() {
        StructureType.entries.forEach { assertThat(it.icon()).isEqualTo(it.icon()) }
    }

    @Test
    fun onlyTheTypesTheGameTreatsAsOneShareASprite() {
        val shared =
            StructureType.entries
                .groupBy { it.icon() }
                .filterValues { it.size > 1 }
                .values

        assertThat(shared).containsExactly(listOf(StructureType.RUINED_PORTAL, StructureType.RUINED_PORTAL_NETHER))
    }

    @Test
    fun noStructureWearsTheSpawnIcon() {
        assertThat(StructureType.entries.map { it.icon() }).doesNotContain(SpawnIcon)
    }

    @Test
    fun everySpriteDecodesAtItsOwnSizeWithoutScaling() {
        val sprites = StructureSprites.load(resources)

        AllStructureIcons.forEach { icon ->
            val bitmap = sprites[icon]
            assertThat(bitmap).isNotNull()
            // The sheets are 16 or 32 px; a density-scaled decode would give other sizes.
            assertThat(bitmap!!.width).isIn(listOf(16, 32))
            assertThat(bitmap.height).isEqualTo(bitmap.width)
        }
    }

    @Test
    fun anUnknownIconIsAbsentSoTheCanvasFallsBack() {
        assertThat(StructureSprites.Empty[StructureType.VILLAGE.icon()]).isNull()
    }

    @Test
    fun everyDimensionHasALabelAndAnIcon() {
        Dimension.entries.forEach {
            assertThat(resources.getString(it.labelRes())).isNotEmpty()
            assertThat(resources.getResourceEntryName(it.iconRes())).startsWith("dimension_")
        }
    }

    @Test
    fun pinSpritesAreWholeMultiplesOf32Pixels() {
        listOf(1f, 1.5f, 2f, 2.625f, 3f, 3.5f, 4f).forEach { density ->
            val size = pinSpritePx(density)
            assertThat(size % 32).isEqualTo(0)
            // About 30 dp: never a speck, never huge.
            assertThat(size / density).isIn(
                com.google.common.collect.Range
                    .closed(20f, 48f),
            )
        }
    }

    @Test
    fun aPinReachesHalfWayAcrossItsSprite() {
        assertThat(pinReachPx(2.625f)).isEqualTo(pinSpritePx(2.625f) / 2f)
    }
}
