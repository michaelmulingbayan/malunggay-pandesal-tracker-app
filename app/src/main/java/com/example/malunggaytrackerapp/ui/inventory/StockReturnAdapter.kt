package com.example.malunggaytrackerapp.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.StockReturnDto
import com.example.malunggaytrackerapp.databinding.ItemStockReturnBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.initialsOf
import com.google.android.material.color.MaterialColors

/** Returned stock rows. Pending returns get a Verify button; others show their status. */
class StockReturnAdapter(
    private val onVerify: (StockReturnDto) -> Unit,
) : ListAdapter<ReturnRow, StockReturnAdapter.ReturnViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReturnViewHolder =
        ReturnViewHolder(ItemStockReturnBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ReturnViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ReturnViewHolder(private val binding: ItemStockReturnBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ReturnRow) {
            val item = row.stockReturn
            val context = binding.root.context
            val whenText = DateLabels.relativeDateTime(context, item.returnDate)
            val returned = formatCount(item.quantityReturned)

            binding.avatarText.text = initialsOf(item.rider.name)
            binding.nameText.text = item.rider.name
            binding.detailText.text = context.getString(
                R.string.inventory_return_detail, returned, formatCount(item.quantityDistributed), whenText,
            )

            binding.verifyButton.isVisible = item.isPending && !row.isVerifying
            binding.verifyProgress.isVisible = row.isVerifying
            binding.verifyButton.setOnClickListener { onVerify(item) }

            binding.statusText.isVisible = !item.isPending
            if (!item.isPending) {
                val colorAttr = if (item.isVerified) {
                    androidx.appcompat.R.attr.colorPrimary
                } else {
                    androidx.appcompat.R.attr.colorError
                }
                val color = MaterialColors.getColor(binding.statusText, colorAttr)
                binding.statusText.setText(if (item.isVerified) R.string.inventory_verified else R.string.inventory_rejected)
                binding.statusText.setTextColor(color)
                binding.statusText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    if (item.isVerified) R.drawable.ic_check else 0, 0, 0, 0,
                )
                TextViewCompat.setCompoundDrawableTintList(binding.statusText, android.content.res.ColorStateList.valueOf(color))
            }

            binding.root.contentDescription = context.getString(
                R.string.inventory_return_description, item.rider.name, returned, whenText,
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<ReturnRow>() {
        override fun areItemsTheSame(oldItem: ReturnRow, newItem: ReturnRow) = oldItem.stockReturn.id == newItem.stockReturn.id
        override fun areContentsTheSame(oldItem: ReturnRow, newItem: ReturnRow) = oldItem == newItem
    }
}
