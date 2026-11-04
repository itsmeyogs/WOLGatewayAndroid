package com.yihs.wolgateway.gateway.auth

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

@Singleton
class AndroidApiTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : ApiTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun getOrCreate(): String = synchronized(lock) {
        readToken() ?: generateToken().also(::writeToken)
    }

    override fun read(): String? = synchronized(lock) { readToken() }

    override fun createNew(): String = synchronized(lock) {
        generateToken().also(::writeToken)
    }

    override fun replace(token: String): String = synchronized(lock) {
        require(token.length >= MIN_TOKEN_LENGTH) { "API token is too short" }
        require(token.length <= MAX_TOKEN_LENGTH) { "API token is too long" }
        require(token.all { it.code in 0x21..0x7e }) { "API token contains invalid characters" }
        writeToken(token)
        token
    }

    override fun isConfigured(): Boolean = synchronized(lock) { readToken() != null }

    private fun readToken(): String? = preferences.getString(TOKEN_KEY, null)?.let { encoded ->
        runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > GCM_IV_LENGTH_BYTES)
            val iv = packed.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherText = packed.copyOfRange(GCM_IV_LENGTH_BYTES, packed.size)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }.doFinal(cipherText).toString(StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun writeToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val cipherText = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        preferences.edit {
            putString(TOKEN_KEY, Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP))
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun generateToken(): String = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).let {
        Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wol_gateway_api_token"
        const val PREFERENCES_NAME = "gateway_secure_storage"
        const val TOKEN_KEY = "api_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val TOKEN_BYTES = 32
        const val MIN_TOKEN_LENGTH = 32
        const val MAX_TOKEN_LENGTH = 512
    }
}
