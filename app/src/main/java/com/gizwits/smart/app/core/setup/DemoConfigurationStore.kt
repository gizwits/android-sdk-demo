package com.gizwits.smart.app.core.setup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** One encrypted, atomic configuration; excluded from backup along with its device-bound key. */
@Singleton
class DemoConfigurationStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "demo-configuration.enc"))
    private val gson = Gson()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun load(): DemoConfiguration {
        if (!file.baseFile.exists()) return DemoConfiguration()
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val config = gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), DemoConfiguration::class.java)
        require(config.server in SERVERS)
        require(config.usesDefaultApp || (ProductCredentials.valid(config.customAppId) && ProductCredentials.valid(config.customAppSecret)))
        require(config.products.all { ProductCredentials.valid(it.productKey) && ProductCredentials.valid(it.productSecret) })
        return config
    }
    @Synchronized fun save(config: DemoConfiguration) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val persisted = DemoConfiguration(config.customAppId, config.customAppSecret, config.server, config.products,
            if (config.usesDefaultApp) DemoDefaults.APP_ID else config.appliedDefaultAppId,
            config.onboardingCompleted)
        val encrypted = cipher.iv + cipher.doFinal(gson.toJson(persisted).toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    companion object {
        private const val ALIAS = "demo-configuration-v1"
        val SERVERS = listOf("api.gizwits.com", "usapi.gizwits.com", "euapi.gizwits.com")
    }
}
