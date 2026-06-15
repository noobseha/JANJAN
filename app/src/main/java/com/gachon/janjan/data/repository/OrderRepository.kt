package com.gachon.janjan.data.repository

import android.util.Log
import com.gachon.janjan.MenuCategories
import com.gachon.janjan.data.model.MenuItem
import com.gachon.janjan.data.model.Order
import com.gachon.janjan.data.model.Session
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class OrderRepository {
    private val db = FirebaseFirestore.getInstance()

    fun getMenuItems(storeId: String?, onResult: (List<MenuItem>?) -> Unit) {
        val normalizedStoreId = storeId?.trim().orEmpty()
        if (normalizedStoreId.isBlank()) {
            onResult(emptyList())
            return
        }

        db.collection("stores").document(normalizedStoreId)
            .collection("menuItems")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                val menuList = documents.map { document ->
                    MenuItem(
                        id = document.id,
                        menuId = document.getString("menuId") ?: document.id,
                        storeId = document.getString("storeId") ?: normalizedStoreId,
                        name = document.getString("name").orEmpty(),
                        price = document.getIntValue("price"),
                        category = MenuCategories.normalize(document.getString("category").orEmpty()),
                        imageUrl = document.getString("imageUrl").orEmpty(),
                        isSoldOut = document.getBoolean("isSoldOut") ?: false,
                        displayOrder = document.getIntValue("displayOrder"),
                        isActive = document.getBoolean("isActive") ?: true
                    )
                }.sortedBy { it.displayOrder }
                onResult(menuList)
            }
            .addOnFailureListener { exception ->
                Log.e("JANJAN_BUG", "메뉴 통신 실패: ${exception.message}")
                onResult(null)
            }
    }

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
                                Log.e("JANJAN_BUG", "세션 조회 실패: sessionId=$sessionId")
                                onResult(null)
                            } else {
                                attachStoreDetails(sessionDoc.toLegacySession(), onResult)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("JANJAN_BUG", "세션 쿼리 실패: ${e.message}")
                            onResult(null)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "세션 문서 조회 실패: ${e.message}")
                onResult(null)
            }
    }

    fun submitOrderToFirebase(
        order: Order,
        userId: String,
        totalSojuAdded: Int,
        totalPriceAdded: Int,
        onComplete: (Boolean) -> Unit
    ) {
        if (order.sessionId.isBlank()) {
            Log.e("JANJAN_BUG", "주문 실패: sessionId가 비어 있습니다.")
            onComplete(false)
            return
        }

        val sojuPrice = order.items
            .filter { MenuCategories.normalize(it.category) == MenuCategories.SOJU }
            .sumOf { it.subtotal }
        val beerPrice = order.items
            .filter { MenuCategories.normalize(it.category) == MenuCategories.BEER }
            .sumOf { it.subtotal }
        val foodPrice = order.items
            .filter {
                val category = MenuCategories.normalize(it.category)
                category != MenuCategories.SOJU && category != MenuCategories.BEER
            }
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
                                Log.e("JANJAN_BUG", "주문 실패: sessionId=${order.sessionId} 세션이 없습니다.")
                                onComplete(false)
                            } else {
                                commitOrder(sessionDoc.reference, order, sojuPrice, beerPrice, foodPrice, totalPriceAdded, onComplete)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("JANJAN_BUG", "세션 검색 실패: ${e.message}")
                            onComplete(false)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "세션 문서 조회 실패: ${e.message}")
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
            batch.set(
                newOrderRef,
                mapOf(
                    "id" to newOrderRef.id,
                    "sessionId" to order.sessionId,
                    "storeId" to order.storeId,
                    "tableId" to order.tableId,
                    "tableNumber" to order.tableNumber,
                    "userId" to order.userId,
                    "timestamp" to order.timestamp,
                    "totalPrice" to order.totalPrice,
                    "items" to order.items,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                sessionDocRef,
                "totalSojuPrice", FieldValue.increment(sojuPrice.toLong()),
                "totalBeerPrice", FieldValue.increment(beerPrice.toLong()),
                "totalFoodPrice", FieldValue.increment(foodPrice.toLong()),
                "totalPrice", FieldValue.increment(totalPriceAdded.toLong()),
                "orderCount", FieldValue.increment(1),
                "lastOrderAt", FieldValue.serverTimestamp(),
                "updatedAt", FieldValue.serverTimestamp()
            )

            batch.commit()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e ->
                    Log.e("JANJAN_BUG", "배치 주문 실패: ${e.message}")
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e("JANJAN_BUG", "주문 로직 예외: ${e.message}")
            onComplete(false)
        }
    }

    private fun attachStoreDetails(session: Session, onResult: (Session?) -> Unit) {
        if (session.storeId.isBlank()) {
            onResult(session)
            return
        }
        attachStoreDocumentById(session, onResult)
    }

    private fun attachStoreDocumentById(session: Session, onResult: (Session?) -> Unit) {
        db.collection("stores").document(session.storeId).get()
            .addOnSuccessListener { doc ->
                onResult(if (doc.exists()) session.withStoreDoc(doc) else session)
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "가게 문서 조회 실패: ${e.message}")
                onResult(session)
            }
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
