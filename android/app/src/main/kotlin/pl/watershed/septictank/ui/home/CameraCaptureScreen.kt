package pl.watershed.septictank.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import pl.watershed.septictank.data.photo.PhotoStorage
import pl.watershed.septictank.util.Logging

/**
 * Ekran przechwytywania zdjęcia licznika wody aparatem urządzenia (CameraX, research.md ->
 * "Przechwytywanie zdjęcia licznika", FR-001). Wymaga uprawnienia CAMERA w czasie działania
 * (Android 8+); bez zgody pokazuje prośbę o uprawnienie zamiast podglądu z aparatu.
 */
@Composable
fun CameraCaptureScreen(
    photoStorage: PhotoStorage,
    onPhotoCaptured: (File) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(isCameraPermissionGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Aplikacja potrzebuje dostępu do aparatu, aby zrobić zdjęcie licznika wody.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Zezwól na dostęp do aparatu")
            }
            TextButton(onClick = onCancel) { Text("Anuluj") }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                bindCamera(ctx, lifecycleOwner, previewView, imageCapture)
                previewView
            },
        )

        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
            Button(onClick = {
                val targetFile = photoStorage.createPhotoFile()
                capturePhoto(context, imageCapture, targetFile, onPhotoCaptured)
            }) {
                Text("Zrób zdjęcie licznika")
            }
        }
    }
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        } catch (exception: Exception) {
            Logging.e("Nie udało się powiązać CameraX z cyklem życia", exception)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    targetFile: File,
    onPhotoCaptured: (File) -> Unit,
) {
    val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onPhotoCaptured(targetFile)
            }

            override fun onError(exception: ImageCaptureException) {
                Logging.e("Nie udało się zapisać zdjęcia licznika", exception)
            }
        },
    )
}
