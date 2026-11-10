package com.grandcouncil.remote.scan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * GC-20 扫码配对（B7 §1 规格）：CameraX 预览 + ML Kit QR 识别 + URL 策略校验。
 * 扫到合法 http(s) 地址 → setResult 回传给连接表单；其余内容忽略继续扫。
 * 学 zcode 交互（扫码配对入口），不学壳架构（GC 原生直连）。
 */
class ScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ScannerScreen(
                    onResult = { url ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_URL, url))
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "gc_scan_url"
    }
}

/** URL 策略校验：仅接受 http/https 且带合法 host 的地址（防扫码注入任意 scheme） */
fun isValidPairUrl(raw: String): Boolean {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return false
    if (uri.scheme != "http" && uri.scheme != "https") return false
    return !uri.host.isNullOrBlank()
}

@Composable
private fun ScannerScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }
    var toastShown by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) permissionGranted = true else permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            CameraPreview(
                onQr = { text ->
                    if (isValidPairUrl(text)) {
                        onResult(text.trim())
                    } else if (!toastShown) {
                        toastShown = true
                        Toast.makeText(context, "不是有效的 http(s) 连接地址", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "扫码配对需要相机权限",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                    Text("授权相机", color = Color.White)
                }
            }
        }

        // 取景框遮罩（中心 280dp 透明窗 + 四角提示）
        Box(
            Modifier
                .align(Alignment.Center)
                .size(280.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            "将连接二维码对准取景框\n仅识别 http(s) 连接地址",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Text("关闭", color = Color.White)
        }
    }
}

/** CameraX 预览 + ImageAnalysis 单帧 QR 识别（首帧合法地址即回传） */
@androidx.compose.runtime.Composable
private fun CameraPreview(onQr: (String) -> Unit) {
    val context = LocalContext.current
    var processed by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            // ScannerActivity 即 LifecycleOwner（ComponentActivity）；
            // 不引 lifecycle-compose 依赖，直接从 context 取
            val lifecycleOwner = ctx as androidx.lifecycle.LifecycleOwner
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build(),
                )
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                    @OptIn(ExperimentalGetImage::class)
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !processed) {
                        val image = InputImage.fromMediaImage(
                            mediaImage, imageProxy.imageInfo.rotationDegrees,
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (!processed) {
                                    val url = barcodes.firstOrNull { it.valueType == Barcode.TYPE_URL }?.url?.url
                                        ?: barcodes.firstOrNull()?.rawValue
                                    if (url != null) {
                                        processed = true
                                        onQr(url)
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}
