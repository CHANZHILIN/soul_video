package com.soul_video.recordVideo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
import android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION
import android.media.CamcorderProfile
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import androidx.core.app.ActivityCompat
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.kotlin_baselib.api.Constants
import com.kotlin_baselib.base.BaseViewModelActivity
import com.kotlin_baselib.base.EmptyViewModel
import com.kotlin_baselib.utils.DateUtil
import com.kotlin_baselib.utils.PermissionUtils
import com.kotlin_baselib.utils.SdCardUtil
import com.kotlin_baselib.utils.SnackbarUtil
import com.soul_video.R
import kotlinx.android.synthetic.main.activity_video_record.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Long.signum
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

/**
 *  Created by CHEN on 2019/7/20
 *  Email:1181785848@qq.com
 *  Package:com.soul_video.recordVideo
 *  Introduce:视频录制
 **/
@SuppressLint("NewApi")
@Route(path = Constants.RECORD_VIDEO_ACTIVITY_PATH)
class VideoRecordActivity : BaseViewModelActivity<EmptyViewModel>() {


    override fun providerVMClass(): Class<EmptyViewModel>? = EmptyViewModel::class.java

    override fun getResId(): Int = R.layout.activity_video_record


    private val MAX_VIDEO_RECORD_TIME: Int = 10     //录制时长最长10S
    private val MIN_VIDEO_RECORD_TIME: Int = 2       //录制时长最短2S
    private var currentTime: Int = 0    //录频的时间
    private lateinit var mCaptureSize: Size     //最大尺寸
    private var isLightOn: Boolean = false  //是否打开闪光灯
    private val CAPTURE_OK: Int = 101   //拍照完成
    private val RECORD_VIDEO_OK: Int = 102   //录制完成

    @JvmField
    @Autowired(name = "CameraMode")
    var mCameraMode: Int = 0     //模式：0为拍照   1为录视频
    private var isCameraFront = false//当前是否是前置摄像头
    private lateinit var characteristics: CameraCharacteristics
    private var mImageReader: ImageReader? = null

    /**
     * A reference to the opened [android.hardware.camera2.CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * A reference to the current [android.hardware.camera2.CameraCaptureSession] for
     * preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * The [android.util.Size] of video recording.
     */
    private lateinit var videoSize: Size

    /**
     * Whether the app is recording video now
     */
    private var isRecordingVideo = false

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0


    /**
     * Output file for video
     */
    private var nextVideoAbsolutePath: String? = null

    private var mediaRecorder: MediaRecorder? = null


    /**
     * 放大或者缩小
     */
    //手指按下的点为(x1, y1)手指离开屏幕的点为(x2, y2)
    internal var finger_spacing: Float = 0.toFloat()
    internal var zoom_level = 0
    internal var zoom: Rect? = null

