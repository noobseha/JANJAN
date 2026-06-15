package com.gachon.janjan

import java.util.Locale
import kotlin.random.Random

object TableInviteCodes {
    private const val CODE_LENGTH = 6
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(existingCodes: Set<String> = emptySet()): String {
        val normalizedExisting = existingCodes.map { normalize(it) }.toSet()
        repeat(50) {
            val code = buildString(CODE_LENGTH) {
                repeat(CODE_LENGTH) {
                    append(ALPHABET[Random.nextInt(ALPHABET.length)])
                }
            }
            if (code !in normalizedExisting) return code
        }
        return System.currentTimeMillis()
            .toString(36)
            .takeLast(CODE_LENGTH)
            .uppercase(Locale.US)
    }

    fun normalize(code: String): String =
        code.trim()
            .trim('"', '\'')
            .replace(" ", "")
            .replace("-", "")
            .uppercase(Locale.US)
}
