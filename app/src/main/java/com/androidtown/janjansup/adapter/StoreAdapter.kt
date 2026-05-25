package com.androidtown.janjansup.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidtown.janjansup.api.KakaoPlace
import com.androidtown.janjansup.databinding.ItemStoreBinding

class StoreAdapter(
    private val onItemClick: (KakaoPlace) -> Unit
) : ListAdapter<KakaoPlace, StoreAdapter.StoreViewHolder>(diffUtil) {

    inner class StoreViewHolder(private val binding: ItemStoreBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: KakaoPlace) {
            binding.tvStoreName.text = item.place_name
            binding.tvStoreAddress.text = item.road_address_name.ifEmpty { item.address_name }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val binding = ItemStoreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StoreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<KakaoPlace>() {
            override fun areItemsTheSame(oldItem: KakaoPlace, newItem: KakaoPlace) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: KakaoPlace, newItem: KakaoPlace) =
                oldItem == newItem
        }
    }
}