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
import com.google.firebase.firestore.firestore

class BusinessRegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessRegisterBinding

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

                    val store = hashMapOf(
                        "login_id" to email,
                        "owner_name" to email,
                        "name" to storeName,
                        "phone" to storePhone,
                        "address" to storeAddress,
                        "is_active" to true,
                        "table_count" to 0,
                        "created_at" to com.google.firebase.Timestamp.now(),
                        "updated_at" to com.google.firebase.Timestamp.now()
                    )

                    Firebase.firestore.collection("stores").document(uid).set(store)
                        .addOnSuccessListener {
                            Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                            finish()
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
            binding.etSelectedStoreName.setText(name)
            binding.etStorePhone.setText(phone)
            binding.etStoreAddress.setText(address)
            binding.btnStoreName.text = "가게 변경"
        }
    }
}