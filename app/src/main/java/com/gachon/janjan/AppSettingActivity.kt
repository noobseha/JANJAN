package com.gachon.janjan

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gachon.janjan.databinding.ActivityAppSettingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSettingBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 뒤로가기
        binding.btnBack.setOnClickListener { finish() }

        // NumberPicker 설정
        binding.pickerHour.minValue = 0
        binding.pickerHour.maxValue = 23
        binding.pickerMinute.minValue = 0
        binding.pickerMinute.maxValue = 59
        binding.pickerSecond.minValue = 0
        binding.pickerSecond.maxValue = 59

        // 술 알림 토글
        binding.switchDrinkReminder.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutTimePicker.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Firebase에서 설정 불러오기
        loadSettings()

        // 회원탈퇴
        binding.btnWithdraw.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("회원탈퇴")
                .setMessage("정말 탈퇴하시겠습니까? 모든 데이터가 삭제됩니다.")
                .setPositiveButton("탈퇴") { _, _ ->
                    withdraw()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun loadSettings() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("user_app_settings").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val reminderEnabled = doc.getBoolean("drink_reminder") ?: false
                    val reminderMinutes = doc.getLong("drink_reminder_minutes")?.toInt() ?: 30
                    val isPrivate = doc.getBoolean("is_private_account") ?: false

                    binding.switchDrinkReminder.isChecked = reminderEnabled
                    binding.layoutTimePicker.visibility = if (reminderEnabled) View.VISIBLE else View.GONE
                    binding.pickerHour.value = reminderMinutes / 60
                    binding.pickerMinute.value = reminderMinutes % 60
                    binding.switchPrivate.isChecked = isPrivate
                }
            }
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    private fun saveSettings() {
        val uid = auth.currentUser?.uid ?: return
        val totalMinutes = binding.pickerHour.value * 60 + binding.pickerMinute.value

        val data = hashMapOf<String, Any>(
            "drink_reminder" to binding.switchDrinkReminder.isChecked,
            "drink_reminder_minutes" to totalMinutes,
            "is_private_account" to binding.switchPrivate.isChecked,
            "updated_at" to com.google.firebase.Timestamp.now()
        )

        db.collection("user_app_settings").document(uid).set(data)
    }

    private fun withdraw() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).delete()
            .addOnSuccessListener {
                auth.currentUser?.delete()
                    ?.addOnSuccessListener {
                        Toast.makeText(this, "탈퇴되었습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
    }
}