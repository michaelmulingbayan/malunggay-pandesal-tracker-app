package com.example.malunggaytrackerapp.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ItemNearbyRiderBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.formatDistance
import com.example.malunggaytrackerapp.ui.common.initialsOf

/** Riders near the customer, nearest first. Tapping a row centers the map on that rider. */
class NearbyRiderAdapter(
    private val onClick: (NearbyRiderRow) -> Unit,
) : ListAdapter<NearbyRiderRow, NearbyRiderAdapter.RowViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(ItemNearbyRiderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) = holder.bind(getItem(position))

    inner class RowViewHolder(private val binding: ItemNearbyRiderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: NearbyRiderRow) {
            val context = binding.root.context
            val seen = context.getString(R.string.customer_seen, DateLabels.ago(context, row.rider.recordedAt))
            val distance = row.distanceMeters?.let { context.getString(R.string.customer_distance, formatDistance(it)) }

            binding.avatarText.text = initialsOf(row.rider.firstName)
            binding.nameText.text = row.rider.firstName
            binding.detailText.text = distance?.let { context.getString(R.string.customer_distance_away, it) + " · " + seen }
                ?: seen
            binding.distanceText.text = distance ?: "—"
            binding.root.contentDescription = context.getString(
                R.string.customer_row_description,
                row.rider.firstName,
                binding.detailText.text,
            )
            binding.root.setOnClickListener { onClick(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<NearbyRiderRow>() {
        override fun areItemsTheSame(oldItem: NearbyRiderRow, newItem: NearbyRiderRow) = oldItem.rider.riderId == newItem.rider.riderId
        override fun areContentsTheSame(oldItem: NearbyRiderRow, newItem: NearbyRiderRow) = oldItem == newItem
    }
}
