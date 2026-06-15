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
import com.gachon.janjan.domain.session.viewmodel.HistoryHealthViewModel
import com.gachon.janjan.domain.session.viewmodel.RankingViewModel
import com.gachon.janjan.domain.session.viewmodel.SessionViewModel
import com.gachon.janjan.ui.settlement.PaymentMethod

class SessionHomeFragment : Fragment() {
    private val sessionViewModel: SessionViewModel by activityViewModels()
    private val historyHealthViewModel: HistoryHealthViewModel by activityViewModels()
    private val rankingViewModel: RankingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SessionHomeScreen(
                sessionViewModel = sessionViewModel,
                historyHealthViewModel = historyHealthViewModel,
                rankingViewModel = rankingViewModel,
                onQrScan = { findNavController().navigate(R.id.qrScanFragment) },
                onInviteCode = { findNavController().navigate(R.id.inviteCodeFragment) },
                onOrder = {
                    findNavController().navigate(
                        R.id.orderFragment,
                        Bundle().apply {
                            putString("sessionId", sessionViewModel.activeSessionId.value)
                        }
                    )
                },
                onGlassColor = { sessionId ->
                    findNavController().navigate(
                        R.id.glassColorFragment,
                        Bundle().apply {
                            putString("sessionId", sessionId)
                            putBoolean("showDone", false)
                        }
                    )
                },
                onShowPaymentMethod = { price, storeName, onComplete ->
                    val dialog = PaymentMethod(
                        price = price,
                        storeName = storeName
                    ) { selectedPay ->
                        onComplete(selectedPay)
                    }
                    dialog.show(childFragmentManager, "PaymentMethod")
                }
            )
        }
    }
}
