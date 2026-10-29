package de.mbraisch.garminwidget.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val clientId: String,
)

/**
 * Speichert die Garmin-Tokens verschlüsselt. Der Schlüssel liegt im Android Keystore
 * und verlässt das Gerät nie; in den SharedPreferences steht nur der verschlüsselte Text.
 */
class TokenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): Tokens? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_SIZE)
            val cipherText = raw.copyOfRange(IV_SIZE, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
            Tokens(
                accessToken = json.getString("access"),
                refreshToken = json.optString("refresh").ifBlank { null },
                clientId = json.getString("client"),
            )
        }.getOrNull()
    }

    @Synchronized
    fun save(tokens: Tokens) {
        val json = JSONObject()
            .put("access", tokens.accessToken)
            .put("refresh", tokens.refreshToken ?: "")
            .put("client", tokens.clientId)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
        prefs.edit().putString(KEY_BLOB, blob).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "garmin_tokens"
        const val KEY_BLOB = "tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
