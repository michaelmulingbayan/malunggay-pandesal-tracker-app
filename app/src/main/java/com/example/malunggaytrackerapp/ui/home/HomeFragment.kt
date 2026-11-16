package com.example.malunggaytrackerapp.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.UserDto
import com.example.malunggaytrackerapp.databinding.FragmentHomeBinding
import com.example.malunggaytrackerapp.ui.common.initialsOf
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import kotlinx.coroutines.launch

/** Home tab: who is signed in, plus logout. Dashboard widgets can be added here later. */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val sessionViewModel: SessionViewModel by activityViewModels { SessionViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        binding.header.padTopForStatusBar()

        binding.logoutButton.setOnClickListener { sessionViewModel.logout() }
        binding.retryButton.setOnClickListener { sessionViewModel.loadUser() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.state.collect { state -> render(binding, state) }
            }
        }
    }

    private fun render(binding: FragmentHomeBinding, state: SessionState) {
        binding.progress.isVisible = state is SessionState.Loading
        binding.logoutButton.isEnabled = state !is SessionState.Loading
        binding.errorState.isVisible = state is SessionState.Error

        when (state) {
            is SessionState.Ready -> showUser(binding, state.user)
            is SessionState.Error -> binding.messageText.setText(state.message)
            else -> Unit
        }
    }

    private fun showUser(binding: FragmentHomeBinding, user: UserDto) {
        binding.avatarText.text = initialsOf(user.name)
        binding.welcomeText.text = getString(R.string.home_welcome, user.name.substringBefore(' '))
        binding.emailText.text = user.email
        binding.roleText.text = user.role.replaceFirstChar { it.uppercase() }
        binding.roleText.isVisible = true
    }
}
