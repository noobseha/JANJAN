package com.gachon.janjan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.gachon.janjan.databinding.ActivitySettlementBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class SettlementActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettlementBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: ParticipantAdapter
    private var settlementId = ""
    private var tableId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettlementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tableId = intent.getIntExtra("tableId", 0)
        binding.tvTitle.text = "결제 현황 - 테이블 $tableId"

        adapter = ParticipantAdapter(mutableListOf()) { participant, isChecked ->
            updatePaidStatus(participant, isChecked)
        }

        binding.rvParticipants.layoutManager = LinearLayoutManager(this)
        binding.rvParticipants.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        loadSettlement()
    }

    private fun loadSettlement() {
        db.collection("settlements")
            .whereEqualTo("tableId", tableId)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "결제 정보가 없습니다", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = result.documents[0]
                settlementId = doc.id

                val totalPrice = doc.getLong("totalPrice") ?: 0
                val timeInfo = doc.getString("timeInfo") ?: ""

                binding.tvTotalPrice.text = "${String.format(Locale.KOREA, "%,d", totalPrice)}원"
                binding.tvTimeInfo.text = timeInfo

                val participantsList = doc.getParticipantMaps()
                val participants = participantsList.map { map ->
                    SettlementParticipant(
                        userId = map["userId"].asString(),
                        userName = map["userName"].asString(),
                        myTotal = map["mytotal"].asLong().takeIf { it > 0 } ?: map["myTotal"].asLong(),
                        paidStatus = map["paidStatus"] as? Boolean ?: false,
                        beerCupCount = map["beerCupCount"].asLong().toInt(),
                        sojuCupCount = map["sojuCupCount"].asLong().toInt()
                    )
                }
                adapter.updateItems(participants)
            }
            .addOnFailureListener {
                Toast.makeText(this, "결제 정보를 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updatePaidStatus(participant: SettlementParticipant, isChecked: Boolean) {
        if (settlementId.isBlank()) return

        db.collection("settlements").document(settlementId).get()
            .addOnSuccessListener { doc ->
                val participantsList = doc.getParticipantMaps()
                val updatedList = participantsList.map { map ->
                    if (map["userId"] == participant.userId) {
                        map.toMutableMap().apply { put("paidStatus", isChecked) }
                    } else map
                }
                db.collection("settlements").document(settlementId)
                    .update("participants", updatedList)
                    .addOnSuccessListener {
                        loadSettlement()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "결제 상태 저장에 실패했습니다", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "결제 상태를 확인하지 못했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.getParticipantMaps(): List<Map<String, Any?>> {
        val rawParticipants = get("participants") as? List<*> ?: return emptyList()
        return rawParticipants.mapNotNull { raw ->
            (raw as? Map<*, *>)?.mapKeys { it.key.toString() }
        }
    }

    private fun Any?.asLong(): Long = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun Any?.asString(): String = this as? String ?: ""
}
