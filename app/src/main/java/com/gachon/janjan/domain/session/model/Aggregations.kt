package com.gachon.janjan.domain.session.model

import com.google.firebase.Timestamp
import java.time.LocalDate

data class DrinkParticipantSummary(
    val name: String,
    val sojuCount: Int,
    val beerCount: Int,
    val imageUrl: String = ""
) {
    val totalCount: Int get() = sojuCount + beerCount
}

data class DrinkHistoryItem(
    val sessionId: String,
    val storeName: String,
    val startedAt: Timestamp,
    val endedAt: Timestamp?,
    val participantCount: Int,
    val mySojuCount: Int,
    val myBeerCount: Int,
    val myAmount: Int,
    val participants: List<DrinkParticipantSummary> = emptyList()
) {
    val totalDrinkCount: Int get() = mySojuCount + myBeerCount
}

data class CalendarDrinkDay(
    val date: LocalDate,
    val sojuCount: Int,
    val beerCount: Int
) {
    val totalCount: Int get() = sojuCount + beerCount
}

data class OrderSummaryItem(
    val name: String,
    val quantity: Int,
    val amount: Int,
    val category: String = ""
)

data class HealthSummary(
    val totalSojuCount: Int,
    val totalBeerCount: Int,
    val totalSessions: Int,
    val avgSojuPerSession: Float,
    val avgBeerPerSession: Float,
    val totalCalories: Int,
    val totalSpending: Int,
    val sojuRatio: Float,
    val beerRatio: Float,
    val weeklyDrinkCount: Int,
    val calendarDays: List<CalendarDrinkDay> = emptyList()
) {
    val totalDrinkCount: Int get() = totalSojuCount + totalBeerCount

    companion object {
        fun calculate(
            soju: Int,
            beer: Int,
            sessions: Int,
            spending: Int,
            weeklyDrinkCount: Int,
            calendarDays: List<CalendarDrinkDay>
        ): HealthSummary {
            val total = soju + beer
            val safeSessions = sessions.coerceAtLeast(1)
            return HealthSummary(
                totalSojuCount = soju,
                totalBeerCount = beer,
                totalSessions = sessions,
                avgSojuPerSession = soju.toFloat() / safeSessions,
                avgBeerPerSession = beer.toFloat() / safeSessions,
                totalCalories = soju * 70 + beer * 150,
                totalSpending = spending,
                sojuRatio = if (total == 0) 0f else soju.toFloat() / total,
                beerRatio = if (total == 0) 0f else beer.toFloat() / total,
                weeklyDrinkCount = weeklyDrinkCount,
                calendarDays = calendarDays
            )
        }
    }
}
