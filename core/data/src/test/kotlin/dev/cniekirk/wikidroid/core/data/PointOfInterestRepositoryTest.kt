package dev.cniekirk.wikidroid.core.data

import com.google.common.truth.Truth.assertThat
import dev.cniekirk.wikidroid.core.database.PointOfInterestEntity
import dev.cniekirk.wikidroid.core.model.PoiCategory
import dev.cniekirk.wikidroid.core.model.PoiColor
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PointOfInterestRepositoryTest : DatabaseRepositoryTest() {
    private val clock = FakeClock()
    private lateinit var repository: RoomPointOfInterestRepository

    @Before
    fun createRepository() {
        repository = RoomPointOfInterestRepository(database.pointOfInterestDao(), clock)
    }

    private val base =
        PointOfInterest(
            seed = 262,
            dimension = "OVERWORLD",
            x = 100,
            z = -200,
            y = 64,
            name = "Base",
            category = PoiCategory.BASE,
            note = "Behind the waterfall",
        )

    private suspend fun listed() = repository.observe(262, "OVERWORLD").first()

    @Test
    fun `a new point is stamped with the time and given an id`() =
        runTest {
            val id = repository.save(base)

            assertThat(listed()).containsExactly(base.copy(id = id, createdAt = 1_000_000))
            assertThat(base.color).isEqualTo(PoiColor.BLUE)
        }

    @Test
    fun `saving a point with an id replaces it and keeps its time`() =
        runTest {
            val id = repository.save(base)
            val saved = listed().single()
            clock.advanceBy(5000)

            val again = repository.save(saved.copy(name = "Home", color = PoiColor.PINK, y = null))

            assertThat(again).isEqualTo(id)
            assertThat(listed()).containsExactly(saved.copy(name = "Home", color = PoiColor.PINK, y = null))
        }

    @Test
    fun `points are kept apart by seed and dimension`() =
        runTest {
            repository.save(base)
            repository.save(base.copy(dimension = "NETHER"))
            repository.save(base.copy(seed = 1))

            assertThat(listed()).hasSize(1)
            assertThat(repository.observe(262, "NETHER").first()).hasSize(1)
        }

    @Test
    fun `delete removes the point`() =
        runTest {
            val id = repository.save(base)

            repository.delete(base.copy(id = id))

            assertThat(listed()).isEmpty()
        }

    @Test
    fun `a restored point comes back with its id and time`() =
        runTest {
            val id = repository.save(base)
            val saved = listed().single()
            repository.delete(saved)
            clock.advanceBy(5000)

            repository.restore(saved)

            assertThat(listed()).containsExactly(saved)
            assertThat(listed().single().id).isEqualTo(id)
        }

    @Test
    fun `a category or colour this build does not know falls back`() =
        runTest {
            database.pointOfInterestDao().insert(
                PointOfInterestEntity(0, 262, "OVERWORLD", 0, 0, null, null, "CASTLE", "MAUVE", null, 0),
            )

            val point = listed().single()

            assertThat(point.category).isEqualTo(PoiCategory.OTHER)
            assertThat(point.color).isEqualTo(PoiCategory.OTHER.defaultColor)
        }
}
