package com.gachon.janjan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gachon.janjan.databinding.ItemMenuBinding

class MenuAdapter(
    private val items: MutableList<MenuItem>,
    private val onEdit: (MenuItem) -> Unit,
    private val onDelete: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    inner class MenuViewHolder(val binding: ItemMenuBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvMenuName.text = item.name
            tvMenuPrice.text = "${item.price}원"
            tvMenuCategory.text = MenuCategories.label(item.category)
            tvSoldOut.visibility = if (item.isSoldOut) android.view.View.VISIBLE else android.view.View.GONE
            if (item.imageUrl.isNotEmpty()) {
                Glide.with(root.context).load(item.imageUrl).into(ivMenuImage)
            }
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<MenuItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
