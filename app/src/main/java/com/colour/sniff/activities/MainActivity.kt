package com.colour.sniff.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.colour.sniff.R
import com.colour.sniff.adapter.ColorAdapter
import com.colour.sniff.base.BaseActivity
import com.colour.sniff.database.ColorViewModel
import com.colour.sniff.databinding.ActivityMainBinding
import com.colour.sniff.dialog.ColorDetailDialog
import com.colour.sniff.dialog.ColorDialog
import com.colour.sniff.fragments.ColorsFragment
import com.colour.sniff.handler.ColorDetectHandler
import com.colour.sniff.model.UserColor
import com.colour.sniff.utils.timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : BaseActivity() {

    companion object {
        private const val TAG = "CameraXBasic"
        private const val REQUEST_CODE_PERMISSIONS = 26
        private val REQUIRED_PERMISSIONS = mutableListOf<String>().apply {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.READ_MEDIA_IMAGES)
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        private const val REQUEST_CODE = 112
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isBackCamera = true
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzerUseCase: ImageAnalysis? = null
    private val colorDetectHandler = ColorDetectHandler()
    private var timerTask: Job? = null
    private var currentColor = UserColor()
    private var isImageShown = false
    private var currentColorList: MutableList<UserColor> = mutableListOf()
    private val colorAdapter: ColorAdapter by lazy {
        ColorAdapter(this) {
            val detailDialog = ColorDetailDialog(this, it, removeColorInList)
            detailDialog.show()
        }
    }
    private val colorsFragment: ColorsFragment by lazy {
        ColorsFragment()
    }
    private val colorViewModel: ColorViewModel by lazy {
        ViewModelProvider(
            this,
            ColorViewModel.ColorViewModelFactory(application)
        )[ColorViewModel::class.java]
    }

    override fun getLayoutId(): Int = R.layout.activity_main

    override fun initControls(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.bind(findViewById<ViewGroup>(android.R.id.content).getChildAt(0))

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.rvColor.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvColor.setHasFixedSize(true)
        binding.rvColor.adapter = colorAdapter

        if (allPermissionsGranted()) {
            initCameraProvider()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun initEvents() {
        binding.btnPickColor.setOnClickListener {
            addColor()
        }
        binding.btnAddListColor.setOnClickListener {
            if (currentColorList.isNotEmpty()) {
                val colorDialog = ColorDialog(this, colorViewModel, colorAdapter, clearColorList)
                colorDialog.show()
            }
        }
        binding.btnChangeCamera.setOnClickListener {
            if (!isImageShown) {
                isBackCamera = !isBackCamera
                cameraSelector = if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                bindUseCases()
            }
        }
        binding.btnPickImage.setOnClickListener {
            pickImageFromGallery()
        }
        binding.btnShowCamera.setOnClickListener {
            if (isImageShown) {
                binding.btnShowCamera.visibility = View.GONE
                binding.imageView.visibility = View.GONE
                isImageShown = false
                bindUseCases()
            }
        }
        binding.btnShowColors.setOnClickListener {
            showBottomSheetFragment()
        }
        binding.btnCapture.setOnClickListener {
            takePictureAndProcess()
        }
    }

    private fun initCameraProvider() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "unbindAll failed", e)
        }

        val preview = Preview.Builder().build()
        imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()

        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                try {
                    val pointerX = (binding.pointer.x + binding.pointer.width / 2f).toInt().coerceIn(0, bitmap.width - 1)
                    val pointerY = (binding.pointer.y + binding.pointer.height / 2f).toInt().coerceIn(0, bitmap.height - 1)
                    val sampledBitmap = Bitmap.createBitmap(bitmap, pointerX, pointerY, 1, 1)
                    val sampledColor = sampledBitmap.getPixel(0, 0)
                    val hex = String.format("#%06X", 0xFFFFFF and sampledColor)
                    currentColor.hex = hex
                    runOnUiThread {
                        binding.txtHex.text = currentColor.hex
                        binding.cardColor.setCardBackgroundColor(Color.parseColor(currentColor.hex))
                    }
                } catch (ex: Exception) {
                    Log.w(TAG, "frame sampling failed", ex)
                }
            }
            imageProxy.close()
        }

        imageAnalyzerUseCase = analyzer

        preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

        try {
            provider.bindToLifecycle(this, cameraSelector, preview, imageCapture, analyzer)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "imageProxyToBitmap failed", e)
            null
        }
    }

    private fun takePictureAndProcess() {
        val capture = imageCapture ?: return
        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bitmap = imageProxyToBitmap(imageProxy)
                imageProxy.close()
                if (bitmap != null) {
                    CoroutineScope(Dispatchers.Default).timer(0) {
                        val centerX = bitmap.width / 2
                        val centerY = bitmap.height / 2
                        val sampledBitmap = Bitmap.createBitmap(bitmap, centerX, centerY, 1, 1)
                        val colorInt = sampledBitmap.getPixel(0, 0)
                        currentColor.hex = String.format("#%06X", 0xFFFFFF and colorInt)
                        colorDetectHandler.convertRgbToHsl(currentColor)
                        withContext(Dispatchers.Main) {
                            binding.txtHex.text = currentColor.hex
                            binding.cardColor.setCardBackgroundColor(Color.parseColor(currentColor.hex))
                            currentColorList.add(0, currentColor)
                            colorAdapter.notifyData(currentColorList)
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, getString(R.string.unknown_color), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Image capture failed", exception)
            }
        })
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE) {
            if (!isImageShown) {
                binding.imageView.visibility = View.VISIBLE
                binding.btnShowCamera.visibility = View.VISIBLE
                isImageShown = true
            }
            if (data?.data != null) {
                val uri = data.data!!
                binding.imageView.setImageURI(uri)
                val bitmap = decodeUriToBitmap(uri)
                startDetectColorFromImage(bitmap)
            }
        }
    }

    private fun startDetectColorFromImage(bitmap: Bitmap) {
        cameraProvider?.unbindAll()
        timerTask?.cancel()
        setPointerCoordinates(binding.imageView.width / 2f, binding.imageView.height / 2f)
        var isFitHorizontally = true
        var marginTop: Float = binding.layoutTop.height.toFloat()
        var marginLeft = 0f
        val ratio = if (bitmap.width >= bitmap.height) {
            bitmap.width / (binding.imageView.width * 1.0f)
        } else {
            isFitHorizontally = false
            bitmap.height / (binding.imageView.height * 1.0f)
        }
        if (isFitHorizontally) {
            marginTop += (binding.imageView.height - bitmap.height / ratio) / 2
        } else {
            marginLeft += (binding.imageView.width - bitmap.width / ratio) / 2
        }
        timerTask = CoroutineScope(Dispatchers.Default).timer(1000) {
            currentColor = colorDetectHandler.detect(bitmap, binding.pointer, marginTop, marginLeft, ratio)
            Log.d(TAG, "Color : ${currentColor.hex}")
            withContext(Dispatchers.Main) {
                binding.txtHex.text = currentColor.hex
                binding.cardColor.setCardBackgroundColor(Color.parseColor(currentColor.hex))
            }
        }
    }

    private fun setPointerCoordinates(x: Float, y: Float) {
        binding.pointer.x = x
        binding.pointer.y = y
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initCameraProvider()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun addColor() {
        try {
            Color.parseColor(currentColor.hex)
            colorDetectHandler.convertRgbToHsl(currentColor)
            currentColorList.add(0, currentColor)
            colorAdapter.notifyData(currentColorList)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, resources.getString(R.string.unknown_color), Toast.LENGTH_SHORT).show()
        }
    }

    private val clearColorList: () -> Unit = {
        currentColorList.clear()
        colorAdapter.notifyData(currentColorList)
    }

    private fun showBottomSheetFragment() {
        colorsFragment.show(supportFragmentManager, colorsFragment.tag)
    }

    private val removeColorInList: (UserColor) -> Unit = {
        currentColorList.remove(it)
        colorAdapter.notifyData(currentColorList)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerTask?.cancel()
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "unbindAll onDestroy failed", e)
        }
        cameraExecutor.shutdown()
    }

    private fun decodeUriToBitmap(uri: Uri): Bitmap = try {
        val inputStream = contentResolver.openInputStream(uri)
        val bmp = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        (binding.imageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}