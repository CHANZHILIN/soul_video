package com.soul_video.editVideo

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.kotlin_baselib.api.Constants
import com.kotlin_baselib.base.BaseActivity
import com.kotlin_baselib.base.EmptyModelImpl
import com.kotlin_baselib.base.EmptyPresenterImpl
import com.kotlin_baselib.base.EmptyView
import com.soul_video.R
import kotlinx.android.synthetic.main.activity_edit_video.*

/**
 *  Created by CHEN on 2019/8/01
 *  Email:1181785848@qq.com
 *  Package:com.soul_video.editVideo
 *  Introduce:编辑视频
 **/
@Route(path = Constants.EDIT_VIDEO_ACTIVITY_PATH)
class EditVideoActivity : BaseActivity<EmptyView, EmptyModelImpl, EmptyPresenterImpl>(), EmptyView {

    @JvmField
    @Autowired(name = "videoPath")
    var videoPath: String? = null

    private lateinit var extractVideoFrameTask: ExtractVideoFrameTask

    override fun createPresenter(): EmptyPresenterImpl = EmptyPresenterImpl(this)

    override fun getResId(): Int = R.layout.activity_edit_video

    override fun initData() {
        setTitle("编辑视频")
        ARouter.getInstance().inject(this)
        extractVideoFrameTask = ExtractVideoFrameTask(this,videoPath!!)
        extractVideoFrameTask.execute()
        extractVideoFrameTask.setOnCompletedListener(object : ExtractVideoFrameTask.OnCallBack {
            override fun completed(bitmap: Bitmap) {
                image_frame_bar.background = BitmapDrawable(bitmap)
            }
        })
    }

    override fun initListener() {
    }

}
