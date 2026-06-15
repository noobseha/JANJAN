package com.gachon.janjan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.gachon.janjan.databinding.ActivityMenuAddBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class MenuAddActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuAddBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedImageUri: Uri? = null
    private var selectedCategory = MenuCategories.SOJU
    private val PICK_IMAGE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.ivMenuImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE)
        }

        binding.btnSoju.setOnClickListener { selectCategory(MenuCategories.SOJU) }
        binding.btnBeer.setOnClickListener { selectCategory(MenuCategories.BEER) }
        binding.btnFood.setOnClickListener { selectCategory(MenuCategories.FOOD) }
        binding.btnDrink.setOnClickListener { selectCategory(MenuCategories.DRINK) }

        binding.btnAdd.setOnClickListener { addMenu() }

        selectCategory(MenuCategories.SOJU)
    }

    private fun selectCategory(category: String) {
        selectedCategory = MenuCategories.normalize(category)
        val teal = android.graphics.Color.parseColor("#4DB6AC")
        val gray = android.graphics.Color.parseColor("#E0E0E0")
        binding.btnSoju.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedCategory == MenuCategories.SOJU) teal else gray)
        binding.btnBeer.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedCategory == MenuCategories.BEER) teal else gray)
        binding.btnFood.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedCategory == MenuCategories.FOOD) teal else gray)
        binding.btnDrink.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedCategory == MenuCategories.DRINK) teal else gray)
        binding.btnSoju.setTextColor(if (selectedCategory == MenuCategories.SOJU) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#333333"))
        binding.btnBeer.setTextColor(if (selectedCategory == MenuCategories.BEER) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#333333"))
        binding.btnFood.setTextColor(if (selectedCategory == MenuCategories.FOOD) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#333333"))
        binding.btnDrink.setTextColor(if (selectedCategory == MenuCategories.DRINK) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#333333"))
    }

    private fun addMenu() {
        val name = binding.etMenuName.text.toString().trim()
        val priceStr = binding.etMenuPrice.text.toString().trim()

        if (name.isEmpty()) { binding.etMenuName.error = "메뉴 이름을 입력해주세요"; return }
        if (priceStr.isEmpty()) { binding.etMenuPrice.error = "가격을 입력해주세요"; return }

        val uid = auth.currentUser?.uid ?: return
        val price = priceStr.toInt()

        val menuData = hashMapOf<String, Any>(
            "storeId" to uid,
            "name" to name,
            "price" to price,
            "category" to selectedCategory,
            "isSoldOut" to false,
            "isActive" to true,
            "displayOrder" to 0,
            "imageUrl" to "",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        if (selectedImageUri != null) {
            val file = com.gachon.janjan.utils.ImageUtils.getFileFromUri(this, selectedImageUri!!)
            if (file != null) {
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = okhttp3.MultipartBody.Part.createFormData("image", file.name, requestFile)
                val userIdBody = uid.toRequestBody("text/plain".toMediaTypeOrNull())

                com.gachon.janjan.api.RetrofitClient.api.uploadImage(userIdBody, body).enqueue(object : retrofit2.Callback<com.gachon.janjan.api.UploadResponse> {
                    override fun onResponse(call: retrofit2.Call<com.gachon.janjan.api.UploadResponse>, response: retrofit2.Response<com.gachon.janjan.api.UploadResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            val url = response.body()?.url ?: ""
                            menuData["imageUrl"] = url
                            saveMenu(uid, menuData)
                        } else {
                            Toast.makeText(this@MenuAddActivity, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<com.gachon.janjan.api.UploadResponse>, t: Throwable) {
                        Toast.makeText(this@MenuAddActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                Toast.makeText(this, "이미지 파일을 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        } else {
            saveMenu(uid, menuData)
        }
    }

    private fun saveMenu(uid: String, menuData: HashMap<String, Any>) {
        val menuRef = db.collection("stores").document(uid)
            .collection("menuItems").document()
        menuData["menuId"] = menuRef.id

        menuRef.set(menuData)
            .addOnSuccessListener {
                Toast.makeText(this, "메뉴가 추가되었습니다", Toast.LENGTH_SHORT).show()
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
