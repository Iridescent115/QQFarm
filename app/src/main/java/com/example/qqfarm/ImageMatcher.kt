package com.example.qqfarm

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

data class MatchResult(
    val confidence: Double,
    val centerX: Int,
    val centerY: Int,
    val found: Boolean
)

object ImageMatcher {

    private const val DEFAULT_THRESHOLD = 0.8

    init {
        OpenCVLoader.initLocal()
    }

    /**
     * 在截图中查找模板图片
     * @param screenshot 当前屏幕截图
     * @param template   要查找的模板图片
     * @param threshold  置信度阈值，超过此值才算找到（默认 0.8）
     */
    fun find(screenshot: Bitmap, template: Bitmap, threshold: Double = DEFAULT_THRESHOLD): MatchResult {
        val sourceMat = Mat()
        val templateMat = Mat()
        val resultMat = Mat()

        // 硬件加速的 Bitmap 需先转为软件 Bitmap，OpenCV 才能处理
        val softScreenshot = screenshot.copy(Bitmap.Config.ARGB_8888, false)
        val softTemplate = template.copy(Bitmap.Config.ARGB_8888, false)

        Utils.bitmapToMat(softScreenshot, sourceMat)
        Utils.bitmapToMat(softTemplate, templateMat)

        Imgproc.matchTemplate(sourceMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

        val mmr = Core.minMaxLoc(resultMat)
        val confidence = mmr.maxVal
        val centerX = (mmr.maxLoc.x + templateMat.cols() / 2).toInt()
        val centerY = (mmr.maxLoc.y + templateMat.rows() / 2).toInt()

        sourceMat.release()
        templateMat.release()
        resultMat.release()

        return MatchResult(
            confidence = confidence,
            centerX = centerX,
            centerY = centerY,
            found = confidence >= threshold
        )
    }
}