    private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
    private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
    private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }
    private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 270)
        append(Surface.ROTATION_90, 180)
        append(Surface.ROTATION_180, 90)
        append(Surface.ROTATION_270, 0)
    }

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener: TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(
                texture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                openCamera(width, height)
            }

            override fun onSurfaceTextureSizeChanged(
                texture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                configureTransform(width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture) = true

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

        }


    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@VideoRecordActivity.cameraDevice = cameraDevice
            startPreview()
            configureTransform(texture.width, texture.height)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@VideoRecordActivity.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@VideoRecordActivity.cameraDevice = null
            finish()
        }

    }

    override fun preSetContentView() {
        super.preSetContentView()
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

    }


    override fun initData() {
        btn_record_video.setButtonText(if (mCameraMode == 0) "拍照" else "录制")
        iv_switch_picture_video.setImageBitmap(
            BitmapFactory.decodeResource(
                resources,
                if (mCameraMode == 0) R.mipmap.video else R.mipmap.camera
            )
        )
    }


    override fun initListener() {
        btn_record_video.setOnClickListener {
            when (mCameraMode) {
                0 -> {       //拍照
                    capture()
                }
                1 -> {        //录制视频
                    if (isRecordingVideo) stopRecordingVideo() else startRecordingVideo()
                }
            }

        }
        iv_record_video_close.setOnClickListener {
            finish()
        }

        iv_switch_front_back.setOnClickListener {
            if (!isRecordingVideo)
                switchCamera()  //切换前后摄像头
            else SnackbarUtil.ShortSnackbar(
                texture,
                "视频正在录制中，请完成录制再操作",
                SnackbarUtil.WARNING
            ).show()
        }

        iv_switch_picture_video.setOnClickListener {
            if (!isRecordingVideo)
            //切换拍照，视频
                switchCameraMode()
            else SnackbarUtil.ShortSnackbar(
                texture,
                "视频正在录制中，请完成录制再操作",
                SnackbarUtil.WARNING
            ).show()
        }
        iv_switch_flash_light.setOnClickListener {
            //开启闪光灯
            switchFlashLight()
        }

        texture.setOnTouchListener { view, event ->
            //两指缩放
            changeZoom(event)
            true
        }
    }


    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }


    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (texture.isAvailable) {
            openCamera(texture.width, texture.height)
        } else {
            texture.surfaceTextureListener = surfaceTextureListener
        }
    }


    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper) {
            when (it.what) {
                CAPTURE_OK -> {
                    //这里拍照保存完成，可以进行相关的操作，例如再次压缩等
                    SnackbarUtil.ShortSnackbar(texture, "拍照完成，保存路径为${it.obj}", SnackbarUtil.WARNING)
                        .show()
                }
                RECORD_VIDEO_OK -> {
                    //这里录制视频保存完成，可以进行相关的操作，例如再次压缩等
                    ARouter.getInstance().build(Constants.EDIT_VIDEO_ACTIVITY_PATH)
                        .withString("videoPath", it.obj as String).navigation()
                }
            }
            true
        }
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(Constants.DEBUG_TAG, e.toString())
        }
    }


    /**
     * Tries to open a [CameraDevice]. The result is listened by [stateCallback].
     *
     * Lint suppression - permission is checked in [hasPermissionsGranted]
     */
    private fun openCamera(width: Int, height: Int) {

        //申请权限
        if (PermissionUtils.isGranted(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {

        } else {
            PermissionUtils.permission(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
                .callBack(object : PermissionUtils.PermissionCallBack {
                    override fun onGranted(permissionUtils: PermissionUtils) {
                        //重新打开
                        if (texture.isAvailable) {
                            openCamera(texture.width, texture.height)
                        } else {
                            texture.surfaceTextureListener = surfaceTextureListener
                        }
                    }

                    override fun onDenied(permissionUtils: PermissionUtils) {
                        SnackbarUtil.ShortSnackbar(
                            texture,
                            "拒绝了权限，将无法使用相机功能",
                            SnackbarUtil.WARNING
                        ).show()
                        return
                    }
                }).request(this)
        }


        if (this.isFinishing) return

        val manager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            //0表示后置摄像头,1表示前置摄像头
            val cameraId = manager.cameraIdList[0]
            val cameraIdFront = manager.cameraIdList[1]

            //前置摄像头和后置摄像头的参数属性不同，所以这里要做下判断
            if (isCameraFront) {  // Choose the sizes for camera preview and video recording
                characteristics = manager.getCameraCharacteristics(cameraIdFront)
            } else {
                characteristics = manager.getCameraCharacteristics(cameraId)
            }

//            val characteristics = manager.getCameraCharacteristics(mCameraId)
            val map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw RuntimeException("Cannot get available preview/video sizes")
            sensorOrientation = characteristics.get(SENSOR_ORIENTATION)!!
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            previewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture::class.java),
                width, height, videoSize
            )

            //获取相机支持的最大拍照尺寸
            mCaptureSize =
                Collections.max(Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG))) { lhs, rhs ->
                    signum((lhs.width * lhs.height - rhs.height * rhs.width).toLong())
                }

            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                texture.setAspectRatio(mCaptureSize.width, mCaptureSize.height)
            } else {
                texture.setAspectRatio(mCaptureSize.height, mCaptureSize.width)
            }
            configureTransform(width, height)
            //此ImageReader用于拍照所需
            setupImageReader()

            mediaRecorder = MediaRecorder() //用于摄像

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (isCameraFront)
                manager.openCamera(cameraIdFront, stateCallback, null)
            else
                manager.openCamera(cameraId, stateCallback, null)
        } catch (e: CameraAccessException) {
            SnackbarUtil.ShortSnackbar(
                texture,
                "打开相机失败",
                SnackbarUtil.WARNING
            ).show()
            this.finish()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.

            SnackbarUtil.ShortSnackbar(
                texture,
                "打开相机失败",
                SnackbarUtil.WARNING
            ).show()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    //配置ImageReader
    private fun setupImageReader() {

        //2代表ImageReader中最多可以获取两帧图像流
        mImageReader = ImageReader.newInstance(
            mCaptureSize.width, mCaptureSize.height,
            ImageFormat.JPEG, 2
        )
        mImageReader!!.setOnImageAvailableListener({ reader ->

            val mImage = reader.acquireNextImage()
            val buffer = mImage.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val picSavePath = getPictureFilePath()
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(picSavePath).apply {
                    write(data, 0, data.size)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                mImage.close()
                fos?.let {
                    try {
                        it.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                val msg = Message()
                msg.what = CAPTURE_OK
                msg.obj = picSavePath
                backgroundHandler?.sendMessage(msg)
            }

        }, backgroundHandler)
    }

    /**
     * 拍照
     */
    private fun capture() {
        if (null == cameraDevice || !texture.isAvailable) {
            return
        }
        try {
            val mCaptureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            //获取屏幕方向
            val rotation = windowManager.defaultDisplay.rotation
            mCaptureBuilder.addTarget(mImageReader!!.surface)
            //isCameraFront是自定义的一个boolean值，用来判断是不是前置摄像头，是的话需要旋转180°，不然拍出来的照片会歪了
            if (isCameraFront) {
                mCaptureBuilder.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    DEFAULT_ORIENTATIONS.get(Surface.ROTATION_180)
                )
            } else {
                mCaptureBuilder.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    DEFAULT_ORIENTATIONS.get(rotation)
                )
            }

            //锁定焦点
            mCaptureBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START
            )

            //判断预览的时候是不是已经开启了闪光灯
            if (isLightOn) {
                mCaptureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                mCaptureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }

            //判断预览的时候是否两指缩放过,是的话需要保持当前的缩放比例
            mCaptureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)

            val CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    //拍完照unLockFocus
                    unLockFocus()
                }
            }
            captureSession?.stopRepeating()
            //咔擦拍照
            captureSession?.capture(mCaptureBuilder.build(), CaptureCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }


    private fun unLockFocus() {
        try {
            // 构建失能AF的请求
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            //闪光灯重置为未开启状态
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            //继续开启预览
            captureSession?.setRepeatingRequest(
                previewRequestBuilder.build(),
                null,
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * 切换摄像头
     */
    fun switchCamera() {
        cameraDevice?.close()
        cameraDevice = null


        if (isCameraFront) {
            isCameraFront = false
            openCamera(texture.width, texture.height)
        } else {
            isCameraFront = true
            openCamera(texture.width, texture.height)
        }
    }

    /**
     * 切换拍照或者录制视频
     */
    private fun switchCameraMode() {
        when (mCameraMode) {
            0 -> {       //拍照换成视频
                btn_record_video.setButtonText("录制")
                iv_switch_picture_video.setImageBitmap(
                    BitmapFactory.decodeResource(
                        resources,
                        R.mipmap.camera
                    )
                )
                mCameraMode = 1
                isRecordingVideo = false
                video_record_progress_view.visibility = View.VISIBLE
                currentTime = 0
            }
            1 -> {        //录制视频换成拍照
                btn_record_video.setButtonText("拍照")
                iv_switch_picture_video.setImageBitmap(
                    BitmapFactory.decodeResource(
                        resources,
                        R.mipmap.video
                    )
                )
                mCameraMode = 0
                isRecordingVideo = false
                video_record_progress_view.visibility = View.GONE
            }
        }
    }

    /**
     * 开光闪光灯
     */
    private fun switchFlashLight() {
        when (isLightOn) {
            true -> {   //关闭闪光灯
                iv_switch_flash_light.setImageBitmap(
                    BitmapFactory.decodeResource(
                        resources,
                        R.mipmap.flash_off
                    )
                )
                previewRequestBuilder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_OFF
                )
                isLightOn = false
            }
            false -> {//开启闪光灯
                iv_switch_flash_light.setImageBitmap(
                    BitmapFactory.decodeResource(
                        resources,
                        R.mipmap.flash_on
                    )
                )
                previewRequestBuilder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_TORCH
                )
                isLightOn = true
            }
        }

        try {
            captureSession?.setRepeatingRequest(
                previewRequestBuilder.build(),
                null,
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * 关闭摄像头
     */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            closePreviewSession()
            cameraDevice?.close()
            cameraDevice = null
            mImageReader?.close()
            mImageReader = null
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * 开启预览
     */
    private fun startPreview() {
        if (cameraDevice == null || !texture.isAvailable) return

        try {
            closePreviewSession()
            val surfaceTexture = texture.surfaceTexture
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            val previewSurface = Surface(surfaceTexture)
            previewRequestBuilder.addTarget(previewSurface)

            //默认预览不开启闪光灯
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)

            cameraDevice?.createCaptureSession(
                listOf(previewSurface, mImageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        SnackbarUtil.ShortSnackbar(
                            texture,
                            "Failed",
                            SnackbarUtil.WARNING
                        ).show()
                    }
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(Constants.DEBUG_TAG, e.toString())
        }

    }

    /**
     * 更新预览
     */
    private fun updatePreview() {
        if (cameraDevice == null) return

        try {
            setUpCaptureRequestBuilder(previewRequestBuilder)
            HandlerThread("CameraPreview").start()
            captureSession?.setRepeatingRequest(
                previewRequestBuilder.build(),
                null, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(Constants.DEBUG_TAG, e.toString())
        }

    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder?) {
        builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {

        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            with(matrix) {
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        }
        texture.setTransform(matrix)
    }


    /**
     * 保存视频地址
     */
    private fun getVideoFilePath(): String {
        val filename = "soul_video_${DateUtil.parseToString(
            System.currentTimeMillis(),
            DateUtil.yyyyMMddHHmmss
        )}.mp4"
        val dir = SdCardUtil.DEFAULT_VIDEO_PATH
        return "${dir}/$filename"

    }

    /**
     * 保存图片地址
     */
    private fun getPictureFilePath(): String {
        val filename = "soul_picture_${DateUtil.parseToString(
            System.currentTimeMillis(),
            DateUtil.yyyyMMddHHmmss
        )}.jpg"
        val dir = SdCardUtil.DEFAULT_PHOTO_PATH
        return "${dir}/$filename"

    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {

        if (nextVideoAbsolutePath.isNullOrEmpty()) {
            nextVideoAbsolutePath = getVideoFilePath()
        }

        val rotation = windowManager.defaultDisplay.rotation
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                mediaRecorder?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES ->
                mediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }


        mediaRecorder?.apply {

            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)

            if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
                val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P)
                profile.videoBitRate = previewSize.width * previewSize.height
                setProfile(profile)
//                setPreviewDisplay(Surface(texture.surfaceTexture))
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
                val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P)
                profile.videoBitRate = previewSize.width * previewSize.height

                setProfile(profile)
//                setPreviewDisplay(Surface(texture.surfaceTexture))
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA)) {
                setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA))
