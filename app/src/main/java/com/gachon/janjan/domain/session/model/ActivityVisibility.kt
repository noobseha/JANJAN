package com.gachon.janjan.domain.session.model

enum class ActivityVisibility(val storageValue: String, val label: String, val description: String) {
    PUBLIC("public", "전체 공개", "랭킹과 활동 기록을 모든 사용자에게 공개"),
    FRIENDS("friends", "친구 공개", "친구 관계인 사용자에게만 활동 공개"),
    PRIVATE("private", "비공개", "내 활동을 다른 사용자에게 숨김");

    companion object {
        fun fromStorage(value: String?, legacyPrivate: Boolean?): ActivityVisibility {
            return entries.firstOrNull { it.storageValue == value } ?: if (legacyPrivate == true) {
                PRIVATE
            } else {
                PUBLIC
            }
        }
    }
}
