package dev.cniekirk.wikidroid.core.seedmap

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BlockAreaTest {
    @Test
    fun `an area is half open`() {
        val area = BlockArea(0, 0, 10, 20)

        assertThat(BlockPos(0, 0) in area).isTrue()
        assertThat(BlockPos(9, 19) in area).isTrue()
        assertThat(BlockPos(10, 0) in area).isFalse()
        assertThat(BlockPos(0, 20) in area).isFalse()
        assertThat(BlockPos(-1, 5) in area).isFalse()
        assertThat(area.width).isEqualTo(10)
        assertThat(area.height).isEqualTo(20)
    }

    @Test
    fun `an empty or inverted area is refused`() {
        assertThrows(IllegalArgumentException::class.java) { BlockArea(0, 0, 0, 5) }
        assertThrows(IllegalArgumentException::class.java) { BlockArea(0, 5, 5, 5) }
        assertThrows(IllegalArgumentException::class.java) { BlockArea(5, 0, 0, 5) }
    }

    @Test
    fun `expanding grows every side`() {
        assertThat(BlockArea(0, 0, 10, 10).expandedBy(5)).isEqualTo(BlockArea(-5, -5, 15, 15))
    }
}
