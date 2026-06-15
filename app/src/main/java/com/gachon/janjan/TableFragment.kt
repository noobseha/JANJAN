package com.gachon.janjan

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.gachon.janjan.databinding.FragmentTableBinding
import com.gachon.janjan.domain.owner.model.BusinessTable
import com.gachon.janjan.domain.owner.repository.BusinessCameraRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class TableFragment : Fragment() {

    private var _binding: FragmentTableBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val cameraRepository = BusinessCameraRepository()
    private lateinit var adapter: TableAdapter
    private var isSettingMode = false
    private var tableList = mutableListOf<StoreTable>()
    private var currentStoreName = ""
    private var tableListener: ListenerRegistration? = null
    private var todaySalesListener: ListenerRegistration? = null
    private val sessionListeners = mutableMapOf<String, ListenerRegistration>()
    private val sessionSummaries = mutableMapOf<String, TableSessionSummary>()
    private var todayClosedSales = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TableAdapter(
            mutableListOf(),
            isSettingMode,
            onTableClick = { table -> showTableDetail(table) },
            onIpClick = { table -> showIpDialog(table) },
            onDeleteClick = { table -> showDeleteDialog(table) },
            onAddClick = { addTable() }
        )

        binding.rvTables.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvTables.adapter = adapter

        binding.btnSettings.setOnClickListener {
            isSettingMode = !isSettingMode
            binding.btnSettings.setImageResource(
                if (isSettingMode) {
                    R.drawable.ic_close_modern
                } else {
                    R.drawable.ic_tune_modern
                }
            )
            refreshAdapter()
        }

        binding.ivStoreImage.setOnClickListener { navigateToStoreProfile() }
        binding.layoutStoreProfile.setOnClickListener { navigateToStoreProfile() }
        binding.btnEditName.setOnClickListener { navigateToStoreProfile() }

        loadTables()
    }

    private fun loadTables() {
        val uid = auth.currentUser?.uid ?: return
        val storeRef = db.collection("stores").document(uid)

        tableListener?.remove()
        tableListener = null
        storeRef.get()
            .addOnSuccessListener { storeDoc ->
                if (_binding == null) return@addOnSuccessListener
                currentStoreName = storeDoc.getString("name").orEmpty().ifBlank { "가게 이름" }
                binding.tvStoreName.text = currentStoreName
                listenTodayClosedSales(uid)

                tableListener = storeRef.collection("tables")
                    .orderBy("tableNumber")
                    .addSnapshotListener { result, error ->
                        if (_binding == null) return@addSnapshotListener
                        if (error != null) {
                            Toast.makeText(requireContext(), "테이블 조회 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                            return@addSnapshotListener
                        }
                        if (result == null) return@addSnapshotListener

                        if (result.isEmpty) {
                            createDefaultTables(uid, currentStoreName)
                            return@addSnapshotListener
                        }

                        tableList = result.documents.mapNotNull { doc ->
                            val tableNumber = doc.getLong("tableNumber")?.toInt()
                                ?: doc.getString("tableNumber")?.toIntOrNull()
                                ?: doc.id.filter { it.isDigit() }.toIntOrNull()
                                ?: return@mapNotNull null
                            StoreTable(
                                id = doc.getString("tableId").orEmpty().ifBlank { doc.id },
                                tableNumber = tableNumber,
                                ipAddress = doc.getString("cameraIp")
                                    ?: doc.getString("ipAddress")
                                    ?: "",
                                isActive = doc.getBoolean("isActive") ?: true,
                                activeSessionId = doc.getString("activeSessionId").orEmpty(),
                                inviteCode = doc.getString("inviteCode").orEmpty()
                            )
                        }.sortedBy { it.tableNumber }.toMutableList()

                        storeRef.set(
                            mapOf(
                                "tableCount" to tableList.size,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        updateSessionListeners(tableList.map { it.activeSessionId }.filter { it.isNotBlank() }.toSet())
                        updateTableHeader()
                        refreshAdapter()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "가게 정보 조회 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createDefaultTables(storeId: String, storeName: String) {
        val storeRef = db.collection("stores").document(storeId)
        val now = FieldValue.serverTimestamp()
        val batch = db.batch()
        val inviteCodes = mutableSetOf<String>()

        (1..3).forEach { tableNumber ->
            val inviteCode = TableInviteCodes.generate(inviteCodes)
            inviteCodes += inviteCode
            val tableId = "table_$tableNumber"
            batch.set(
                storeRef.collection("tables").document(tableId),
                tablePayload(
                    storeId = storeId,
                    storeName = storeName,
                    tableId = tableId,
                    tableNumber = tableNumber,
                    inviteCode = inviteCode,
                    now = now
                ),
                SetOptions.merge()
            )
        }

        batch.set(
            storeRef,
            mapOf(
                "tableCount" to 3,
                "updatedAt" to now
            ),
            SetOptions.merge()
        )
        batch.commit()
            .addOnSuccessListener { }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "기본 테이블 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun refreshAdapter() {
        adapter.updateItems(tableList.withSessionSummaries(), isSettingMode)
    }

    private fun updateTableHeader() {
        binding.tvTableCount.text = "테이블 현황 (${tableList.size}개)"
        binding.tvTableStatus.text = "${tableList.count { it.activeSessionId.isNotBlank() }} / ${tableList.size}"
        binding.tvTodaySales.text = "${String.format(Locale.KOREA, "%,d", todayClosedSales)}원"
    }

    private fun addTable() {
        val uid = auth.currentUser?.uid ?: return
        val storeName = currentStoreName.ifBlank { binding.tvStoreName.text.toString().ifBlank { "가게 이름" } }
        val newNumber = (tableList.maxOfOrNull { it.tableNumber } ?: 0) + 1
        val tableId = "table_$newNumber"
        val inviteCode = TableInviteCodes.generate(tableList.map { it.inviteCode }.toSet())
        val now = FieldValue.serverTimestamp()
        val storeRef = db.collection("stores").document(uid)

        val batch = db.batch()
        batch.set(
            storeRef.collection("tables").document(tableId),
            tablePayload(
                storeId = uid,
                storeName = storeName,
                tableId = tableId,
                tableNumber = newNumber,
                inviteCode = inviteCode,
                now = now
            ),
            SetOptions.merge()
        )
        batch.set(
            storeRef,
            mapOf(
                "tableCount" to tableList.size + 1,
                "updatedAt" to now
            ),
            SetOptions.merge()
        )
        batch.commit()
            .addOnSuccessListener { }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "테이블 추가 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteDialog(table: StoreTable) {
        AlertDialog.Builder(requireContext())
            .setTitle("테이블 삭제")
            .setMessage("${table.tableNumber}번 테이블을 삭제할까요?")
            .setPositiveButton("확인") { _, _ -> deleteTable(table) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteTable(table: StoreTable) {
        if (table.activeSessionId.isNotBlank()) {
            Toast.makeText(requireContext(), "사용 중인 테이블은 세션 종료 후 삭제할 수 있어요.", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        val storeRef = db.collection("stores").document(uid)
        val now = FieldValue.serverTimestamp()

        val batch = db.batch()
        batch.delete(storeRef.collection("tables").document(table.id))
        batch.set(
            storeRef,
            mapOf(
                "tableCount" to (tableList.size - 1).coerceAtLeast(0),
                "updatedAt" to now
            ),
            SetOptions.merge()
        )
        batch.commit()
            .addOnSuccessListener { }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "테이블 삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showIpDialog(table: StoreTable) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val ipEditText = EditText(requireContext()).apply {
            setText(table.ipAddress)
            hint = "예: 192.168.0.101"
        }
        val streamEditText = EditText(requireContext()).apply {
            hint = "스트림 URL (선택)"
        }
        container.addView(ipEditText)
        container.addView(streamEditText)
        AlertDialog.Builder(requireContext())
            .setTitle("${table.tableNumber}번 테이블 IP 주소")
            .setView(container)
            .setPositiveButton("저장") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val ip = ipEditText.text.toString().trim()
                val streamUrl = streamEditText.text.toString().trim()
                val storeName = currentStoreName.ifBlank { binding.tvStoreName.text.toString().ifBlank { "가게 이름" } }
                if (ip.isBlank()) {
                    Toast.makeText(requireContext(), "IP를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        cameraRepository.saveCameraMapping(
                            storeId = uid,
                            storeName = storeName,
                            table = table.toBusinessTable(uid, storeName),
                            cameraName = "",
                            cameraIp = ip,
                            cameraStreamUrl = streamUrl,
                            ownerUserId = uid
                        )
                    }.onSuccess {
                        Toast.makeText(requireContext(), "테이블 카메라가 매핑되었습니다.", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(
                            requireContext(),
                            "카메라 매핑 실패: ${it.message ?: "알 수 없는 오류"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditNameDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "매장 이름"
            setText(currentStoreName)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("매장 이름 변경")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val name = editText.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                db.collection("stores").document(uid)
                    .set(
                        mapOf(
                            "name" to name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        currentStoreName = name
                        binding.tvStoreName.text = name
                        Toast.makeText(requireContext(), "매장 이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun navigateToStoreProfile() {
        (activity as? MainActivity)?.navigateToBusinessProfile()
    }

    private fun showTableDetail(table: StoreTable) {
        val intent = Intent(requireContext(), SettlementActivity::class.java)
        intent.putExtra("tableId", table.tableNumber)
        intent.putExtra("tableDocId", table.id)
        intent.putExtra("sessionId", table.activeSessionId)
        intent.putExtra("storeId", auth.currentUser?.uid.orEmpty())
        startActivity(intent)
    }

    private fun tablePayload(
        storeId: String,
        storeName: String,
        tableId: String,
        tableNumber: Int,
        inviteCode: String,
        now: Any
    ): Map<String, Any?> = mapOf(
        "storeId" to storeId,
        "storeName" to storeName,
        "tableId" to tableId,
        "tableNumber" to tableNumber,
        "label" to "${tableNumber}번 테이블",
        "inviteCode" to inviteCode,
        "activeSessionId" to "",
        "isActive" to true,
        "createdAt" to now,
        "updatedAt" to now
    )

    private fun StoreTable.toBusinessTable(storeId: String, storeName: String): BusinessTable =
        BusinessTable(
            tableId = id.ifBlank { "table_$tableNumber" },
            tableNumber = tableNumber,
            label = "${tableNumber}번 테이블",
            storeId = storeId,
            storeName = storeName,
            activeSessionId = activeSessionId
        )

    private fun updateSessionListeners(activeSessionIds: Set<String>) {
        val removedSessionIds = sessionListeners.keys.filter { it !in activeSessionIds }
        removedSessionIds.forEach { sessionId ->
            sessionListeners.remove(sessionId)?.remove()
            sessionSummaries.remove(sessionId)
        }

        activeSessionIds
            .filter { it !in sessionListeners }
            .forEach { sessionId ->
                sessionListeners[sessionId] = db.collection("sessions").document(sessionId)
                    .addSnapshotListener { snapshot, _ ->
                        if (_binding == null) return@addSnapshotListener
                        sessionSummaries[sessionId] = snapshot?.toTableSessionSummary()
                            ?: TableSessionSummary()
                        updateTableHeader()
                        refreshAdapter()
                    }
            }
    }

    private fun listenTodayClosedSales(storeId: String) {
        todaySalesListener?.remove()
        todaySalesListener = db.collection("sessions")
            .whereEqualTo("storeId", storeId)
            .whereEqualTo("status", "closed")
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                val todayStartMs = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                todayClosedSales = snapshot?.documents
                    ?.filter { (it.salesTimeMs() ?: 0L) >= todayStartMs }
                    ?.sumOf { it.toTableSessionSummary().totalPrice }
                    ?: 0
                updateTableHeader()
            }
    }

    private fun List<StoreTable>.withSessionSummaries(): List<StoreTable> =
        map { table ->
            val summary = sessionSummaries[table.activeSessionId]
            if (summary == null) {
                table.copy(currentAmount = 0, sojuDrinkCount = 0, beerDrinkCount = 0)
            } else {
                table.copy(
                    currentAmount = summary.totalPrice,
                    sojuDrinkCount = summary.totalSojuDrinkCount,
                    beerDrinkCount = summary.totalBeerDrinkCount
                )
            }
        }

    private fun DocumentSnapshot.toTableSessionSummary(): TableSessionSummary {
        val totalPrice = getIntValue("totalPrice").takeIf { it > 0 }
            ?: (getIntValue("totalSojuPrice") + getIntValue("totalBeerPrice") + getIntValue("totalFoodPrice"))
                .takeIf { it > 0 }
            ?: getIntValue("totalAmount")
        return TableSessionSummary(
            totalPrice = totalPrice,
            totalSojuDrinkCount = getIntValue("totalSojuDrinkCount"),
            totalBeerDrinkCount = getIntValue("totalBeerDrinkCount")
        )
    }

    private fun DocumentSnapshot.salesTimeMs(): Long? =
        timestampMs("salesRecordedAt")
            ?: timestampMs("endedAt")
            ?: timestampMs("updatedAt")

    private fun DocumentSnapshot.timestampMs(field: String): Long? =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

    private fun DocumentSnapshot.getIntValue(field: String): Int =
        when (val value = get(field)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }

    private data class TableSessionSummary(
        val totalPrice: Int = 0,
        val totalSojuDrinkCount: Int = 0,
        val totalBeerDrinkCount: Int = 0
    )

    override fun onDestroyView() {
        super.onDestroyView()
        tableListener?.remove()
        todaySalesListener?.remove()
        tableListener = null
        todaySalesListener = null
        sessionListeners.values.forEach { it.remove() }
        sessionListeners.clear()
        sessionSummaries.clear()
        _binding = null
    }
}
