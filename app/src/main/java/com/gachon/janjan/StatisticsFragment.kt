package com.gachon.janjan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gachon.janjan.databinding.FragmentStatisticsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var dailyAdapter: DailySalesAdapter
    private var sessionsListener: ListenerRegistration? = null
    private var ordersListener: ListenerRegistration? = null
    private var sessionDocs: List<DocumentSnapshot> = emptyList()
    private var orderDocs: List<DocumentSnapshot> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dailyAdapter = DailySalesAdapter(mutableListOf())
        binding.rvDailySales.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvDailySales.adapter = dailyAdapter

        loadStatistics()
    }

    private fun loadStatistics() {
        val uid = auth.currentUser?.uid ?: return

        sessionsListener?.remove()
        ordersListener?.remove()

        sessionsListener = db.collection("sessions")
            .whereEqualTo("storeId", uid)
            .addSnapshotListener { snapshot, _ ->
                sessionDocs = snapshot?.documents.orEmpty()
                renderStatistics()
            }

        ordersListener = db.collection("orders")
            .whereEqualTo("storeId", uid)
            .addSnapshotListener { snapshot, _ ->
                orderDocs = snapshot?.documents.orEmpty()
                renderStatistics()
            }
    }

    private fun renderStatistics() {
        if (_binding == null) return

        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sevenDaysAgoMs = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaySessions = sessionDocs.filter { it.isRecordedSale() && it.sessionTimeMs() >= todayStartMs }
        val todaySessionIds = todaySessions.map { it.id }.toSet()
        val totalSales = todaySessions.sumOf { it.sessionTotalPrice() }
        binding.tvTotalSales.text = "${String.format(Locale.KOREA, "%,d", totalSales)}원"

        renderMenuSales(todayStartMs, todaySessionIds)
        renderWeeklySales(sevenDaysAgoMs)
    }

    private fun renderMenuSales(todayStartMs: Long, todaySessionIds: Set<String>) {
        val menuSalesMap = mutableMapOf<String, Int>()

        orderDocs
            .filter {
                val sessionId = it.getString("sessionId").orEmpty()
                sessionId in todaySessionIds
            }
            .forEach { doc ->
                val rawItems = doc.get("items") as? List<*> ?: return@forEach
                rawItems.forEach { raw ->
                    val item = raw as? Map<*, *> ?: return@forEach
                    val name = item["itemName"].asString().ifBlank { item["name"].asString() }
                    if (name.isBlank()) return@forEach
                    val quantity = item["quantity"].asInt().coerceAtLeast(0)
                    val amount = item["subtotal"].asInt().takeIf { it > 0 }
                        ?: item["amount"].asInt().takeIf { it > 0 }
                        ?: (item["unitPrice"].asInt() * quantity)
                    menuSalesMap[name] = (menuSalesMap[name] ?: 0) + amount
                }
            }

        binding.llMenuSales.removeAllViews()
        if (menuSalesMap.isEmpty()) {
            addMenuSalesRow("오늘 주문 없음", "0원")
            return
        }

        menuSalesMap.entries.sortedByDescending { it.value }.forEach { (name, amount) ->
            addMenuSalesRow(name, "${String.format(Locale.KOREA, "%,d", amount)}원")
        }
    }

    private fun addMenuSalesRow(name: String, amountText: String) {
        val row = LayoutInflater.from(requireContext())
            .inflate(android.R.layout.simple_list_item_2, binding.llMenuSales, false)
        row.findViewById<TextView>(android.R.id.text1).text = name
        row.findViewById<TextView>(android.R.id.text2).text = amountText
        binding.llMenuSales.addView(row)
    }

    private fun renderWeeklySales(sevenDaysAgoMs: Long) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val salesByDay = mutableMapOf<String, Int>()

        sessionDocs
            .filter { it.isRecordedSale() && it.sessionTimeMs() >= sevenDaysAgoMs }
            .forEach { doc ->
                val dateKey = sdf.format(java.util.Date(doc.sessionTimeMs()))
                salesByDay[dateKey] = (salesByDay[dateKey] ?: 0) + doc.sessionTotalPrice()
            }

        val dailyData = mutableListOf<Pair<String, Int>>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dateKey = sdf.format(cal.time)
            val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "월"
                Calendar.TUESDAY -> "화"
                Calendar.WEDNESDAY -> "수"
                Calendar.THURSDAY -> "목"
                Calendar.FRIDAY -> "금"
                Calendar.SATURDAY -> "토"
                else -> "일"
            }
            dailyData.add(Pair(dayName, salesByDay[dateKey] ?: 0))
        }

        dailyAdapter.updateItems(dailyData)

        val totalWeek = dailyData.sumOf { it.second }
        val average = if (dailyData.isNotEmpty()) totalWeek / dailyData.size else 0
        binding.tvTotalWeek.text = "총 ${String.format(Locale.KOREA, "%,d", totalWeek)}원"
        binding.tvDailyAverage.text = "일평균 ${String.format(Locale.KOREA, "%,d", average)}원"
    }

    private fun DocumentSnapshot.sessionTimeMs(): Long =
        timestampMs("salesRecordedAt")
            ?: timestampMs("endedAt")
            ?: timestampMs("startedAt")
            ?: timestampMs("createdAt")
            ?: 0L

    private fun DocumentSnapshot.isRecordedSale(): Boolean =
        getString("status") == "closed" || timestampMs("salesRecordedAt") != null

    private fun DocumentSnapshot.orderTimeMs(): Long =
        timestampMs("createdAt")
            ?: getLong("timestamp")
            ?: 0L

    private fun DocumentSnapshot.timestampMs(field: String): Long? =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

    private fun DocumentSnapshot.sessionTotalPrice(): Int {
        val totalPrice = getIntValue("totalPrice")
        if (totalPrice > 0) return totalPrice
        val splitTotal = getIntValue("totalSojuPrice") +
            getIntValue("totalBeerPrice") +
            getIntValue("totalFoodPrice")
        return splitTotal.takeIf { it > 0 } ?: getIntValue("totalAmount")
    }

    private fun DocumentSnapshot.getIntValue(field: String): Int =
        when (val value = get(field)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }

    private fun Any?.asInt(): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

    private fun Any?.asString(): String = this as? String ?: ""

    override fun onDestroyView() {
        super.onDestroyView()
        sessionsListener?.remove()
        ordersListener?.remove()
        sessionsListener = null
        ordersListener = null
        _binding = null
    }
}
