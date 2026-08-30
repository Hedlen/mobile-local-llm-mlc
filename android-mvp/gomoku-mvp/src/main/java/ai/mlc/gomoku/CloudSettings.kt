package ai.mlc.gomoku

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CloudModelConfig(val endpoint: String, val model: String, val apiKey: String)

class CloudSettings(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_settings", Context.MODE_PRIVATE)

    fun load(): CloudModelConfig? {
        val key = decrypt(preferences.getString("key", null), preferences.getString("iv", null)) ?: return null
        val endpoint = preferences.getString("endpoint", DEFAULT_ENDPOINT).orEmpty()
        val model = preferences.getString("model", DEFAULT_MODEL).orEmpty()
        return CloudModelConfig(endpoint, model, key).takeIf { it.endpoint.startsWith("https://") && it.model.isNotBlank() }
    }

    fun save(endpoint: String, model: String, apiKey: String): Boolean {
        if (!endpoint.startsWith("https://") || model.isBlank() || apiKey.isBlank()) return false
        val encrypted = encrypt(apiKey) ?: return false
        preferences.edit().putString("endpoint", endpoint).putString("model", model)
            .putString("key", encrypted.first).putString("iv", encrypted.second).apply()
        return true
    }

    fun clear() { preferences.edit().clear().apply() }

    private fun encrypt(value: String): Pair<String, String>? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) to Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(payload: String?, iv: String?): String? {
        if (payload == null || iv == null) return null
        return runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }
        String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)))
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
        const val DEFAULT_MODEL = "deepseek-v4-flash-ga-260731"
        private const val KEY_ALIAS = "gomoku_cloud_api_key_v1"
    }
}
