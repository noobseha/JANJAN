package com.gachon.janjan.domain.session.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.gachon.janjan.R
import com.gachon.janjan.domain.session.viewmodel.SessionViewModel

class QrScanFragment : Fragment() {
    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            QrScanScreen(
                sessionViewModel = sessionViewModel,
                onBack = { findNavController().popBackStack() },
                onInvite = { findNavController().navigate(R.id.inviteCodeFragment) },
                onOrderReady = { sessionId ->
                    findNavController().navigate(
                        R.id.orderFragment,
                        Bundle().apply { putString("sessionId", sessionId) }
                    )
                }
            )
        }
    }
}
