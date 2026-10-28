package com.restaurantorders.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 4-digit staff PINs, stored salted and hashed so the database never holds them in plain text. */
object Pins {
    private const val ITERATIONS = 20_000
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()

    fun isValid(pin: String) = pin.length == 4 && pin.all { it.isDigit() }

    fun hash(pin: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        return b64.encodeToString(salt) + ":" + b64.encodeToString(derive(pin, salt))
    }

    fun matches(pin: String, stored: String): Boolean {
        val (salt, hash) = stored.split(":").map { Base64.getDecoder().decode(it) }
        return MessageDigest.isEqual(derive(pin, salt), hash)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256))
            .encoded
}
