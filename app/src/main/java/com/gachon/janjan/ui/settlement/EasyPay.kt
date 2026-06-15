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
    private val storeName: String,
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

        binding.btnTossPay.setOnClickListener {
            try {
                val uriString = "mocktoss://pay?storeName=${storeName}&amount=${price}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString))
                startActivity(intent)
                dismiss() // 앱이 성공적으로 열리면 팝업만 닫고 대기 (딥링크 복귀 대기)
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "토스 앱이 설치되어 있지 않습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnKakaoPay.setOnClickListener {
            try {
                val uriString = "mockkakao://pay?storeName=${storeName}&amount=${price}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString))
                startActivity(intent)
                dismiss() // 앱이 성공적으로 열리면 팝업만 닫고 대기 (딥링크 복귀 대기)
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "카카오페이 앱이 설치되어 있지 않습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnNaverPay.setOnClickListener {
            try {
                val uriString = "mocknaver://pay?storeName=${storeName}&amount=${price}"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString))
                startActivity(intent)
                dismiss() // 앱이 성공적으로 열리면 팝업만 닫고 대기 (딥링크 복귀 대기)
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "네이버페이 앱이 설치되어 있지 않습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
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