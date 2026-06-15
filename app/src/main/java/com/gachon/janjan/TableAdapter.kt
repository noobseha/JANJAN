package com.gachon.janjan

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gachon.janjan.databinding.ItemTableNormalBinding
import com.gachon.janjan.databinding.ItemTableSettingBinding
import java.util.Locale

class TableAdapter(
    private val items: MutableList<StoreTable?>,
    private var isSettingMode: Boolean,
    private val onTableClick: (StoreTable) -> Unit,
    private val onIpClick: (StoreTable) -> Unit,
    private val onDeleteClick: (StoreTable) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val typeNormal = 0
        const val typeSetting = 1
        const val typeAdd = 2
    }

    inner class NormalViewHolder(val binding: ItemTableNormalBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SettingViewHolder(val binding: ItemTableSettingBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class AddViewHolder(val binding: ItemTableSettingBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        if (!isSettingMode) return typeNormal
        return if (items[position] == null) typeAdd else typeSetting
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            typeSetting -> {
                val binding = ItemTableSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SettingViewHolder(binding)
            }
            typeAdd -> {
                val binding = ItemTableSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AddViewHolder(binding)
            }
            else -> {
                val binding = ItemTableNormalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                NormalViewHolder(binding)
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NormalViewHolder -> {
                val table = items[position] ?: return
                holder.binding.apply {
                    tvTableNumber.text = "${table.tableNumber}"
                    val tableInUse = table.activeSessionId.isNotBlank()
                    tvTableAmount.text = "${String.format(Locale.KOREA, "%,d", table.currentAmount)}원"
                    tvTablePeople.text = if (tableInUse) {
                        "소주 ${table.sojuDrinkCount}잔 · 맥주 ${table.beerDrinkCount}잔"
                    } else {
                        table.inviteCode
                            .takeIf { it.isNotBlank() }
                            ?.let { "초대코드 $it" }
                            .orEmpty()
                    }
                    tvTableState.text = if (tableInUse) {
                        table.inviteCode
                            .takeIf { it.isNotBlank() }
                            ?.let { "사용중 · $it" }
                            ?: "사용중"
                    } else {
                        "비어있음"
                    }
                    tvTableState.setTextColor(
                        Color.parseColor(if (tableInUse) "#4DB6AC" else "#999999")
                    )
                    root.setBackgroundColor(Color.WHITE)
                    root.setOnClickListener { onTableClick(table) }
                }
            }
            is SettingViewHolder -> {
                val table = items[position] ?: return
                holder.binding.apply {
                    tvTableNumber.text = "${table.tableNumber}"
                    btnIp.visibility = View.VISIBLE
                    btnDelete.visibility = View.VISIBLE
                    btnIp.setOnClickListener { onIpClick(table) }
                    btnDelete.setOnClickListener { onDeleteClick(table) }
                }
            }
            is AddViewHolder -> {
                holder.binding.apply {
                    tvTableNumber.text = "+"
                    tvTableNumber.textSize = 24f
                    tvTableNumber.setTextColor(Color.parseColor("#4DB6AC"))
                    btnIp.visibility = View.GONE
                    btnDelete.visibility = View.GONE
                    root.setBackgroundColor(Color.WHITE)
                    root.setOnClickListener { onAddClick() }
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(tables: List<StoreTable>, settingMode: Boolean) {
        isSettingMode = settingMode
        items.clear()
        items.addAll(tables.map { it as StoreTable? })
        if (isSettingMode) items.add(null)
        notifyDataSetChanged()
    }
}
