package com.gachon.janjan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gachon.janjan.databinding.ItemParticipantBinding
import java.util.Locale

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
            tvAmount.text = "${String.format(Locale.KOREA, "%,d", item.myTotal)}원"
            cbPaid.setOnCheckedChangeListener(null)
            cbPaid.isChecked = item.paidStatus

            when {
                item.paidStatus -> {
                    tvPaidStatus.text = "결제완료"
                    tvPaidStatus.setTextColor(android.graphics.Color.parseColor("#4DB6AC"))
                    tvPaidStatus.setBackgroundColor(android.graphics.Color.parseColor("#C8E6C9"))
                }
                item.pendingApproval -> {
                    tvPaidStatus.text = "승인대기"
                    tvPaidStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                    tvPaidStatus.setBackgroundColor(android.graphics.Color.parseColor("#FEF3C7"))
                }
                else -> {
                    tvPaidStatus.text = "미결제"
                    tvPaidStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                    tvPaidStatus.setBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
                }
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
