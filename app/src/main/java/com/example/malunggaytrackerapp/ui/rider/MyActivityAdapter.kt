package com.example.malunggaytrackerapp.ui.rider

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.malunggaytrackerapp.databinding.ItemMyActivityBinding
import com.google.android.material.color.MaterialColors

/** One row in the rider's "Today's sales" or "My returns" list. */
data class ActivityRow(
    val id: Long,
    @DrawableRes val icon: Int,
    val title: String,
    val time: String,
    val trailing: String,
    @AttrRes val trailingColorAttr: Int,
    @DrawableRes val trailingIcon: Int = 0,
)

class MyActivityAdapter : ListAdapter<ActivityRow, MyActivityAdapter.RowViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(ItemMyActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) = holder.bind(getItem(position))

    class RowViewHolder(private val binding: ItemMyActivityBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ActivityRow) {
            val color = MaterialColors.getColor(binding.trailingText, row.trailingColorAttr)
            binding.iconImage.setImageResource(row.icon)
            binding.titleText.text = row.title
            binding.timeText.text = row.time
            binding.trailingText.text = row.trailing
            binding.trailingText.setTextColor(color)
            binding.trailingText.setCompoundDrawablesRelativeWithIntrinsicBounds(row.trailingIcon, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(binding.trailingText, ColorStateList.valueOf(color))
            binding.root.contentDescription = "${row.title}, ${row.trailing}, ${row.time}"
        }
    }

    private object Diff : DiffUtil.ItemCallback<ActivityRow>() {
        override fun areItemsTheSame(oldItem: ActivityRow, newItem: ActivityRow) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ActivityRow, newItem: ActivityRow) = oldItem == newItem
    }
}
