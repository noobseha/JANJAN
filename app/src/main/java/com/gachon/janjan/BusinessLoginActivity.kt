package com.gachon.janjan

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gachon.janjan.databinding.ActivityBusinessLoginBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

class BusinessLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 로그인 버튼
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Firebase.auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid = Firebase.auth.currentUser?.uid
                    if (uid.isNullOrBlank()) {
                        Firebase.auth.signOut()
                        Toast.makeText(this, "로그인 정보를 확인하지 못했습니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    Firebase.firestore.collection("stores").document(uid).get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                Firebase.auth.signOut()
                                Toast.makeText(this, "사업자 회원 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, MainActivity::class.java).apply {
                                putExtra("userType", "business")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Firebase.auth.signOut()
                            Toast.makeText(this, "사업자 정보 확인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "로그인 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 회원가입 버튼
        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, BusinessRegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
