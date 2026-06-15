package com.gachon.janjan.domain.camera.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gachon.janjan.databinding.ActivityCameraDeviceBinding
import com.gachon.janjan.domain.camera.analyzer.CameraFrameAnalyzer
import com.gachon.janjan.domain.camera.model.CameraDeviceConfig
import com.gachon.janjan.domain.camera.repository.CameraDeviceRepository
import com.gachon.janjan.domain.camera.viewmodel.CameraDeviceViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraDeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraDeviceBinding
    private val viewModel: CameraDeviceViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private val deviceId: String by lazy { buildDeviceId() }
    private var frameAnalyzer: CameraFrameAnalyzer? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            bindCamera()
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyBottomSystemBarPadding()
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.tvDeviceInfo.text = "장치 ID: $deviceId"
        restoreSavedConfig()
        setupActions()
        observeState()
        ensureCameraPermission()
    }

    private fun applyBottomSystemBarPadding() {
        val defaultBottomPadding = binding.layoutStatusOverlay.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutStatusOverlay) { view, insets ->
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = defaultBottomPadding + navigationBottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.layoutStatusOverlay)
    }

    private fun setupActions() {
        binding.btnRegister.setOnClickListener {
            val config = readConfigFromInputs()
            if (config.storeId.isBlank() || config.tableId.isBlank()) {
                Toast.makeText(this, "storeId와 tableId를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveConfig(config)
            viewModel.configure(config)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                bindCamera()
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.btnRegister.isEnabled = !state.isLoading
                    binding.tvCameraStatus.text = "상태: ${state.cameraStatus.toKoreanStatus()}"
                    binding.tvSessionStatus.text = if (state.activeSessionId.isBlank()) {
                        "세션: 활성 세션 대기 중"
                    } else {
                        "세션: ${state.activeSessionId}"
                    }
                    binding.tvDetectionSummary.text = "최근 감지: ${state.lastDetectionSummary}"
                    binding.tvUploadStatus.text = "마지막 업로드: ${state.lastUploadLabel}"

                    state.message?.let { message ->
                        Toast.makeText(this@CameraDeviceActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        frameAnalyzer?.close()
                        val confidenceThreshold = readConfidenceThreshold()
                        val analyzer = CameraFrameAnalyzer(applicationContext, deviceId, confidenceThreshold) { result ->
                            runOnUiThread {
                                binding.detectionOverlay.setDetections(
                                    frameWidth = result.frameWidth,
                                    frameHeight = result.frameHeight,
                                    detections = result.detections
                                )
                            }
                            viewModel.submitAnalysis(result)
                        }
                        frameAnalyzer = analyzer
                        useCase.setAnalyzer(
                            cameraExecutor,
                            analyzer
                        )
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun readConfigFromInputs(): CameraDeviceConfig {
        val tableId = binding.etTableId.text.toString().trim().ifBlank {
            val number = binding.etTableNumber.text.toString().toIntOrNull() ?: 1
            "table_$number"
        }
        val tableNumber = binding.etTableNumber.text.toString().toIntOrNull()
            ?: tableId.filter { it.isDigit() }.toIntOrNull()
            ?: 0
        return CameraDeviceConfig(
            deviceId = deviceId,
            storeId = binding.etStoreId.text.toString().trim(),
            tableId = tableId,
            tableNumber = tableNumber,
            confidenceThreshold = readConfidenceThreshold(),
            nearDistanceThreshold = readNearDistanceThreshold(),
            pourThresholdMs = readPourThresholdMs(),
            colorMappingThresholdMs = DEFAULT_COLOR_MAPPING_THRESHOLD_MS
        )
    }

    private fun readConfidenceThreshold(): Float =
        binding.etConfidenceThreshold.text.toString().toFloatOrNull()
            ?.coerceIn(0.1f, 0.9f)
            ?: DEFAULT_CONFIDENCE_THRESHOLD

    private fun readNearDistanceThreshold(): Float =
        binding.etNearThreshold.text.toString().toFloatOrNull()
            ?.coerceIn(MIN_NEAR_THRESHOLD, MAX_NEAR_THRESHOLD)
            ?: DEFAULT_NEAR_THRESHOLD

    private fun readPourThresholdMs(): Int =
        binding.etPourThresholdMs.text.toString().toIntOrNull()
            ?.coerceIn(500, 5000)
            ?: DEFAULT_POUR_THRESHOLD_MS

    private fun restoreSavedConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storeId = prefs.getString(KEY_STORE_ID, "").orEmpty()
        val tableId = prefs.getString(KEY_TABLE_ID, "table_1").orEmpty()
        val tableNumber = prefs.getInt(KEY_TABLE_NUMBER, 1)
        val confidenceThreshold = prefs.getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)
        val savedNearThreshold = prefs.getFloat(KEY_NEAR_THRESHOLD, DEFAULT_NEAR_THRESHOLD)
        val nearThreshold = when (savedNearThreshold) {
            OLD_DEFAULT_NEAR_THRESHOLD, PREVIOUS_DEFAULT_NEAR_THRESHOLD -> DEFAULT_NEAR_THRESHOLD
            else -> savedNearThreshold.coerceIn(MIN_NEAR_THRESHOLD, MAX_NEAR_THRESHOLD)
        }
        val pourThresholdMs = prefs.getInt(KEY_POUR_THRESHOLD_MS, DEFAULT_POUR_THRESHOLD_MS)

        binding.etStoreId.setText(storeId)
        binding.etTableId.setText(tableId)
        binding.etTableNumber.setText(tableNumber.toString())
        binding.etConfidenceThreshold.setText(confidenceThreshold.toString())
        binding.etNearThreshold.setText(nearThreshold.toString())
        binding.etPourThresholdMs.setText(pourThresholdMs.toString())

        if (storeId.isNotBlank() && tableId.isNotBlank()) {
            viewModel.configure(
                CameraDeviceConfig(
                    deviceId = deviceId,
                    storeId = storeId,
                    tableId = tableId,
                    tableNumber = tableNumber,
                    confidenceThreshold = confidenceThreshold,
                    nearDistanceThreshold = nearThreshold,
                    pourThresholdMs = pourThresholdMs,
                    colorMappingThresholdMs = DEFAULT_COLOR_MAPPING_THRESHOLD_MS
                )
            )
        }
    }

    private fun saveConfig(config: CameraDeviceConfig) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STORE_ID, config.storeId)
            .putString(KEY_TABLE_ID, config.tableId)
            .putInt(KEY_TABLE_NUMBER, config.tableNumber)
            .putFloat(KEY_CONFIDENCE_THRESHOLD, config.confidenceThreshold)
            .putFloat(KEY_NEAR_THRESHOLD, config.nearDistanceThreshold)
            .putInt(KEY_POUR_THRESHOLD_MS, config.pourThresholdMs)
            .apply()
    }

    private fun buildDeviceId(): String {
        val rawId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_camera"
        return "camera_${rawId.replace(Regex("[^A-Za-z0-9_-]"), "_")}"
    }

    private fun String.toKoreanStatus(): String =
        when (this) {
            CameraDeviceRepository.STATUS_RECOGNIZING -> "인식 중"
            CameraDeviceRepository.STATUS_WAITING -> "대기 중"
            "setup" -> "설정 필요"
            else -> this
        }

    override fun onDestroy() {
        frameAnalyzer?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NAME = "janjan_camera_device"
        private const val KEY_STORE_ID = "store_id"
        private const val KEY_TABLE_ID = "table_id"
        private const val KEY_TABLE_NUMBER = "table_number"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_NEAR_THRESHOLD = "near_threshold"
        private const val KEY_POUR_THRESHOLD_MS = "pour_threshold_ms"
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.35f
        private const val DEFAULT_NEAR_THRESHOLD = 0.08f
        private const val OLD_DEFAULT_NEAR_THRESHOLD = 0.18f
        private const val PREVIOUS_DEFAULT_NEAR_THRESHOLD = 0.24f
        private const val MIN_NEAR_THRESHOLD = 0.01f
        private const val MAX_NEAR_THRESHOLD = 0.25f
        private const val DEFAULT_POUR_THRESHOLD_MS = 1500
        private const val DEFAULT_COLOR_MAPPING_THRESHOLD_MS = 1800
    }
}
