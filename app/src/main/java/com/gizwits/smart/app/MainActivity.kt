package com.gizwits.smart.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat
import com.gizwits.smart.app.core.data.repository.AppPreferencesRepository
import com.gizwits.smart.app.core.data.util.NetworkMonitor
import com.gizwits.smart.app.core.datastore.ThemeMode
import com.gizwits.smart.app.ui.SmartApp
import com.gizwits.smart.app.ui.theme.SmartTheme
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var systemUiController: SystemUiController? = null

    /**
     * 应用首选项存储库
     */
    @Inject
    lateinit var appPreferencesRepository: AppPreferencesRepository

    /**
     * 网络状态监听器
     */
    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        copyAssetsToCustomDirectories();

        setContent {
            val themeMode by appPreferencesRepository.themeMode.collectAsState(initial = null)
            if (themeMode == null) return@setContent
            val darkTheme = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                else -> isSystemInDarkTheme()
            }
            SmartTheme(darkTheme = darkTheme) {
                // 隐藏系统的导航栏并设置状态栏颜色
                systemUiController = rememberSystemUiController()
                val backgroundColor: Color = MaterialTheme.colorScheme.background
                LaunchedEffect(backgroundColor, darkTheme) {
                    systemUiController!!.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    systemUiController!!.setSystemBarsColor(backgroundColor, darkIcons = !darkTheme)
                }

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SmartApp(
                        networkMonitor = networkMonitor,
                        appPreferencesRepository = appPreferencesRepository
                    )
                }
            }
        }
    }

    private fun copyAssetsToCustomDirectories() {
        val assetManager = assets
        val directories = arrayOf("api.gizwits.com", "usapi.gizwits.com", "euapi.gizwits.com")
        val files: Array<String>? = try {
            assetManager.list("productConfig")
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
        for (directory in directories) {
            if (files != null) {
                for (filename in files) {
                    val fileDirectory = File(filesDir.path + "/productConfig/$directory")
                    val inPath = "productConfig/$filename"
                    val outFile = File(fileDirectory, filename)
                    copyFileWithPath(inPath, outFile)
                }
            }
        }
    }

    private fun copyFileWithPath(assetPath: String, targetFile: File) {
        val assetManager = assets

        if (targetFile.exists()) {
            // File already exists, log it
            Log.d("copyFileWithPath", "File already exists: ${targetFile.absolutePath}")
            return
        }

        try {
            // Create directory
            val parentDir = targetFile.parentFile
            if (parentDir?.exists() == false) {
                parentDir?.mkdirs()
            }

            val `in` = assetManager.open(assetPath)
            val out = FileOutputStream(targetFile)

            val buffer = ByteArray(1024)
            var read: Int
            while (`in`.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }

            `in`.close()
            out.flush()
            out.close()

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun copyAssets(assetDir: String, targetDir: File) {
        val assetManager = assets

        try {
            // Get all files and subfolders in the assets directory
            val assets = assetManager.list(assetDir)
            Log.d("Start CopyAssets", assets.toString())
            if (assets != null) {
                for (asset in assets) {
                    val assetPath = "$assetDir${File.separator}$asset"
                    val targetPath = "${targetDir.absolutePath}${File.separator}$asset"
                    // If it's a directory, copy recursively
                    if (assetManager.list(assetPath)!!.isNotEmpty()) {
                        copyAssets(assetPath, File(targetPath))
                    } else {
                        copyFileWithPath(assetPath, File(targetPath))
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}
