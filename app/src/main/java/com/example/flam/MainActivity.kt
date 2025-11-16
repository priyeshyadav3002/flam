// Aapka package name
package com.example.flam

// --- Imports ---
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat // NAYA: ImageReader ke liye
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader // NAYA: Yeh frame buffer hai
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log // NAYA: Logging ke liye
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.flam.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 101

    // --- Member Variables ---
    private lateinit var textureView: TextureView
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraId: String
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private lateinit var binding: ActivityMainBinding

    // --- NAYA: C++ Processing ke liye ImageReader ---
    private lateinit var imageReader: ImageReader
    private var isProcessingFrame = false // Ek flag taaki processing overflow na ho

    // Yeh listener har baar fire hota hai jab camera se naya frame taiyaar hota hai
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        // Sabse naya frame lo
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

        // Hum ek flag use karte hain taaki agar C++ busy hai toh frame drop kar dein
        // Isse app responsive rehta hai
        if (!isProcessingFrame) {
            isProcessingFrame = true

            // Y, U, aur V planes (YUV_420_888 format) ka data lo
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            // --- YEH HAI ASLI CHEEZ ---
            // Data ko seedha humare C++ function mein bhejo
            processFrame(
                image.width,
                image.height,
                yBuffer,
                uBuffer,
                vBuffer
            )
            // -----------------------

            isProcessingFrame = false
        }

        // Frame ko close karna na bhoolein!
        image.close()
    }


    // --- Activity Lifecycle Methods ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                checkCameraPermission()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        if (textureView.isAvailable) {
            checkCameraPermission()
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    // --- Permission Handling Methods (Koi change nahi) ---

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        } else {
            openCamera()
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Background Thread Methods (Koi change nahi) ---

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    // --- Camera Methods (Yeh badle hain) ---

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = manager.cameraIdList[0]

            // --- NAYA: ImageReader ko set up karein ---
            // Hum 1920x1080 size ke YUV_420_888 format ke frames receive karenge
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)

            // Listener ko background thread par run karein
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            // ------------------------------------

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture ?: return

            texture.setDefaultBufferSize(1920, 1080)
            val surface = Surface(texture)

            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            // --- MODIFIED: Ab DO (2) targets hain ---
            // 1. 'surface' (Screen par live preview ke liye)
            previewRequestBuilder.addTarget(surface)
            // 2. 'imageReader.surface' (Frames ko C++ mein bhejne ke liye)
            previewRequestBuilder.addTarget(imageReader.surface) // <-- NAYA

            // --- MODIFIED: Session ko DO (2) surfaces ke saath banayein ---
            cameraDevice?.createCaptureSession(
                listOf(surface, imageReader.surface), // <-- NAYA
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return

                        captureSession = session
                        try {
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        // --- NAYA: ImageReader ko bhi close karein ---
        imageReader?.close()
    }

    // --- JNI Function Declarations ---

    /**
     * Yeh NAYA native function hai jise hum call kar rahe hain.
     */
    external fun processFrame(
        width: Int,
        height: Int,
        yBuffer: java.nio.ByteBuffer,
        uBuffer: java.nio.ByteBuffer,
        vBuffer: java.nio.ByteBuffer
    )

    /**
     * Template ka default function.
     */
    external fun stringFromJNI(): String

    companion object {
        init {
            // Library ka naam "flam" load karein
            System.loadLibrary("flam")
        }
    }
}