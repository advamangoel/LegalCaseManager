package com.legalcasemanager

object CnrValidator {
    fun normalize(value: String): String =
        value.trim().uppercase().replace(" ", "").replace("-", "")

    fun isValid(value: String): Boolean {
        val v = normalize(value)
        return v.length == 16 && v.all { it.isLetterOrDigit() }
    }

    fun message(value: String): String {
        if (value.isBlank()) return "CNR not entered"
        return if (isValid(value)) {
            "CNR format valid (offline validation)"
        } else {
            "Invalid CNR format: enter exactly 16 alphanumeric characters."
        }
    }
}
