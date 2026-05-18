package com.gachon.janjan.data.repository

import android.util.Log
import com.gachon.janjan.data.model.MenuItem
import com.gachon.janjan.data.model.Order
import com.gachon.janjan.data.model.Session
import com.google.firebase.firestore.FirebaseFirestore

class OrderRepository {
    private val db = FirebaseFirestore.getInstance()

    // 1️⃣ 메뉴 리스트 가져오기 (전체 로딩)
    fun getMenuItems(storeId: Long, onResult: (List<MenuItem>?) -> Unit) {
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
                onResult(menuList)
            }
            .addOnFailureListener { exception ->
                Log.e("JANJAN_BUG", "❌ 메뉴 통신 실패: ${exception.message}")
                onResult(null)
            }
    }

    // 2️⃣ 가게 정보 연동하기 (🔥 문서 ID가 아니라 "storeId" 필드로 검색하도록 수정!)
    fun getSessionWithStoreDetails(sessionId: String, onResult: (Session?) -> Unit) {
        db.collection("sessions")
            .whereEqualTo("sessionId", sessionId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val sessionDoc = querySnapshot.documents[0]
                    val session = sessionDoc.toObject(Session::class.java)

                    if (session != null) {
                        // 🌟 네 지적대로 stores 컬렉션도 "storeId" 필드로 내부 내용물 검색!
                        db.collection("stores")
                            .whereEqualTo("storeId", session.storeId)
                            .get()
                            .addOnSuccessListener { storeSnapshot ->
                                if (!storeSnapshot.isEmpty) {
                                    val storeDoc = storeSnapshot.documents[0]
                                    val updatedSession = session.copy(
                                        storeName = storeDoc.getString("name") ?: "알 수 없는 가게",
                                        imageUrl = storeDoc.getString("imageUrl") ?: ""
                                    )
                                    Log.d("JANJAN_BUG", "✅ 가게 이름 로딩 성공: ${updatedSession.storeName}")
                                    onResult(updatedSession)
                                } else {
                                    Log.e("JANJAN_BUG", "❌ 가게 정보 없음: storeId가 ${session.storeId}인 가게 문서가 없음")
                                    onResult(session)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("JANJAN_BUG", "❌ 가게 조회 실패: ${e.message}")
                                onResult(session)
                            }
                    } else {
                        onResult(null)
                    }
                } else {
                    Log.e("JANJAN_BUG", "❌ 세션 조회 실패: sessionId가 '${sessionId}'인 문서가 없음")
                    onResult(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "❌ 세션 쿼리 실패: ${e.message}")
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

        // 세션 문서 검색
        db.collection("sessions")
            .whereEqualTo("sessionId", order.sessionId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Log.e("JANJAN_BUG", "❌ 주문 실패: sessionId가 '${order.sessionId}'인 세션 없음!")
                    onComplete(false)
                    return@addOnSuccessListener
                }

                val actualSessionDocRef = querySnapshot.documents[0].reference

                try {
                    val batch = db.batch()

                    // 영수증 추가
                    val newOrderRef = db.collection("orders").document()
                    batch.set(newOrderRef, order)
                    batch.update(
                        actualSessionDocRef,
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
            .addOnFailureListener { e ->
                Log.e("JANJAN_BUG", "❌ 문서 검색 실패: ${e.message}")
                onComplete(false)
            }
    }
}