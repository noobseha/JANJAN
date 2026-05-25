package com.gachon.janjan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gachon.janjan.databinding.ItemParticipantBinding

class ParticipantAdapter(
    private val items: MutableList<SettlementParticipant>,
    private val onPaidChanged: (SettlementParticipant, Boolean) -> Unit
) : RecyclerView.Adapter<ParticipantAdapter.ParticipantViewHolder>() {

    inner class ParticipantViewHolder(val binding: ItemParticipantBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val binding = ItemParticipantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParticipantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvInitial.text = if (item.userName.isNotEmpty()) item.userName[0].toString() else "?"
            tvName.text = item.userName
            tvAmount.text = "${String.format("%,d", item.myTotal)}원"
            cbPaid.isChecked = item.paidStatus

            if (item.paidStatus) {
                tvPaidStatus.text = "결제완료"
                tvPaidStatus.setTextColor(android.graphics.Color.parseColor("#4DB6AC"))
                tvPaidStatus.setBackgroundColor(android.graphics.Color.parseColor("#C8E6C9"))
            } else {
                tvPaidStatus.text = "미결제"
                tvPaidStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                tvPaidStatus.setBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
            }

            cbPaid.setOnCheckedChangeListener { _, isChecked ->
                onPaidChanged(item, isChecked)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<SettlementParticipant>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}