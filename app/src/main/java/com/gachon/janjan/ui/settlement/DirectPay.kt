package com.gachon.janjan.ui.settlement // ⚠️ 프로젝트 패키지 경로에 맞게 확인해줘!

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.gachon.janjan.databinding.DirectPayBinding
import java.text.DecimalFormat

class DirectPay(
    private val price: Int
) : DialogFragment() {

    private var _binding: DirectPayBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DirectPayBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 결제 금액 세팅 및 원화 포맷팅
        val formattedPrice = DecimalFormat("#,###").format(price)
        binding.tvTargetPrice.text = "결제 금액 ${formattedPrice}원"

        // X 버튼 누르면 창 닫기
        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onResume() {
        super.onResume()
        // 가로 90% 와이드 스크린 최적화 코드
        dialog?.window?.let { window ->
            val params = window.attributes
            val displayMetrics = requireContext().resources.displayMetrics
            params.width = (displayMetrics.widthPixels * 0.9).toInt()
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = params
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}