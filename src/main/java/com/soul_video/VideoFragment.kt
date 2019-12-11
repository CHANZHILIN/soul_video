package com.soul_video

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.kotlin_baselib.api.Constants
import com.kotlin_baselib.base.BaseViewModelFragment
import com.kotlin_baselib.base.EmptyViewModel
import com.kotlin_baselib.glide.GlideApp
import com.kotlin_baselib.recyclerview.decoration.StaggeredDividerItemDecoration
import com.kotlin_baselib.recyclerview.setSingleUp
import com.kotlin_baselib.utils.SdCardUtil
import com.kotlin_baselib.utils.SnackbarUtil
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
        val videoSrc = SdCardUtil.DEFAULT_VIDEO_PATH;
        val fileData = SdCardUtil.getFilesAllName(videoSrc)

        val pictureData = ArrayList<VideoEntity>()
        for (fileDatum in fileData) {   //封装实体类，加入随机高度，解决滑动过程中位置变换的问题
            pictureData.add(VideoEntity(fileDatum, (200 + Math.random() * 400).toInt()))
        }

        fragment_video_recyclerview.setSingleUp(
            pictureData,
            R.layout.layout_item_video,
            StaggeredGridLayoutManager(Constants.SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL),
            { holder, item ->
                val width =
                    (holder.itemView.item_video_iv_image.getContext() as Activity).windowManager.defaultDisplay.width //获取屏幕宽度
                val params = holder.itemView.item_video_iv_image.getLayoutParams()
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
            {
                SnackbarUtil.ShortSnackbar(
                    fragment_video_recyclerview,
                    "点击${it.path}！",
                    SnackbarUtil.CONFIRM
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
    }

    override fun initListener() {
        /*record_video.setOnClickListener {
            ARouter.getInstance().build(Constants.RECORD_VIDEO_ACTIVITY_PATH).navigation()
        }*/
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
