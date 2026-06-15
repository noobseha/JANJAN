package com.gachon.janjan.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gachon.janjan.R
import com.gachon.janjan.data.model.MenuItem
import com.gachon.janjan.databinding.ItemBinding

class MenuAdapter(
    private val onPlusClick: (String) -> Unit,
    private val onMinusClick: (String) -> Unit
) : ListAdapter<MenuItem, MenuAdapter.MenuViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MenuViewHolder(private val binding: ItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MenuItem) {
            binding.apply {
                tvMenuName.text = item.name
                tvMenuPrice.text = "${item.price}원"
                tvQuantity.text = item.quantity.toString()
                tvSoldOut.visibility = if (item.isSoldOut) View.VISIBLE else View.GONE
                btnPlus.isEnabled = !item.isSoldOut
                btnMinus.isEnabled = !item.isSoldOut && item.quantity > 0
                btnPlus.alpha = if (item.isSoldOut) 0.35f else 1f
                btnMinus.alpha = if (item.isSoldOut || item.quantity == 0) 0.35f else 1f

                Glide.with(ivMenu.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.bg_circle_white)
                    .error(R.drawable.img_food_default) // 사진이 없을 때 보여줄 기본 음식 이미지
                    .centerCrop()
                    .into(binding.ivMenu)

                btnPlus.setOnClickListener { onPlusClick(item.id) }
                btnMinus.setOnClickListener { onMinusClick(item.id) }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MenuItem>() {
        override fun areItemsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem == newItem
    }
}
