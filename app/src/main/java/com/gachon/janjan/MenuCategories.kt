package com.gachon.janjan

object MenuCategories {
    const val ALL = "all"
    const val SOJU = "soju"
    const val BEER = "beer"
    const val FOOD = "food"
    const val DRINK = "drink"

    fun label(category: String): String =
        when (normalize(category)) {
            SOJU -> "소주"
            BEER -> "맥주"
            FOOD -> "안주"
            DRINK -> "음료"
            ALL -> "전체"
            else -> category
        }

    fun normalize(category: String): String =
        when (category.trim().lowercase()) {
            ALL, "전체" -> ALL
            SOJU, "소주", "주류" -> SOJU
            BEER, "맥주" -> BEER
            FOOD, "안주" -> FOOD
            DRINK, "음료" -> DRINK
            else -> category.trim()
        }
}
