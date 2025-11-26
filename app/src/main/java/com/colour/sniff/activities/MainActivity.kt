package com.colour.sniff.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.colour.sniff.R
import com.colour.sniff.adapter.ColorAdapter
import com.colour.sniff.base.BaseActivity
import com.colour.sniff.database.ColorViewModel
import com.colour.sniff.dialog.ColorDetailDialog
import com.colour.sniff.dialog.ColorDialog
import com.colour.sniff.fragments.ColorsFragment
import com.colour.sniff.handler.ColorDetectHandler
import com.colour.sniff.model.UserColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var cameraPreview: PreviewView
    private lateinit var txtHex: TextView
    private lateinit var cardColor: CardView
    private lateinit var pointer: View
    private lateinit var rvColor: RecyclerView
    private lateinit var btnPickImage: ImageView
    private lateinit var btnPickColor: ImageView
    private lateinit var btnChangeCamera: ImageView
    private lateinit var btnCapture: ImageView
    private lateinit var btnShowCamera: ImageView
    private lateinit var btnAddListColor: ImageView
    private lateinit var imageView: ImageView
    private lateinit var layoutTop: RelativeLayout

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

    override fun getLayoutId(): Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupProgrammaticUI()
        setContentView(rootLayout)
        
        initControls(savedInstanceState)
        initEvents()
    }

    private fun setupProgrammaticUI() {
        rootLayout = ConstraintLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            keepScreenOn = true
        }

        cameraPreview = PreviewView(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(0, 0)
        }
        rootLayout.addView(cameraPreview)

        val guidelineTop = Guideline(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                orientation = ConstraintLayout.LayoutParams.VERTICAL 
            }
        }
        val lpGuidelineTop = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            orientation = ConstraintLayout.LayoutParams.HORIZONTAL
            guidePercent = 0.08f
        }
        guidelineTop.layoutParams = lpGuidelineTop
        rootLayout.addView(guidelineTop)

        layoutTop = RelativeLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        rootLayout.addView(layoutTop)

        btnShowCamera = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_baseline_camera_enhance)
            visibility = View.GONE
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
        }
        val btnShowCameraParams = RelativeLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            marginStart = dpToPx(10)
        }
        layoutTop.addView(btnShowCamera, btnShowCameraParams)

        val txtName = TextView(this).apply {
            id = View.generateViewId()
            text = "مصطفى عبد القادر محمد عيسى"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
            textSize = 14f 
        }
        val txtNameParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }
        layoutTop.addView(txtName, txtNameParams)

        val btnShowColors = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_baseline_done)
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
        }
        val btnShowColorsParams = RelativeLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply {
            addRule(RelativeLayout.ALIGN_PARENT_END)
            marginEnd = dpToPx(10)
        }
        layoutTop.addView(btnShowColors, btnShowColorsParams)

        pointer = View(this).apply {
            id = View.generateViewId()
            setBackgroundResource(R.drawable.pointer)
        }
        rootLayout.addView(pointer)

        val cardColorPreview = CardView(this).apply {
            id = View.generateViewId()
            radius = dpToPx(5).toFloat()
        }
        
        val linearLayoutColor = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dpToPx(2), 0, dpToPx(2), 0)
        }
        
        cardColor = CardView(this).apply {
            id = View.generateViewId()
            radius = dpToPx(8).toFloat()
            setCardBackgroundColor(Color.WHITE)
        }
        val cardColorParams = LinearLayout.LayoutParams(dpToPx(15), dpToPx(15))
        linearLayoutColor.addView(cardColor, cardColorParams)

        txtHex = TextView(this).apply {
            id = View.generateViewId()
            text = getString(R.string.color_default)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.textColor))
            textSize = 10f
        }
        val txtHexParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        linearLayoutColor.addView(txtHex, txtHexParams)
        
        cardColorPreview.addView(linearLayoutColor, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootLayout.addView(cardColorPreview)

        imageView = ImageView(this).apply {
            id = View.generateViewId()
            visibility = View.VISIBLE
        }
        rootLayout.addView(imageView)

        val guidelineBottom1 = Guideline(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                orientation = ConstraintLayout.LayoutParams.HORIZONTAL
                guidePercent = 0.8f
            }
        }
        rootLayout.addView(guidelineBottom1)

        val bottomContainer = ConstraintLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
        }
        rootLayout.addView(bottomContainer)

        val guidelineBottom2 = Guideline(this).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                orientation = ConstraintLayout.LayoutParams.HORIZONTAL
                guidePercent = 0.3f
            }
        }
        bottomContainer.addView(guidelineBottom2)

        btnAddListColor = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_plus)
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
        }
        bottomContainer.addView(btnAddListColor)

        rvColor = RecyclerView(this).apply {
            id = View.generateViewId()
        }
        bottomContainer.addView(rvColor)

        btnPickImage = ImageView(this).apply {
            id = View.generateViewId()
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_images_outline)
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
        }
        bottomContainer.addView(btnPickImage)

        btnPickColor = ImageView(this).apply {
            id = View.generateViewId()
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_baseline_camera)
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
        }
        bottomContainer.addView(btnPickColor)

        btnCapture = ImageView(this).apply {
            id = View.generateViewId()
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_baseline_camera)
            visibility = View.INVISIBLE
        }
        bottomContainer.addView(btnCapture)

        btnChangeCamera = ImageView(this).apply {
            id = View.generateViewId()
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_baseline_flip_camera)
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.colorPrimary))
        }
        bottomContainer.addView(btnChangeCamera)

        val set = ConstraintSet()
        set.clone(rootLayout)

        set.connect(cameraPreview.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(cameraPreview.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.connect(cameraPreview.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(cameraPreview.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        set.connect(layoutTop.id, ConstraintSet.TOP, cameraPreview.id, ConstraintSet.TOP)
        set.connect(layoutTop.id, ConstraintSet.BOTTOM, guidelineTop.id, ConstraintSet.TOP)
        set.connect(layoutTop.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(layoutTop.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.constrainHeight(layoutTop.id, 0)

        set.connect(imageView.id, ConstraintSet.TOP, guidelineTop.id, ConstraintSet.BOTTOM)
        set.connect(imageView.id, ConstraintSet.BOTTOM, guidelineBottom1.id, ConstraintSet.TOP)
        set.connect(imageView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(imageView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.constrainHeight(imageView.id, 0)

        set.connect(pointer.id, ConstraintSet.TOP, guidelineTop.id, ConstraintSet.BOTTOM)
        set.connect(pointer.id, ConstraintSet.BOTTOM, guidelineBottom1.id, ConstraintSet.TOP)
        set.connect(pointer.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(pointer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.setMargin(pointer.id, ConstraintSet.TOP, dpToPx(10))
        set.constrainWidth(pointer.id, dpToPx(10))
        set.constrainHeight(pointer.id, dpToPx(10))

        set.connect(cardColorPreview.id, ConstraintSet.BOTTOM, pointer.id, ConstraintSet.TOP)
        set.connect(cardColorPreview.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(cardColorPreview.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.setMargin(cardColorPreview.id, ConstraintSet.BOTTOM, dpToPx(10))
        set.constrainWidth(cardColorPreview.id, dpToPx(100))
        set.constrainHeight(cardColorPreview.id, dpToPx(20))

        set.connect(bottomContainer.id, ConstraintSet.TOP, guidelineBottom1.id, ConstraintSet.BOTTOM)
        set.connect(bottomContainer.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.connect(bottomContainer.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(bottomContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.constrainHeight(bottomContainer.id, 0)
        
        set.applyTo(rootLayout)

        val bottomSet = ConstraintSet()
        bottomSet.clone(bottomContainer)
        
        bottomSet.connect(btnAddListColor.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(btnAddListColor.id, ConstraintSet.BOTTOM, guidelineBottom2.id, ConstraintSet.TOP)
        bottomSet.connect(btnAddListColor.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.setMargin(btnAddListColor.id, ConstraintSet.START, dpToPx(5))
        bottomSet.setMargin(btnAddListColor.id, ConstraintSet.TOP, dpToPx(10))
        bottomSet.constrainWidth(btnAddListColor.id, dpToPx(24))
        bottomSet.constrainHeight(btnAddListColor.id, dpToPx(24))

        bottomSet.connect(rvColor.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        bottomSet.connect(rvColor.id, ConstraintSet.BOTTOM, guidelineBottom2.id, ConstraintSet.TOP)
        bottomSet.connect(rvColor.id, ConstraintSet.START, btnAddListColor.id, ConstraintSet.END)
        bottomSet.connect(rvColor.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        bottomSet.setMargin(rvColor.id, ConstraintSet.START, dpToPx(5))
        bottomSet.setMargin(rvColor.id, ConstraintSet.TOP, dpToPx(10))
        bottomSet.constrainHeight(rvColor.id, 0)
        bottomSet.constrainWidth(rvColor.id, 0)

        bottomSet.connect(btnPickImage.id, ConstraintSet.TOP, guidelineBottom2.id, ConstraintSet.BOTTOM)
        bottomSet.connect(btnPickImage.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.connect(btnPickImage.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        bottomSet.connect(btnPickImage.id, ConstraintSet.END, btnPickColor.id, ConstraintSet.START)
        bottomSet.setHorizontalBias(btnPickImage.id, 0.5f)
        bottomSet.constrainWidth(btnPickImage.id, dpToPx(24))
        bottomSet.constrainHeight(btnPickImage.id, dpToPx(24))

        bottomSet.connect(btnPickColor.id, ConstraintSet.TOP, guidelineBottom2.id, ConstraintSet.BOTTOM)
        bottomSet.connect(btnPickColor.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.connect(btnPickColor.id, ConstraintSet.START, btnPickImage.id, ConstraintSet.END)
        bottomSet.connect(btnPickColor.id, ConstraintSet.END, btnChangeCamera.id, ConstraintSet.START)
        bottomSet.setHorizontalBias(btnPickColor.id, 0.5f)
        bottomSet.constrainWidth(btnPickColor.id, dpToPx(48))
        bottomSet.constrainHeight(btnPickColor.id, dpToPx(48))

        bottomSet.connect(btnCapture.id, ConstraintSet.TOP, guidelineBottom2.id, ConstraintSet.BOTTOM)
        bottomSet.connect(btnCapture.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.connect(btnCapture.id, ConstraintSet.START, btnPickImage.id, ConstraintSet.END)
        bottomSet.connect(btnCapture.id, ConstraintSet.END, btnChangeCamera.id, ConstraintSet.START)
        bottomSet.constrainWidth(btnCapture.id, dpToPx(24))
        bottomSet.constrainHeight(btnCapture.id, dpToPx(24))

        bottomSet.connect(btnChangeCamera.id, ConstraintSet.TOP, guidelineBottom2.id, ConstraintSet.BOTTOM)
        bottomSet.connect(btnChangeCamera.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        bottomSet.connect(btnChangeCamera.id, ConstraintSet.START, btnPickColor.id, ConstraintSet.END)
        bottomSet.connect(btnChangeCamera.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        bottomSet.setHorizontalBias(btnChangeCamera.id, 0.5f)
        bottomSet.constrainWidth(btnChangeCamera.id, dpToPx(24))
        bottomSet.constrainHeight(btnChangeCamera.id, dpToPx(24))

        bottomSet.applyTo(bottomContainer)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun initControls(savedInstanceState: Bundle?) {
        cameraExecutor = Executors.newSingleThreadExecutor()

        rvColor.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvColor.setHasFixedSize(true)
        rvColor.adapter = colorAdapter

        if (allPermissionsGranted()) {
            initCameraProvider()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun initEvents() {
        btnPickColor.setOnClickListener {
            addColor()
        }
        btnAddListColor.setOnClickListener {
            if (currentColorList.isNotEmpty()) {
                val colorDialog = ColorDialog(this, colorViewModel, colorAdapter, clearColorList)
                colorDialog.show()
            }
        }
        btnChangeCamera.setOnClickListener {
            if (!isImageShown) {
                isBackCamera = !isBackCamera
                cameraSelector = if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                bindUseCases()
            }
        }
        btnPickImage.setOnClickListener {
            pickImageFromGallery()
        }
        btnShowCamera.setOnClickListener {
            if (isImageShown) {
                btnShowCamera.visibility = View.GONE
                imageView.visibility = View.GONE
                isImageShown = false
                bindUseCases()
            }
        }
        layoutTop.setOnClickListener {
             showBottomSheetFragment()
        }
        btnCapture.setOnClickListener {
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

        val analyzerBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

        if (Build.VERSION.SDK_INT >= 23) {
            analyzerBuilder.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        }

        val analyzer = analyzerBuilder.build()

        analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        imageAnalyzerUseCase = analyzer

        preview.setSurfaceProvider(cameraPreview.surfaceProvider)

        try {
            provider.bindToLifecycle(this, cameraSelector, preview, imageCapture, analyzer)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            if (imageProxy.format == android.graphics.ImageFormat.YUV_420_888) {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    processBitmapCenter(bitmap)
                }
            } else {
                val planes = imageProxy.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * imageProxy.width
                
                val width = imageProxy.width
                val height = imageProxy.height
                
                val centerX = width / 2
                val centerY = height / 2
                
                val offset = (centerY * rowStride) + (centerX * pixelStride)
                
                if (offset + 3 < buffer.capacity()) {
                     val r = buffer.get(offset).toInt() and 0xFF
                     val g = buffer.get(offset + 1).toInt() and 0xFF
                     val b = buffer.get(offset + 2).toInt() and 0xFF
                     val a = buffer.get(offset + 3).toInt() and 0xFF
                     
                     val color = Color.argb(a, r, g, b)
                     val hex = String.format("#%06X", 0xFFFFFF and color)
                     
                     updateColorUI(hex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }
    
    private fun updateColorUI(hex: String) {
         currentColor.hex = hex
         runOnUiThread {
             txtHex.text = currentColor.hex
             cardColor.setCardBackgroundColor(Color.parseColor(currentColor.hex))
         }
    }

    private fun processBitmapCenter(bitmap: Bitmap) {
         try {
            val pointerX = (pointer.x + pointer.width / 2f).toInt().coerceIn(0, bitmap.width - 1)
            val pointerY = (pointer.y + pointer.height / 2f).toInt().coerceIn(0, bitmap.height - 1)
            val sampledColor = bitmap.getPixel(pointerX, pointerY)
            val hex = String.format("#%06X", 0xFFFFFF and sampledColor)
            updateColorUI(hex)
        } catch (ex: Exception) {
            Log.w(TAG, "frame sampling failed", ex)
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
            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            val out = java.io.ByteArrayOutputStream()
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
                if(imageProxy.format == android.graphics.ImageFormat.JPEG) {
                     val buffer = imageProxy.planes[0].buffer
                     val bytes = ByteArray(buffer.remaining())
                     buffer.get(bytes)
                     val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                     processCapturedBitmap(bitmap)
                } else {
                     val bitmap = imageProxyToBitmap(imageProxy)
                     if(bitmap != null) processCapturedBitmap(bitmap)
                }
                imageProxy.close()
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Image capture failed", exception)
            }
        })
    }
    
    private fun processCapturedBitmap(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Default).launch {
            val centerX = bitmap.width / 2
            val centerY = bitmap.height / 2
            val sampledBitmap = Bitmap.createBitmap(bitmap, centerX, centerY, 1, 1)
            val colorInt = sampledBitmap.getPixel(0, 0)
            currentColor.hex = String.format("#%06X", 0xFFFFFF and colorInt)
            colorDetectHandler.convertRgbToHsl(currentColor)
            withContext(Dispatchers.Main) {
                txtHex.text = currentColor.hex
                cardColor.setCardBackgroundColor(Color.parseColor(currentColor.hex))
                currentColorList.add(0, currentColor)
                colorAdapter.notifyData(currentColorList)
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == android.app.Activity.RESULT_OK && requestCode == REQUEST_CODE) {
            if (!isImageShown) {
                imageView.visibility = View.VISIBLE
                btnShowCamera.visibility = View.VISIBLE
                isImageShown = true
            }
            if (data?.data != null) {
                val uri = data.data!!
                imageView.setImageURI(uri)
                val bitmap = decodeUriToBitmap(uri)
                if(bitmap != null) startDetectColorFromImage(bitmap)
            }
        }
    }

    private fun startDetectColorFromImage(bitmap: Bitmap) {
        cameraProvider?.unbindAll()
        timerTask?.cancel()
        setPointerCoordinates(imageView.width / 2f, imageView.height / 2f)
        var isFitHorizontally = true
        var marginTop: Float = layoutTop.height.toFloat()
        var marginLeft = 0f
        val ratio = if (bitmap.width >= bitmap.height) {
            bitmap.width / (imageView.width * 1.0f)
        } else {
            isFitHorizontally = false
            bitmap.height / (imageView.height * 1.0f)
        }
        if (isFitHorizontally) {
            marginTop += (imageView.height - bitmap.height / ratio) / 2
        } else {
            marginLeft += (imageView.width - bitmap.width / ratio) / 2
        }
        
        timerTask = CoroutineScope(Dispatchers.Default).launch {
             while(true) {
                try {
                     currentColor = colorDetectHandler.detect(bitmap, pointer, marginTop, marginLeft, ratio)
                     Log.d(TAG, "Color : ${currentColor.hex}")
                     withContext(Dispatchers.Main) {
                        txtHex.text = currentColor.hex
                        cardColor.setCardBackgroundColor(Color.parseColor(currentColor.hex))
                     }
                } catch(e: Exception) {
                    Log.e(TAG, "Error detecting from image", e)
                }
                delay(1000) 
             }
        }
    }

    private fun setPointerCoordinates(x: Float, y: Float) {
        pointer.x = x
        pointer.y = y
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

    private fun decodeUriToBitmap(uri: Uri): Bitmap? = try {
        val inputStream = contentResolver.openInputStream(uri)
        val bmp = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        (imageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }
}