package com.gizwits.smart.app.update

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gizwits.smart.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class AppRelease(val version: String, val build: Long, val url: String, val sha256: String,
                      val size: Long, val notes: String, val required: Boolean)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    var release by mutableStateOf<AppRelease?>(null); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf<Int?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var apk by mutableStateOf<File?>(null); private set
    var installRequested by mutableStateOf(false); private set
    private val preferences = application.getSharedPreferences("app_updates", 0)
    private var automaticChecked = false
    private val context get() = getApplication<Application>()
    @Suppress("DEPRECATION")
    private fun installed() = context.packageManager.getPackageInfo(context.packageName, 0)
    @Suppress("DEPRECATION")
    private fun build(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    val currentVersion: String get() = installed().versionName ?: "0.0"

    fun check(manual: Boolean = false) {
        if (busy || (!manual && automaticChecked)) return
        if (!manual) automaticChecked = true
        if (release != null) return
        val base = BuildConfig.APP_UPDATE_BASE_URL.trimEnd('/')
        if (base.isEmpty()) { if (manual) message = "暂未配置更新服务"; return }
        busy = true
        viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    val query = mapOf("app_id" to context.packageName, "platform" to "android",
                        "channel" to BuildConfig.APP_UPDATE_CHANNEL, "version" to currentVersion,
                        "build" to build(installed()).toString()).entries.joinToString("&") {
                        "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
                    val connection = connect("$base/updates/mobile?$query", allowConfiguredHttp = true)
                    try {
                        if (connection.responseCode == 204) null else {
                            check(connection.responseCode == 200) { "检查更新失败，请稍后重试" }
                            val bytes = connection.inputStream.use { it.readBytesLimited(65536) }
                            val value = JSONObject(String(bytes, Charsets.UTF_8))
                            check(value.getString("app_id") == context.packageName && value.getString("platform") == "android" &&
                                value.getString("channel") == BuildConfig.APP_UPDATE_CHANNEL) { "更新信息与当前应用不匹配" }
                            AppRelease(value.getString("version"), value.getLong("build"), value.getString("url"),
                                value.getString("sha256"), value.getLong("size"), value.optString("notes"), value.getBoolean("required")).also {
                                check(it.build > build(installed()) && it.size in 1..2147483648L && it.sha256.matches(Regex("[a-f0-9]{64}"))) { "更新信息无效" }
                                check(URL(it.url).protocol == "https") { "更新包必须使用 HTTPS" }
                            }
                        }
                    } finally { connection.disconnect() }
                }
                val lastPrompt = preferences.getLong("prompt_${next?.build}", 0)
                if (next != null && (manual || next.required || System.currentTimeMillis() - lastPrompt >= 86400000L)) {
                    release = next; apk = null
                    preferences.edit().putLong("prompt_${next.build}", System.currentTimeMillis()).apply()
                } else if (manual) message = "已是最新版本（$currentVersion）"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (manual) message = e.message ?: "检查更新失败，请稍后重试" }
            finally { busy = false }
        }
    }
    fun dismiss() { if (release?.required != true && !busy) { release = null; apk = null } }
    fun clearMessage() { message = null }
    fun installationHandled() { installRequested = false }
    fun installError(text: String) { installRequested = false; message = text }
    fun download() {
        val target = release ?: return
        if (busy) return
        if (apk?.isFile == true) { installRequested = true; return }
        busy = true; progress = 0
        viewModelScope.launch {
            try {
                apk = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
                    // Only our private update cache is touched.
                    directory.listFiles()?.forEach { it.delete() }
                    val partial = File(directory, "update.part")
                    val result = File(directory, "update.apk")
                    try {
                        val connection = connect(target.url)
                        try {
                            check(connection.responseCode == 200) { "安装包下载失败" }
                            val hash = MessageDigest.getInstance("SHA-256")
                            var total = 0L
                            connection.inputStream.use { input -> partial.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val count = input.read(buffer); if (count < 0) break
                                    total += count
                                    check(total <= target.size) { "安装包大小不匹配" }
                                    output.write(buffer, 0, count); hash.update(buffer, 0, count)
                                    val percent = (total * 100 / target.size).toInt()
                                    withContext(Dispatchers.Main) { progress = percent }
                                }
                            } }
                            check(total == target.size && hash.digest().joinToString("") { "%02x".format(it) } == target.sha256) { "安装包校验失败，请重新下载" }
                        } finally { connection.disconnect() }
                        verifyPackage(partial, target)
                        check(partial.renameTo(result)) { "无法保存安装包" }
                        result
                    } finally { partial.delete() }
                }
                installRequested = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "下载失败，请重试" }
            finally { busy = false; progress = null }
        }
    }
    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, target: AppRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("安装包无法解析")
        val current = pm.getPackageInfo(context.packageName, flags)
        check(archive.packageName == context.packageName && build(archive) == target.build && build(archive) > build(current) && archive.versionName == target.version) { "安装包的应用或版本不匹配" }
        val nextSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val currentSigners = if (Build.VERSION.SDK_INT >= 28) current.signingInfo?.apkContentsSigners else current.signatures
        check(!nextSigners.isNullOrEmpty() && !currentSigners.isNullOrEmpty() && nextSigners.toSet() == currentSigners.toSet()) { "安装包签名与当前应用不同，无法覆盖更新" }
    }
    private fun connect(address: String, allowConfiguredHttp: Boolean = false): HttpURLConnection {
        val url = URL(address)
        check(url.protocol == "https" || (allowConfiguredHttp && BuildConfig.APP_UPDATE_ALLOW_HTTP && url.protocol == "http")) { "更新服务需要 HTTPS" }
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
        }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val count = read(buffer); if (count < 0) break; check(out.size() + count <= limit) { "更新信息过大" }; out.write(buffer, 0, count) }
        return out.toByteArray()
    }
}
