package com.gachon.janjan.domain.camera.repository

import com.gachon.janjan.domain.camera.model.CvDetection
import com.gachon.janjan.domain.session.FirebaseConfig
import com.gachon.janjan.domain.session.util.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlin.math.hypot

class DirectDrinkCountRepository(
    private val db: FirebaseFirestore = FirebaseConfig.db
) {
    private val mutex = Mutex()
    private val recentDetections = linkedMapOf<String, RecentDetection>()
    private val pairStates = linkedMapOf<String, LocalPairState>()

    suspend fun processFrame(
        sessionId: String,
        cameraId: String,
        detections: List<CvDetection>,
        nearDistanceThreshold: Float,
        pourThresholdMs: Int,
        colorMappingThresholdMs: Int
    ): DirectDrinkCountResult = mutex.withLock {
        if (sessionId.isBlank() || cameraId.isBlank() || detections.isEmpty()) {
            return@withLock DirectDrinkCountResult()
        }

        val now = System.currentTimeMillis()
        rememberDetections(cameraId, detections, now)
        pruneOldState(now)

        val activeDetections = recentDetections.values
            .filter { now - it.seenAtMs <= RECENT_DETECTION_TTL_MS }
            .map { it.detection }
        val pendingMappingParticipants = loadPendingMappingParticipants(sessionId)
        val screens = if (pendingMappingParticipants.isEmpty()) {
            emptyList()
        } else {
            activeDetections.filter {
                it.objectType.normalizedObjectType() in COLOR_SCREEN_TYPES &&
                    it.screenColorHex.normalizeHexColorOrNull() != null
            }
        }
        val glasses = activeDetections.filter { it.drinkType.normalizeDrinkTypeOrNull() != null }
        val bottles = activeDetections.filter { it.objectType.normalizedObjectType() in BOTTLE_TYPES }

        var mappedCount = 0
        var drinkCountedCount = 0
        val activePairIds = mutableSetOf<String>()

        for (screen in screens) {
            val screenColor = screen.screenColorHex.normalizeHexColorOrNull() ?: continue
            for (glass in glasses) {
                val drinkType = glass.drinkType.normalizeDrinkTypeOrNull() ?: continue
                if (!isNear(screen, glass, nearDistanceThreshold)) continue

                val physicalGlassId = glass.physicalGlassId.ifBlankOrNull()
                    ?: "${cameraId}_${glass.trackId.ifBlank { glass.objectType }}"
                val pairId = "color_${cameraId}_${screen.trackId}_${glass.trackId}_$drinkType".toSafeDocId()
                activePairIds += pairId

                val pairResult = updateLocalPairState(pairId, now, colorMappingThresholdMs)
                writeCvPairState(
                    sessionId = sessionId,
                    pairId = pairId,
                    pairType = "colorGlass",
                    cameraId = cameraId,
                    payload = mapOf(
                        "screenTrackId" to screen.trackId,
                        "glassTrackId" to glass.trackId,
                        "screenColorHex" to screenColor,
                        "physicalGlassId" to physicalGlassId,
                        "drinkType" to drinkType,
                        "durationMs" to pairResult.durationMs,
                        "thresholdMs" to colorMappingThresholdMs,
                        "isActive" to true
                    )
                )

                if (pairResult.shouldFire && mapColorToPhysicalGlass(
                        sessionId = sessionId,
                        eventId = "androidDirect:$pairId:$now",
                        screenColorHex = screenColor,
                        physicalGlassId = physicalGlassId,
                        drinkType = drinkType,
                        cameraId = cameraId,
                        glass = glass,
                        pendingParticipants = pendingMappingParticipants
                    )
                ) {
                    mappedCount += 1
                }
            }
        }

        for (glass in glasses) {
            val drinkType = glass.drinkType.normalizeDrinkTypeOrNull() ?: continue
            val physicalGlassId = glass.physicalGlassId.ifBlankOrNull()
                ?: "${cameraId}_${glass.trackId.ifBlank { glass.objectType }}"
            val sameDrinkBottles = bottles.filter {
                it.objectType.toBottleDrinkTypeOrNull() == drinkType
            }

            for (bottle in sameDrinkBottles) {
                if (!isNear(glass, bottle, nearDistanceThreshold)) continue

                val pairId = "pour_${cameraId}_${physicalGlassId}_${bottle.trackId}_$drinkType".toSafeDocId()
                activePairIds += pairId

                val pairResult = updateLocalPairState(pairId, now, pourThresholdMs)
                writeCvPairState(
                    sessionId = sessionId,
                    pairId = pairId,
                    pairType = "pour",
                    cameraId = cameraId,
                    payload = mapOf(
                        "glassTrackId" to glass.trackId,
                        "bottleTrackId" to bottle.trackId,
                        "physicalGlassId" to physicalGlassId,
                        "drinkType" to drinkType,
                        "bottleType" to bottle.objectType,
                        "durationMs" to pairResult.durationMs,
                        "thresholdMs" to pourThresholdMs,
                        "isActive" to true
                    )
                )

                if (pairResult.shouldFire && incrementDrinkForGlass(
                        sessionId = sessionId,
                        eventId = "androidDirect:$pairId:$now",
                        glassId = physicalGlassId,
                        expectedDrinkType = drinkType,
                        countSource = "androidDirectPour",
                        cameraId = cameraId,
                        glass = glass
                    )
                ) {
                    drinkCountedCount += 1
                }
            }
        }

        markInactivePairs(sessionId, activePairIds, now)
        DirectDrinkCountResult(
            mappedCount = mappedCount,
            drinkCountedCount = drinkCountedCount
        )
    }

    private fun rememberDetections(cameraId: String, detections: List<CvDetection>, now: Long) {
        detections.forEachIndexed { index, detection ->
            val type = detection.objectType.normalizedObjectType()
            if (type.isBlank()) return@forEachIndexed
            val key = listOf(
                cameraId,
                type,
                detection.trackId.ifBlank { index.toString() },
                detection.drinkType.orEmpty(),
                detection.screenColorHex.orEmpty()
            ).joinToString("_").toSafeDocId()
            recentDetections[key] = RecentDetection(detection.copy(objectType = type), now)
        }
    }

    private fun pruneOldState(now: Long) {
        recentDetections.entries.removeAll { now - it.value.seenAtMs > RECENT_DETECTION_TTL_MS }
        pairStates.entries.removeAll { now - it.value.lastSeenAtMs > PAIR_STATE_TTL_MS }
    }

    private suspend fun loadPendingMappingParticipants(sessionId: String): List<DocumentSnapshot> =
        db.collection(FirestorePaths.participants(sessionId))
            .get()
            .await()
            .documents
            .filter { participant ->
                val glassColor = participant.getString("glassColor").normalizeHexColorOrNull()
                if (glassColor == null) return@filter false

                val status = participant.getString("glassMappingStatus").orEmpty()
                val physicalGlassId = participant.getString("physicalGlassId").orEmpty()
                val alreadyMapped = status == "mapped" ||
                    (status.isBlank() && physicalGlassId.isNotBlank())
                !alreadyMapped
            }

    private fun updateLocalPairState(
        pairId: String,
        now: Long,
        thresholdMs: Int
    ): PairUpdateResult {
        val previous = pairStates[pairId]
        val shouldReset = previous == null || now - previous.lastSeenAtMs > MAX_TRACK_GAP_MS
        val firstSeenAt = if (shouldReset) now else previous.firstSeenAtMs
        val alreadyFired = if (shouldReset) false else previous.hasFired
        val duration = (now - firstSeenAt).coerceAtLeast(0)
        val shouldFire = !alreadyFired && duration >= thresholdMs
        pairStates[pairId] = LocalPairState(
            firstSeenAtMs = firstSeenAt,
            lastSeenAtMs = now,
            hasFired = alreadyFired || shouldFire
        )
        return PairUpdateResult(shouldFire = shouldFire, durationMs = duration)
    }

    private suspend fun mapColorToPhysicalGlass(
        sessionId: String,
        eventId: String,
        screenColorHex: String,
        physicalGlassId: String,
        drinkType: String,
        cameraId: String,
        glass: CvDetection,
        pendingParticipants: List<DocumentSnapshot>
    ): Boolean {
        val normalizedColor = screenColorHex.normalizeHexColorOrNull() ?: return false
        val colorMatch = findParticipantByScreenColor(normalizedColor, pendingParticipants) ?: return false
        val participant = colorMatch.participant
        val userId = participant.getString("userId").orEmpty().ifBlank { participant.id }
        val mappingCollection = db.collection(FirestorePaths.glassMappings(sessionId))
        val existing = mappingCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("drinkType", drinkType)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        val mappingRef = existing?.reference ?: mappingCollection.document(mappingDocumentId(userId, drinkType))
        val stablePhysicalGlassId = existing?.getString("physicalGlassId").realGlassIdOrNull()
            ?: existing?.getString("glassId").realGlassIdOrNull()
            ?: physicalGlassId
        val now = FieldValue.serverTimestamp()
        val payload = mapOf(
            "userId" to userId,
            "glassId" to stablePhysicalGlassId,
            "physicalGlassId" to stablePhysicalGlassId,
            "lastDetectionGlassId" to physicalGlassId,
            "drinkType" to drinkType,
            "mappingSource" to "androidDirectColorProximity",
            "screenColorHex" to normalizedColor,
            "matchedGlassColor" to colorMatch.glassColor,
            "colorMatchDistance" to colorMatch.distance,
            "colorGlassEventId" to eventId,
            "cameraId" to cameraId,
            "lastTrackId" to glass.trackId,
            "lastCenterX" to glass.centerX.toDouble(),
            "lastCenterY" to glass.centerY.toDouble(),
            "lastSeenAt" to now,
            "mappedAt" to now,
            "updatedAt" to now
        )

        db.runBatch { batch ->
            if (existing == null) {
                batch.set(
                    mappingRef,
                    payload + mapOf(
                        "drinkCount" to 0,
                        "sojuDrinkCount" to 0,
                        "beerDrinkCount" to 0,
                        "createdAt" to now
                    ),
                    SetOptions.merge()
                )
            } else {
                batch.set(mappingRef, payload, SetOptions.merge())
            }
            batch.set(
                participant.reference,
                mapOf(
                    "physicalGlassId" to stablePhysicalGlassId,
                    "lastDetectionGlassId" to physicalGlassId,
                    "glassMappingType" to "androidDirectColorProximity",
                    "glassMappingStatus" to "mapped",
                    "mappedScreenColorHex" to normalizedColor,
                    "matchedScreenColorHex" to normalizedColor,
                    "colorMatchDistance" to colorMatch.distance,
                    "lastGlassTrackId" to glass.trackId,
                    "lastGlassCenterX" to glass.centerX.toDouble(),
                    "lastGlassCenterY" to glass.centerY.toDouble(),
                    "glassMappedAt" to now,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
        }.await()
        return true
    }

    private fun findParticipantByScreenColor(
        screenColorHex: String,
        participants: List<DocumentSnapshot>
    ): ColorMatchedParticipant? {
        val colorCandidates = participants.mapNotNull { participant ->
            val glassColor = participant.getString("glassColor").normalizeHexColorOrNull()
                ?: return@mapNotNull null
            ColorMatchedParticipant(
                participant = participant,
                glassColor = glassColor,
                distance = screenColorHex.rgbDistanceTo(glassColor)
            )
        }

        if (colorCandidates.isEmpty()) return null
        colorCandidates.firstOrNull { it.distance == 0.0 }?.let { return it }
        return colorCandidates.minByOrNull { it.distance }
            ?.takeIf { it.distance <= MAX_COLOR_MATCH_DISTANCE }
    }

    private suspend fun incrementDrinkForGlass(
        sessionId: String,
        eventId: String,
        glassId: String,
        expectedDrinkType: String,
        countSource: String,
        cameraId: String,
        glass: CvDetection
    ): Boolean {
        val resolvedMapping = resolveDrinkMappingForGlass(
            sessionId = sessionId,
            glassId = glassId,
            expectedDrinkType = expectedDrinkType,
            glass = glass
        ) ?: return false
        val mapping = resolvedMapping.mapping
        val userId = mapping.getString("userId").orEmpty()
        if (userId.isBlank()) return false

        val participant = db.collection(FirestorePaths.participants(sessionId))
            .whereEqualTo("userId", userId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull() ?: return false

        if (isInDrinkCooldown(mapping, participant)) return false

        val mappingDrinkCountField = if (expectedDrinkType == "beer") "beerDrinkCount" else "sojuDrinkCount"
        val participantDrinkCountField = mappingDrinkCountField
        val sessionDrinkCountField = if (expectedDrinkType == "beer") "totalBeerDrinkCount" else "totalSojuDrinkCount"
        val stablePhysicalGlassId = mapping.getString("physicalGlassId").realGlassIdOrNull()
            ?: mapping.getString("glassId").realGlassIdOrNull()
            ?: participant.getString("physicalGlassId").realGlassIdOrNull()
            ?: glassId
        val now = FieldValue.serverTimestamp()
        val stableIdPayload = if (
            mapping.getString("glassId").realGlassIdOrNull() == null ||
            mapping.getString("physicalGlassId").realGlassIdOrNull() == null
        ) {
            mapOf(
                "glassId" to stablePhysicalGlassId,
                "physicalGlassId" to stablePhysicalGlassId
            )
        } else {
            emptyMap()
        }
        val locationPayload = stableIdPayload + mapOf(
            "lastDetectionGlassId" to glassId,
            "cameraId" to cameraId,
            "lastTrackId" to glass.trackId,
            "lastCenterX" to glass.centerX.toDouble(),
            "lastCenterY" to glass.centerY.toDouble(),
            "lastSeenAt" to now
        ) + if (resolvedMapping.wasRelinked) {
            mapOf(
                "relinkedAt" to now,
                "relinkSource" to resolvedMapping.matchSource,
                "previousGlassId" to resolvedMapping.previousGlassId
            )
        } else {
            emptyMap()
        }

        db.runTransaction { transaction ->
            transaction.set(
                mapping.reference,
                locationPayload + mapOf(
                    "drinkCount" to FieldValue.increment(1),
                    mappingDrinkCountField to FieldValue.increment(1),
                    "lastDrinkAt" to now,
                    "lastDetectionEventId" to eventId,
                    "countSource" to countSource,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            transaction.set(
                participant.reference,
                mapOf(
                    participantDrinkCountField to FieldValue.increment(1),
                    "lastDrinkType" to expectedDrinkType,
                    "lastDrinkAt" to now,
                    "lastGlassId" to glassId,
                    "physicalGlassId" to stablePhysicalGlassId,
                    "lastDetectionGlassId" to glassId,
                    "glassMappingStatus" to "mapped",
                    "lastGlassTrackId" to glass.trackId,
                    "lastGlassCenterX" to glass.centerX.toDouble(),
                    "lastGlassCenterY" to glass.centerY.toDouble(),
                    "lastDetectionEventId" to eventId,
                    "countSource" to countSource,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            transaction.set(
                db.collection(FirestorePaths.SESSIONS).document(sessionId),
                mapOf(
                    sessionDrinkCountField to FieldValue.increment(1),
                    "lastDrinkAt" to now,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
        }.await()
        return true
    }

    private suspend fun resolveDrinkMappingForGlass(
        sessionId: String,
        glassId: String,
        expectedDrinkType: String,
        glass: CvDetection
    ): ResolvedDrinkMapping? {
        val collection = db.collection(FirestorePaths.glassMappings(sessionId))
        val exact = collection
            .whereEqualTo("glassId", glassId)
            .whereEqualTo("drinkType", expectedDrinkType)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?: collection
                .whereEqualTo("physicalGlassId", glassId)
                .whereEqualTo("drinkType", expectedDrinkType)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()

        if (exact != null && exact.isMappedDrinkMapping()) {
            return ResolvedDrinkMapping(
                mapping = exact,
                previousGlassId = exact.getString("glassId").orEmpty(),
                matchSource = "exactGlassId",
                wasRelinked = false
            )
        }

        val mappedCandidates = collection
            .whereEqualTo("drinkType", expectedDrinkType)
            .get()
            .await()
            .documents
            .filter { it.isMappedDrinkMapping() }

        val bestByLocation = mappedCandidates
            .mapNotNull { candidate ->
                val x = candidate.getFloatValue("lastCenterX") ?: return@mapNotNull null
                val y = candidate.getFloatValue("lastCenterY") ?: return@mapNotNull null
                candidate to hypot(
                    (x - glass.centerX).toDouble(),
                    (y - glass.centerY).toDouble()
                )
            }
            .filter { (_, distance) -> distance <= MAX_REMAPPED_GLASS_DISTANCE }
            .minByOrNull { (_, distance) -> distance }
            ?.first

        if (bestByLocation != null) {
            return ResolvedDrinkMapping(
                mapping = bestByLocation,
                previousGlassId = bestByLocation.getString("glassId").orEmpty(),
                matchSource = "nearestMappedGlass",
                wasRelinked = true
            )
        }

        if (mappedCandidates.size == 1) {
            val only = mappedCandidates.first()
            return ResolvedDrinkMapping(
                mapping = only,
                previousGlassId = only.getString("glassId").orEmpty(),
                matchSource = "singleMappedParticipant",
                wasRelinked = true
            )
        }

        return null
    }

    private fun DocumentSnapshot.isMappedDrinkMapping(): Boolean {
        val userId = getString("userId").orEmpty()
        val source = getString("mappingSource").orEmpty()
        val glassId = getString("glassId").orEmpty()
        val physicalGlassId = getString("physicalGlassId").orEmpty()
        val lastDetectionGlassId = getString("lastDetectionGlassId").orEmpty()
        val hasKnownLocation = getFloatValue("lastCenterX") != null && getFloatValue("lastCenterY") != null
        return userId.isNotBlank() &&
            source != "colorPending" &&
            (
                glassId.realGlassIdOrNull() != null ||
                    physicalGlassId.realGlassIdOrNull() != null ||
                    lastDetectionGlassId.realGlassIdOrNull() != null ||
                    hasKnownLocation
                )
    }

    private fun isInDrinkCooldown(
        mapping: DocumentSnapshot,
        participant: DocumentSnapshot
    ): Boolean {
        val lastDrinkAt = mapping.getTimestamp("lastDrinkAt")?.toDate()?.time
            ?: participant.getTimestamp("lastDrinkAt")?.toDate()?.time
            ?: return false
        return System.currentTimeMillis() - lastDrinkAt < DRINK_COUNT_COOLDOWN_MS
    }

    private fun DocumentSnapshot.getFloatValue(field: String): Float? =
        when (val value = get(field)) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }

    private suspend fun writeCvPairState(
        sessionId: String,
        pairId: String,
        pairType: String,
        cameraId: String,
        payload: Map<String, Any?>
    ) {
        db.collection(FirestorePaths.cvPairStates(sessionId))
            .document(pairId)
            .set(
                payload + mapOf(
                    "cameraId" to cameraId,
                    "pairType" to pairType,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "processor" to "androidDirect"
                ),
                SetOptions.merge()
            )
            .await()
    }

    private suspend fun markInactivePairs(
        sessionId: String,
        activePairIds: Set<String>,
        now: Long
    ) {
        val inactiveIds = pairStates
            .filter { (pairId, state) ->
                pairId !in activePairIds && now - state.lastSeenAtMs > MAX_TRACK_GAP_MS
            }
            .keys
            .toList()
        inactiveIds.forEach { pairId ->
            db.collection(FirestorePaths.cvPairStates(sessionId))
                .document(pairId)
                .set(
                    mapOf(
                        "isActive" to false,
                        "lastMissingAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "processor" to "androidDirect"
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    private fun isNear(first: CvDetection, second: CvDetection, threshold: Float): Boolean {
        val firstBox = first.toNormalizedBox()
        val secondBox = second.toNormalizedBox()
        val horizontalGap = maxOf(
            firstBox.left - secondBox.right,
            secondBox.left - firstBox.right,
            0f
        )
        val verticalGap = maxOf(
            firstBox.top - secondBox.bottom,
            secondBox.top - firstBox.bottom,
            0f
        )
        val distance = hypot(
            horizontalGap.toDouble(),
            verticalGap.toDouble()
        )
        return distance <= threshold
    }

    private fun CvDetection.toNormalizedBox(): NormalizedBox {
        val boxWidth = width?.takeIf { it > 0f }
        val boxHeight = height?.takeIf { it > 0f }
        val left = if (boxWidth != null) {
            x ?: (centerX - boxWidth / 2f)
        } else {
            centerX
        }.coerceIn(0f, 1f)
        val top = if (boxHeight != null) {
            y ?: (centerY - boxHeight / 2f)
        } else {
            centerY
        }.coerceIn(0f, 1f)
        val right = if (boxWidth != null) {
            left + boxWidth
        } else {
            centerX
        }.coerceIn(left, 1f)
        val bottom = if (boxHeight != null) {
            top + boxHeight
        } else {
            centerY
        }.coerceIn(top, 1f)
        return NormalizedBox(left = left, top = top, right = right, bottom = bottom)
    }

    private fun String?.normalizeDrinkTypeOrNull(): String? =
        when (this?.trim()?.lowercase()) {
            "soju" -> "soju"
            "beer" -> "beer"
            else -> null
        }

    private fun String.normalizedObjectType(): String =
        trim().lowercase().replace(Regex("[\\s-]+"), "_")

    private fun String.toBottleDrinkTypeOrNull(): String? =
        when (normalizedObjectType()) {
            in SOJU_BOTTLE_TYPES -> "soju"
            in BEER_BOTTLE_TYPES -> "beer"
            else -> null
        }

    private fun String?.normalizeHexColorOrNull(): String? {
        val raw = this?.trim()?.ifBlank { null } ?: return null
        val withHash = if (raw.startsWith("#")) raw else "#$raw"
        return if (Regex("^#[0-9a-fA-F]{6}$").matches(withHash)) {
            withHash.lowercase()
        } else {
            null
        }
    }

    private fun String.rgbDistanceTo(other: String): Double {
        val first = toRgbOrNull() ?: return Double.MAX_VALUE
        val second = other.toRgbOrNull() ?: return Double.MAX_VALUE
        val red = first.red - second.red
        val green = first.green - second.green
        val blue = first.blue - second.blue
        return kotlin.math.sqrt((red * red + green * green + blue * blue).toDouble())
    }

    private fun String.toRgbOrNull(): Rgb? {
        val normalized = normalizeHexColorOrNull() ?: return null
        return Rgb(
            red = normalized.substring(1, 3).toInt(16),
            green = normalized.substring(3, 5).toInt(16),
            blue = normalized.substring(5, 7).toInt(16)
        )
    }

    private fun String?.ifBlankOrNull(): String? =
        this?.takeIf { it.isNotBlank() }

    private fun String?.realGlassIdOrNull(): String? =
        this?.takeIf { it.isNotBlank() && !it.startsWith("glass_pending") }

    private fun mappingDocumentId(userId: String, drinkType: String): String =
        "${userId}_${drinkType}".toSafeDocId()

    private fun String.toSafeDocId(): String =
        trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(140)
            .ifBlank { "unknown" }

    private data class RecentDetection(
        val detection: CvDetection,
        val seenAtMs: Long
    )

    private data class LocalPairState(
        val firstSeenAtMs: Long,
        val lastSeenAtMs: Long,
        val hasFired: Boolean
    )

    private data class PairUpdateResult(
        val shouldFire: Boolean,
        val durationMs: Long
    )

    private data class ColorMatchedParticipant(
        val participant: DocumentSnapshot,
        val glassColor: String,
        val distance: Double
    )

    private data class ResolvedDrinkMapping(
        val mapping: DocumentSnapshot,
        val previousGlassId: String,
        val matchSource: String,
        val wasRelinked: Boolean
    )

    private data class Rgb(
        val red: Int,
        val green: Int,
        val blue: Int
    )

    private data class NormalizedBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    companion object {
        private const val RECENT_DETECTION_TTL_MS = 2_500L
        private const val PAIR_STATE_TTL_MS = 30_000L
        private const val MAX_TRACK_GAP_MS = 1_500L
        private const val MAX_COLOR_MATCH_DISTANCE = 115.0
        private const val MAX_REMAPPED_GLASS_DISTANCE = 0.22
        private const val DRINK_COUNT_COOLDOWN_MS = 8_000L

        private val COLOR_SCREEN_TYPES = setOf(
            "phone_screen",
            "smartphone_screen",
            "color_screen",
            "screen_color",
            "user_color_screen"
        )
        private val SOJU_BOTTLE_TYPES = setOf(
            "soju",
            "soju_bottle",
            "green_soju_bottle",
            "green_bottle"
        )
        private val BEER_BOTTLE_TYPES = setOf(
            "beer",
            "beer_bottle",
            "brown_beer_bottle",
            "clear_beer_bottle",
            "transparent_beer_bottle"
        )
        private val BOTTLE_TYPES = SOJU_BOTTLE_TYPES + BEER_BOTTLE_TYPES
    }
}

data class DirectDrinkCountResult(
    val mappedCount: Int = 0,
    val drinkCountedCount: Int = 0
) {
    val hasUpdates: Boolean
        get() = mappedCount > 0 || drinkCountedCount > 0

    fun toSummaryLabel(): String {
        val parts = buildList {
            if (mappedCount > 0) add("매핑 ${mappedCount}건")
            if (drinkCountedCount > 0) add("카운트 ${drinkCountedCount}건")
        }
        return parts.joinToString(" · ")
    }
}
