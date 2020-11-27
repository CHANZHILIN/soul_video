package com.soul_video.main

import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.kotlin_baselib.api.Constants
import com.kotlin_baselib.base.BaseViewModelFragment
import com.kotlin_baselib.glide.GlideApp
import com.kotlin_baselib.recyclerview.SingleAdapter
import com.kotlin_baselib.recyclerview.decoration.StaggeredDividerItemDecoration
import com.kotlin_baselib.recyclerview.setSingleItemUp
import com.kotlin_baselib.utils.ScreenUtils
import com.kotlin_baselib.utils.SnackBarUtil
import com.soul_video.R
import com.soul_video.editVideo.EditVideoActivity
import com.soul_video.entity.VideoEntity
import kotlinx.android.synthetic.main.fragment_video.*
import kotlinx.android.synthetic.main.layout_item_video.view.*


private const val ARG_PARAM1 = "param1"

/**
 *  Created by CHEN on 2019/6/20
 *  Email:1181785848@qq.com
 *  Package:com.soul_video
 *  Introduce:视频Fragment
 **/
class VideoFragment : BaseViewModelFragment<VideoViewModel>() {

    override fun providerVMClass(): Class<VideoViewModel>? = VideoViewModel::class.java

    override fun getResId(): Int {
        return R.layout.fragment_video
    }

    private lateinit var fileData: MutableList<String>
    private lateinit var videoData: MutableList<VideoEntity>
    private var videoAdapter: SingleAdapter<VideoEntity>? = null
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
//        fileData = SdCardUtil.getFilesAllName(SdCardUtil.DEFAULT_VIDEO_PATH)

        videoData = ArrayList<VideoEntity>()
//        for (fileDatum in fileData) {   //封装实体类，加入随机高度，解决滑动过程中位置变换的问题
//            videoData.add(VideoEntity(fileDatum, (200 + Math.random() * 400).toInt()))
//        }

        videoAdapter = fragment_video_recyclerview.setSingleItemUp(
            videoData,
            R.layout.layout_item_video,
            {  _,holder, item ->
                val width = ScreenUtils.screenWidth //获取屏幕宽度
                val params = holder.itemView.item_video_iv_image.layoutParams
                //设置图片的相对于屏幕的宽高比
                params.width =
                    (width - (Constants.SPAN_COUNT + 1) * Constants.ITEM_SPACE) / Constants.SPAN_COUNT
                params.height = item.randomHeight

                GlideApp.with(mContext)
                    .load(
                        getVideoThumbnail(
                            item.path,
                            params.width,
                            params.height,
                            MediaStore.Images.Thumbnails.FULL_SCREEN_KIND
                        )
                    )
                    .into(holder.itemView.item_video_iv_image)

                holder.itemView.item_video_iv_image.setOnClickListener {
                    val i = Intent(mContext, EditVideoActivity::class.java)
                    val videoPair = androidx.core.util.Pair<View, String>(
                        holder.itemView.item_video_iv_image,
                        "video"
                    )
                    val playPair = androidx.core.util.Pair<View, String>(
                        holder.itemView.item_video_iv_play,
                        "play"
                    )
                    val optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        mContext,
                        videoPair,
                        playPair
                    )
                    i.putExtra("keyVideo", item.path)
                    startActivity(i, optionsCompat.toBundle())
                }
            },
            StaggeredGridLayoutManager(Constants.SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL),
            {position, it ->
                SnackBarUtil.shortSnackBar(
                    fragment_video_recyclerview,
                    "点击${it.path}！",
                    SnackBarUtil.CONFIRM
                )
                    .show()
            }
        )
        fragment_video_recyclerview.addItemDecoration(
            StaggeredDividerItemDecoration(
                mContext,
                Constants.ITEM_SPACE
            )
        )
        viewModel.getVideoListData().observe(this, Observer {
            it?.run {
                videoAdapter?.replaceData(this)
            }
        })

    }

    override fun initListener() {
        /*record_video.setOnClickListener {
            ARouter.getInstance().build(Constants.RECORD_VIDEO_ACTIVITY_PATH).navigation()
        }*/
        fragment_video_refresh_layout.setOnRefreshListener {

            if (videoData.size >= 0) {
                videoData.clear()
            }
            val videoViewModel = VideoViewModel()
            videoViewModel.getVideoListData().observe(this, Observer {
                it?.run {
                    videoAdapter?.replaceData(this)
                    fragment_video_refresh_layout.isRefreshing = false
                    lifecycle.removeObserver(videoViewModel)
                }
            })

        }
    }

    /**
     * 获取视频的缩略图
     */
    fun getVideoThumbnail(videoPath: String, width: Int, height: Int, kind: Int): Bitmap {
        var bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind)
        if (bitmap != null)
            bitmap = ThumbnailUtils.extractThumbnail(
                bitmap,
                width,
                height,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT
            )
        return bitmap
    }

}
