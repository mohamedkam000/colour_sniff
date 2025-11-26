package com.colour.sniff.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.colour.sniff.R
import com.colour.sniff.base.BaseActivity
import com.colour.sniff.database.ColorViewModel
import com.colour.sniff.dialog.ColorDetailDialog
import com.colour.sniff.dialog.ColorDialog
import com.colour.sniff.fragments.ColorsFragment
import com.colour.sniff.handler.ColorDetectHandler
import com.colour.sniff.model.UserColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : BaseActivity() {

    private val colorDetectHandler = ColorDetectHandler()
    private val colorViewModel: ColorViewModel by lazy {
        ViewModelProvider(
            this,
            ColorViewModel.ColorViewModelFactory(application)
        )[ColorViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorSniffApp()
        }
    }

    override fun getLayoutId(): Int = 0 
    override fun initControls(savedInstanceState: Bundle?) {}
    override fun initEvents() {}

    @Composable
    fun ColorSniffApp() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()

        var hasPermission by remember { mutableStateOf(checkPermissions(context)) }
        var isBackCamera by remember { mutableStateOf(true) }
        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
        var currentHex by remember { mutableStateOf("#FFFFFF") }
        val colorList = remember { mutableStateListOf<UserColor>() }
        
        var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isImageMode by remember { mutableStateOf(false) }
        
        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
        
        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val bitmap = decodeUriToBitmap(context, uri)
                    selectedImageBitmap = bitmap
                    isImageMode = true
                }
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            hasPermission = permissions.values.all { it }
        }

        LaunchedEffect(Unit) {
            if (!hasPermission) {
                permissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission && !isImageMode) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    update = { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            val cameraSelector = if (isBackCamera) 
                                CameraSelector.DEFAULT_BACK_CAMERA 
                            else 
                                CameraSelector.DEFAULT_FRONT_CAMERA

                            val analyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()

                            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val buffer = imageProxy.planes[0].buffer
                                val width = imageProxy.width
                                val height = imageProxy.height
                                
                                val pixelStride = imageProxy.planes[0].pixelStride
                                val rowStride = imageProxy.planes[0].rowStride
                                
                                val centerX = width / 2
                                val centerY = height / 2
                                
                                val offset = (centerY * rowStride) + (centerX * pixelStride)
                                
                                if (offset + 3 < buffer.capacity()) {
                                    val r = buffer.get(offset).toInt() and 0xFF
                                    val g = buffer.get(offset + 1).toInt() and 0xFF
                                    val b = buffer.get(offset + 2).toInt() and 0xFF
                                    val hex = String.format("#%02X%02X%02X", r, g, b)
                                    
                                    scope.launch(Dispatchers.Main) {
                                        currentHex = hex
                                    }
                                }
                                imageProxy.close()
                            }

                            imageCapture = ImageCapture.Builder().build()

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    analyzer,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("Camera", "Binding failed", e)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                )
            }

            if (isImageMode && selectedImageBitmap != null) {
                ImageDetectorView(
                    bitmap = selectedImageBitmap!!,
                    onColorDetected = { hex -> currentHex = hex }
                )
                
                Image(
                    painter = painterResource(id = R.drawable.ic_baseline_camera_enhance),
                    contentDescription = "Back to Camera",
                    modifier = Modifier
                        .padding(top = 16.dp, start = 16.dp)
                        .size(32.dp)
                        .clickable {
                            isImageMode = false
                            selectedImageBitmap = null
                        },
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF2196F3))
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                val screenHeight = LocalConfiguration.current.screenHeightDp
                val guidelineTop = (screenHeight * 0.08).dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(guidelineTop)
                        .background(Color.White)
                ) {
                    Text(
                        text = "مصطفى عبد القادر محمد عيسى",
                        color = Color(0xFF2196F3),
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    Image(
                        painter = painterResource(id = R.drawable.ic_baseline_done),
                        contentDescription = "Show Colors",
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .size(32.dp)
                            .clickable {
                                ColorsFragment().show(supportFragmentManager, "ColorsFragment")
                            },
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF2196F3))
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp)
            ) {
                 Image(
                    painter = painterResource(id = R.drawable.pointer),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 20.dp)
                        .width(150.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    elevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Card(
                            backgroundColor = Color(android.graphics.Color.parseColor(currentHex)),
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = currentHex, color = Color.Black)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LocalConfiguration.current.screenHeightDp.dp * 0.2f)
                        .background(Color.White)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_plus),
                            contentDescription = "Add List Color",
                            modifier = Modifier
                                .padding(16.dp)
                                .size(32.dp)
                                .clickable {
                                    if (colorList.isNotEmpty()) {
                                        ColorDialog(
                                            this@MainActivity,
                                            colorViewModel,
                                            com.colour.sniff.adapter.ColorAdapter(this@MainActivity) {}, // Dummy adapter for dialog
                                            { colorList.clear() }
                                        ).show()
                                    }
                                },
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF2196F3))
                        )

                        LazyRow(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(colorList) { color ->
                                Card(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clickable {
                                            ColorDetailDialog(this@MainActivity, color) {
                                                colorList.remove(it)
                                            }.show()
                                        },
                                    backgroundColor = Color(android.graphics.Color.parseColor(color.hex)),
                                    shape = CircleShape
                                ) {}
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_images_outline),
                            contentDescription = "Pick Image",
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                                    galleryLauncher.launch(intent)
                                },
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF2196F3))
                        )

                        Image(
                            painter = painterResource(id = R.drawable.ic_baseline_camera),
                            contentDescription = "Pick Color",
                            modifier = Modifier
                                .size(64.dp)
                                .clickable {
                                    val newColor = UserColor().apply {
                                        hex = currentHex
                                        colorDetectHandler.convertRgbToHsl(this)
                                    }
                                    colorList.add(0, newColor)
                                },
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF2196F3))
                        )

                        Image(
                            painter = painterResource(id = R.drawable.ic_baseline_flip_camera),
                            contentDescription = "Change Camera",
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { isBackCamera = !isBackCamera },
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF2196F3))
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ImageDetectorView(bitmap: Bitmap, onColorDetected: (String) -> Unit) {
        var pointerOffset by remember { mutableStateOf(Offset.Zero) }
        val density = LocalDensity.current

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val imageWidth = bitmap.width
            val imageHeight = bitmap.height
            
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            if (pointerOffset == Offset.Zero) {
                 pointerOffset = Offset(
                     with(density) { maxWidth.toPx() / 2 },
                     with(density) { maxHeight.toPx() / 2 }
                 )
            }

            LaunchedEffect(pointerOffset) {
                val viewWidth = with(density) { maxWidth.toPx() }
                val viewHeight = with(density) { maxHeight.toPx() }
                
                val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
                
                val scaledWidth = imageWidth * scale
                val scaledHeight = imageHeight * scale
                
                val imageOffsetX = (viewWidth - scaledWidth) / 2
                val imageOffsetY = (viewHeight - scaledHeight) / 2
                
                val relativeX = pointerOffset.x - imageOffsetX
                val relativeY = pointerOffset.y - imageOffsetY
                
                if (relativeX in 0f..scaledWidth && relativeY in 0f..scaledHeight) {
                    val bitmapX = (relativeX / scale).toInt().coerceIn(0, imageWidth - 1)
                    val bitmapY = (relativeY / scale).toInt().coerceIn(0, imageHeight - 1)
                    
                    val pixel = bitmap.getPixel(bitmapX, bitmapY)
                    val hex = String.format("#%06X", 0xFFFFFF and pixel)
                    onColorDetected(hex)
                }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(pointerOffset.x.roundToInt() - 25, pointerOffset.y.roundToInt() - 25) }
                    .size(20.dp) 
            ) {
                 // The pointer is drawn by the parent composable normally, 
                 // but in image mode we might want to make it draggable?
                 // For now, keeping it static center to match original logic or allowing drag:
            }
        }
    }

    private fun checkPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf<String>().apply {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.READ_MEDIA_IMAGES)
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
}