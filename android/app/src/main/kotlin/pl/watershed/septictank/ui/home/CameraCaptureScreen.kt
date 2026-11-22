package pl.watershed.septictank.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import pl.watershed.septictank.data.photo.PhotoStorage
import pl.watershed.septictank.ui.common.EmptyState
import pl.watershed.septictank.ui.theme.AppIcons
import pl.watershed.septictank.util.Logging

/**
 * Screen for capturing a water meter photo with the device camera (CameraX, research.md ->
 * "Meter photo capture", FR-001). Requires the CAMERA runtime permission (Android 8+); without
 * consent it shows a permission request instead of the camera preview.
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

    // System back closes the camera instead of leaving the app.
    BackHandler(onBack = onCancel)

    if (!hasCameraPermission) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            EmptyState(
                icon = AppIcons.PhotoCamera,
                title = "Potrzebny dostęp do aparatu",
                message = "Aplikacja używa aparatu tylko do zrobienia zdjęcia licznika wody. " +
                    "Zdjęcie jest analizowane wyłącznie na tym telefonie.",
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Zezwól na dostęp do aparatu")
                        }
                        TextButton(onClick = onCancel) { Text("Anuluj") }
                    }
                },
            )
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    // Blocks double taps while a photo is being saved and gives visible feedback.
    var isCapturing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                bindCamera(ctx, lifecycleOwner, previewView, imageCapture)
                previewView
            },
        )

        // Framing guide: helps keep the meter's digits inside the area OCR reads best.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.4f)
                    .border(BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)), RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
                Text(
                    "Umieść cyfry licznika w ramce",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        FilledIconButton(
            onClick = onCancel,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(48.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Zamknij aparat")
        }

        // Large round shutter button (72 dp), thumb-reachable at the bottom centre.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            Surface(
                onClick = {
                    if (!isCapturing) {
                        isCapturing = true
                        val targetFile = photoStorage.createPhotoFile()
                        capturePhoto(context, imageCapture, targetFile, onPhotoCaptured) {
                            isCapturing = false
                            Toast.makeText(context, "Nie udało się zrobić zdjęcia. Spróbuj ponownie.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isCapturing,
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(4.dp, Color.White.copy(alpha = 0.5f)),
                modifier = Modifier
                    .size(76.dp)
                    .semantics { contentDescription = "Zrób zdjęcie licznika" },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCapturing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(AppIcons.PhotoCamera, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
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
    onFailed: () -> Unit,
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
                onFailed()
            }
        },
    )
}
