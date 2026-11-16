package com.example.malunggaytrackerapp.ui.riders

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ActivityAddRiderBinding
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.setupBrandedEdgeToEdge
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Admin › Riders › Add: creates the rider's sign-in account and profile. Returns RESULT_OK on success. */
class AddRiderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddRiderBinding
    private val viewModel: AddRiderViewModel by viewModels { AddRiderViewModel.Factory }
    private var lastErrorKeys: Set<String> = emptySet()

    private val fields: Map<String, Pair<TextInputLayout, TextInputEditText>> by lazy {
        mapOf(
            RiderField.NAME to (binding.nameLayout to binding.nameInput),
            RiderField.EMAIL to (binding.emailLayout to binding.emailInput),
            RiderField.PASSWORD to (binding.passwordLayout to binding.passwordInput),
            RiderField.PHONE to (binding.phoneLayout to binding.phoneInput),
            RiderField.ADDRESS to (binding.addressLayout to binding.addressInput),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRiderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBrandedEdgeToEdge(root = binding.root, header = binding.header)

        binding.backButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { save() }
        fields.forEach { (field, views) ->
            views.second.doAfterTextChanged { viewModel.clearFieldError(field) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun save() {
        viewModel.save(
            name = binding.nameInput.text?.toString().orEmpty(),
            email = binding.emailInput.text?.toString().orEmpty(),
            password = binding.passwordInput.text?.toString().orEmpty(),
            phone = binding.phoneInput.text?.toString().orEmpty(),
            address = binding.addressInput.text?.toString().orEmpty(),
        )
    }

    private fun render(state: AddRiderUiState) {
        if (state.isSaved) {
            setResult(Activity.RESULT_OK)
            finish()
            return
        }

        fields.forEach { (field, views) ->
            val layout = views.first
            layout.error = state.fieldErrors[field]?.asString(this)
            layout.isEnabled = !state.isSaving
        }
        // When a save attempt produces new errors, move focus to the first invalid field.
        val errorKeys = state.fieldErrors.keys
        if (!lastErrorKeys.containsAll(errorKeys)) {
            fields.entries.firstOrNull { it.key in errorKeys }?.value?.second?.requestFocus()
        }
        lastErrorKeys = errorKeys

        val general = state.generalError?.asString(this)
        binding.errorText.text = general
        binding.errorBanner.isVisible = general != null

        binding.progress.visibility = if (state.isSaving) View.VISIBLE else View.GONE
        binding.saveButton.text = if (state.isSaving) "" else getString(R.string.add_rider_save)
        binding.saveButton.contentDescription = if (state.isSaving) getString(R.string.add_rider_saving) else null
        binding.saveButton.isClickable = !state.isSaving
        binding.backButton.isEnabled = !state.isSaving
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, AddRiderActivity::class.java)
    }
}
