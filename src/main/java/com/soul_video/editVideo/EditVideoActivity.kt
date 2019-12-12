package com.soul_video.editVideo

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.AsyncTask
import android.os.Build
import android.view.View
import android.view.WindowManager
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.kotlin_baselib.api.Constants
import com.kotlin_baselib.base.BaseViewModelActivity
import com.kotlin_baselib.base.EmptyViewModel
import com.kotlin_baselib.media.decoder.AudioDecoder
import com.kotlin_baselib.media.decoder.BaseDecoder
import com.kotlin_baselib.media.decoder.IDecoderStateListener
import com.kotlin_baselib.media.decoder.VideoDecoder
import com.soul_video.R
import kotlinx.android.synthetic.main.activity_edit_video.*
import java.util.concurrent.Executors

/**
 *  Created by CHEN on 2019/8/01
 *  Email:1181785848@qq.com
 *  Package:com.soul_video.editVideo
 *  Introduce:编辑视频
 **/
@Route(path = Constants.EDIT_VIDEO_ACTIVITY_PATH)
class EditVideoActivity : BaseViewModelActivity<EmptyViewModel>() {

    override fun providerVMClass(): Class<EmptyViewModel>? = EmptyViewModel::class.java

    override fun getResId(): Int = R.layout.activity_edit_video

    @JvmField
    @Autowired(name = "videoPath")
    var videoPath: String? = null

    private lateinit var extractVideoFrameTask: ExtractVideoFrameTask

    lateinit var videoDecoder: VideoDecoder
    lateinit var audioDecoder: AudioDecoder

    val threadPool = Executors.newFixedThreadPool(10)


    override fun preSetContentView() {
        super.preSetContentView()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
    }

    override fun initData() {
//        videoPath = "/storage/emulated/0/test.mp4"
        if (intent.getStringExtra("keyVideo") != null) {
            videoPath = intent.getStringExtra("keyVideo")
        }
        initPlayer()

        extractVideoFrameTask = ExtractVideoFrameTask(this, videoPath!!)
        extractVideoFrameTask.execute()
        extractVideoFrameTask.setOnCompletedListener(object : ExtractVideoFrameTask.OnCallBack {
            override fun completed(bitmap: Bitmap) {
                image_frame_bar.background = BitmapDrawable(bitmap)
            }
        })
    }

    override fun initListener() {

        btn_seek_to.setOnClickListener {
            videoDecoder.apply {
                seekAndPlay(0)
            }
            audioDecoder.apply {
                seekAndPlay(0)
            }

        }
        btn_pause.setOnClickListener {
            videoDecoder.pause()
            audioDecoder.pause()
            edit_video_iv_play.visibility = View.VISIBLE
        }
        btn_start.setOnClickListener {
            videoDecoder.goOnDecode()
            audioDecoder.goOnDecode()
            edit_video_iv_play.visibility = View.GONE
        }
        edit_video_iv_play.setOnClickListener {
            videoDecoder.goOnDecode()
            audioDecoder.goOnDecode()
            edit_video_iv_play.visibility = View.GONE
        }
    }



    private fun initPlayer() {

        videoDecoder = VideoDecoder(videoPath!!, edit_video_sfv, null)
        threadPool.execute(videoDecoder)

        audioDecoder = AudioDecoder(videoPath!!)
        threadPool.execute(audioDecoder)


    }

    override fun onDestroy() {
        videoDecoder.stop()
        audioDecoder.stop()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        videoDecoder.pause()
        audioDecoder.pause()
    }

}
