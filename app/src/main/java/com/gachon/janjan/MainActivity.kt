package com.gachon.janjan

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gachon.janjan.ui.order.OrderFragment
import androidx.fragment.app.Fragment
import com.gachon.janjan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val testData = hashMapOf("status" to "연결 성공")

        val userType = intent.getStringExtra("userType") ?: "personal"

        if (userType == "business") {
            binding.bottomNavigation.visibility = android.view.View.VISIBLE
            binding.fragmentContainer.visibility = android.view.View.VISIBLE
            binding.navHostFragment.visibility = android.view.View.GONE

            if (savedInstanceState == null) {
                loadFragment(StoreProfileFragment())
            }
            binding.bottomNavigation.selectedItemId = R.id.nav_profile

            binding.bottomNavigation.setOnItemSelectedListener { item ->
                val fragment: Fragment = when (item.itemId) {
                    R.id.nav_table -> TableFragment()
                    R.id.nav_menu -> MenuFragment()
                    R.id.nav_statistics -> StatisticsFragment()
                    R.id.nav_profile -> StoreProfileFragment()
                    else -> StoreProfileFragment()
                }
                loadFragment(fragment)
                true
            }
        } else {
            binding.bottomNavigation.visibility = android.view.View.GONE
            binding.fragmentContainer.visibility = android.view.View.GONE
            binding.navHostFragment.visibility = android.view.View.VISIBLE
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun navigateToNotification() {
        val containerId = if (binding.fragmentContainer.visibility == android.view.View.VISIBLE) {
            R.id.fragment_container
        } else {
            R.id.nav_host_fragment
        }
        supportFragmentManager.beginTransaction()
            .replace(containerId, NotificationFragment())
            .addToBackStack(null)
            .commit()
    }
}