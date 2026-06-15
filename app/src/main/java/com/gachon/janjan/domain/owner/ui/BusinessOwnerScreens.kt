package com.gachon.janjan.domain.owner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gachon.janjan.domain.owner.model.BusinessTable
import com.gachon.janjan.domain.owner.model.TableCameraMapping
import com.gachon.janjan.domain.owner.viewmodel.BusinessOwnerViewModel

private val OwnerMint = Color(0xFF5AC4AF)
private val OwnerBg = Color(0xFFF6FAF9)
private val OwnerLine = Color(0xFFD7ECE8)
private val OwnerText = Color(0xFF1A1A1E)
private val OwnerSub = Color(0xFF6B7280)
private val OwnerMuted = Color(0xFFF0F7F5)
private val OwnerWarn = Color(0xFFF59E0B)
private val OwnerBlue = Color(0xFF3B82F6)

@Composable
fun BusinessOwnerScreen(
    viewModel: BusinessOwnerViewModel,
    onBack: () -> Unit
) {
    val storeId by viewModel.storeId.collectAsStateWithLifecycle()
    val storeName by viewModel.storeName.collectAsStateWithLifecycle()
    val tables by viewModel.tables.collectAsStateWithLifecycle()
    val mappings by viewModel.cameraMappings.collectAsStateWithLifecycle()
    val selectedTable by viewModel.selectedTable.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var editableStoreId by remember(storeId) { mutableStateOf(storeId) }
    var editableStoreName by remember(storeName) { mutableStateOf(storeName) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.consumeMessage()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val contentMaxWidth = when {
                maxWidth < 360.dp -> maxWidth
                maxWidth < 600.dp -> 520.dp
                maxWidth < 840.dp -> 640.dp
                else -> 720.dp
            }
            val horizontalPadding = when {
                maxWidth < 360.dp -> 16.dp
                maxWidth < 600.dp -> 24.dp
                else -> 28.dp
            }
            val tableColumns = when {
                maxWidth < 360.dp -> 2
                maxWidth < 720.dp -> 3
                else -> 4
            }
            Scaffold(containerColor = Color.White) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = contentMaxWidth),
                        contentPadding = PaddingValues(horizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = OwnerText)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("사업자 테이블 관리", color = OwnerText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("카메라 연결 · 실시간 인식", color = OwnerSub, fontSize = 13.sp)
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = OwnerBg),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editableStoreId,
                                        onValueChange = { editableStoreId = it },
                                        label = { Text("매장 ID") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = ownerTextFieldColors()
                                    )
                                    OutlinedTextField(
                                        value = editableStoreName,
                                        onValueChange = { editableStoreName = it },
                                        label = { Text("매장명") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = ownerTextFieldColors()
                                    )
                                    Button(
                                        onClick = { viewModel.loadStore(editableStoreId, editableStoreName) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OwnerMint),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("매장 불러오기", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        message?.let { text ->
                            item {
                                Text(
                                    text = text,
                                    color = OwnerText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(OwnerMuted)
                                        .padding(12.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        item {
                            Text("테이블", color = OwnerText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        tables.chunked(tableColumns).forEach { rowTables ->
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    rowTables.forEach { table ->
                                        OwnerTableTile(
                                            table = table,
                                            mapping = mappings.firstOrNull { it.tableId == table.tableId },
                                            modifier = Modifier.weight(1f),
                                            onClick = { viewModel.selectTable(table) }
                                        )
                                    }
                                    repeat(tableColumns - rowTables.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTable?.let { table ->
        val mapping = mappings.firstOrNull { it.tableId == table.tableId }
        CameraMappingDialog(
            table = table,
            mapping = mapping,
            isSaving = isSaving,
            onDismiss = viewModel::dismissTableDialog,
            onSave = { cameraName, cameraIp, streamUrl ->
                viewModel.saveCameraMapping(table, cameraName, cameraIp, streamUrl)
            }
        )
    }
}

@Composable
private fun OwnerTableTile(
    table: BusinessTable,
    mapping: TableCameraMapping?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val active = mapping?.isActive == true
    val status = mapping?.cameraStatus.orEmpty()
    val statusColor = when (status) {
        "recognizing" -> OwnerMint
        "waiting" -> OwnerBlue
        "activationRequested" -> OwnerWarn
        else -> if (active) OwnerMint else OwnerWarn
    }
    val statusLabel = when (status) {
        "recognizing" -> "인식 중"
        "waiting" -> "대기 중"
        "activationRequested" -> "연결 요청"
        else -> if (active) "연결됨" else "미연결"
    }
    val cameraInfo = if (mapping == null) {
        "카메라 정보 없음"
    } else {
        mapping.cameraDeviceId.ifBlank { mapping.cameraIp }.ifBlank { "카메라 정보 없음" }
    }
    val sessionInfo = mapping?.effectiveSessionId
        ?.ifBlank { table.activeSessionId }
        ?.ifBlank { "세션 대기" }
        ?: "세션 대기"
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (active) OwnerMint.copy(alpha = 0.45f) else OwnerLine)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(OwnerMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = OwnerMint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    table.displayName,
                    color = OwnerText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(statusLabel, color = OwnerSub, fontSize = 11.sp)
                }
                Text(
                    cameraInfo,
                    color = OwnerSub,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    sessionInfo,
                    color = OwnerSub,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CameraMappingDialog(
    table: BusinessTable,
    mapping: TableCameraMapping?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var cameraName by remember(mapping?.cameraName, table.tableId) {
        mutableStateOf(mapping?.cameraName.orEmpty().ifBlank { "${table.displayName} 카메라" })
    }
    var cameraIp by remember(mapping?.cameraIp, table.tableId) {
        mutableStateOf(mapping?.cameraIp.orEmpty())
    }
    var streamUrl by remember(mapping?.cameraStreamUrl, table.tableId) {
        mutableStateOf(mapping?.cameraStreamUrl.orEmpty())
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(table.displayName, color = OwnerText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cameraName,
                    onValueChange = { cameraName = it },
                    label = { Text("카메라 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = ownerTextFieldColors()
                )
                OutlinedTextField(
                    value = cameraIp,
                    onValueChange = { cameraIp = it },
                    label = { Text("스마트폰 카메라 IP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = ownerTextFieldColors()
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("스트림 URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = ownerTextFieldColors()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = OwnerMint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "세션 ${mapping?.sessionId?.ifBlank { "자동 생성" } ?: "자동 생성"}",
                        color = OwnerSub,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(cameraName, cameraIp, streamUrl) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = OwnerMint),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("연결")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("닫기", color = OwnerSub)
            }
        },
        containerColor = Color.White
    )
}

@Composable
private fun ownerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OwnerMint,
    focusedLabelColor = OwnerMint,
    cursorColor = OwnerMint
)
