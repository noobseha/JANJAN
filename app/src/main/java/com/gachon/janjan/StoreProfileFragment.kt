package com.gachon.janjan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.gachon.janjan.data.repository.AccountDeletionRepository
import com.gachon.janjan.databinding.FragmentStoreProfileBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StoreProfileFragment : Fragment() {

    private var _binding: FragmentStoreProfileBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val accountDeletionRepository = AccountDeletionRepository()
    private var selectedImageUri: Uri? = null
    private var isWithdrawing = false
    private val PICK_IMAGE = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStoreProfile()

        binding.ivProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE)
        }

        binding.btnSave.setOnClickListener {
            saveProfile()
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            navigateToLanding()
        }

        binding.btnWithdraw.setOnClickListener {
            showWithdrawDialog()
        }
    }

    private fun showWithdrawDialog() {
        if (isWithdrawing) return

        val passwordInput = EditText(requireContext()).apply {
            hint = "현재 비밀번호"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(requireContext())
            .setTitle("회원탈퇴")
            .setMessage("탈퇴하면 계정과 매장 데이터가 삭제됩니다. 확인을 위해 현재 비밀번호를 입력해 주세요.")
            .setView(passwordInput)
            .setPositiveButton("탈퇴") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isBlank()) {
                    Toast.makeText(requireContext(), "현재 비밀번호를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                withdraw(password)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun withdraw(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email
        if (email.isNullOrBlank()) {
            Toast.makeText(requireContext(), "이메일 정보를 확인하지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        isWithdrawing = true
        _binding?.btnWithdraw?.isEnabled = false
        user.reauthenticate(EmailAuthProvider.getCredential(email, password))
            .addOnSuccessListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        accountDeletionRepository.deleteBusinessData(user.uid)
                        user.delete().await()
                    }.onSuccess {
                        auth.signOut()
                        Toast.makeText(requireContext(), "탈퇴되었습니다.", Toast.LENGTH_SHORT).show()
                        navigateToLanding()
                    }.onFailure { error ->
                        isWithdrawing = false
                        _binding?.btnWithdraw?.isEnabled = true
                        Toast.makeText(
                            requireContext(),
                            "회원탈퇴 실패: ${error.localizedMessage ?: "알 수 없는 오류"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .addOnFailureListener {
                isWithdrawing = false
                _binding?.btnWithdraw?.isEnabled = true
                Toast.makeText(requireContext(), "현재 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToLanding() {
        val intent = Intent(requireContext(), LandingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun loadStoreProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("stores").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    binding.etStoreName.setText(doc.getString("name") ?: "")
                    binding.etStoreAddress.setText(doc.getString("address") ?: "")
                    binding.etStorePhone.setText(doc.getString("phone") ?: "")
                    val imageUrl = doc.getString("imageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(imageUrl).circleCrop().into(binding.ivProfileImage)
                    }
                }
            }
    }

    private fun saveProfile() {
        val uid = auth.currentUser?.uid ?: return
        val name = binding.etStoreName.text.toString().trim()
        val address = binding.etStoreAddress.text.toString().trim()
        val phone = binding.etStorePhone.text.toString().trim()

        if (name.isEmpty()) {
            binding.etStoreName.error = "업장 이름을 입력해주세요"
            return
        }

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "address" to address,
            "phone" to phone
        )

        if (selectedImageUri != null) {
            val ref = storage.reference.child("store_images/$uid.jpg")
            ref.putFile(selectedImageUri!!)
                .continueWithTask { ref.downloadUrl }
                .addOnSuccessListener { uri ->
                    updates["imageUrl"] = uri.toString()
                    db.collection("stores").document(uid).update(updates)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "저장되었습니다", Toast.LENGTH_SHORT).show()
                        }
                }
        } else {
            db.collection("stores").document(uid).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "저장되었습니다", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            Glide.with(this).load(selectedImageUri).circleCrop().into(binding.ivProfileImage)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
