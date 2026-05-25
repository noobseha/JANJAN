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

class GlassColorFragment : Fragment() {
    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val sessionId = requireArguments().getString("sessionId").orEmpty()
        val showDone = requireArguments().getBoolean("showDone", false)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GlassColorScreen(
                    sessionViewModel = sessionViewModel,
                    sessionId = sessionId,
                    onBack = { findNavController().popBackStack() },
                    onDone = {
                        if (showDone) {
                            findNavController().navigate(
                                R.id.doneFragment,
                                Bundle().apply { putString("sessionId", sessionId) }
                            )
                        } else {
                            val returnedHome = findNavController().popBackStack(R.id.sessionHomeFragment, false)
                            if (!returnedHome) {
                                findNavController().navigate(R.id.sessionHomeFragment)
                            }
                        }
                    }
                )
            }
        }
    }
}
