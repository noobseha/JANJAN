package com.gachon.janjan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.gachon.janjan.databinding.ActivityMenuEditBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MenuEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuEditBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var selectedImageUri: Uri? = null
    private var selectedCategory = "주류"
    private var isSoldOut = false
    private var menuId = ""
    private val PICK_IMAGE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        menuId = intent.getStringExtra("menuId") ?: ""
        binding.etMenuName.setText(intent.getStringExtra("name") ?: "")
        binding.etMenuPrice.setText(intent.getIntExtra("price", 0).toString())
        isSoldOut = intent.getBooleanExtra("isSoldOut", false)
        binding.switchSoldOut.isChecked = isSoldOut

        val imageUrl = intent.getStringExtra("imageUrl") ?: ""
        if (imageUrl.isNotEmpty()) {
            Glide.with(this).load(imageUrl).into(binding.ivMenuImage)
        }

        selectCategory(intent.getStringExtra("category") ?: "주류")

        binding.btnBack.setOnClickListener { finish() }

        binding.ivMenuImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE)
        }

        binding.btnSoju.setOnClickListener { selectCategory("주류") }
        binding.btnFood.setOnClickListener { selectCategory("안주") }
        binding.btnDrink.setOnClickListener { selectCategory("음료") }

        binding.switchSoldOut.setOnCheckedChangeListener { _, checked ->
            isSoldOut = checked
        }

        binding.btnEdit.setOnClickListener { editMenu() }
    }

    private fun selectCategory(category: String) {
        selectedCategory = category
        val teal = android.graphics.Color.parseColor("#4DB6AC")
        val gray = android.graphics.Color.parseColor("#E0E0E0")
        binding.btnSoju.backgroundTintList = android.content.res.ColorStateList.valueOf(if (category == "주류") teal else gray)
        binding.btnFood.backgroundTintList = android.content.res.ColorStateList.valueOf(if (category == "안주") teal else gray)
        binding.btnDrink.backgroundTintList = android.content.res.ColorStateList.valueOf(if (category == "음료") teal else gray)
        binding.btnSoju.setTextColor(if (category == "주류") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#333333"))
        binding.btnFood.setTextColor(if (category == "안주") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#333333"))
        binding.btnDrink.setTextColor(if (category == "음료") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#333333"))
    }

    private fun editMenu() {
        val name = binding.etMenuName.text.toString().trim()
        val priceStr = binding.etMenuPrice.text.toString().trim()

        if (name.isEmpty()) { binding.etMenuName.error = "메뉴 이름을 입력해주세요"; return }
        if (priceStr.isEmpty()) { binding.etMenuPrice.error = "가격을 입력해주세요"; return }

        val uid = auth.currentUser?.uid ?: return
        val price = priceStr.toInt()

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "price" to price,
            "category" to selectedCategory,
            "isSoldOut" to isSoldOut,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        if (selectedImageUri != null) {
            val ref = storage.reference.child("menu_images/$uid/${System.currentTimeMillis()}.jpg")
            ref.putFile(selectedImageUri!!)
                .continueWithTask { ref.downloadUrl }
                .addOnSuccessListener { uri ->
                    updates["imageUrl"] = uri.toString()
                    saveEdit(uid, updates)
                }
        } else {
            saveEdit(uid, updates)
        }
    }

    private fun saveEdit(uid: String, updates: HashMap<String, Any>) {
        db.collection("stores").document(uid)
            .collection("menuItems").document(menuId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "수정되었습니다", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            Glide.with(this).load(selectedImageUri).into(binding.ivMenuImage)
        }
    }
}