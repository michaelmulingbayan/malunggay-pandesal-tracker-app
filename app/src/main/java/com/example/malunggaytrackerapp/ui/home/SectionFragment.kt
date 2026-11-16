package com.example.malunggaytrackerapp.ui.home

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.FragmentSectionBinding
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar

/**
 * Placeholder for an admin section (Stock, Riders, Sales, Reports) until its API endpoints exist.
 * Replace each with a dedicated fragment as the feature is built.
 */
class SectionFragment : Fragment(R.layout.fragment_section) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSectionBinding.bind(view)
        val args = requireArguments()

        binding.header.padTopForStatusBar()
        binding.titleText.setText(args.getInt(ARG_TITLE))
        binding.subtitleText.setText(args.getInt(ARG_SUBTITLE))
        binding.emptyText.setText(args.getInt(ARG_EMPTY))
        binding.iconImage.setImageResource(args.getInt(ARG_ICON))
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_EMPTY = "empty"
        private const val ARG_ICON = "icon"

        fun newInstance(
            @StringRes title: Int,
            @StringRes subtitle: Int,
            @StringRes emptyMessage: Int,
            @DrawableRes icon: Int,
        ) = SectionFragment().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_SUBTITLE to subtitle,
                ARG_EMPTY to emptyMessage,
                ARG_ICON to icon,
            )
        }
    }
}
