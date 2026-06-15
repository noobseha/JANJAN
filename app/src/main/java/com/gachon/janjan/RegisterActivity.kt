package com.gachon.janjan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gachon.janjan.databinding.ActivityRegisterBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var userAddress: String = ""
    private var userAddressDetail: String = ""

    private val addressLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val address = result.data?.getStringExtra("address") ?: ""
            val zonecode = result.data?.getStringExtra("zonecode") ?: ""
            userAddress = address
            binding.etAddress.setText("($zonecode) $address")
            binding.btnAddress.text = "주소 변경"
            binding.etAddressDetail.visibility = android.view.View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 생년월일 자동 . 입력
        binding.etBirthdate.addTextChangedListener(object : TextWatcher {
            var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s.toString().replace(".", "")
                val formatted = when {
                    digits.length <= 4 -> digits
                    digits.length <= 6 -> "${digits.substring(0, 4)}.${digits.substring(4)}"
                    else -> "${digits.substring(0, 4)}.${digits.substring(4, 6)}.${digits.substring(6, minOf(digits.length, 8))}"
                }
                binding.etBirthdate.setText(formatted)
                binding.etBirthdate.setSelection(formatted.length)
                isFormatting = false
            }
        })

        // 전화번호 자동 - 입력
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s.toString().replace("-", "")
                val formatted = when {
                    digits.length <= 3 -> digits
                    digits.length <= 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
                    else -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7, minOf(digits.length, 11))}"
                }
                binding.etPhone.setText(formatted)
                binding.etPhone.setSelection(formatted.length)
                isFormatting = false
            }
        })

        // 주소 검색 버튼
        binding.btnAddress.setOnClickListener {
            val intent = Intent(this, AddressSearchActivity::class.java)
            addressLauncher.launch(intent)
        }

        // 가입하기 버튼
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val passwordConfirm = binding.etPasswordConfirm.text.toString()
            val nickname = binding.etNickname.text.toString()
            val birthdate = binding.etBirthdate.text.toString()
            val phone = binding.etPhone.text.toString()
            userAddressDetail = binding.etAddressDetail.text.toString()

            if (email.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != passwordConfirm) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Firebase.auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener

                    val user = hashMapOf(
                        "uid" to uid,
                        "role" to "personal",
                        "email" to email,
                        "loginId" to email,
                        "nickname" to nickname,
                        "phone" to phone,
                        "birthdate" to birthdate,
                        "address" to userAddress,
                        "addressDetail" to userAddressDetail,
                        "isActive" to true,
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )

                    Firebase.firestore.collection("users").document(uid).set(user)
                        .addOnSuccessListener {
                            Firebase.auth.signOut()
                            Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, LoginActivity::class.java)
                            intent.putExtra("userType", "personal")
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            result.user?.delete()
                            Toast.makeText(
                                this,
                                "회원 정보 저장 실패: ${e.message}. 다시 가입해주세요.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "회원가입 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
