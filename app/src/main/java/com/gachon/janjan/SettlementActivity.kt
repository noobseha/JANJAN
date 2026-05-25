package com.gachon.janjan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.gachon.janjan.databinding.ActivitySettlementBinding
import com.google.firebase.firestore.FirebaseFirestore

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

                binding.tvTotalPrice.text = "${String.format("%,d", totalPrice)}원"
                binding.tvTimeInfo.text = timeInfo

                val participantsList = doc.get("participants") as? List<Map<String, Any>> ?: emptyList()
                val participants = participantsList.map { map ->
                    SettlementParticipant(
                        userId = map["userId"] as? String ?: "",
                        userName = map["userName"] as? String ?: "",
                        myTotal = (map["mytotal"] as? Long) ?: 0,
                        paidStatus = map["paidStatus"] as? Boolean ?: false,
                        beerCupCount = (map["beerCupCount"] as? Long)?.toInt() ?: 0,
                        sojuCupCount = (map["sojuCupCount"] as? Long)?.toInt() ?: 0
                    )
                }
                adapter.updateItems(participants)
            }
    }

    private fun updatePaidStatus(participant: SettlementParticipant, isChecked: Boolean) {
        db.collection("settlements").document(settlementId).get()
            .addOnSuccessListener { doc ->
                val participantsList = doc.get("participants") as? MutableList<Map<String, Any>> ?: return@addOnSuccessListener
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
            }
    }
}