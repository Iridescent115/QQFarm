package com.example.qqfarm

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
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

    fun find(screenshot: Bitmap, template: Bitmap, threshold: Double = DEFAULT_THRESHOLD): MatchResult {
        val sourceMat = Mat()
        val templateMat = Mat()
        val resultMat = Mat()
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

    fun findAll(
        screenshot: Bitmap,
        template: Bitmap,
        threshold: Double = DEFAULT_THRESHOLD,
        maxResults: Int = 20
    ): List<MatchResult> {
        val sourceMat = Mat()
        val templateMat = Mat()
        val resultMat = Mat()
        val softScreenshot = screenshot.copy(Bitmap.Config.ARGB_8888, false)
        val softTemplate = template.copy(Bitmap.Config.ARGB_8888, false)

        Utils.bitmapToMat(softScreenshot, sourceMat)
        Utils.bitmapToMat(softTemplate, templateMat)
        Imgproc.matchTemplate(sourceMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

        val matches = mutableListOf<MatchResult>()
        while (matches.size < maxResults) {
            val mmr = Core.minMaxLoc(resultMat)
            if (mmr.maxVal < threshold) break

            val centerX = (mmr.maxLoc.x + templateMat.cols() / 2).toInt()
            val centerY = (mmr.maxLoc.y + templateMat.rows() / 2).toInt()
            matches += MatchResult(
                confidence = mmr.maxVal,
                centerX = centerX,
                centerY = centerY,
                found = true
            )

            val halfWidth = templateMat.cols() / 2
            val halfHeight = templateMat.rows() / 2
            val left = (mmr.maxLoc.x - halfWidth).coerceAtLeast(0.0)
            val top = (mmr.maxLoc.y - halfHeight).coerceAtLeast(0.0)
            val right = (mmr.maxLoc.x + halfWidth).coerceAtMost((resultMat.cols() - 1).toDouble())
            val bottom = (mmr.maxLoc.y + halfHeight).coerceAtMost((resultMat.rows() - 1).toDouble())
            Imgproc.rectangle(
                resultMat,
                Point(left, top),
                Point(right, bottom),
                Scalar(0.0),
                -1
            )
        }

        sourceMat.release()
        templateMat.release()
        resultMat.release()

        return matches
    }
}
