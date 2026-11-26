package com.gizwits.smart.app.ui.component

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One scanner per composition; owned CameraX use cases, analyzer and ML Kit client are released together. */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrScanner(onResult: (String) -> Unit, onCancel: () -> Unit, cancelLabel: String = "返回手动输入") {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onResult)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) error = "相机权限未开启，你仍可以手动输入。也可在系统设置中允许相机权限。"
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    val previewHeight = minOf(300.dp, LocalConfiguration.current.screenHeightDp.dp * 0.4f)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (granted) {
            val previewView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    clipChildren = true
                    clipToPadding = true
                }
            }
            // PreviewView transforms the camera texture to fill its bounds; clip the drawing
            // as well as constraining measurement so it cannot cover headings or controls.
            Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(16.dp))) {
                AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
            }
            DisposableEffect(owner, previewView) {
                val disposed = AtomicBoolean(false)
                val delivered = AtomicBoolean(false)
                val executor = Executors.newSingleThreadExecutor()
                val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                var provider: ProcessCameraProvider? = null
                analysis.setAnalyzer(executor) { frame ->
                    val image = frame.image
                    if (disposed.get() || delivered.get() || image == null) frame.close()
                    else scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            val raw = codes.firstOrNull()?.rawValue
                            if (!disposed.get() && raw != null && delivered.compareAndSet(false, true)) callback(raw)
                        }.addOnCompleteListener { frame.close() }
                }
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (!disposed.get()) {
                        try {
                            provider = future.get()
                            provider?.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (_: Exception) { error = "无法打开相机，请返回手动输入，或检查相机是否被占用。" }
                    }
                }, ContextCompat.getMainExecutor(context))
                onDispose {
                    disposed.set(true)
                    analysis.clearAnalyzer()
                    provider?.unbind(preview, analysis)
                    scanner.close()
                    executor.shutdown()
                }
            }
            Text("将完整二维码放入画面中")
        }
        error?.let { SoftMessage(it, true) }
        SoftAction(cancelLabel, onCancel, secondary = true)
    }
}
