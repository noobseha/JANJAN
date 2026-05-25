package com.androidtown.janjansup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidtown.janjansup.databinding.ItemRankingBinding
import com.androidtown.janjansup.model.RankingModel

class RankingAdapter(private val onItemClick: (RankingModel) -> Unit) :
    ListAdapter<RankingModel, RankingAdapter.RankingViewHolder>(diffUtil) {

    inner class RankingViewHolder(private val binding: ItemRankingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RankingModel) {
            binding.tvRank.text = item.rank.toString()
            binding.tvNickname.text = item.userName
            binding.tvCups.text = "${item.totalDrinks.toInt()}잔"
            binding.tvProfileInitial.text = item.userName.firstOrNull()?.toString() ?: "?"

            binding.btnAddFriend.visibility = View.VISIBLE
            binding.btnAddFriend.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val binding = ItemRankingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RankingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<RankingModel>() {
            override fun areItemsTheSame(oldItem: RankingModel, newItem: RankingModel) = oldItem.userId == newItem.userId
            override fun areContentsTheSame(oldItem: RankingModel, newItem: RankingModel) = oldItem == newItem
        }
    }
}