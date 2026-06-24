package com.example.trabalhomobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var imageCapture: ImageCapture? = null
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TelaPrincipal(
                        activity = this,
                        fusedLocationClient = fusedLocationClient,
                        onPhotoCaptured = { path ->
                            currentPhotoPath = path
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TelaPrincipal(
    activity: ComponentActivity,
    fusedLocationClient: FusedLocationProviderClient,
    onPhotoCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var latitude by remember { mutableStateOf("-") }
    var longitude by remember { mutableStateOf("-") }
    var jsonString by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasLocationPermission = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    }

    // Solicita as permissões de câmera e localização ao abrir o app.
    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = cameraGranted
        hasLocationPermission = locationGranted

        if (!cameraGranted || !locationGranted) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("App de Captura de Foto", style = MaterialTheme.typography.headlineSmall)

        if (!hasCameraPermission || !hasLocationPermission) {
            Text("É necessário conceder câmera e localização para continuar.")
            return@Column
        }

        Button(onClick = {
            previewVisible = true
        }) {
            Text("Capturar Foto")
        }

        if (previewVisible) {
            // Exibe o preview da câmera usando CameraX.
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val executor = Executors.newSingleThreadExecutor()
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder().build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    previewView
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Button(onClick = {
                // Quando o usuário toca no botão, a foto é capturada e o fluxo continua.
                capturePhoto(
                    activity = activity,
                    imageCapture = imageCapture,
                    onSuccess = { filePath ->
                        onPhotoCaptured(filePath)
                        previewVisible = false

                        val bitmap = BitmapFactory.decodeFile(filePath)
                        capturedBitmap = bitmap

                        // Após salvar a imagem, busca a localização atual e monta o JSON.
                        CoroutineScope(Dispatchers.IO).launch {
                            val locationResult = getCurrentLocation(fusedLocationClient, context)
                            val (lat, lon) = locationResult
                            val imageBase64 = encodeImageToBase64(filePath)
                            val json = JSONObject().apply {
                                put("latitude", lat)
                                put("longitude", lon)
                                put("imagemBase64", imageBase64)
                            }

                            withContext(Dispatchers.Main) {
                                latitude = lat
                                longitude = lon
                                jsonString = json.toString()
                            }

                            preparePostRequest(jsonString)
                        }
                    },
                    onError = { message ->
                        errorMessage = message
                    }
                )
            }) {
                Text("Tirar Foto")
            }
        }

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }

        capturedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto capturada",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Latitude: $latitude")
        Text("Longitude: $longitude")

        if (jsonString.isNotEmpty()) {
            Text("JSON gerado:")
            Text(jsonString)
        }
    }
}

private fun capturePhoto(
    activity: ComponentActivity,
    imageCapture: ImageCapture?,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = activity.baseContext
    val outputDir = activity.cacheDir
    val fileName = "foto_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val photoFile = File(outputDir, fileName)

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture?.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSuccess(photoFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Erro ao capturar foto: ${exception.message}")
            }
        }
    )
}

// Converte a imagem para Base64 para que ela possa ser colocada dentro do JSON.
private fun encodeImageToBase64(filePath: String): String {
    val imageBytes = File(filePath).readBytes()
    return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
}

// Recupera latitude e longitude do usuário com FusedLocationProviderClient.
private suspend fun getCurrentLocation(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context
): Pair<String, String> {
    return try {
        val result = fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).await()

        val latitudeValue = result?.latitude?.toString() ?: "0.0"
        val longitudeValue = result?.longitude?.toString() ?: "0.0"
        Pair(latitudeValue, longitudeValue)
    } catch (e: Exception) {
        Pair("0.0", "0.0")
    }
}

// Prepara a requisição HTTP POST com o JSON gerado para um endpoint fake.
private fun preparePostRequest(jsonString: String) {
    val client = OkHttpClient()
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = jsonString.toRequestBody(mediaType)

    val request = Request.Builder()
        .url("https://example.com/api/foto")
        .post(body)
        .build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            client.newCall(request).execute().use { response ->
                Log.d("POST", "Resposta do servidor: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("POST", "Erro ao preparar/enviar requisição: ${e.message}")
        }
    }
}
