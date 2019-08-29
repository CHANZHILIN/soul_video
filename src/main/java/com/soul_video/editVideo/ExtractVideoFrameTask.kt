package com.soul_video.editVideo

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.AsyncTask
import com.kotlin_baselib.utils.BitmapUtil
import java.util.*

/**
 *  Created by CHEN on 2019/8/7
 *  Email:1181785848@qq.com
 *  Package:com.soul_video.editVideo
 *  Introduce: 提取视频帧
 **/
class ExtractVideoFrameTask(context: Context, path: String) : AsyncTask<Void, Void, Bitmap>() {

    private var keyFrameList: ArrayList<Int>? = null
    private var videoPath: String = path
    private var mContext: Context = context

    private var progressDialog: AlertDialog? = null


    override fun onPreExecute() {
        super.onPreExecute()
        if (progressDialog == null) {
            progressDialog = AlertDialog.Builder(mContext).setMessage("正在加载").create()
            progressDialog!!.show()
        } else {
            progressDialog!!.show()
        }
    }

    override fun doInBackground(vararg params: Void?): Bitmap {
        return BitmapUtil.addHBitmap(addFrames(videoPath))!!
    }

    override fun onPostExecute(result: Bitmap) {
        super.onPostExecute(result)
        progressDialog!!.dismiss()
        progressDialog!!.cancel()
        progressDialog == null
        onCallBack?.completed(result)
    }


    /**
     * 获取视频帧
     * @param path 视频路径
     */
    private fun addFrames(path: String): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        // 取得视频的长度(单位为毫秒)
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val duration = Integer.parseInt(time)
        val totalFrames = duration / 1000   //总帧数
        keyFrameList = ArrayList<Int>()
        var interval = 0

        for (i in 0 until totalFrames) {
            val frameTime = Integer.valueOf(interval) / 1000
            keyFrameList?.add(frameTime)
            interval += duration / totalFrames
        }
        val bits = ArrayList<Bitmap>()
        for (i in keyFrameList!!.indices) {
            val bitmap = retriever.getFrameAtTime((keyFrameList!!.get(i) * 1000 * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) // 一秒一帧
            if (bitmap != null) {
                val bmpWidth = bitmap.width
                val bmpHeight = bitmap.height
                val scale = 0.7f
                /* 产生reSize后的Bitmap对象 */
                val matrix = Matrix()
                matrix.postScale(scale, scale)
                val resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bmpWidth,
                        bmpHeight, matrix, true)
                bits.add(resizeBmp)
            }
        }
        return bits
    }

    private var onCallBack: OnCallBack? = null

    fun setOnCompletedListener(callback: OnCallBack) {
        onCallBack = callback
    }

    interface OnCallBack {
        fun completed(bitmap: Bitmap)
    }
}