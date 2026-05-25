package com.gachon.janjan.data.repository

import android.util.Log
import com.gachon.janjan.data.model.MenuItem
import com.gachon.janjan.data.model.Order
import com.gachon.janjan.data.model.Session
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class OrderRepository {
    private val db = FirebaseFirestore.getInstance()

    // 1️⃣ 메뉴 리스트 가져오기 (전체 로딩)
    fun getMenuItems(storeId: String?, onResult: (List<MenuItem>?) -> Unit) {
        db.collection("menu_items")
            .get()
            .addOnSuccessListener { documents ->
                val menuList = mutableListOf<MenuItem>()
                for (document in documents) {
                    try {
                        val item = document.toObject(MenuItem::class.java)
                        menuList.add(item)
                    } catch (e: Exception) {
                        Log.e("JANJAN_BUG", "❌ 메뉴 변환 실패: 문서ID=${document.id}, 이유=${e.message}")
                    }
                }
                val normalizedStoreId = storeId?.trim().orEmpty()
                val storeMenu = if (normalizedStoreId.isBlank()) {
                    emptyList()
                } else {
                    menuList.filter { it.storeId.toString() == normalizedStoreId }
                }
                onResult(storeMenu.ifEmpty { menuList })
            }
            .addOnFailureListener { exception ->
                Log.e("JANJAN_BUG", "❌ 메뉴 통신 실패: ${exception.message}")
                onResult(null)
            }
    }

    // 2️⃣ 가게 정보 연동하기 (🔥 문서 ID가 아니라 "storeId" 필드로 검색하도록 수정!)
    fun getSessionWithStoreDetails(sessionId: String, onResult: (Session?) -> Unit) {
        db.collection("sessions").document(sessionId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    attachStoreDetails(document.toLegacySession(), onResult)
                } else {
                    db.collection("sessions")
                        .whereEqualTo("sessionId", sessionId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            val sessionDoc = querySnapshot.documents.firstOrNull()
                            if (sessionDoc == null) {
                                Log.e("JANJAN_BUG", "❌ 세션 조회 실패: sessionId가 '${sessionId}'인 문서가 없음")
                                onResult(null)
                            } else {
                                attachStoreDetails(sessionDoc.toLegacySession(), onResult)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("JANJAN_BUG", "❌ 세션 쿼리 실패: ${e.message}")
                            onResult(null)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "❌ 세션 문서 조회 실패: ${e.message}")
                onResult(null)
            }
    }

    // 3️⃣ 주문하기 (🔥 소주, 맥주, 안주 값을 완벽하게 분리해서 업데이트!)
    fun submitOrderToFirebase(
        order: Order,
        userId: String,
        totalSojuAdded: Int,  // 뷰모델 호환용 유지
        totalPriceAdded: Int, // 뷰모델 호환용 유지
        onComplete: (Boolean) -> Unit
    ) {
        if (order.sessionId.isEmpty()) {
            Log.e("JANJAN_BUG", "❌ 주문 실패: sessionId가 비어있습니다!")
            onComplete(false)
            return
        }

        // 장바구니 리스트(order.items)에서 카테고리별 추가된 금액만 계산!
        val sojuPrice = order.items.filter { it.category == "soju" }.sumOf { it.subtotal }
        val beerPrice = order.items.filter { it.category == "beer" }.sumOf { it.subtotal }
        val foodPrice = order.items.filter { it.category != "soju" && it.category != "beer" }
            .sumOf { it.subtotal }

        db.collection("sessions").document(order.sessionId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    commitOrder(document.reference, order, sojuPrice, beerPrice, foodPrice, totalPriceAdded, onComplete)
                } else {
                    db.collection("sessions")
                        .whereEqualTo("sessionId", order.sessionId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            val sessionDoc = querySnapshot.documents.firstOrNull()
                            if (sessionDoc == null) {
                                Log.e("JANJAN_BUG", "❌ 주문 실패: sessionId가 '${order.sessionId}'인 세션 없음!")
                                onComplete(false)
                            } else {
                                commitOrder(sessionDoc.reference, order, sojuPrice, beerPrice, foodPrice, totalPriceAdded, onComplete)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("JANJAN_BUG", "❌ 문서 검색 실패: ${e.message}")
                            onComplete(false)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "❌ 세션 문서 조회 실패: ${e.message}")
                onComplete(false)
            }
    }

    private fun commitOrder(
        sessionDocRef: com.google.firebase.firestore.DocumentReference,
        order: Order,
        sojuPrice: Int,
        beerPrice: Int,
        foodPrice: Int,
        totalPriceAdded: Int,
        onComplete: (Boolean) -> Unit
    ) {
        try {
            val batch = db.batch()
            val newOrderRef = db.collection("orders").document()
            batch.set(newOrderRef, order)
            batch.update(
                sessionDocRef,
                "totalSojuPrice",
                com.google.firebase.firestore.FieldValue.increment(sojuPrice.toLong()),
                "totalBeerPrice",
                com.google.firebase.firestore.FieldValue.increment(beerPrice.toLong()),
                "totalFoodPrice",
                com.google.firebase.firestore.FieldValue.increment(foodPrice.toLong()),
                "totalPrice",
                com.google.firebase.firestore.FieldValue.increment(totalPriceAdded.toLong())
            )

            batch.commit()
                .addOnSuccessListener {
                    Log.d("JANJAN_BUG", "✅ 주문 성공! 소주/맥주/안주 가격만 정상 반영 완료!")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e("JANJAN_BUG", "❌ 배치 주문 실패: ${e.message}")
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e("JANJAN_BUG", "❌ 주문 로직 터짐: ${e.message}")
            onComplete(false)
        }
    }

    private fun attachStoreDetails(session: Session, onResult: (Session?) -> Unit) {
        if (session.storeId.isBlank()) {
            onResult(session)
            return
        }

        db.collection("stores")
            .whereEqualTo("storeId", session.storeId)
            .limit(1)
            .get()
            .addOnSuccessListener { storeSnapshot ->
                val storeDoc = storeSnapshot.documents.firstOrNull()
                if (storeDoc != null) {
                    onResult(session.withStoreDoc(storeDoc))
                } else {
                    val numericStoreId = session.storeId.toLongOrNull()
                    if (numericStoreId != null) {
                        db.collection("stores")
                            .whereEqualTo("storeId", numericStoreId)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { numericSnapshot ->
                                val numericDoc = numericSnapshot.documents.firstOrNull()
                                if (numericDoc != null) {
                                    onResult(session.withStoreDoc(numericDoc))
                                } else {
                                    attachStoreDocumentById(session, onResult)
                                }
                            }
                            .addOnFailureListener { attachStoreDocumentById(session, onResult) }
                    } else {
                        attachStoreDocumentById(session, onResult)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "❌ 가게 조회 실패: ${e.message}")
                onResult(session)
            }
    }

    private fun attachStoreDocumentById(session: Session, onResult: (Session?) -> Unit) {
        db.collection("stores").document(session.storeId).get()
            .addOnSuccessListener { doc ->
                onResult(if (doc.exists()) session.withStoreDoc(doc) else session)
            }
            .addOnFailureListener { onResult(session) }
    }

    private fun Session.withStoreDoc(doc: DocumentSnapshot): Session =
        copy(
            storeName = doc.getString("name") ?: storeName,
            imageUrl = doc.getString("imageUrl") ?: imageUrl
        )

    private fun DocumentSnapshot.toLegacySession(): Session {
        val tableId = getStringValue("tableId")
        val tableNumber = getIntValue("tableNumber").takeIf { it > 0 }
            ?: tableId.toIntOrNull()
            ?: 0
        return Session(
            id = id,
            sessionId = getStringValue("sessionId").ifBlank { id },
            storeId = getStringValue("storeId"),
            tableId = tableId,
            tableNumber = tableNumber,
            inviteCode = getString("inviteCode").orEmpty(),
            status = getString("status").orEmpty(),
            startedAt = when (val startedAt = get("startedAt")) {
                is Timestamp -> startedAt.toDate().time
                is Number -> startedAt.toLong()
                else -> 0L
            },
            storeName = getString("storeName").orEmpty().ifBlank { "알 수 없는 가게" },
            imageUrl = getString("imageUrl").orEmpty(),
            totalSojuDrinkCount = getIntValue("totalSojuDrinkCount"),
            totalBeerDrinkCount = getIntValue("totalBeerDrinkCount")
        )
    }

    private fun DocumentSnapshot.getStringValue(field: String): String =
        when (val value = get(field)) {
            is String -> value
            is Number -> value.toLong().toString()
            else -> ""
        }

    private fun DocumentSnapshot.getIntValue(field: String): Int =
        when (val value = get(field)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
}