//                setPreviewDisplay(Surface(texture.surfaceTexture))
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_CIF)) {
                setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_CIF))
//                setPreviewDisplay(Surface(texture.surfaceTexture))
            } else {
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(2500000)
                setVideoFrameRate(20)
                setVideoSize(videoSize.width, videoSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }

//            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(nextVideoAbsolutePath)
//            setVideoEncodingBitRate(10000000)
//            setVideoFrameRate(30)
//            setVideoSize(videoSize.width, videoSize.height)
//            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }
    }

    /**
     * 开始录制视频
     */
    private fun startRecordingVideo() {
        if (cameraDevice == null || !texture.isAvailable) return

        try {
            closePreviewSession()
            setUpMediaRecorder()
            val texture = texture.surfaceTexture.apply {
                setDefaultBufferSize(previewSize.width, previewSize.height)
            }

            // Set up Surface for camera preview and MediaRecorder
            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder!!.surface
            val surfaces = ArrayList<Surface>().apply {
                add(previewSurface)
                add(recorderSurface)
            }
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(previewSurface)
                    addTarget(recorderSurface)

                    //判断预览之前有没有开闪光灯
                    if (isLightOn) {
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    } else {
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }

                }

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        captureSession = cameraCaptureSession
                        updatePreview()
                        runOnUiThread {
                            isRecordingVideo = true
                            mediaRecorder?.start()
                            btn_record_video.setButtonText("暂停")
                            video_record_progress_view.visibility = View.VISIBLE
                            video_record_progress_view.setIsStart(true)
                            currentTime = 0
                        }
                        val recordTimer = Timer()
                        val recordTask: TimerTask = object : TimerTask() {
                            override fun run() {
                                currentTime++
                                if (currentTime > MAX_VIDEO_RECORD_TIME) {
                                    runOnUiThread { stopRecordingVideo() }
                                    recordTimer.cancel()
                                }
                            }
                        }
                        recordTimer.schedule(recordTask, 1000, 1000)
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        SnackbarUtil.ShortSnackbar(
                            iv_record_video_close,
                            "Failed",
                            SnackbarUtil.WARNING
                        ).show()
                    }
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(Constants.DEBUG_TAG, e.toString())
        } catch (e: IOException) {
            Log.e(Constants.DEBUG_TAG, e.toString())
        }

    }

    private fun closePreviewSession() {
        captureSession?.close()
        captureSession = null
    }

    /**
     * 停止录制
     */
    private fun stopRecordingVideo() {
        isRecordingVideo = false
        btn_record_video.setButtonText("录制")
        video_record_progress_view.setIsStart(false)
        try {
            mediaRecorder?.apply {
                setOnErrorListener(null)
                setOnInfoListener(null)
                setPreviewDisplay(null)
                stop()
                reset()
            }
        } catch (e: Exception) {
            Log.e(Constants.DEBUG_TAG, e.toString())
        }

        if (currentTime < MIN_VIDEO_RECORD_TIME) {
            SnackbarUtil.ShortSnackbar(
                texture,
                "录制时间过短",
                SnackbarUtil.ALERT
            ).show()
            val tempFile = File(SdCardUtil.DEFAULT_RECORD_PATH, nextVideoAbsolutePath)
            if (tempFile.exists()) tempFile.delete()
        } else {
            val msg = Message()
            msg.what = RECORD_VIDEO_OK
            msg.obj = nextVideoAbsolutePath
            backgroundHandler?.sendMessage(msg)
            nextVideoAbsolutePath = null
        }
//        startPreview()
        currentTime = 0
        resetCamera()
    }

    /**
     * 重新设置camera
     */
    private fun resetCamera() {
        cameraDevice?.close()
        cameraDevice = null
        openCamera(texture.width, texture.height)
        video_record_progress_view.visibility = View.GONE
        video_record_progress_view.reset()
    }


    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
        it.width == it.height * 4 / 3 && it.width <= 1080
    } ?: choices[choices.size - 1]

    /**
     * Given [choices] of [Size]s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal [Size], or an arbitrary one if none were big enough
     */
    private fun chooseOptimalSize(
        choices: Array<Size>,
        width: Int,
        height: Int,
        aspectRatio: Size
    ): Size {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val w = aspectRatio.width
        val h = aspectRatio.height
        val bigEnough = choices.filter {
            it.height == it.width * h / w && it.width >= width && it.height >= height
        }

        // Pick the smallest of those, assuming we found any
        return if (bigEnough.isNotEmpty()) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else {
            choices[0]
        }
    }


    fun changeZoom(event: MotionEvent) {
        try {
            //活动区域宽度和作物区域宽度之比和活动区域高度和作物区域高度之比的最大比率
            val maxZoom =
                characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) * 10
            val m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

            val action = event.action
            val current_finger_spacing: Float
            //判断当前屏幕的手指数
            if (event.pointerCount > 1) {
                //计算两个触摸点的距离
                current_finger_spacing = getFingerSpacing(event)

                if (finger_spacing != 0f) {
                    if (current_finger_spacing > finger_spacing && maxZoom > zoom_level) {
                        zoom_level++

                    } else if (current_finger_spacing < finger_spacing && zoom_level > 1) {
                        zoom_level--
                    }

                    val minW = (m!!.width() / maxZoom).toInt()
                    val minH = (m.height() / maxZoom).toInt()
                    val difW = m.width() - minW
                    val difH = m.height() - minH
                    var cropW = difW / 100 * zoom_level
                    var cropH = difH / 100 * zoom_level
                    cropW -= cropW and 3
                    cropH -= cropH and 3
                    zoom = Rect(cropW, cropH, m.width() - cropW, m.height() - cropH)
                    previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)
                }
                finger_spacing = current_finger_spacing
            } else {
                if (action == MotionEvent.ACTION_UP) {
                    //single touch logic,可做点击聚焦操作
                }
            }

            try {
                captureSession!!.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                        }
                    },
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } catch (e: Exception) {
            throw RuntimeException("can not access camera.", e)
        }

    }

    //计算两个触摸点的距离
    private fun getFingerSpacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

}
