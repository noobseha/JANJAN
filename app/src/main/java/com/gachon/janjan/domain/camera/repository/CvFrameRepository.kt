package com.gachon.janjan.domain.camera.repository

import com.gachon.janjan.domain.camera.model.CvFrame
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CvFrameRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    suspend fun submitFrame(sessionId: String, frame: CvFrame) {
        require(sessionId.isNotBlank()) { "활성 세션이 없습니다." }
        db.collection(FirestorePaths.cvFrames(sessionId))
            .document()
            .set(frame)
            .await()
    }
}
