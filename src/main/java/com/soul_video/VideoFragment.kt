package com.soul_video

import android.os.Bundle
import com.kotlin_baselib.mvvmbase.BaseViewModelFragment
import com.kotlin_baselib.mvvmbase.EmptyViewModel
import kotlinx.android.synthetic.main.fragment_video.*


private const val ARG_PARAM1 = "param1"

/**
 *  Created by CHEN on 2019/6/20
 *  Email:1181785848@qq.com
 *  Package:com.soul_video
 *  Introduce:视频Fragment
 **/
class VideoFragment : BaseViewModelFragment<EmptyViewModel>() {

    override fun providerVMClass(): Class<EmptyViewModel>? = EmptyViewModel::class.java

    override fun getResId(): Int {
        return R.layout.fragment_video
    }

    private var param1: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
        }
    }


    companion object {
        @JvmStatic
        fun newInstance(param1: String) =
            VideoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                }
            }
    }


    override fun initData() {
        fragment_text.setText(param1)
    }

    override fun initListener() {
        /*record_video.setOnClickListener {
            ARouter.getInstance().build(Constants.RECORD_VIDEO_ACTIVITY_PATH).navigation()
        }*/
    }
}
