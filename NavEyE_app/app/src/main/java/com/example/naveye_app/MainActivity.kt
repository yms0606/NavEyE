package com.example.naveye_app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProcessor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.naveye_app.ui.theme.NavEyE_appTheme
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.core.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.Manifest
import android.R
import android.R.attr.bitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontVariation.width
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationManagerCompat.getCurrentLocation
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import kotlinx.coroutines.launch
import okhttp3.FormBody
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private val client = OkHttpClient()
    private  var tts: TextToSpeech? = null
    private val flaskUrl = "http://34.64.206.83:5000/"
    //private val flaskUrl = "http://192.168.0.2:5000/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    Log.d("Permission", "Camera permission granted")
                } else {
                    Log.e("Permission", "Camera permission denied")
                }
            }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),1001)
        }


        cameraExecutor = Executors.newSingleThreadExecutor()

        lifecycleScope.launch {
            while (true){
                delay(3000)
                fetchAndSpeakMessage()
            }
        }

        lifecycleScope.launch {
            while (true){
                delay(3000)
                sendCurrentLocation(this@MainActivity)
            }
        }

        lifecycleScope.launch {
            while(true){
                delay(2000)
                alertSpeakMessage()
            }
        }


        setContent {
            MyCameraUI()
        }
    }


    @Composable
    fun MyCameraUI() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { PreviewView(context) }

        val isCapturing = remember { mutableStateOf(false) }
        var lastSendTime by remember { mutableStateOf(0L) }


        // 카메라 초기화
        LaunchedEffect(Unit) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }


                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            val currentTime = System.currentTimeMillis()
                            if (isCapturing.value && currentTime - lastSendTime >= 350) { // 200ms = 5FPS 제한
                                val bitmap = imageProxy.toBitmap()
                                if (bitmap != null) {
                                    Log.d("Analyzer", "Bitmap created, sending to server")
                                    takePhotoAndSend(context, bitmap)
                                    lastSendTime = currentTime
                                }
                            }
                            imageProxy.close()
                        }
                    }

                //imageCapture = ImageCapture.Builder().build()
                //val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                    Log.d("Camera", "Camera initialized")
                } catch (e: Exception) {
                    Log.e("Camera", "Camera use case binding failed", e)
                }

            }, ContextCompat.getMainExecutor(context))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9F9F9))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isCapturing.value) "실시간 인식 중 (5 FPS)" else "기능이 꺼져 있습니다",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (isCapturing.value) {
                        speakOut("네비아이 기능을 종료 합니다. 다시 시작 하려면 화면을 터치해 주세요")
                    } else {
                        speakOut("네비아이 기능을 시작 합니다. 멈추려면 화면을 터치해 주세요")
                    }
                    isCapturing.value = !isCapturing.value
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF))
            ) {
                Text(
                    text = if (isCapturing.value) "중지하기" else "시작하기",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

    }

    fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }



    private fun takePhotoAndSend(context: Context, bitmap: Bitmap){
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "frame", "frame.jpg",
                byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url((flaskUrl+"upload_frame"))  // ← 실제 서버 주소로 교체
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Upload", "이미지 전송 실패", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("Upload", "이미지 전송 성공: ${response.code}")
            }
        })
    }


    override fun onInit(p0: Int) {
        if(p0 == TextToSpeech.SUCCESS){
            tts?.language = Locale.KOREAN
            speakOut("네비아이를 시작합니다. 화면을 터치해주세요")
        }
    }
    private fun speakOut(text: String){
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun speakMsg(text: String){
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private  fun alertSpeakMessage(){
        val request = Request.Builder().url(flaskUrl+"alert").build()

        client.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TTS", "장애물 Flask 통신 실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                if(!json.isNullOrEmpty() && json != "{}") {
                    val obj = JSONObject(json)

                    if(obj.has("label")){
                        val label = obj.getString("label")
                        runOnUiThread {
                            AlertVibrate(this@MainActivity)
                            speakMsg(" 전방에 ${label}가 있습니다. 주의하세요!")
                        }
                    }

                }
            }
        })
    }

    private  fun fetchAndSpeakMessage(){
        val request = Request.Builder().url(flaskUrl+"get_message").build()

        client.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TTS", "Flask 통신 실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let{ json ->
                    val message = JSONObject(json).getString("message")
                    if(!message.isNullOrEmpty() && message != "null"){
                        runOnUiThread {
                            speakMsg("보호자로부터 전송된 메세지입니다.")
                            speakMsg(message)
                        }
                    }
                }
            }
        })
    }

    private fun sendCurrentLocation(activity: ComponentActivity){
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)

        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location?->
            location?.let {
                val lat = it.latitude
                val lon = it.longitude
                sendToServer(lat,lon)
            }
        }

    }
    private fun sendToServer(lat: Double, lon: Double){
        val client = OkHttpClient()

        val requestBody = FormBody.Builder()
            .add("lat",lat.toString())
            .add("lon",lon.toString())
            .build()

        val request = Request.Builder()
            .url(flaskUrl+"update_gps")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object: Callback{
            override fun onFailure(call: Call, e: IOException) {
                println("전송 실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                println("전송 성공: ${response.body?.string()}")
            }

        })
    }

    private fun AlertVibrate(context: Context){
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        }else{
            vibrator.vibrate(500)
        }
    }
}
