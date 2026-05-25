package com.gachon.janjan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gachon.janjan.databinding.ActivityProfileSettingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSettingBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var userAddress: String = ""

    private val addressLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
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
        binding = ActivityProfileSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserData()

        binding.btnBack.setOnClickListener { finish() }

        binding.etBio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tvBioCount.text = "${s?.length ?: 0}/60"
            }
        })

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

        binding.btnAddress.setOnClickListener {
            val intent = Intent(this, AddressSearchActivity::class.java)
            addressLauncher.launch(intent)
        }

        binding.layoutChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        binding.btnSave.setOnClickListener {
            saveUserData()
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                binding.etNickname.setText(doc.getString("nickname") ?: "")
                binding.etBio.setText(doc.getString("bio") ?: "")
                binding.etPhone.setText(doc.getString("phone") ?: "")
                val address = doc.getString("address") ?: ""
                if (address.isNotEmpty()) {
                    binding.etAddress.setText(address)
                    binding.btnAddress.text = "주소 변경"
                    userAddress = address
                }
            }
    }

    private fun saveUserData() {
        val uid = auth.currentUser?.uid ?: return
        val addressDetail = binding.etAddressDetail.text.toString()
        val fullAddress = if (addressDetail.isNotEmpty()) "$userAddress $addressDetail" else userAddress

        val data = hashMapOf<String, Any>(
            "nickname" to binding.etNickname.text.toString(),
            "bio" to binding.etBio.text.toString(),
            "phone" to binding.etPhone.text.toString(),
            "address" to fullAddress
        )

        db.collection("users").document(uid).update(data)
            .addOnSuccessListener {
                Toast.makeText(this, "저장되었습니다!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}