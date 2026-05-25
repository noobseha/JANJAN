package com.androidtown.janjansup.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidtown.janjansup.databinding.ItemFriendRequestBinding

data class FriendRequestItem(
    val requestId: String = "",
    val fromUid: String = "",
    val fromNickname: String = "",
    val fromUserId: String = ""
)

class FriendRequestAdapter(
    private val onAccept: (FriendRequestItem) -> Unit,
    private val onReject: (FriendRequestItem) -> Unit
) : ListAdapter<FriendRequestItem, FriendRequestAdapter.ViewHolder>(diffUtil) {

    inner class ViewHolder(private val binding: ItemFriendRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendRequestItem) {
            binding.tvNickname.text = item.fromNickname
            binding.tvProfileInitial.text = item.fromNickname.firstOrNull()?.toString() ?: "?"
            binding.btnAccept.setOnClickListener { onAccept(item) }
            binding.btnReject.setOnClickListener { onReject(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<FriendRequestItem>() {
            override fun areItemsTheSame(oldItem: FriendRequestItem, newItem: FriendRequestItem) =
                oldItem.requestId == newItem.requestId
            override fun areContentsTheSame(oldItem: FriendRequestItem, newItem: FriendRequestItem) =
                oldItem == newItem
        }
    }
}