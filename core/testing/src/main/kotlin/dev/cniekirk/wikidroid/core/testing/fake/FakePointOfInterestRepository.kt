package dev.cniekirk.wikidroid.core.testing.fake

import dev.cniekirk.wikidroid.core.data.PointOfInterestRepository
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Behaves like the real store: new points get the next id and the [now] time, and are listed oldest first. */
class FakePointOfInterestRepository(
    initial: List<PointOfInterest> = emptyList(),
    var now: Long = 1_000,
) : PointOfInterestRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    /** Every saved point, for assertions. */
    val current: List<PointOfInterest> get() = state.value

    override fun observe(
        seed: Long,
        dimension: String,
    ): Flow<List<PointOfInterest>> = state.map { all -> all.filter { it.seed == seed && it.dimension == dimension } }

    override suspend fun save(point: PointOfInterest): Long {
        if (point.id == 0L) {
            val id = nextId++
            state.update { it + point.copy(id = id, createdAt = now) }
            return id
        }
        state.update { all -> all.map { if (it.id == point.id) point else it } }
        return point.id
    }

    override suspend fun delete(point: PointOfInterest) {
        state.update { all -> all.filterNot { it.id == point.id } }
    }

    override suspend fun restore(point: PointOfInterest) {
        state.update { all -> (all + point).sortedWith(compareBy({ it.createdAt }, { it.id })) }
    }
}
