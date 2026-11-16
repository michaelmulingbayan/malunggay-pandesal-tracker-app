package com.example.malunggaytrackerapp.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.remote.UserDto
import com.example.malunggaytrackerapp.databinding.ActivityHomeBinding
import com.example.malunggaytrackerapp.ui.common.setupBrandedEdgeToEdge
import com.example.malunggaytrackerapp.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * Placeholder home screen: confirms the token works by calling /api/v1/auth/me and offers logout.
 * Replace with the real admin / rider screens later.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val authRepository by lazy { (application as MalunggayApp).authRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBrandedEdgeToEdge(root = binding.root, header = binding.header)

        binding.logoutButton.setOnClickListener { logout() }
        loadUser()
    }

    private fun loadUser() {
        setLoading(true)
        lifecycleScope.launch {
            when (val result = authRepository.currentUser()) {
                is ApiResult.Success -> showUser(result.data)
                ApiResult.Unauthorized -> {
                    Toast.makeText(this@HomeActivity, R.string.home_session_expired, Toast.LENGTH_LONG).show()
                    goToLogin()
                }
                ApiResult.NetworkError -> showMessage(R.string.error_network)
                else -> showMessage(R.string.error_unexpected)
            }
            setLoading(false)
        }
    }

    private fun showUser(user: UserDto) {
        binding.messageText.visibility = View.GONE
        binding.avatarText.text = initials(user.name)
        binding.welcomeText.text = getString(R.string.home_welcome, user.name.substringBefore(' '))
        binding.emailText.text = user.email
        binding.roleText.text = user.role.replaceFirstChar { it.uppercase() }
        binding.roleText.visibility = View.VISIBLE
    }

    private fun showMessage(@StringRes messageRes: Int) {
        binding.messageText.setText(messageRes)
        binding.messageText.visibility = View.VISIBLE
    }

    private fun logout() {
        setLoading(true)
        lifecycleScope.launch {
            authRepository.logout()
            goToLogin()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.logoutButton.isEnabled = !isLoading
    }

    private fun goToLogin() {
        startActivity(LoginActivity.newIntent(this))
        finish()
    }

    private fun initials(name: String): String =
        name.split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
