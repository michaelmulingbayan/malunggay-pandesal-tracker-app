package com.example.malunggaytrackerapp.ui.sales

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ItemRiderSalesBinding
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import com.example.malunggaytrackerapp.ui.common.initialsOf

/** "Sales per rider": pieces, number of sales, amount and share of today's total. */
class RiderSalesAdapter : ListAdapter<RiderSalesRow, RiderSalesAdapter.RiderSalesViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiderSalesViewHolder =
        RiderSalesViewHolder(ItemRiderSalesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RiderSalesViewHolder, position: Int) = holder.bind(getItem(position))

    class RiderSalesViewHolder(private val binding: ItemRiderSalesBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: RiderSalesRow) {
            val context = binding.root.context
            val sales = row.sales
            val pieces = formatCount(sales.piecesSold)
            val amount = formatPeso(sales.totalAmount)
            val count = context.resources.getQuantityString(R.plurals.sales_count, sales.salesCount, sales.salesCount)

            binding.avatarText.text = initialsOf(sales.riderName)
            binding.nameText.text = sales.riderName
            binding.detailText.text = context.getString(R.string.sales_rider_detail, pieces, count)
            binding.amountText.text = amount
            binding.shareText.text = context.getString(R.string.sales_share, row.sharePercent)
            binding.shareBar.setProgressCompat(row.sharePercent, true)
            binding.root.contentDescription =
                context.getString(R.string.sales_rider_description, sales.riderName, pieces, amount)
        }
    }

    private object Diff : DiffUtil.ItemCallback<RiderSalesRow>() {
        override fun areItemsTheSame(oldItem: RiderSalesRow, newItem: RiderSalesRow) =
            oldItem.sales.riderId == newItem.sales.riderId

        override fun areContentsTheSame(oldItem: RiderSalesRow, newItem: RiderSalesRow) = oldItem == newItem
    }
}
