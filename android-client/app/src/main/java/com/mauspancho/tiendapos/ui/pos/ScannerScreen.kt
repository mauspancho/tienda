package com.mauspancho.tiendapos.ui.pos

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable fun ScannerScreen(onCode: (String, Long) -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var failure by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    val callback by rememberUpdatedState(onCode)
    val preview = remember { PreviewView(context) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Escanear productos", style = MaterialTheme.typography.titleLarge)
            TextButton(close) { Text("Cerrar") }
        }
        if (!permission) {
            Text("Permite el acceso a cámara para leer códigos de barras.", Modifier.padding(16.dp))
            Button({ launcher.launch(Manifest.permission.CAMERA) }, Modifier.padding(16.dp)) { Text("Permitir cámara") }
        } else {
            AndroidView({ preview }, Modifier.fillMaxWidth().weight(1f))
            Text("Apunta al código. Puedes escanear varios productos.", Modifier.padding(16.dp))
            failure?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            DisposableEffect(lifecycle, preview) {
                val executor = Executors.newSingleThreadExecutor()
                val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128).build())
                val future = ProcessCameraProvider.getInstance(context)
                val busy = AtomicBoolean(false)
                val disposed = AtomicBoolean(false)
                var cameraProvider: ProcessCameraProvider? = null
                val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { frame ->
                    if (disposed.get() || !busy.compareAndSet(false, true)) { frame.close() }
                    else {
                        @androidx.annotation.OptIn(ExperimentalGetImage::class)
                        val media = frame.image
                        if (media == null) { frame.close(); busy.set(false) }
                        else scanner.process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
                            .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { codes ->
                                if (!disposed.get()) codes.firstOrNull()?.rawValue?.let { callback(it, SystemClock.elapsedRealtime()) }
                            }.addOnCompleteListener { frame.close(); busy.set(false) }
                    }
                }
                future.addListener({
                    if (!disposed.get()) try {
                        cameraProvider = future.get()
                        cameraProvider!!.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, analysis)
                    } catch (_: Exception) { failure = "No se pudo abrir la cámara. Puedes buscar el producto manualmente." }
                }, ContextCompat.getMainExecutor(context))
                onDispose { disposed.set(true); analysis.clearAnalyzer(); cameraProvider?.unbind(cameraPreview, analysis); scanner.close(); executor.shutdown() }
            }
        }
    }
}
