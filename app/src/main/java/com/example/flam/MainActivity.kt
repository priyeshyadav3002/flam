package com.example.flam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.*

class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 101

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: MyGLRenderer

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private lateinit var cameraId: String
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var imageReader: ImageReader? = null
    private var isProcessingFrame = false

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

        if (!isProcessingFrame) {
            isProcessingFrame = true

            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val processedData: ByteArray = processFrame(
                image.width,
                image.height,
                yBuffer,
                uBuffer,
                vBuffer
            )

            val byteBuffer = ByteBuffer.allocateDirect(processedData.size)
            byteBuffer.put(processedData)
            byteBuffer.position(0)

            renderer.updateTexture(byteBuffer, image.width, image.height)
            glSurfaceView.requestRender()

            isProcessingFrame = false
        }
        image.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        renderer = MyGLRenderer(this)

        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        glSurfaceView.onResume()
        checkCameraPermission()
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        glSurfaceView.onPause()
        super.onPause()
    }

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

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = manager.cameraIdList[0]

            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)

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
            Log.e("MainActivity", "Failed to open camera", e)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            // Yeh 'let' block null crash se bachayega
            imageReader?.surface?.let { readerSurface ->
                previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                previewRequestBuilder?.addTarget(readerSurface)

                cameraDevice?.createCaptureSession(
                    listOf(readerSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            if (cameraDevice == null) return

                            captureSession = session
                            try {
                                previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                previewRequestBuilder?.build()?.let { request ->
                                    captureSession?.setRepeatingRequest(request, null, backgroundHandler)
                                }
                            } catch (e: CameraAccessException) {
                                Log.e("MainActivity", "Failed to set repeating request", e)
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("MainActivity", "Failed to configure capture session")
                        }
                    }, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e("MainActivity", "Failed to create capture session", e)
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
    }

    external fun processFrame(
        width: Int,
        height: Int,
        yBuffer: java.nio.ByteBuffer,
        uBuffer: java.nio.ByteBuffer,
        vBuffer: java.nio.ByteBuffer
    ): ByteArray

    external fun stringFromJNI(): String

    companion object {
        init {
            System.loadLibrary("flam")
        }
    }
}