package com.gachon.janjan.domain.owner.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.gachon.janjan.domain.owner.viewmodel.BusinessOwnerViewModel

class BusinessOwnerFragment : Fragment() {
    private val viewModel: BusinessOwnerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            BusinessOwnerScreen(
                viewModel = viewModel,
                onBack = { findNavController().popBackStack() }
            )
        }
    }
}
