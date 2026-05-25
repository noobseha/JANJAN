package com.gachon.janjan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gachon.janjan.databinding.ItemDailySalesBinding

class DailySalesAdapter(
    private val items: MutableList<Pair<String, Int>>
) : RecyclerView.Adapter<DailySalesAdapter.DailySalesViewHolder>() {

    inner class DailySalesViewHolder(val binding: ItemDailySalesBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DailySalesViewHolder {
        val binding = ItemDailySalesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DailySalesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DailySalesViewHolder, position: Int) {
        val (day, amount) = items[position]
        val maxAmount = items.maxOfOrNull { it.second } ?: 1
        holder.binding.apply {
            tvDay.text = day
            tvAmount.text = if (amount >= 10000) "${amount / 10000}만" else "${amount}원"
            val ratio = if (maxAmount > 0) amount.toFloat() / maxAmount else 0f
            barView.layoutParams.height = (200 * ratio).toInt().coerceAtLeast(4)
            barView.requestLayout()
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Pair<String, Int>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}