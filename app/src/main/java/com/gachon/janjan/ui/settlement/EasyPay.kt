package com.gachon.janjan.ui.settlement

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.gachon.janjan.databinding.EasyPayBinding
import java.text.DecimalFormat

class EasyPay(
    private val price: Int,
    private val onPaySelected: (String) -> Unit
) : DialogFragment() {

    private var _binding: EasyPayBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = EasyPayBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 금액 세팅
        val formattedPrice = DecimalFormat("#,###").format(price)
        binding.tvTargetPrice.text = "결제 금액 ${formattedPrice}원"

        binding.btnClose.setOnClickListener { dismiss() }

        // 페이 종류 클릭 이벤트 전달 및 창 닫기
        binding.btnTossPay.setOnClickListener {
            onPaySelected("토스페이")
            dismiss()
        }
        binding.btnKakaoPay.setOnClickListener {
            onPaySelected("카카오페이")
            dismiss()
        }
        binding.btnNaverPay.setOnClickListener {
            onPaySelected("네이버페이")
            dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
        // 가로 90% 황금 비율 코드 장착!
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