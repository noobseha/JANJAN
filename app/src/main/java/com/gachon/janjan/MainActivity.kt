package com.gachon.janjan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gachon.janjan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadFragment(StoreProfileFragment())
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
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun navigateToNotification() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, NotificationFragment())
            .addToBackStack(null)
            .commit()
    }
}