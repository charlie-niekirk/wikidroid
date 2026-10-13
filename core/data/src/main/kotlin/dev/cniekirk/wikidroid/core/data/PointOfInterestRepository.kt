package dev.cniekirk.wikidroid.core.data

import dev.cniekirk.wikidroid.core.database.PointOfInterestDao
import dev.cniekirk.wikidroid.core.database.PointOfInterestEntity
import dev.cniekirk.wikidroid.core.model.PoiCategory
import dev.cniekirk.wikidroid.core.model.PoiColor
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** The places the player marked on the seed map, per seed and dimension. */
interface PointOfInterestRepository {
    /** The points of [seed] in [dimension], oldest first. */
    fun observe(
        seed: Long,
        dimension: String,
    ): Flow<List<PointOfInterest>>

    /**
     * Adds [point] when its id is 0, stamping when it was made, and otherwise replaces the saved point with that
     * id. Returns the point's id.
     */
    suspend fun save(point: PointOfInterest): Long

    suspend fun delete(point: PointOfInterest)

    /** Puts back a deleted point (for undo) with its original id and time. */
    suspend fun restore(point: PointOfInterest)
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class RoomPointOfInterestRepository(
    private val dao: PointOfInterestDao,
    private val clock: Clock,
) : PointOfInterestRepository {
    override fun observe(
        seed: Long,
        dimension: String,
    ): Flow<List<PointOfInterest>> =
        dao.observe(seed, dimension).map { rows ->
            rows.map(PointOfInterestEntity::toModel)
        }

    override suspend fun save(point: PointOfInterest): Long =
        if (point.id == 0L) {
            dao.insert(point.copy(createdAt = clock.now().toEpochMilliseconds()).toEntity())
        } else {
            dao.update(point.toEntity())
            point.id
        }

    override suspend fun delete(point: PointOfInterest) = dao.delete(point.id)

    override suspend fun restore(point: PointOfInterest) {
        dao.insert(point.toEntity())
    }
}

private fun PointOfInterest.toEntity(): PointOfInterestEntity =
    PointOfInterestEntity(
        id = id,
        seed = seed,
        dimension = dimension,
        x = x,
        z = z,
        y = y,
        name = name,
        category = category.name,
        color = color.name,
        note = note,
        createdAt = createdAt,
    )

/** A category or colour this build no longer has falls back to "other" and that category's colour. */
private fun PointOfInterestEntity.toModel(): PointOfInterest {
    val category = PoiCategory.entries.firstOrNull { it.name == category } ?: PoiCategory.OTHER
    return PointOfInterest(
        id = id,
        seed = seed,
        dimension = dimension,
        x = x,
        z = z,
        y = y,
        name = name,
        category = category,
        color = PoiColor.entries.firstOrNull { it.name == color } ?: category.defaultColor,
        note = note,
        createdAt = createdAt,
    )
}
