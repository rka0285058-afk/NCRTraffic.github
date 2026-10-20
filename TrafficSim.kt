package com.example.delhitrafficsim

import org.osmdroid.util.GeoPoint
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A major road corridor. [bias] > 1 means it jams more easily; [freeFlow] is km/h. */
data class Road(
    val name: String,
    val area: String,
    val freeFlow: Int,
    val bias: Double,
    val points: List<GeoPoint>
)

/** One short piece of a road, coloured by its own congestion level (0 = free, 1 = standstill). */
class Segment(val road: Road, val index: Int, val from: GeoPoint, val to: GeoPoint) {
    var congestion = 0.3
    val phase = Random.nextDouble(0.0, 6.28)
    val speedKmh get() = max(3, (road.freeFlow * (1.0 - 0.88 * congestion)).toInt())
}

object RoadData {
    private fun p(lat: Double, lon: Double) = GeoPoint(lat, lon)

    val roads = listOf(
        Road("NH-48 Delhi-Gurugram", "Gurugram", 80, 1.15, listOf(
            p(28.5925, 77.1620), p(28.5460, 77.1250), p(28.5200, 77.0850),
            p(28.4720, 77.0720), p(28.4620, 77.0430), p(28.4150, 76.9900), p(28.3600, 76.9400))),
        Road("Golf Course Road", "Gurugram", 55, 1.0, listOf(
            p(28.4810, 77.0930), p(28.4600, 77.1000), p(28.4300, 77.1000), p(28.4050, 77.0950))),
        Road("Sohna Road", "Gurugram", 50, 1.1, listOf(
            p(28.4600, 77.0400), p(28.4300, 77.0430), p(28.4000, 77.0500), p(28.3700, 77.0650))),
        Road("Outer Ring Road", "Delhi", 55, 1.2, listOf(
            p(28.5570, 77.1700), p(28.5450, 77.1930), p(28.5490, 77.2300),
            p(28.5530, 77.2510), p(28.5700, 77.2600))),
        Road("Ring Road (ITO-Ashram)", "Delhi", 45, 1.25, listOf(
            p(28.5700, 77.2610), p(28.5900, 77.2540), p(28.6290, 77.2410),
            p(28.6520, 77.2340), p(28.6670, 77.2290))),
        Road("Mathura Road", "Delhi", 55, 1.0, listOf(
            p(28.6290, 77.2410), p(28.5900, 77.2530), p(28.5570, 77.2650), p(28.5200, 77.2900))),
        Road("GT Karnal Road (NH-44)", "Delhi", 65, 0.95, listOf(
            p(28.7070, 77.1750), p(28.7400, 77.1500), p(28.7900, 77.1250), p(28.8650, 77.1000))),
        Road("DND Flyway", "Noida", 60, 1.2, listOf(
            p(28.5700, 77.2620), p(28.5710, 77.2750), p(28.5730, 77.2920), p(28.5780, 77.3130))),
        Road("Noida-Greater Noida Expy", "Noida", 80, 0.9, listOf(
            p(28.5700, 77.3250), p(28.5500, 77.3900), p(28.5150, 77.4300),
            p(28.4640, 77.5100), p(28.4000, 77.5500))),
        Road("Noida Sec 62 - NH-24", "Noida", 55, 1.0, listOf(
            p(28.6280, 77.3650), p(28.6050, 77.3600), p(28.5800, 77.3550), p(28.5700, 77.3250))),
        Road("NH-9 Delhi-Meerut Expy", "Ghaziabad", 75, 1.1, listOf(
            p(28.5900, 77.2600), p(28.6190, 77.2780), p(28.6470, 77.3160),
            p(28.6560, 77.3700), p(28.6700, 77.4200), p(28.7000, 77.4900))),
        Road("Indirapuram - Vaishali", "Ghaziabad", 45, 1.15, listOf(
            p(28.6450, 77.3400), p(28.6460, 77.3700), p(28.6480, 77.3920), p(28.6600, 77.4250))),
        Road("Mathura Rd - Badarpur", "Faridabad", 60, 1.05, listOf(
            p(28.5200, 77.2900), p(28.4930, 77.3010), p(28.4500, 77.3100), p(28.4089, 77.3178))),
        Road("Faridabad - Ballabgarh", "Faridabad", 50, 0.95, listOf(
            p(28.4089, 77.3178), p(28.3700, 77.3200), p(28.3400, 77.3190)))
    )

    val areas = linkedMapOf(
        "All NCR" to Triple(28.58, 77.22, 10.0),
        "Delhi" to Triple(28.6139, 77.2090, 11.5),
        "Gurugram" to Triple(28.4595, 77.0266, 12.0),
        "Noida" to Triple(28.5355, 77.3910, 12.0),
        "Ghaziabad" to Triple(28.6692, 77.4000, 12.0),
        "Faridabad" to Triple(28.4089, 77.3178, 12.0)
    )
}

class TrafficSim {
    val segments: List<Segment> = RoadData.roads.flatMap { road ->
        var idx = 0
        road.points.zipWithNext().flatMap { (a, b) ->
            // split each leg into 3 pieces so colours can vary along a road
            (0 until 3).map { i ->
                val t0 = i / 3.0
                val t1 = (i + 1) / 3.0
                Segment(
                    road, idx++,
                    GeoPoint(a.latitude + (b.latitude - a.latitude) * t0, a.longitude + (b.longitude - a.longitude) * t0),
                    GeoPoint(a.latitude + (b.latitude - a.latitude) * t1, a.longitude + (b.longitude - a.longitude) * t1)
                )
            }
        }
    }

    /** Typical Delhi pattern: morning peak ~9:30, evening peak ~18:30, quiet at night. */
    fun baseLoad(hour: Double): Double {
        fun bump(centre: Double, width: Double) = exp(-((hour - centre) * (hour - centre)) / (2 * width * width))
        val peak = max(bump(9.5, 1.6), bump(18.5, 2.0))
        val midday = 0.35 * bump(13.5, 2.5)
        return 0.12 + 0.62 * max(peak, midday)
    }

    fun step(hour: Double, tick: Int) {
        val base = baseLoad(hour)
        for (s in segments) {
            val wave = 0.18 * sin(tick * 0.15 + s.phase + s.index * 0.7)
            val noise = Random.nextDouble(-0.07, 0.07)
            val target = min(1.0, max(0.0, base * s.road.bias + wave + noise))
            s.congestion += (target - s.congestion) * 0.35   // ease towards target
            s.congestion = min(1.0, max(0.0, s.congestion))
        }
    }

    fun cityIndex() = segments.map { it.congestion }.average()

    fun avgSpeed() = segments.map { it.speedKmh }.average()

    companion object {
        fun color(c: Double): Int = when {
            c < 0.35 -> 0xFF34A853.toInt()
            c < 0.60 -> 0xFFF9A825.toInt()
            c < 0.82 -> 0xFFD93025.toInt()
            else -> 0xFF7B1113.toInt()
        }

        fun label(c: Double) = when {
            c < 0.35 -> "Free flow"
            c < 0.60 -> "Moderate"
            c < 0.82 -> "Heavy"
            else -> "Jam"
        }
    }
}
