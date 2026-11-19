package com.example.malunggaytrackerapp.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ActivityHomeBinding
import com.example.malunggaytrackerapp.ui.common.LocalNetworkAccess
import com.example.malunggaytrackerapp.ui.common.enableBrandedEdgeToEdge
import com.example.malunggaytrackerapp.ui.inventory.InventoryFragment
import com.example.malunggaytrackerapp.ui.login.LoginActivity
import com.example.malunggaytrackerapp.ui.reports.ReportsFragment
import com.example.malunggaytrackerapp.ui.rider.RiderHomeFragment
import com.example.malunggaytrackerapp.ui.rider.RiderStockFragment
import com.example.malunggaytrackerapp.ui.rider.SellFragment
import com.example.malunggaytrackerapp.ui.riders.RidersFragment
import com.example.malunggaytrackerapp.ui.sales.SalesFragment
import kotlinx.coroutines.launch

/**
 * Signed-in shell with a bottom navigation that depends on the role:
 *  - Admin: Home (dashboard), Stock, Riders, Sales, Reports
 *  - Rider: Home, Sell, Stock
 *
 * Until the user is loaded, the admin HomeFragment shows the loading / error state.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val sessionViewModel: SessionViewModel by viewModels { SessionViewModel.Factory }

    /** null until the role is known; then true (admin) or false (rider). */
    private var navigationIsAdmin: Boolean? = null

    @IdRes
    private var homeTabId: Int = R.id.nav_home

    @IdRes
    private var restoredTabId: Int? = null

    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.bottomNav.selectedItemId = homeTabId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableBrandedEdgeToEdge()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()
        // If the user launches straight into Home (saved token) the first load may have been blocked; retry once granted.
        LocalNetworkAccess.requestIfNeeded(this) { granted -> if (granted) sessionViewModel.loadUser() }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { /* already showing */ }
        onBackPressedDispatcher.addCallback(this, backToHome)

        restoredTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB)?.takeIf { it != 0 }
        if (savedInstanceState == null) {
            showTab(R.id.nav_home)
        }
        // After a config change the user is already known: rebuild the right navigation immediately.
        sessionViewModel.lastUser?.let { configureNavigation(it.isAdmin) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.state.collect(::render)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, binding.bottomNav.selectedItemId)
    }

    /** Lets a tab open another tab (e.g. rider Home → Sell). */
    fun selectTab(@IdRes tabId: Int) {
        binding.bottomNav.selectedItemId = tabId
    }

    private fun render(state: SessionState) {
        binding.logoutOverlay.isVisible = state is SessionState.LoggingOut
        when (state) {
            is SessionState.Ready -> configureNavigation(state.user.isAdmin)
            SessionState.Expired -> {
                Toast.makeText(this, R.string.home_session_expired, Toast.LENGTH_LONG).show()
                goToLogin()
            }
            SessionState.LoggedOut -> goToLogin()
            SessionState.Loading, SessionState.LoggingOut, is SessionState.Error -> Unit
        }
    }

    private fun configureNavigation(isAdmin: Boolean) {
        if (navigationIsAdmin == isAdmin) return
        navigationIsAdmin = isAdmin

        val menu = binding.bottomNav.menu
        menu.clear()
        binding.bottomNav.inflateMenu(if (isAdmin) R.menu.admin_bottom_nav else R.menu.rider_bottom_nav)
        homeTabId = if (isAdmin) R.id.nav_home else R.id.nav_rider_home

        if (!isAdmin) {
            // The admin home (used only while loading) isn't needed for riders.
            supportFragmentManager.findFragmentByTag(tagFor(R.id.nav_home))?.let {
                supportFragmentManager.beginTransaction().remove(it).commitNow()
            }
        }

        val tab = restoredTabId?.takeIf { menu.findItem(it) != null } ?: homeTabId
        restoredTabId = null
        menu.findItem(tab)?.isChecked = true
        showTab(tab)

        binding.bottomNavContainer.isVisible = true
        ViewCompat.requestApplyInsets(binding.root)
    }

    /** Shows one tab and keeps the others alive (hidden) so their state survives switching. */
    private fun showTab(@IdRes tabId: Int) {
        val tag = tagFor(tabId)
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)

        fragmentManager.fragments.filter { it.tag != tag }.forEach { transaction.hide(it) }

        val existing = fragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            transaction.show(existing)
        } else {
            transaction.add(R.id.fragmentContainer, createTab(tabId), tag)
        }
        transaction.commit()

        backToHome.isEnabled = tabId != homeTabId
    }

    private fun tagFor(@IdRes tabId: Int) = "tab_$tabId"

    private fun createTab(@IdRes tabId: Int): Fragment = when (tabId) {
        // Admin
        R.id.nav_stock -> InventoryFragment()
        R.id.nav_riders -> RidersFragment()
        R.id.nav_sales -> SalesFragment()
        R.id.nav_reports -> ReportsFragment()
        // Rider
        R.id.nav_rider_home -> RiderHomeFragment()
        R.id.nav_sell -> SellFragment()
        R.id.nav_rider_stock -> RiderStockFragment()
        else -> HomeFragment()
    }

    /** The bottom navigation pads itself for the system nav bar; without it, the content does. */
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.root.updatePadding(left = bars.left, right = bars.right)
            binding.fragmentContainer.updatePadding(
                bottom = if (binding.bottomNavContainer.isVisible) 0 else bars.bottom,
            )
            insets
        }
    }

    private fun goToLogin() {
        startActivity(LoginActivity.newIntent(this))
        finish()
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"

        fun newIntent(context: Context): Intent =
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
