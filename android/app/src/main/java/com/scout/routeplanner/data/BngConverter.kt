package com.scout.routeplanner.data

import kotlin.math.*

/**
 * Converts abbreviated Dovetrek BNG grid references to WGS84 lat/lng.
 *
 * The Dovetrek CSVs store refs as "258 778" meaning easting=258, northing=778
 * within a fixed area. The notebooks prepend "4" to eastings and "3" to northings,
 * giving full OSGB36 coordinates (e.g. E=425800, N=377800).
 */
object BngConverter {

    data class LatLng(val lat: Double, val lng: Double)

    /** Convert a Dovetrek BNG string like "258 778" to WGS84 lat/lng. */
    fun convert(bngRef: String): LatLng? {
        val parts = bngRef.trim().split("\\s+".toRegex())
        if (parts.size != 2) return null
        val eDigits = parts[0].toIntOrNull() ?: return null
        val nDigits = parts[1].toIntOrNull() ?: return null

        // Dovetrek convention: prepend 4 to easting, 3 to northing, append 00
        val easting = 400000.0 + eDigits * 100.0
        val northing = 300000.0 + nDigits * 100.0

        return osgb36ToWgs84(easting, northing)
    }

    /**
     * Convert OSGB36 easting/northing to WGS84 latitude/longitude.
     * Two-step process:
     *   1. Reverse Transverse Mercator (E,N → lat,lng on Airy 1830)
     *   2. Helmert transformation (OSGB36 → WGS84)
     */
    private fun osgb36ToWgs84(easting: Double, northing: Double): LatLng {
        // Step 1: E,N to lat,lng on OSGB36 (Airy 1830 ellipsoid)
        val (osgbLat, osgbLng) = enToLatLngOsgb36(easting, northing)

        // Step 2: OSGB36 to WGS84 via Helmert
        return helmertToWgs84(osgbLat, osgbLng)
    }

    private fun enToLatLngOsgb36(e: Double, n: Double): Pair<Double, Double> {
        // Airy 1830 ellipsoid
        val a = 6377563.396
        val b = 6356256.909
        val f0 = 0.9996012717       // scale factor on central meridian
        val lat0 = Math.toRadians(49.0)  // true origin latitude
        val lng0 = Math.toRadians(-2.0)  // true origin longitude
        val e0 = 400000.0           // false easting
        val n0 = -100000.0          // false northing

        val e2 = (a * a - b * b) / (a * a)
        val n1 = (a - b) / (a + b)
        val n2 = n1 * n1
        val n3 = n1 * n1 * n1

        // Iteratively compute latitude
        var lat = lat0 + (n - n0) / (a * f0)
        var m = computeM(lat, lat0, b, f0, n1, n2, n3)
        while (abs(n - n0 - m) > 0.00001) {
            lat += (n - n0 - m) / (a * f0)
            m = computeM(lat, lat0, b, f0, n1, n2, n3)
        }

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val tanLat = tan(lat)
        val sinLat2 = sinLat * sinLat

        val nu = a * f0 / sqrt(1.0 - e2 * sinLat2)
        val rho = a * f0 * (1.0 - e2) / (1.0 - e2 * sinLat2).pow(1.5)
        val eta2 = nu / rho - 1.0

        val de = e - e0

        val vii = tanLat / (2.0 * rho * nu)
        val viii = tanLat / (24.0 * rho * nu.pow(3)) *
                (5.0 + 3.0 * tanLat * tanLat + eta2 - 9.0 * tanLat * tanLat * eta2)
        val ix = tanLat / (720.0 * rho * nu.pow(5)) *
                (61.0 + 90.0 * tanLat * tanLat + 45.0 * tanLat.pow(4))

        val x = 1.0 / (cosLat * nu)
        val xi = 1.0 / (6.0 * cosLat * nu.pow(3)) *
                (nu / rho + 2.0 * tanLat * tanLat)
        val xii = 1.0 / (120.0 * cosLat * nu.pow(5)) *
                (5.0 + 28.0 * tanLat * tanLat + 24.0 * tanLat.pow(4))
        val xiia = 1.0 / (5040.0 * cosLat * nu.pow(7)) *
                (61.0 + 662.0 * tanLat * tanLat + 1320.0 * tanLat.pow(4) + 720.0 * tanLat.pow(6))

        val resultLat = lat - vii * de.pow(2) + viii * de.pow(4) - ix * de.pow(6)
        val resultLng = lng0 + x * de - xi * de.pow(3) + xii * de.pow(5) - xiia * de.pow(7)

        return Pair(Math.toDegrees(resultLat), Math.toDegrees(resultLng))
    }

    private fun computeM(
        lat: Double, lat0: Double,
        b: Double, f0: Double,
        n1: Double, n2: Double, n3: Double
    ): Double {
        val dLat = lat - lat0
        val sLat = lat + lat0
        return b * f0 * (
            (1.0 + n1 + 5.0 / 4.0 * n2 + 5.0 / 4.0 * n3) * dLat -
            (3.0 * n1 + 3.0 * n2 + 21.0 / 8.0 * n3) * sin(dLat) * cos(sLat) +
            (15.0 / 8.0 * n2 + 15.0 / 8.0 * n3) * sin(2.0 * dLat) * cos(2.0 * sLat) -
            (35.0 / 24.0 * n3) * sin(3.0 * dLat) * cos(3.0 * sLat)
        )
    }

    private fun helmertToWgs84(osgbLatDeg: Double, osgbLngDeg: Double): LatLng {
        val lat = Math.toRadians(osgbLatDeg)
        val lng = Math.toRadians(osgbLngDeg)

        // Airy 1830 ellipsoid
        val a = 6377563.396
        val b = 6356256.909
        val e2 = (a * a - b * b) / (a * a)

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val nu = a / sqrt(1.0 - e2 * sinLat * sinLat)

        // Cartesian coordinates on Airy 1830
        val x1 = (nu + 0.0) * cosLat * cos(lng)
        val y1 = (nu + 0.0) * cosLat * sin(lng)
        val z1 = (nu * (1.0 - e2)) * sinLat

        // Helmert transformation parameters (OSGB36 → WGS84)
        val tx = 446.448
        val ty = -125.157
        val tz = 542.060
        val s = -20.4894e-6  // scale in ppm
        val rx = Math.toRadians(0.1502 / 3600.0)
        val ry = Math.toRadians(0.2470 / 3600.0)
        val rz = Math.toRadians(0.8421 / 3600.0)

        // Apply transformation
        val x2 = tx + (1.0 + s) * x1 + (-rz) * y1 + (ry) * z1
        val y2 = ty + (rz) * x1 + (1.0 + s) * y1 + (-rx) * z1
        val z2 = tz + (-ry) * x1 + (rx) * y1 + (1.0 + s) * z1

        // Convert back to lat/lng on WGS84 ellipsoid
        val aWgs = 6378137.0
        val bWgs = 6356752.3142
        val e2Wgs = (aWgs * aWgs - bWgs * bWgs) / (aWgs * aWgs)

        val p = sqrt(x2 * x2 + y2 * y2)
        var latWgs = atan2(z2, p * (1.0 - e2Wgs))

        // Iterate
        for (i in 0 until 10) {
            val nuWgs = aWgs / sqrt(1.0 - e2Wgs * sin(latWgs) * sin(latWgs))
            latWgs = atan2(z2 + e2Wgs * nuWgs * sin(latWgs), p)
        }
        val lngWgs = atan2(y2, x2)

        return LatLng(Math.toDegrees(latWgs), Math.toDegrees(lngWgs))
    }
}
