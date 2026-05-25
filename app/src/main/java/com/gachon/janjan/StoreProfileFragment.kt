package com.gachon.janjan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.gachon.janjan.databinding.FragmentStoreProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class StoreProfileFragment : Fragment() {

    private var _binding: FragmentStoreProfileBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var selectedImageUri: Uri? = null
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
            val intent = Intent(requireContext(), LandingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
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