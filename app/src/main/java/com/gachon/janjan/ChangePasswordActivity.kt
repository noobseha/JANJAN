package com.gachon.janjan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gachon.janjan.databinding.ActivityChangePasswordBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnChange.setOnClickListener {
            val current = binding.etCurrentPassword.text.toString()
            val newPw = binding.etNewPassword.text.toString()
            val confirm = binding.etNewPasswordConfirm.text.toString()

            if (current.isEmpty() || newPw.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPw != confirm) {
                Toast.makeText(this, "새 비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser ?: return@setOnClickListener
            val email = user.email ?: return@setOnClickListener
            val credential = EmailAuthProvider.getCredential(email, current)

            user.reauthenticate(credential)
                .addOnSuccessListener {
                    user.updatePassword(newPw)
                        .addOnSuccessListener {
                            Toast.makeText(this, "비밀번호가 변경되었습니다!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "변경 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "현재 비밀번호가 틀렸습니다", Toast.LENGTH_SHORT).show()
                }
        }
    }
}