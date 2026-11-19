package com.example.malunggaytrackerapp.ui.riders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.RiderAccountDto
import com.example.malunggaytrackerapp.databinding.ItemRiderBinding
import com.example.malunggaytrackerapp.ui.common.initialsOf

class RiderAdapter(
    private val onActiveChanged: (rider: RiderAccountDto, isActive: Boolean) -> Unit,
) : ListAdapter<RiderRow, RiderAdapter.RiderViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiderViewHolder =
        RiderViewHolder(ItemRiderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RiderViewHolder, position: Int) = holder.bind(getItem(position))

    inner class RiderViewHolder(private val binding: ItemRiderBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: RiderRow) {
            val rider = row.rider
            val context = binding.root.context

            binding.nameText.text = rider.name
            binding.detailText.text = rider.phoneNumber
            binding.avatarText.text = initialsOf(rider.name)
            binding.avatarText.setBackgroundResource(
                if (rider.isActive) R.drawable.bg_avatar else R.drawable.bg_avatar_inactive,
            )

            // Detach the listener while setting state so binding doesn't trigger a toggle.
            binding.activeSwitch.setOnCheckedChangeListener(null)
            binding.activeSwitch.isChecked = rider.isActive
            binding.activeSwitch.isEnabled = !row.isUpdating
            binding.activeSwitch.contentDescription = context.getString(R.string.riders_toggle_description, rider.name)
            binding.activeSwitch.setOnCheckedChangeListener { _, isChecked -> onActiveChanged(rider, isChecked) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RiderRow>() {
        override fun areItemsTheSame(oldItem: RiderRow, newItem: RiderRow) = oldItem.rider.id == newItem.rider.id
        override fun areContentsTheSame(oldItem: RiderRow, newItem: RiderRow) = oldItem == newItem
    }
}
