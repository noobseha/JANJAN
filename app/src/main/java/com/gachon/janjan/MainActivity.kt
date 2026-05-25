package com.gachon.janjan

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gachon.janjan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        when (intent.getStringExtra(EXTRA_USER_TYPE)) {
            USER_TYPE_BUSINESS -> showBusinessDashboard(savedInstanceState)
            else -> showPersonalApp()
        }
    }

    private fun showPersonalApp() {
        binding.navHostFragment.visibility = android.view.View.VISIBLE
        binding.fragmentContainer.visibility = android.view.View.GONE
        binding.bottomNavigation.visibility = android.view.View.GONE
    }

    private fun showBusinessDashboard(savedInstanceState: Bundle?) {
        binding.navHostFragment.visibility = android.view.View.GONE
        binding.fragmentContainer.visibility = android.view.View.VISIBLE
        binding.bottomNavigation.visibility = android.view.View.VISIBLE

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
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun navigateToNotification() {
        if (binding.fragmentContainer.visibility != android.view.View.VISIBLE) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, NotificationFragment())
            .addToBackStack(null)
            .commit()
    }

    companion object {
        const val EXTRA_USER_TYPE = "userType"
        const val USER_TYPE_BUSINESS = "business"
    }
}
