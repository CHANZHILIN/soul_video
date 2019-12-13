package com.soul_video.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kotlin_baselib.base.BaseViewModel
import com.soul_video.entity.VideoEntity

/**
 *  Created by CHEN on 2019/12/13
 *  Email:1181785848@qq.com
 *  Introduce:
 **/
class VideoViewModel : BaseViewModel() {
    private val data: MutableLiveData<MutableList<VideoEntity>> by lazy {
        MutableLiveData<MutableList<VideoEntity>>().also {
            loadDatas()
        }
    }


    private val repository = VideoRepository()

    fun getVideoListData(): LiveData<MutableList<VideoEntity>> {
        return data
    }

    private fun loadDatas() = launchUI {
        val result = repository.getVideoData()
        data.value = result
    }

}