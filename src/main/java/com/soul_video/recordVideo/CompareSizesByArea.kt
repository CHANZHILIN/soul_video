package com.soul_video.recordVideo

import android.annotation.SuppressLint
import android.util.Size
import java.lang.Long.signum

/**
 *  Created by CHEN on 2019/7/25
 *  Email:1181785848@qq.com
 *  Package:com.soul_video.recordVideo
 *  Introduce:
 **/
class CompareSizesByArea : Comparator<Size> {

    // We cast here to ensure the multiplications won't overflow
    @SuppressLint("NewApi")
    override fun compare(lhs: Size, rhs: Size) =
            signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
}