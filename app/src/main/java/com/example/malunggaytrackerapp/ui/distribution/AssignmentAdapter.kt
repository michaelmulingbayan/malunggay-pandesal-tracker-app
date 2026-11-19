package com.example.malunggaytrackerapp.ui.distribution

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ItemAssignmentBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.initialsOf

/** "Assigned today": one row per rider with their total pieces. */
class AssignmentAdapter : ListAdapter<RiderAssignment, AssignmentAdapter.AssignmentViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssignmentViewHolder =
        AssignmentViewHolder(ItemAssignmentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: AssignmentViewHolder, position: Int) = holder.bind(getItem(position))

    class AssignmentViewHolder(private val binding: ItemAssignmentBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(assignment: RiderAssignment) {
            val context = binding.root.context
            val quantity = formatCount(assignment.totalQuantity)
            val count = context.resources.getQuantityString(
                R.plurals.distribute_assignment_count, assignment.assignmentCount, assignment.assignmentCount,
            )

            binding.avatarText.text = initialsOf(assignment.riderName)
            binding.nameText.text = assignment.riderName
            binding.detailText.text = context.getString(
                R.string.distribute_row_detail, count, DateLabels.timeOf(assignment.lastAssignedAt),
            )
            binding.quantityText.text = quantity
            binding.root.contentDescription =
                context.getString(R.string.distribute_row_description, assignment.riderName, quantity)
        }
    }

    private object Diff : DiffUtil.ItemCallback<RiderAssignment>() {
        override fun areItemsTheSame(oldItem: RiderAssignment, newItem: RiderAssignment) = oldItem.riderId == newItem.riderId
        override fun areContentsTheSame(oldItem: RiderAssignment, newItem: RiderAssignment) = oldItem == newItem
    }
}
