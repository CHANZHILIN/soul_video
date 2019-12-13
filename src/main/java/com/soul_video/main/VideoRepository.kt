package com.soul_video.main

import com.kotlin_baselib.base.BaseRepository
import com.kotlin_baselib.utils.SdCardUtil
import com.soul_video.entity.VideoEntity

/**
 *  Created by CHEN on 2019/12/13
 *  Email:1181785848@qq.com
 *  Introduce:
 **/
class VideoRepository : BaseRepository() {
/*    suspend fun getPictureData():ResponseData<EmptyEntity> = request {
        ApiEngine.apiService.getVersionData()
    }*/

    suspend fun getVideoData(): MutableList<VideoEntity> = requestLocal {
        val fileData = SdCardUtil.getFilesAllName(SdCardUtil.DEFAULT_VIDEO_PATH)
        val videoData = ArrayList<VideoEntity>()
        for (fileDatum in fileData) {   //封装实体类，加入随机高度，解决滑动过程中位置变换的问题
            videoData.add(VideoEntity(fileDatum, (200 + Math.random() * 400).toInt()))
        }
        videoData
    }
}