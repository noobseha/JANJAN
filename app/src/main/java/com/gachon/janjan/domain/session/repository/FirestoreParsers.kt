package com.gachon.janjan.domain.session.repository

import com.gachon.janjan.data.model.Session
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

internal fun DocumentSnapshot.stringValue(field: String): String? =
    when (val value = get(field)) {
        is String -> value
        is Number -> value.toLong().toString()
        else -> null
    }

internal fun DocumentSnapshot.intValue(field: String): Int =
    when (val value = get(field)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

internal fun DocumentSnapshot.toSessionModel(): Session? {
    if (!exists()) return null
    val tableNumber = intValue("tableNumber").takeIf { it > 0 } ?: intValue("tableId")
    return Session(
        id = id,
        sessionId = stringValue("sessionId").orEmpty().ifBlank { id },
        storeId = stringValue("storeId").orEmpty(),
        storeName = stringValue("storeName").orEmpty(),
        tableId = stringValue("tableId").orEmpty(),
        tableNumber = tableNumber,
        inviteCode = stringValue("inviteCode").orEmpty(),
        status = stringValue("status") ?: "active",
        startedAt = timestampMillis("startedAt").takeIf { it > 0 } ?: Timestamp.now().toDate().time,
        endedAt = timestampMillis("endedAt"),
        totalSojuDrinkCount = intValue("totalSojuDrinkCount"),
        totalBeerDrinkCount = intValue("totalBeerDrinkCount")
    )
}

internal fun DocumentSnapshot.timestampMillis(field: String): Long =
    when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> 0L
    }

internal fun Any?.asInt(): Int =
    when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

internal fun Any?.asString(): String =
    when (this) {
        is String -> this
        is Number -> toLong().toString()
        else -> ""
    }
