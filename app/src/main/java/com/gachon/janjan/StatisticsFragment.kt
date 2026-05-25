package com.gachon.janjan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gachon.janjan.databinding.FragmentStatisticsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var dailyAdapter: DailySalesAdapter

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

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        db.collection("stores").document(uid)
            .collection("sessions")
            .whereGreaterThanOrEqualTo("createdAt", com.google.firebase.Timestamp(todayStart.time))
            .get()
            .addOnSuccessListener { result ->
                var totalSales = 0
                val menuSalesMap = mutableMapOf<String, Int>()

                result.documents.forEach { doc ->
                    val amount = (doc.getLong("totalAmount") ?: 0).toInt()
                    totalSales += amount

                    val menuOrders = doc.get("menuOrders") as? Map<*, *>
                    menuOrders?.forEach { (menuName, menuAmount) ->
                        val name = menuName?.toString().orEmpty()
                        if (name.isBlank()) return@forEach
                        val current = menuSalesMap[name] ?: 0
                        menuSalesMap[name] = current + menuAmount.asInt()
                    }
                }

                // 오늘 총 매출
                binding.tvTotalSales.text = "${String.format(Locale.KOREA, "%,d", totalSales)}원"

                // 메뉴별 매출
                binding.llMenuSales.removeAllViews()
                menuSalesMap.entries.sortedByDescending { it.value }.forEach { (name, amount) ->
                    val row = LayoutInflater.from(requireContext())
                        .inflate(android.R.layout.simple_list_item_2, binding.llMenuSales, false)
                    row.findViewById<TextView>(android.R.id.text1).text = name
                    row.findViewById<TextView>(android.R.id.text2).text =
                        "${String.format(Locale.KOREA, "%,d", amount)}원"
                    binding.llMenuSales.addView(row)
                }

                // 최근 7일 매출
                loadWeeklySales(uid)
            }
    }

    private fun loadWeeklySales(uid: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val dailyData = mutableListOf<Pair<String, Int>>()

        val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        sevenDaysAgo.set(Calendar.HOUR_OF_DAY, 0)
        sevenDaysAgo.set(Calendar.MINUTE, 0)
        sevenDaysAgo.set(Calendar.SECOND, 0)
        sevenDaysAgo.set(Calendar.MILLISECOND, 0)

        db.collection("stores").document(uid)
            .collection("sessions")
            .whereGreaterThanOrEqualTo("createdAt", com.google.firebase.Timestamp(sevenDaysAgo.time))
            .get()
            .addOnSuccessListener { result ->
                val salesByDay = mutableMapOf<String, Int>()

                result.documents.forEach { doc ->
                    val timestamp = doc.getTimestamp("createdAt")
                    val amount = (doc.getLong("totalAmount") ?: 0).toInt()
                    if (timestamp != null) {
                        val dateKey = sdf.format(timestamp.toDate())
                        salesByDay[dateKey] = (salesByDay[dateKey] ?: 0) + amount
                    }
                }

                // 최근 7일 날짜 생성
                for (i in 6 downTo 0) {
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                    val dateKey = sdf.format(cal.time)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    val dayName = when (dayOfWeek) {
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
    }

    private fun Any?.asInt(): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
