package com.gachon.janjan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.gachon.janjan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTopSystemBarPadding()

        when (intent.getStringExtra(EXTRA_USER_TYPE)) {
            USER_TYPE_BUSINESS -> showBusinessDashboard(savedInstanceState)
            else -> showPersonalApp()
        }
        
        handleDeepLink(intent)
    }

    private fun applyTopSystemBarPadding() {
        val fallbackTop = statusBarHeight()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, topInset.takeIf { it > 0 } ?: fallbackTop, 0, 0)
            insets
        }
    }

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "janjan" && uri.host == "payment_complete") {
            val method = uri.getQueryParameter("method") ?: "unknown"
            val sessionViewModel = androidx.lifecycle.ViewModelProvider(this)[com.gachon.janjan.domain.session.viewmodel.SessionViewModel::class.java]
            sessionViewModel.triggerExternalPaymentComplete(method)
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
            loadFragment(TableFragment())
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_table
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

    fun navigateToBusinessProfile() {
        if (binding.fragmentContainer.visibility != android.view.View.VISIBLE) return
        if (binding.bottomNavigation.selectedItemId == R.id.nav_profile) {
            loadFragment(StoreProfileFragment())
        } else {
            binding.bottomNavigation.selectedItemId = R.id.nav_profile
        }
    }

    companion object {
        const val EXTRA_USER_TYPE = "userType"
        const val USER_TYPE_BUSINESS = "business"
    }
}
