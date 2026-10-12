package com.reeflive.app.update

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Updater preferences. The GitHub token is encrypted with an AES-GCM key that lives in the Android Keystore
 * (the key never leaves the phone or goes into backups); a token that cannot be decrypted (restored backup, new phone) is dropped.
 */
class UpdatePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("reeflive_updates", Context.MODE_PRIVATE)

    var autoCheck: Boolean
        get() = prefs.getBoolean(K_AUTO, true)
        set(v) { prefs.edit().putBoolean(K_AUTO, v).apply() }

    var lastCheckMs: Long
        get() = prefs.getLong(K_LAST, 0L)
        set(v) { prefs.edit().putLong(K_LAST, v).apply() }

    /** A version the user dismissed with "Later": automatic checks don't pop the sheet again for it. */
    var dismissedCode: Long
        get() = prefs.getLong(K_DISMISSED, 0L)
        set(v) { prefs.edit().putLong(K_DISMISSED, v).apply() }

    val hasToken: Boolean get() = prefs.contains(K_TOKEN)

    fun token(): String? {
        val stored = prefs.getString(K_TOKEN, null) ?: return null
        return runCatching {
            val (iv, ct) = stored.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrElse {
            prefs.edit().remove(K_TOKEN).apply()
            null
        }
    }

    fun setToken(token: String?) {
        val t = token?.trim()
        if (t.isNullOrEmpty()) {
            prefs.edit().remove(K_TOKEN).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(t.toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString(K_TOKEN, value).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "reeflive_update_token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val K_AUTO = "auto_check"
        const val K_LAST = "last_check"
        const val K_DISMISSED = "dismissed_code"
        const val K_TOKEN = "github_token"
    }
}
