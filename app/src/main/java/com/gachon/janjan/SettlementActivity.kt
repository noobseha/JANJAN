package com.gachon.janjan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gachon.janjan.data.repository.PaymentRepository
import com.gachon.janjan.databinding.ActivitySettlementBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class SettlementActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettlementBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val paymentRepository = PaymentRepository()
    private lateinit var adapter: ParticipantAdapter
    private var settlementId = ""
    private var sessionId = ""
    private var storeId = ""
    private var tableDocId = ""
    private var tableNumber = 0
    private var settlementListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null
    private var participantListener: ListenerRegistration? = null
    private var liveSessionDoc: DocumentSnapshot? = null
    private var liveParticipantDocs: List<DocumentSnapshot> = emptyList()
    private var liveSessionListenerId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettlementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tableNumber = intent.getIntExtra("tableId", 0)
        tableDocId = intent.getStringExtra("tableDocId").orEmpty()
            .ifBlank { tableNumber.takeIf { it > 0 }?.let { "table_$it" }.orEmpty() }
        sessionId = intent.getStringExtra("sessionId").orEmpty()
        storeId = intent.getStringExtra("storeId").orEmpty()
            .ifBlank { auth.currentUser?.uid.orEmpty() }

        binding.tvTitle.text = if (tableNumber > 0) {
            "결제 현황 - 테이블 $tableNumber"
        } else {
            "결제 현황"
        }

        adapter = ParticipantAdapter(mutableListOf()) { participant, isChecked ->
            updatePaidStatus(participant, isChecked)
        }

        binding.rvParticipants.layoutManager = LinearLayoutManager(this)
        binding.rvParticipants.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        loadSettlement()
    }

    private fun loadSettlement() {
        if (sessionId.isNotBlank()) {
            listenSettlementBySession(sessionId)
            return
        }

        val resolvedStoreId = storeId.ifBlank { auth.currentUser?.uid.orEmpty() }
        if (resolvedStoreId.isBlank() || tableDocId.isBlank()) {
            Toast.makeText(this, "테이블 세션 정보를 확인하지 못했습니다", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("stores").document(resolvedStoreId)
            .collection("tables").document(tableDocId)
            .get()
            .addOnSuccessListener { tableDoc ->
                val activeSessionId = tableDoc.getString("activeSessionId").orEmpty()
                if (activeSessionId.isBlank()) {
                    Toast.makeText(this, "현재 사용 중인 세션이 없습니다", Toast.LENGTH_SHORT).show()
                    renderEmptySettlement()
                } else {
                    sessionId = activeSessionId
                    listenSettlementBySession(activeSessionId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "테이블 정보를 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenSettlementBySession(targetSessionId: String) {
        settlementListener?.remove()
        settlementListener = db.collection("settlements")
            .whereEqualTo("sessionId", targetSessionId)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "결제 정보를 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val settlementDoc = snapshot?.documents?.firstOrNull()
                if (settlementDoc != null) {
                    settlementId = settlementDoc.id
                    stopLiveSessionListeners()
                    renderSettlementDocument(settlementDoc)
                } else {
                    settlementId = ""
                    listenLiveSession(targetSessionId)
                }
            }
    }

    private fun listenLiveSession(targetSessionId: String) {
        if (liveSessionListenerId == targetSessionId && sessionListener != null && participantListener != null) {
            return
        }

        stopLiveSessionListeners()
        liveSessionListenerId = targetSessionId
        sessionListener = db.collection("sessions").document(targetSessionId)
            .addSnapshotListener { snapshot, _ ->
                liveSessionDoc = snapshot
                renderLiveSettlement()
            }
        participantListener = db.collection("sessions").document(targetSessionId)
            .collection("participants")
            .addSnapshotListener { snapshot, _ ->
                liveParticipantDocs = snapshot?.documents.orEmpty()
                renderLiveSettlement()
            }
    }

    private fun renderSettlementDocument(doc: DocumentSnapshot) {
        val totalPrice = doc.getLongValue("totalPrice")
        val timeInfo = doc.getString("timeInfo").orEmpty()
        val titleTableNumber = doc.getLongValue("tableId").toInt().takeIf { it > 0 } ?: tableNumber

        binding.tvTitle.text = if (titleTableNumber > 0) {
            "결제 현황 - 테이블 $titleTableNumber"
        } else {
            "결제 현황"
        }
        binding.tvTotalPrice.text = "${String.format(Locale.KOREA, "%,d", totalPrice)}원"
        binding.tvTimeInfo.text = timeInfo.ifBlank { "정산 문서 기준" }

        val participantsList = doc.getParticipantMaps()
        val participants = participantsList.map { map ->
            SettlementParticipant(
                userId = map["userId"].asString(),
                userName = map["userName"].asString(),
                myTotal = map["mytotal"].asLong().takeIf { it > 0 } ?: map["myTotal"].asLong(),
                paidStatus = map["paidStatus"] as? Boolean ?: false,
                pendingApproval = map["pendingApproval"] as? Boolean ?: false,
                beerCupCount = map["beerCupCount"].asLong().toInt(),
                sojuCupCount = map["sojuCupCount"].asLong().toInt()
            )
        }
        adapter.updateItems(participants)
    }

    private fun renderLiveSettlement() {
        val sessionDoc = liveSessionDoc ?: return
        if (!sessionDoc.exists()) {
            renderEmptySettlement()
            return
        }

        val totals = sessionDoc.toSettlementTotals(liveParticipantDocs)
        binding.tvTotalPrice.text = "${String.format(Locale.KOREA, "%,d", totals.totalPrice)}원"
        binding.tvTimeInfo.text = buildTimeInfo(sessionDoc, liveParticipantDocs.size)

        val participants = liveParticipantDocs.map { participantDoc ->
            participantDoc.toLiveSettlementParticipant(totals)
        }
        adapter.updateItems(participants)
    }

    private fun renderEmptySettlement() {
        binding.tvTotalPrice.text = "0원"
        binding.tvTimeInfo.text = ""
        adapter.updateItems(emptyList())
    }

    private fun updatePaidStatus(participant: SettlementParticipant, isChecked: Boolean) {
        if (settlementId.isNotBlank()) {
            updateSettlementPaidStatus(participant, isChecked)
        } else {
            updateLiveParticipantPaidStatus(participant, isChecked)
        }
    }

    private fun updateSettlementPaidStatus(participant: SettlementParticipant, isChecked: Boolean) {
        lifecycleScope.launch {
            runCatching {
                paymentRepository.updateParticipantApproval(
                    settlementId = settlementId,
                    userId = participant.userId,
                    paidStatus = isChecked
                )
            }.onFailure {
                Toast.makeText(this@SettlementActivity, "결제 상태 저장에 실패했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateLiveParticipantPaidStatus(participant: SettlementParticipant, isChecked: Boolean) {
        val targetSessionId = sessionId.ifBlank { liveSessionListenerId }
        if (targetSessionId.isBlank() || participant.userId.isBlank()) return

        db.collection("sessions").document(targetSessionId)
            .collection("participants").document(participant.userId)
            .set(
                mapOf(
                    "paidStatus" to isChecked,
                    "pendingApproval" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener {
                Toast.makeText(this, "결제 상태 저장에 실패했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun DocumentSnapshot.toSettlementTotals(participants: List<DocumentSnapshot>): SettlementTotals {
        val totalSojuPrice = getIntValue("totalSojuPrice")
        val totalBeerPrice = getIntValue("totalBeerPrice")
        val totalFoodPrice = getIntValue("totalFoodPrice")
        val totalPrice = getIntValue("totalPrice").takeIf { it > 0 }
            ?: (totalSojuPrice + totalBeerPrice + totalFoodPrice).takeIf { it > 0 }
            ?: getIntValue("totalAmount")
        val totalSojuCount = getIntValue("totalSojuDrinkCount").takeIf { it > 0 }
            ?: participants.sumOf { it.getIntValue("sojuDrinkCount") }
        val totalBeerCount = getIntValue("totalBeerDrinkCount").takeIf { it > 0 }
            ?: participants.sumOf { it.getIntValue("beerDrinkCount") }
        return SettlementTotals(
            totalPrice = totalPrice,
            totalSojuPrice = totalSojuPrice,
            totalBeerPrice = totalBeerPrice,
            totalFoodPrice = totalFoodPrice,
            totalSojuCount = totalSojuCount,
            totalBeerCount = totalBeerCount,
            participantCount = participants.size.coerceAtLeast(1)
        )
    }

    private fun DocumentSnapshot.toLiveSettlementParticipant(totals: SettlementTotals): SettlementParticipant {
        val sojuCount = getIntValue("sojuDrinkCount")
        val beerCount = getIntValue("beerDrinkCount")
        val foodTotal = getIntValue("foodTotal").takeIf { it > 0 }
            ?: (totals.totalFoodPrice / totals.participantCount)
        val sojuTotal = getIntValue("sojuTotal").takeIf { it > 0 }
            ?: if (totals.totalSojuCount > 0) {
                (totals.totalSojuPrice * sojuCount) / totals.totalSojuCount
            } else {
                0
            }
        val beerTotal = getIntValue("beerTotal").takeIf { it > 0 }
            ?: if (totals.totalBeerCount > 0) {
                (totals.totalBeerPrice * beerCount) / totals.totalBeerCount
            } else {
                0
            }
        val calculatedTotal = getIntValue("totalPrice").takeIf { it > 0 }
            ?: (foodTotal + sojuTotal + beerTotal)

        return SettlementParticipant(
            userId = getString("userId").orEmpty().ifBlank { id },
            userName = getString("userName").orEmpty().ifBlank { "사용자" },
            myTotal = calculatedTotal.toLong(),
            paidStatus = getBoolean("paidStatus") ?: false,
            pendingApproval = getBoolean("pendingApproval") ?: false,
            beerCupCount = beerCount,
            sojuCupCount = sojuCount
        )
    }

    private fun buildTimeInfo(sessionDoc: DocumentSnapshot, participantCount: Int): String {
        val startedAt = sessionDoc.timestampMs("startedAt")
        val status = sessionDoc.getString("status").orEmpty()
        val startText = startedAt?.let {
            SimpleDateFormat("HH:mm", Locale.KOREA).format(java.util.Date(it))
        } ?: "--:--"
        return "${startText} 시작 · ${participantCount}명 · ${status.ifBlank { "active" }}"
    }

    private fun stopLiveSessionListeners() {
        sessionListener?.remove()
        participantListener?.remove()
        sessionListener = null
        participantListener = null
        liveSessionDoc = null
        liveParticipantDocs = emptyList()
        liveSessionListenerId = ""
    }

    private fun DocumentSnapshot.getParticipantMaps(): List<Map<String, Any?>> {
        val rawParticipants = get("participants") as? List<*> ?: return emptyList()
        return rawParticipants.mapNotNull { raw ->
            (raw as? Map<*, *>)?.mapKeys { it.key.toString() }
        }
    }

    private fun DocumentSnapshot.getIntValue(field: String): Int =
        when (val value = get(field)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }

    private fun DocumentSnapshot.getLongValue(field: String): Long =
        when (val value = get(field)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }

    private fun DocumentSnapshot.timestampMs(field: String): Long? =
        when (val value = get(field)) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

    private fun Any?.asLong(): Long = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun Any?.asString(): String = this as? String ?: ""

    override fun onDestroy() {
        super.onDestroy()
        settlementListener?.remove()
        stopLiveSessionListeners()
    }

    private data class SettlementTotals(
        val totalPrice: Int = 0,
        val totalSojuPrice: Int = 0,
        val totalBeerPrice: Int = 0,
        val totalFoodPrice: Int = 0,
        val totalSojuCount: Int = 0,
        val totalBeerCount: Int = 0,
        val participantCount: Int = 1
    )
}
