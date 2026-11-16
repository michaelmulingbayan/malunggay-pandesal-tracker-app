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
import com.example.malunggaytrackerapp.ui.riders.RidersFragment
import com.example.malunggaytrackerapp.ui.sales.SalesFragment
import kotlinx.coroutines.launch

/**
 * Signed-in shell. Admins get a bottom navigation (Home, Stock, Riders, Sales, Reports);
 * riders only see Home.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val sessionViewModel: SessionViewModel by viewModels { SessionViewModel.Factory }

    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.bottomNav.selectedItemId = R.id.nav_home
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

        if (savedInstanceState == null) {
            showTab(R.id.nav_home)
        }
        // If the last load succeeded before a config change, restore admin navigation immediately.
        sessionViewModel.lastUser?.let { setAdminNavigationVisible(it.isAdmin) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: SessionState) {
        when (state) {
            is SessionState.Ready -> setAdminNavigationVisible(state.user.isAdmin)
            SessionState.Expired -> {
                Toast.makeText(this, R.string.home_session_expired, Toast.LENGTH_LONG).show()
                goToLogin()
            }
            SessionState.LoggedOut -> goToLogin()
            SessionState.Loading, is SessionState.Error -> Unit
        }
    }

    private fun setAdminNavigationVisible(isAdmin: Boolean) {
        if (binding.bottomNavContainer.isVisible == isAdmin) return
        binding.bottomNavContainer.isVisible = isAdmin
        if (!isAdmin && binding.bottomNav.selectedItemId != R.id.nav_home) {
            showTab(R.id.nav_home)
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /** Shows one tab and keeps the others alive (hidden) so their state survives switching. */
    private fun showTab(@IdRes tabId: Int) {
        val tag = "tab_$tabId"
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

        backToHome.isEnabled = tabId != R.id.nav_home
    }

    private fun createTab(@IdRes tabId: Int): Fragment = when (tabId) {
        R.id.nav_stock -> InventoryFragment()
        R.id.nav_riders -> RidersFragment()
        R.id.nav_sales -> SalesFragment()
        R.id.nav_reports -> SectionFragment.newInstance(
            R.string.nav_reports, R.string.section_reports_subtitle, R.string.section_reports_empty, R.drawable.ic_nav_reports,
        )
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
        fun newIntent(context: Context): Intent =
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
