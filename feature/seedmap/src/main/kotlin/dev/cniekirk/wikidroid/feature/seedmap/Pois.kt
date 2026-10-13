package dev.cniekirk.wikidroid.feature.seedmap

import dev.cniekirk.wikidroid.core.model.PoiCategory
import dev.cniekirk.wikidroid.core.model.PointOfInterest
import dev.cniekirk.wikidroid.core.seedmap.Dimension
import dev.cniekirk.wikidroid.core.seedmap.SeedMapEngine

/** This point with what the editor holds; blank text is stored as none, and the position is kept in the world. */
internal fun PointOfInterest.edited(input: SeedMapAction.SavePoi): PointOfInterest {
    val limit = SeedMapEngine.MAX_COORDINATE
    return copy(
        name = input.name.trim().ifEmpty { null },
        category = input.category,
        color = input.color,
        x = input.x.coerceIn(-limit, limit),
        z = input.z.coerceIn(-limit, limit),
        y = input.y,
        note = input.note.trim().ifEmpty { null },
    )
}

/** The dimension this point is in, or null for a name this build does not know. */
internal val PointOfInterest.mapDimension: Dimension?
    get() = Dimension.entries.firstOrNull { it.name == dimension }

/**
 * The portal a Nether portal point leads to, as a new point in the other dimension with the same name and
 * colour; null for any other kind of point, and in the End.
 */
internal fun PointOfInterest.linkedPortal(): PointOfInterest? {
    val from = mapDimension.takeIf { category == PoiCategory.PORTAL }
    val (otherDimension, otherPos) = from?.let(pos::linkedPortal) ?: return null
    return PointOfInterest(
        seed = seed,
        dimension = otherDimension.name,
        x = otherPos.x,
        z = otherPos.z,
        name = name,
        category = PoiCategory.PORTAL,
        color = color,
    )
}
