package com.soul_video.recordVideo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager

import com.soul_video.R

/**
 * Created by cxk on 2017/12/8.
 *
 *
 * 长按录制时候的倒数进度条
 */

class ProgressView @JvmOverloads constructor(private val mContext: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(mContext, attrs, defStyleAttr) {
    //constant
    private val millisecond = 1000//每一秒
    private val maxProgressSize =10f * millisecond//总进度是10s
    private var eachProgressWidth = 0f//每一格的宽度
    private var mWindowManager: WindowManager? = null
    private var progressPaint: Paint? = null

    private var initTime: Long = -1L//上一次刷新完成后的时间
    private var isStart = false
    private var countWidth = 0f//进度条进度的进程，每次调用invalidate（）都刷新一次

    init {
        init()
    }

    private fun init() {
        //设置每一刻度的宽度
        val dm = DisplayMetrics()
        mWindowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mWindowManager!!.defaultDisplay.getMetrics(dm)
        eachProgressWidth = dm.widthPixels / (maxProgressSize * 1.0f)
        //进度条的背景颜色
        setBackgroundColor(resources.getColor(R.color.transparent))
        //进度条的前景颜色,画笔
        progressPaint = Paint()
        progressPaint!!.style = Paint.Style.FILL
        progressPaint!!.color = resources.getColor(R.color.colorPrimary)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isStart) {
            canvas.drawRect(0f, 0f, countWidth, measuredHeight.toFloat(), progressPaint!!)
            return
        }
        if (initTime == -1L) {
            initTime = System.currentTimeMillis()
            canvas.drawRect(0f, 0f, countWidth, measuredHeight.toFloat(), progressPaint!!)
            invalidate()
            return
        }
        //这次刷新的时间，用于与上一次刷新完成的时间作差得出进度条需要增加的进度
        val thisTime = System.currentTimeMillis()
        countWidth += eachProgressWidth * (thisTime - initTime).toFloat() * 1.0f
        if (countWidth > measuredWidth) {
            countWidth = measuredWidth.toFloat()
        }
        canvas.drawRect(0f, 0f, countWidth, measuredHeight.toFloat(), progressPaint!!)

        //如果都了最大长度，就不再调用invalidate();了
        if (countWidth < measuredWidth && isStart) {
            initTime = System.currentTimeMillis()
            invalidate()
        } else {
            countWidth = 0f
            initTime = -1
            isStart = false
        }

    }

    //开始或暂停进度条进度刷新
    fun setIsStart(isStart: Boolean) {
        if (isStart == this.isStart)
            return
        this.isStart = isStart
        if (isStart) {
            initTime = -1
            invalidate()
        }
    }

    //重置进度条
    fun reset() {
        countWidth = 0f
        initTime = -1
        isStart = false
        invalidate()
    }

    //设置每一个像素的宽度
    fun setEachProgressWidth(width: Int) {
        eachProgressWidth = width / (maxProgressSize * 1.0f)
    }
}
