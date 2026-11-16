package com.example.malunggaytrackerapp.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.BuildConfig
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ActivityLoginBinding
import com.example.malunggaytrackerapp.ui.common.LocalNetworkAccess
import com.example.malunggaytrackerapp.ui.common.setupBrandedEdgeToEdge
import com.example.malunggaytrackerapp.ui.home.HomeActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBrandedEdgeToEdge(root = binding.root, header = binding.header)
        LocalNetworkAccess.requestIfNeeded(this)

        binding.versionText.text = getString(R.string.login_version, BuildConfig.VERSION_NAME)

        binding.loginButton.setOnClickListener { submit() }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.passwordInput.doAfterTextChanged { binding.passwordLayout.error = null }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun submit() {
        viewModel.login(
            email = binding.emailInput.text?.toString().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
        )
    }

    private fun render(state: LoginUiState) {
        if (state.isLoggedIn) {
            startActivity(HomeActivity.newIntent(this))
            finish()
            return
        }

        renderLoading(state.isLoading)

        binding.emailLayout.error = state.emailError?.asString(this)
        binding.passwordLayout.error = state.passwordError?.asString(this)

        val general = state.generalError?.asString(this)
        binding.errorText.text = general
        binding.errorBanner.visibility = if (general != null) View.VISIBLE else View.GONE
    }

    /**
     * While loading, the button keeps its emerald fill and shows a spinner instead of its label
     * (a greyed-out disabled button would look like an error). Clicks are ignored by the ViewModel.
     */
    private fun renderLoading(isLoading: Boolean) {
        binding.progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loginButton.text = if (isLoading) "" else getString(R.string.login_button)
        binding.loginButton.contentDescription = if (isLoading) getString(R.string.login_signing_in) else null
        binding.loginButton.isClickable = !isLoading
        binding.emailLayout.isEnabled = !isLoading
        binding.passwordLayout.isEnabled = !isLoading
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id)
    is UiText.Raw -> text
}
