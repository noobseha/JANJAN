package com.gachon.janjan

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gachon.janjan.databinding.FragmentMenuBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: MenuAdapter
    private var allItems = mutableListOf<MenuItem>()
    private var currentCategory = "전체"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MenuAdapter(mutableListOf(),
            onEdit = { item ->
                val intent = Intent(requireContext(), MenuEditActivity::class.java)
                intent.putExtra("menuId", item.id)
                intent.putExtra("name", item.name)
                intent.putExtra("price", item.price)
                intent.putExtra("category", item.category)
                intent.putExtra("imageUrl", item.imageUrl)
                intent.putExtra("isSoldOut", item.isSoldOut)
                startActivity(intent)
            },
            onDelete = { item -> deleteMenu(item) }
        )

        binding.rvMenu.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMenu.adapter = adapter

        binding.btnAddMenu.setOnClickListener {
            startActivity(Intent(requireContext(), MenuAddActivity::class.java))
        }

        binding.btnAll.setOnClickListener { filterCategory("전체") }
        binding.btnSoju.setOnClickListener { filterCategory("주류") }
        binding.btnFood.setOnClickListener { filterCategory("안주") }
        binding.btnDrink.setOnClickListener { filterCategory("음료") }

        loadMenus()
    }

    override fun onResume() {
        super.onResume()
        loadMenus()
    }

    private fun loadMenus() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("stores").document(uid)
            .collection("menuItems")
            .whereEqualTo("isActive", true)
            .orderBy("displayOrder")
            .get()
            .addOnSuccessListener { result ->
                allItems = result.documents.map { doc ->
                    MenuItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        price = (doc.getLong("price") ?: 0).toInt(),
                        category = doc.getString("category") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        isSoldOut = doc.getBoolean("isSoldOut") ?: false,
                        displayOrder = (doc.getLong("displayOrder") ?: 0).toInt()
                    )
                }.toMutableList()
                filterCategory(currentCategory)
            }
    }

    private fun filterCategory(category: String) {
        currentCategory = category
        val filtered = if (category == "전체") allItems
        else allItems.filter { it.category == category }
        adapter.updateItems(filtered)

        val teal = "#4DB6AC"
        val gray = "#E0E0E0"
        binding.btnAll.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (category == "전체") teal else gray))
        binding.btnSoju.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (category == "주류") teal else gray))
        binding.btnFood.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (category == "안주") teal else gray))
        binding.btnDrink.backgroundTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (category == "음료") teal else gray))
    }

    private fun deleteMenu(item: MenuItem) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("stores").document(uid)
            .collection("menuItems").document(item.id)
            .update("isActive", false)
            .addOnSuccessListener { loadMenus() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}