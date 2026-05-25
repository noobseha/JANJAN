package com.gachon.janjan.domain.owner.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gachon.janjan.domain.owner.model.BusinessTable
import com.gachon.janjan.domain.owner.model.TableCameraMapping
import com.gachon.janjan.domain.owner.repository.BusinessCameraRepository
import com.gachon.janjan.domain.session.FirebaseConfig
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BusinessOwnerViewModel(
    private val repository: BusinessCameraRepository = BusinessCameraRepository()
) : ViewModel() {
    private var tableListener: ListenerRegistration? = null
    private var mappingListener: ListenerRegistration? = null

    private val _storeId = MutableStateFlow(DEFAULT_STORE_ID)
    val storeId: StateFlow<String> = _storeId.asStateFlow()

    private val _storeName = MutableStateFlow(DEFAULT_STORE_NAME)
    val storeName: StateFlow<String> = _storeName.asStateFlow()

    private val _tables = MutableStateFlow<List<BusinessTable>>(emptyList())
    val tables: StateFlow<List<BusinessTable>> = _tables.asStateFlow()

    private val _cameraMappings = MutableStateFlow<List<TableCameraMapping>>(emptyList())
    val cameraMappings: StateFlow<List<TableCameraMapping>> = _cameraMappings.asStateFlow()

    private val _selectedTable = MutableStateFlow<BusinessTable?>(null)
    val selectedTable: StateFlow<BusinessTable?> = _selectedTable.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val currentUserId: String
        get() = FirebaseConfig.auth.currentUser?.uid ?: "owner_pending_user"

    init {
        loadStore(DEFAULT_STORE_ID, DEFAULT_STORE_NAME)
    }

    fun loadStore(storeId: String, storeName: String = _storeName.value) {
        val resolvedStoreId = storeId.trim().ifBlank { DEFAULT_STORE_ID }
        val resolvedStoreName = storeName.trim().ifBlank { DEFAULT_STORE_NAME }
        _storeId.value = resolvedStoreId
        _storeName.value = resolvedStoreName

        tableListener?.remove()
        mappingListener?.remove()
        tableListener = repository.listenTables(resolvedStoreId, resolvedStoreName) { tables ->
            _tables.value = tables
        }
        mappingListener = repository.listenCameraMappings(resolvedStoreId) { mappings ->
            _cameraMappings.value = mappings
        }
    }

    fun selectTable(table: BusinessTable) {
        _selectedTable.value = table
    }

    fun dismissTableDialog() {
        _selectedTable.value = null
    }

    fun mappingFor(tableId: String): TableCameraMapping? =
        _cameraMappings.value.firstOrNull { it.tableId == tableId }

    fun saveCameraMapping(
        table: BusinessTable,
        cameraName: String,
        cameraIp: String,
        cameraStreamUrl: String
    ) {
        if (cameraIp.trim().isBlank()) {
            _message.value = "카메라 IP를 입력해주세요."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            runCatching {
                ensureSignedIn()
                repository.saveCameraMapping(
                    storeId = _storeId.value,
                    storeName = _storeName.value,
                    table = table,
                    cameraName = cameraName,
                    cameraIp = cameraIp,
                    cameraStreamUrl = cameraStreamUrl,
                    ownerUserId = currentUserId
                )
            }.onSuccess { sessionId ->
                _message.value = "${table.displayName} 카메라 연결 요청을 보냈습니다."
                _selectedTable.value = null
                if (sessionId.isNotBlank()) {
                    loadStore(_storeId.value, _storeName.value)
                }
            }.onFailure {
                _message.value = "카메라 매핑 실패: ${it.message ?: "알 수 없는 오류"}"
            }
            _isSaving.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private suspend fun ensureSignedIn() {
        if (FirebaseConfig.auth.currentUser == null) {
            FirebaseConfig.auth.signInAnonymously().await()
        }
    }

    override fun onCleared() {
        tableListener?.remove()
        mappingListener?.remove()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_STORE_ID = "1"
        private const val DEFAULT_STORE_NAME = "더치페이 테스트포차"
    }
}
