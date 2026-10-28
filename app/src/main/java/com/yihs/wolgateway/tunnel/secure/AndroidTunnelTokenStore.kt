package com.yihs.wolgateway.tunnel.secure

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTunnelTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : TunnelTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun read(): String? = synchronized(lock) {
        preferences.getString(TOKEN_KEY, null)?.let { encoded ->
            runCatching {
                val packed = Base64.decode(encoded, Base64.NO_WRAP)
                require(packed.size > IV_LENGTH)
                val iv = packed.copyOfRange(0, IV_LENGTH)
                val cipherText = packed.copyOfRange(IV_LENGTH, packed.size)
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH, iv))
                }.doFinal(cipherText).toString(StandardCharsets.UTF_8)
            }.getOrNull()
        }
    }

    override fun replace(token: String): String = synchronized(lock) {
        require(token.length in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH)
        require(token.all { it.code in 0x21..0x7e })
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(TOKEN_KEY, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
            .apply()
        token
    }

    override fun isConfigured(): Boolean = read() != null

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
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

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wol_gateway_tunnel_token"
        const val PREFERENCES_NAME = "gateway_tunnel_secure_storage"
        const val TOKEN_KEY = "tunnel_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH = 128
        const val MIN_TOKEN_LENGTH = 32
        const val MAX_TOKEN_LENGTH = 8192
    }
}
