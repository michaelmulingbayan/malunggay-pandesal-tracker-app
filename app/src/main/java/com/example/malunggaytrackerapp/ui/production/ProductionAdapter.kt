package com.example.malunggaytrackerapp.ui.production

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.ProductionDto
import com.example.malunggaytrackerapp.databinding.ItemProductionBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.formatCount

/** Production history rows: "Today", "Yesterday", "3 days ago", then dates. */
class ProductionAdapter : ListAdapter<ProductionDto, ProductionAdapter.ProductionViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductionViewHolder =
        ProductionViewHolder(ItemProductionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ProductionViewHolder, position: Int) = holder.bind(getItem(position))

    class ProductionViewHolder(private val binding: ItemProductionBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(production: ProductionDto) {
            val context = binding.root.context
            val date = production.productionDate
            val isToday = DateLabels.daysAgo(date) == 0
            val relative = DateLabels.relative(context, date)
            val quantity = formatCount(production.quantityProduced)

            binding.dateBadge.setBackgroundResource(if (isToday) R.drawable.bg_date_badge_today else R.drawable.bg_date_badge)
            binding.monthText.text = DateLabels.monthShort(date)
            binding.dayText.text = DateLabels.dayOfMonth(date)

            binding.relativeText.text = relative
            binding.detailText.text = production.recordedBy
                ?.let { context.getString(R.string.production_recorded_by, DateLabels.weekdayDate(date), it.name) }
                ?: DateLabels.weekdayDate(date)

            binding.remarksText.text = production.remarks
            binding.remarksText.isVisible = !production.remarks.isNullOrBlank()

            binding.quantityText.text = quantity
            binding.root.contentDescription = context.getString(R.string.production_item_description, relative, quantity)
        }
    }

    private object Diff : DiffUtil.ItemCallback<ProductionDto>() {
        override fun areItemsTheSame(oldItem: ProductionDto, newItem: ProductionDto) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ProductionDto, newItem: ProductionDto) = oldItem == newItem
    }
}
