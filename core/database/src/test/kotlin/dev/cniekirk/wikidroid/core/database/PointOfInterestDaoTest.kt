package dev.cniekirk.wikidroid.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PointOfInterestDaoTest : DatabaseTest() {
    @Test
    fun `insert assigns ids and the point is listed for its seed and dimension`() =
        runTest {
            val first = points.insert(point(name = "Base"))
            val second = points.insert(point(name = "Farm", createdAt = 1))

            val listed = points.observe(262, "OVERWORLD").first()

            assertThat(first).isNotEqualTo(second)
            assertThat(listed.map { it.id }).containsExactly(first, second).inOrder()
            assertThat(listed.first()).isEqualTo(point(name = "Base").copy(id = first))
        }

    @Test
    fun `other seeds and dimensions are not listed`() =
        runTest {
            points.insert(point(seed = 1))
            points.insert(point(dimension = "NETHER"))

            assertThat(points.observe(262, "OVERWORLD").first()).isEmpty()
            assertThat(points.observe(262, "NETHER").first()).hasSize(1)
        }

    @Test
    fun `points are counted per seed across dimensions`() =
        runTest {
            points.insert(point(seed = 262))
            points.insert(point(seed = 262, dimension = "NETHER"))
            points.insert(point(seed = 1))

            assertThat(points.countBySeed().first())
                .containsExactly(SeedPointCount(262, 2), SeedPointCount(1, 1))
        }

    @Test
    fun `update and delete are seen by observers`() =
        runTest {
            val id = points.insert(point(name = "Base"))

            points.observe(262, "OVERWORLD").test {
                assertThat(awaitItem().single().name).isEqualTo("Base")
                points.update(point(name = "Home").copy(id = id))
                assertThat(awaitItem().single().name).isEqualTo("Home")
                points.delete(id)
                assertThat(awaitItem()).isEmpty()
            }
        }
}
