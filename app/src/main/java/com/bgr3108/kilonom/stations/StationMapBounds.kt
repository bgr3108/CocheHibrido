package com.bgr3108.kilonom.stations

/**
 * Geographic viewport used only while a station map is visible.
 *
 * Longitudes follow the usual geographic convention. A west value greater than east represents a
 * viewport crossing the antimeridian, which keeps the Room boundary independent of MapLibre.
 */
data class StationMapBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    val crossesAntimeridian: Boolean get() = west > east

    fun isValid(): Boolean =
        south in -90.0..90.0 && north in -90.0..90.0 && south <= north &&
            west in -180.0..180.0 && east in -180.0..180.0

    fun contains(coordinates: StationCoordinates): Boolean =
        coordinates.latitude in south..north &&
            if (crossesAntimeridian) {
                coordinates.longitude >= west || coordinates.longitude <= east
            } else {
                coordinates.longitude in west..east
            }

    /** Intersects this viewport with the small, non-wrapping box used by nearby searches. */
    fun intersect(box: StationBoundingBox): StationMapBounds? {
        val intersectionSouth = maxOf(south, box.minLatitude)
        val intersectionNorth = minOf(north, box.maxLatitude)
        if (intersectionSouth > intersectionNorth) return null

        val intersections = longitudeIntervals()
            .mapNotNull { interval ->
                val start = maxOf(interval.first, box.minLongitude)
                val end = minOf(interval.second, box.maxLongitude)
                if (start <= end) start to end else null
            }
        return intersections.singleOrNull()?.let { (west, east) ->
            StationMapBounds(intersectionSouth, west, intersectionNorth, east)
        }
    }

    private fun longitudeIntervals(): List<Pair<Double, Double>> =
        if (crossesAntimeridian) listOf(west to 180.0, -180.0 to east)
        else listOf(west to east)
}

/** Aggregate projection used to position a scoped map without materialising its map points. */
data class StationMapBoundsRow(
    val south: Double?,
    val west: Double?,
    val north: Double?,
    val east: Double?
)

internal fun StationMapBoundsRow.toStationMapBoundsOrNull(): StationMapBounds? =
    if (south == null || west == null || north == null || east == null) null
    else StationMapBounds(south, west, north, east).takeIf(StationMapBounds::isValid)
