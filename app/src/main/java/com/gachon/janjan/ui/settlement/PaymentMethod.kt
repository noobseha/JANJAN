package com.gachon.janjan.ui.settlement

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.gachon.janjan.databinding.PaymentMethodBinding
import java.text.DecimalFormat

class PaymentMethod(
    private val price: Int,
    private val storeName: String,
    private val onPaymentSelected: (String) -> Unit
) : DialogFragment() {

    private var _binding: PaymentMethodBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = PaymentMethodBinding.inflate(inflater, container, false)

        // 다이얼로그 기본 사각형 배경을 투명하게 만들어 우리가 만든 라운드 카드가 보이게 설정
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 부모 화면(Settlement)에서 받아온 금액 적용
        val formattedPrice = DecimalFormat("#,###").format(price)
        binding.tvTargetPrice.text = "결제 금액 ${formattedPrice}원"

        // X 버튼 클릭 시 닫기
        binding.btnClose.setOnClickListener { dismiss() }

        // 각 결제 방식 클릭 이벤트
        binding.btnEasyPay.setOnClickListener {
            // 1. 간편결제를 선택하면 현재 켜져 있는 결제수단 다이얼로그는 닫기
            dismiss()

            // 2. 곧바로 간편결제 전용 상세 팝업창(EasyPayDialog)을 이어서 띄움
            val easyPayDialog = EasyPay(price = price, storeName = storeName) { selectedPay ->
                // 토스, 카카오, 네이버페이 중 하나를 최종 선택했을 때 넘어오는 결과 코드
                // 이 람다식을 통해 부모 Fragment(SettlementFragment)까지 이벤트를 릴레이 시켜주기 위함
                onPaymentSelected(selectedPay)
            }
            easyPayDialog.show(parentFragmentManager, "EasyPay")
        }
        binding.btnCardPay.setOnClickListener {
            // 1. 신용/체크를 선택하면 현재 결제수단 팝업창은 닫기
            dismiss()

            // 2. 곧바로 카드 정보 입력 상세 팝업창(CardPayDialog)을 이어받아 띄움
            val cardPayDialog = CardPay(price = price) { resultMessage ->
                // 카드가 정상 입력되어 최종 결제 버튼을 누르면 넘어오는 콜백 결과
                onPaymentSelected(resultMessage)
            }
            cardPayDialog.show(parentFragmentManager, "CardPayDialog")
        }
        binding.btnDirectPay.setOnClickListener {
            onPaymentSelected("직접 결제")
            dismiss()

            val directPay = DirectPay(price = price)
            directPay.show(parentFragmentManager, "DirectPay")
        }
    }

    override fun onResume() {
        super.onResume()

        // 디바이스의 화면 가로 크기를 구해서 다이얼로그 윈도우에 강제로 주입
        dialog?.window?.let { window ->
            val params = window.attributes

            // 1. 전체 디바이스 화면 가로 크기의 90% 만큼 쓰는 설정
            val displayMetrics = requireContext().resources.displayMetrics
            params.width = (displayMetrics.widthPixels * 0.9).toInt()

            // 2. 높이는 내용물(Wrap Content)에 맞게 알아서 조절되도록 설정
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT

            window.attributes = params
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
