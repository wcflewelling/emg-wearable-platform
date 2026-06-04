package com.example.nrf54l15_ble

import android.content.res.ColorStateList
import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

/**
 * Android analog of the iOS `BodyMapView` — a body silhouette with muscle-placement
 * markers plus a list of muscle groups. Wellness framing: movement context only, no
 * nerve labels. Tapping a muscle highlights its location on the body diagram.
 */
class BodyMapActivity : AppCompatActivity() {

    private data class Muscle(
        val name: String,
        val muscle: String,
        val description: String,
        val color: Int,
        val points: List<PointF>
    )

    private lateinit var bodyMap: BodyMapView
    private lateinit var tvSelected: TextView
    private val muscles = muscles()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bodymap)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bodyMap = findViewById(R.id.bodyMap)
        tvSelected = findViewById(R.id.tvSelected)
        bodyMap.sites = muscles.map { BodyMapView.Site(it.color, it.points) }

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = Adapter()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun select(index: Int) {
        bodyMap.selectedIndex = index
        tvSelected.text = if (index in muscles.indices) {
            val m = muscles[index]; "${m.name} · ${m.muscle}"
        } else "Tap a muscle below to see placement"
    }

    private fun muscles() = listOf(
        Muscle("Quadriceps", "Rectus femoris", "Knee extension. Used in squats, sprints, and cycling.", LEGS,
            listOf(PointF(0.435f, 0.62f), PointF(0.565f, 0.62f))),
        Muscle("Hamstring", "Biceps femoris", "Knee flexion and hip extension. Used in deadlifts and sprinting.", LEGS,
            listOf(PointF(0.42f, 0.67f), PointF(0.58f, 0.67f))),
        Muscle("Calf", "Gastrocnemius", "Ankle plantarflexion. Used in jumping and calf raises.", LEGS,
            listOf(PointF(0.43f, 0.82f), PointF(0.57f, 0.82f))),
        Muscle("Tibialis", "Tibialis anterior", "Dorsiflexion. Often a focus for runners.", LEGS,
            listOf(PointF(0.40f, 0.78f), PointF(0.60f, 0.78f))),
        Muscle("Trapezius", "Upper trapezius", "Shoulder elevation. Used in shrugs and overhead work.", UPPER,
            listOf(PointF(0.44f, 0.205f), PointF(0.56f, 0.205f))),
        Muscle("Pectoral", "Pectoralis major", "Shoulder adduction. Used in bench press and push-ups.", UPPER,
            listOf(PointF(0.44f, 0.31f), PointF(0.56f, 0.31f))),
        Muscle("Deltoid", "Deltoid", "Shoulder abduction. Used in overhead press and lateral raises.", UPPER,
            listOf(PointF(0.31f, 0.245f), PointF(0.69f, 0.245f))),
        Muscle("Bicep", "Biceps brachii", "Elbow flexion. Used in curls and pulling movements.", ARMS,
            listOf(PointF(0.25f, 0.33f), PointF(0.75f, 0.33f))),
        Muscle("Tricep", "Triceps brachii", "Elbow extension. Used in pressing and dips.", ARMS,
            listOf(PointF(0.235f, 0.37f), PointF(0.765f, 0.37f))),
        Muscle("Forearm", "Flexor carpi rad.", "Wrist flexion and grip. Used in pulls and grip training.", ARMS,
            listOf(PointF(0.235f, 0.44f), PointF(0.765f, 0.44f))),
        Muscle("Abdominals", "Rectus abdominis", "Trunk flexion. Used in sit-ups and crunches.", CORE,
            listOf(PointF(0.50f, 0.40f))),
        Muscle("Oblique", "External oblique", "Trunk rotation. Used in twists and anti-rotation work.", CORE,
            listOf(PointF(0.45f, 0.43f), PointF(0.55f, 0.43f)))
    )

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        private var selected = -1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_muscle, parent, false)
            return VH(v)
        }
        override fun getItemCount() = muscles.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = muscles[position]
            holder.name.text = m.name
            holder.muscle.text = m.muscle
            holder.desc.text = m.description
            holder.dot.backgroundTintList = ColorStateList.valueOf(m.color)
            holder.card.setCardBackgroundColor(
                if (position == selected) (m.color and 0x00FFFFFF) or 0x14000000
                else 0xFFFFFFFF.toInt()
            )
            holder.itemView.setOnClickListener {
                val prev = selected
                selected = position
                notifyItemChanged(prev)
                notifyItemChanged(selected)
                select(position)
            }
        }
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card: CardView = v as CardView
            val dot: View = v.findViewById(R.id.groupDot)
            val name: TextView = v.findViewById(R.id.tvName)
            val muscle: TextView = v.findViewById(R.id.tvMuscle)
            val desc: TextView = v.findViewById(R.id.tvDesc)
        }
    }

    companion object {
        private const val LEGS = 0xFF007AFF.toInt()
        private const val UPPER = 0xFFAF52DE.toInt()
        private const val ARMS = 0xFFFF9500.toInt()
        private const val CORE = 0xFF34C759.toInt()
    }
}
