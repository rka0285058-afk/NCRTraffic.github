package com.example.delhitrafficsim

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.delhitrafficsim.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val sim = TrafficSim()
    private val lines = HashMap<Segment, Polyline>()
    private val handler = Handler(Looper.getMainLooper())
    private var tick = 0
    private var manualHour: Int? = null      // null = follow the real clock (IST)

    private val loop = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osm", MODE_PRIVATE))
            userAgentValue = packageName
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.map.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 8.0
            maxZoomLevel = 17.0
            controller.setZoom(10.0)
            controller.setCenter(GeoPoint(28.58, 77.22))
        }

        buildLines()
        buildChips()

        b.timeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    manualHour = value
                    refresh()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.nowBtn.setOnClickListener {
            manualHour = null
            refresh()
        }
    }

    private fun buildLines() {
        for (seg in sim.segments) {
            val line = Polyline().apply {
                setPoints(listOf(seg.from, seg.to))
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.isAntiAlias = true
                setOnClickListener { _, _, _ ->
                    b.detail.text = String.format(
                        Locale.US, "%s - %s, ~%d km/h (simulated)",
                        seg.road.name, TrafficSim.label(seg.congestion), seg.speedKmh
                    )
                    true
                }
            }
            lines[seg] = line
            b.map.overlays.add(line)
        }
    }

    private fun buildChips() {
        val dp = resources.displayMetrics.density
        for ((name, v) in RoadData.areas) {
            val chip = TextView(this).apply {
                text = name
                setTextColor(Color.parseColor("#202124"))
                textSize = 14f
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 20 * dp
                    setStroke((1 * dp).toInt(), Color.parseColor("#DADCE0"))
                }
                elevation = 3 * dp
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * dp).toInt() }
                setOnClickListener {
                    b.map.controller.animateTo(GeoPoint(v.first, v.second))
                    b.map.controller.setZoom(v.third)
                }
            }
            b.chips.addView(chip)
        }
    }

    private fun currentHour(): Double {
        manualHour?.let { return it.toDouble() }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    }

    private fun refresh() {
        val hour = currentHour()
        sim.step(hour, tick++)
        for ((seg, line) in lines) line.outlinePaint.color = TrafficSim.color(seg.congestion)

        val idx = sim.cityIndex()
        b.summary.text = String.format(
            Locale.US, "NCR congestion: %d%% - %s (avg ~%d km/h)",
            (idx * 100).toInt(), TrafficSim.label(idx), sim.avgSpeed().toInt()
        )
        val h = hour.toInt()
        b.timeLabel.text = (if (manualHour == null) "Now " else "At ") + String.format(Locale.US, "%02d:00", h)
        b.timeSeek.progress = h
        b.map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        b.map.onResume()
        handler.post(loop)
    }

    override fun onPause() {
        handler.removeCallbacks(loop)
        b.map.onPause()
        super.onPause()
    }
}
