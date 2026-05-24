package com.gachon.janjan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gachon.janjan.databinding.ItemNotificationBinding

class NotificationAdapter(
    private val items: MutableList<NotificationItem>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvNotificationMessage.text = "${item.tableNumber}번 테이블 ${item.memberCount}명 직접 결제 요청"
            val timestamp = item.createdAt
            if (timestamp != null) {
                val now = System.currentTimeMillis()
                val diff = now - timestamp.toDate().time
                val minutes = diff / 60000
                tvNotificationTime.text = when {
                    minutes < 1 -> "방금 전"
                    minutes < 60 -> "${minutes}분 전"
                    else -> "${minutes / 60}시간 전"
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<NotificationItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}