package com.gachon.janjan

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.gachon.janjan.databinding.FragmentTableBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TableFragment : Fragment() {

    private var _binding: FragmentTableBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
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
                tableList = result.documents.map { doc ->
                    StoreTable(
                        id = doc.id,
                        tableNumber = (doc.getLong("tableNumber") ?: 0).toInt(),
                        ipAddress = doc.getString("ipAddress") ?: "",
                        isActive = doc.getBoolean("isActive") ?: true
                    )
                }.toMutableList()
                binding.tvTableCount.text = "테이블 현황 (${tableList.size}개)"
                binding.tvTableStatus.text = "0 / ${tableList.size}"
                refreshAdapter()
            }
    }

    private fun refreshAdapter() {
        adapter.updateItems(tableList, isSettingMode)
    }

    private fun addTable() {
        val uid = auth.currentUser?.uid ?: return
        val newNumber = tableList.size + 1
        val tableData = hashMapOf(
            "tableNumber" to newNumber,
            "ipAddress" to "",
            "isActive" to true
        )
        db.collection("stores").document(uid)
            .collection("tables")
            .add(tableData)
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
                // 번호 재정렬
                tableList.forEachIndexed { index, t ->
                    db.collection("stores").document(uid)
                        .collection("tables").document(t.id)
                        .update("tableNumber", index + 1)
                }
                loadTables()
            }
    }

    private fun showIpDialog(table: StoreTable) {
        val editText = EditText(requireContext()).apply {
            setText(table.ipAddress)
            hint = "예) 192.168.0.101"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("${table.tableNumber}번 테이블 IP 주소")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val ip = editText.text.toString().trim()
                db.collection("stores").document(uid)
                    .collection("tables").document(table.id)
                    .update("ipAddress", ip)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "IP가 저장되었습니다", Toast.LENGTH_SHORT).show()
                        loadTables()
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
        Toast.makeText(requireContext(), "${table.tableNumber}번 테이블", Toast.LENGTH_SHORT).show()
        // 나중에 담당자4 파트 완성되면 결제현황 화면으로 연결
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}