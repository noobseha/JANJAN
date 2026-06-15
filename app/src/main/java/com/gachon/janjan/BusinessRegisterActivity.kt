package com.gachon.janjan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gachon.janjan.databinding.ActivityBusinessRegisterBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

class BusinessRegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessRegisterBinding
    private var selectedKakaoPlaceId: String = ""
    private var selectedKakaoCategory: String = ""
    private var selectedKakaoPlaceUrl: String = ""
    private var selectedRoadAddress: String = ""
    private var selectedJibunAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStoreName.setOnClickListener {
            showStoreSearch()
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val storeName = binding.etSelectedStoreName.text.toString()
            val storePhone = binding.etStorePhone.text.toString()
            val storeAddress = binding.etStoreAddress.text.toString()

            if (email.isEmpty() || password.isEmpty() || storeName.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Firebase.auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener

                    val now = FieldValue.serverTimestamp()
                    val store = hashMapOf<String, Any?>(
                        "uid" to uid,
                        "storeId" to uid,
                        "role" to "business",
                        "email" to email,
                        "loginId" to email,
                        "name" to storeName,
                        "phone" to storePhone,
                        "address" to storeAddress,
                        "kakaoPlaceId" to selectedKakaoPlaceId,
                        "category" to selectedKakaoCategory,
                        "kakaoCategory" to selectedKakaoCategory,
                        "kakaoPlaceUrl" to selectedKakaoPlaceUrl,
                        "roadAddress" to selectedRoadAddress.ifBlank { storeAddress },
                        "jibunAddress" to selectedJibunAddress,
                        "isActive" to true,
                        "tableCount" to 3,
                        "createdAt" to now,
                        "updatedAt" to now
                    )

                    val db = Firebase.firestore
                    val storeRef = db.collection("stores").document(uid)
                    val batch = db.batch()
                    val inviteCodes = mutableSetOf<String>()

                    batch.set(storeRef, store, SetOptions.merge())
                    (1..3).forEach { tableNumber ->
                        val inviteCode = TableInviteCodes.generate(inviteCodes)
                        inviteCodes += inviteCode
                        val tableId = "table_$tableNumber"
                        batch.set(
                            storeRef.collection("tables").document(tableId),
                            mapOf(
                                "storeId" to uid,
                                "storeName" to storeName,
                                "tableId" to tableId,
                                "tableNumber" to tableNumber,
                                "label" to "${tableNumber}번 테이블",
                                "inviteCode" to inviteCode,
                                "activeSessionId" to "",
                                "isActive" to true,
                                "createdAt" to now,
                                "updatedAt" to now
                            ),
                            SetOptions.merge()
                        )
                    }

                    batch.commit()
                        .addOnSuccessListener {
                            Firebase.auth.signOut()
                            Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            result.user?.delete()
                            Toast.makeText(
                                this,
                                "사업자 정보 저장 실패: ${e.message}. 다시 가입해주세요.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "회원가입 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showStoreSearch() {
        val intent = Intent(this, StoreSearchActivity::class.java)
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val name = data?.getStringExtra("name") ?: ""
            val phone = data?.getStringExtra("phone") ?: ""
            val address = data?.getStringExtra("address") ?: ""
            selectedKakaoPlaceId = data?.getStringExtra("kakaoPlaceId").orEmpty()
            selectedKakaoCategory = data?.getStringExtra("category").orEmpty()
            selectedKakaoPlaceUrl = data?.getStringExtra("placeUrl").orEmpty()
            selectedRoadAddress = data?.getStringExtra("roadAddress").orEmpty()
            selectedJibunAddress = data?.getStringExtra("jibunAddress").orEmpty()
            binding.etSelectedStoreName.setText(name)
            binding.etStorePhone.setText(phone)
            binding.etStoreAddress.setText(address)
            binding.btnStoreName.text = "가게 변경"
        }
    }
}
