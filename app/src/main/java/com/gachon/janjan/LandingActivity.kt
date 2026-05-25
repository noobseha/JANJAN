package com.gachon.janjan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gachon.janjan.databinding.ActivityLandingBinding

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 개인용 버튼 누르면 로그인 화면으로
        binding.btnPersonal.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("userType", "personal")
            startActivity(intent)
        }

        // 사업자용 버튼 누르면 로그인 화면으로
        binding.btnBusiness.setOnClickListener {
            val intent = Intent(this, BusinessLoginActivity::class.java)
            startActivity(intent)
        }
    }
}