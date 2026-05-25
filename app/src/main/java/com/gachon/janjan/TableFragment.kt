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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class TableFragment : Fragment() {

    private var _binding: FragmentTableBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val cameraRepository = BusinessCameraRepository()
    private lateinit var adapter: TableAdapter
    private var isSettingMode = false
    private var tableList = mutableListOf<StoreTable>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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
            if (isSettingMode) {
                binding.btnSettings.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                binding.btnSettings.setImageResource(android.R.drawable.ic_menu_preferences)
            }
            refreshAdapter()
        }

        binding.btnEditName.setOnClickListener {
            showEditNameDialog()
        }

        loadStoreInfo()
        loadTables()
    }

    private fun loadStoreInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("stores").document(uid).get()
            .addOnSuccessListener { doc ->
                binding.tvStoreName.text = doc.getString("name") ?: "가게 이름"
            }
    }

    private fun loadTables() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("stores").document(uid)
            .collection("tables")
            .orderBy("tableNumber")
            .get()
            .addOnSuccessListener { result ->
                tableList = if (result.isEmpty) {
                    defaultTables()
                } else {
                    result.documents.mapNotNull { doc ->
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
                }
                binding.tvTableCount.text = "테이블 현황 (${tableList.size}개)"
                binding.tvTableStatus.text = "${tableList.count { it.activeSessionId.isNotBlank() }} / ${tableList.size}"
                refreshAdapter()
            }
    }

    private fun refreshAdapter() {
        adapter.updateItems(tableList, isSettingMode)
    }

    private fun addTable() {
        val uid = auth.currentUser?.uid ?: return
        val storeName = binding.tvStoreName.text.toString().ifBlank { "가게 이름" }
        val newNumber = (tableList.maxOfOrNull { it.tableNumber } ?: 0) + 1
        val tableId = "table_$newNumber"
        val tableData = hashMapOf(
            "storeId" to uid,
            "storeName" to storeName,
            "tableId" to tableId,
            "tableNumber" to newNumber,
            "ipAddress" to "",
            "isActive" to true
        )
        db.collection("stores").document(uid)
            .collection("tables").document(tableId)
            .set(tableData)
            .addOnSuccessListener {
                loadTables()
            }
    }

    private fun showDeleteDialog(table: StoreTable) {
        AlertDialog.Builder(requireContext())
            .setTitle("테이블 삭제")
            .setMessage("${table.tableNumber}번 테이블을 삭제하시겠습니까?")
            .setPositiveButton("확인") { _, _ -> deleteTable(table) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteTable(table: StoreTable) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("stores").document(uid)
            .collection("tables").document(table.id)
            .delete()
            .addOnSuccessListener {
                tableList.remove(table)
                tableList.forEachIndexed { index, t ->
                    db.collection("stores").document(uid)
                        .collection("tables").document(t.id)
                        .update("tableNumber", index + 1)
                }
                loadTables()
            }
    }

    private fun showIpDialog(table: StoreTable) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val ipEditText = EditText(requireContext()).apply {
            setText(table.ipAddress)
            hint = "예) 192.168.0.101"
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
                val storeName = binding.tvStoreName.text.toString().ifBlank { "가게 이름" }
                if (ip.isBlank()) {
                    Toast.makeText(requireContext(), "IP를 입력해주세요", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), "테이블 카메라가 매핑되었습니다", Toast.LENGTH_SHORT).show()
                        loadTables()
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
            hint = "새 업장 이름"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("업장 이름 변경")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val name = editText.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                db.collection("stores").document(uid)
                    .update("name", name)
                    .addOnSuccessListener {
                        binding.tvStoreName.text = name
                        Toast.makeText(requireContext(), "업장 이름이 변경되었습니다", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showTableDetail(table: StoreTable) {
        val intent = Intent(requireContext(), SettlementActivity::class.java)
        intent.putExtra("tableId", table.tableNumber)
        startActivity(intent)
    }

    private fun defaultTables(): MutableList<StoreTable> =
        (1..9).map { number ->
            StoreTable(
                id = "table_$number",
                tableNumber = number,
                isActive = true
            )
        }.toMutableList()

    private fun StoreTable.toBusinessTable(storeId: String, storeName: String): BusinessTable =
        BusinessTable(
            tableId = id.ifBlank { "table_$tableNumber" },
            tableNumber = tableNumber,
            label = "${tableNumber}번 테이블",
            storeId = storeId,
            storeName = storeName,
            activeSessionId = activeSessionId
        )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
