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
import com.gachon.janjan.databinding.CardPayBinding
import java.text.DecimalFormat
import android.widget.ArrayAdapter

class CardPay(
    private val price: Int,
    private val onPayCompleted: (String) -> Unit
) : DialogFragment() {

    private var _binding: CardPayBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = CardPayBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 결제 금액 세팅
        val formattedPrice = DecimalFormat("#,###").format(price)
        binding.tvTargetPrice.text = "결제 금액 ${formattedPrice}원"

        val cardCompanies = listOf("신한카드", "국민카드", "현대카드", "삼성카드", "농협카드", "BC카드", "하나카드", "롯데카드")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, cardCompanies)
        binding.actvCardCompany.setAdapter(adapter)

        binding.btnClose.setOnClickListener { dismiss() }

        // 결제하기 버튼 클릭 리스너 및 유효성 검사
        binding.btnSubmitPay.setOnClickListener {
            // 선택된 카드사 가져오기
            val selectedCompany = binding.actvCardCompany.text.toString().trim()
            val cardNumber = binding.etCardNumber.text.toString().trim()
            val expiry = binding.etExpiry.text.toString().trim()
            val pwd = binding.etPwd.text.toString().trim()
            val birth = binding.etBirth.text.toString().trim()

            // 🌟 유효성 검사에 '카드사 선택 여부'도 추가!
            if (selectedCompany.isEmpty() || selectedCompany == "카드사 선택") {
                Toast.makeText(requireContext(), "카드사를 선택해주세요.", Toast.LENGTH_SHORT).show()
            } else if (cardNumber.length < 16 || expiry.isEmpty() || pwd.length < 2 || birth.isEmpty()) {
                Toast.makeText(requireContext(), "카드 정보를 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                // 어떤 카드사로 결제했는지 결과에 쏙 넣어주기
                onPayCompleted("$selectedCompany 결제 완료")
                dismiss()
            }
        }
        // 카드번호 4자리마다 자동으로 띄어쓰기 (16자리 입력 -> 19자리 포맷팅)
        binding.etCardNumber.addTextChangedListener(object : android.text.TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdating) return
                isUpdating = true

                // 기존 공백을 모두 제거한 순수 숫자만 추출
                val original = s.toString().replace(" ", "")
                val sb = StringBuilder()

                // 4자리마다 공백 주입
                for (i in original.indices) {
                    if (i > 0 && i % 4 == 0) {
                        sb.append(" ")
                    }
                    sb.append(original[i])
                }

                // ⚠️ XML의 maxLength가 16이면 공백 때문에 잘리므로 코드로 최대 길이를 19로 보정해 줍니다.
                // (숫자 16자 + 공백 3자 = 총 19자)
                binding.etCardNumber.filters = arrayOf(android.text.InputFilter.LengthFilter(19))

                binding.etCardNumber.setText(sb.toString())
                binding.etCardNumber.setSelection(sb.length) // 커서를 항상 맨 뒤로 이동

                isUpdating = false
            }
        })
        // 만료일 월/년 사이 자동으로 슬래시(/) 생성 (MM/YY)
        binding.etExpiry.addTextChangedListener(object : android.text.TextWatcher {
            private var isUpdating = false
            private var oldLength = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                oldLength = s?.length ?: 0
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdating) return
                isUpdating = true

                val currentStr = s.toString().replace("/", "")
                val isDeleting = oldLength > (s?.length ?: 0) // 유저가 지우는 중인지 확인

                val sb = StringBuilder()

                // 2글자(월)가 입력되면 자동으로 뒤에 '/'를 붙여줌 (단, 지우는 중이 아닐 때만)
                if (currentStr.length >= 2) {
                    sb.append(currentStr.substring(0, 2))
                    if (!isDeleting || currentStr.length > 2) {
                        sb.append("/")
                    }
                    if (currentStr.length > 2) {
                        sb.append(currentStr.substring(2))
                    }
                } else {
                    sb.append(currentStr)
                }

                binding.etExpiry.setText(sb.toString())
                binding.etExpiry.setSelection(sb.length) // 커서를 항상 맨 뒤로 이동

                isUpdating = false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // 가로 90% 맞춤 크기 최적화 코드
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